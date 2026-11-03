package com.example.core.intelligence.conversation

import com.example.core.intelligence.knowledge.context.KnowledgeAvailabilityStatus
import com.example.core.intelligence.knowledge.context.PreparedTurnKnowledgeContext
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository
import com.example.device.info.DeviceInfoProvider
import java.util.Locale

/**
 * High-level conversational reasoning engine.
 * Handles semantic intent classification, multi-turn resolution, empathetic response crafting,
 * dialect understanding, and genuine on-device decision-making without misleading canned templates.
 */
class ConversationalReasoner(
  private val deviceInfoProvider: DeviceInfoProvider? = null,
  private val timeProvider: com.example.core.intelligence.time.TimeProvider = com.example.core.intelligence.time.SystemTimeProvider(),
  val knowledgeRepository: KnowledgeRepository = KnowledgeRepository.getDefaultInstance()
) {
  private val batteryForecastCalculator = com.example.core.intelligence.battery.BatteryForecastCalculator(timeProvider)

  @Volatile
  var lastPreparedKnowledgeContext: PreparedTurnKnowledgeContext? = null
    private set

  data class ReasoningResult(
    val replyText: String,
    val topicDetected: String?,
    val proactiveSuggestions: List<ActionableOption>,
    val preparedKnowledgeContext: PreparedTurnKnowledgeContext? = null
  )

  fun reason(
    userQuery: String,
    session: ConversationSession,
    preparedContext: PreparedTurnKnowledgeContext? = null
  ): ReasoningResult {
    val hasArabicChars = userQuery.any { it in '\u0600'..'\u06FF' }
    val isArabic = preparedContext?.isArabic ?: (hasArabicChars || Locale.getDefault().language.startsWith("ar"))
    val query = userQuery.trim()

    // 1. Math and Calculation Check
    val mathResult = LocalKnowledgeBase.evaluateArithmetic(query)
    if (mathResult != null) {
      session.updateTopic("calculation")
      return ReasoningResult(
        replyText = mathResult,
        topicDetected = "calculation",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "عملية أخرى" else "Another calculation", "احسب 50 * 12"),
          ActionableOption(if (isArabic) "حالة الذاكرة" else "Memory status", "الذاكرة")
        ),
        preparedKnowledgeContext = preparedContext
      )
    }

    // Prepare turn-scoped knowledge context once per turn (zero duplicate searches or normalizations)
    val turnContext = preparedContext ?: knowledgeRepository.prepareTurnKnowledgeContext(
      userQuery = query,
      currentTopicId = session.currentTopic,
      isArabicOverride = isArabic
    )
    lastPreparedKnowledgeContext = turnContext
    val normalized = turnContext.relaxedNormalizedQuery

    // 1.5 Explicit Return to an Earlier Topic in Session History (e.g., "نعود إلى موضوع الفيزياء")
    val explicitReturnTopicId = session.resolveExplicitTopicReturn(query, turnContext)
    if (explicitReturnTopicId != null) {
      session.updateTopic(explicitReturnTopicId)
      val kbTopic = turnContext.getTopicById(explicitReturnTopicId)
        ?: knowledgeRepository.getTopicById(explicitReturnTopicId)
      if (kbTopic != null) {
        val followUpElab = LocalKnowledgeBase.getTopicFollowUpElaboration(explicitReturnTopicId, normalized, isArabic)
          ?: knowledgeRepository.resolveDynamicFollowUpElaboration(
            topicId = explicitReturnTopicId,
            query = normalized,
            isArabic = isArabic,
            snapshotOverride = turnContext.snapshotUsed
          )
        val baseText = if (followUpElab != null && !session.hasDeliveredElaboration(explicitReturnTopicId, followUpElab.first)) {
          session.markElaborationDelivered(explicitReturnTopicId, followUpElab.first)
          followUpElab.first
        } else {
          if (isArabic) kbTopic.arabicSummary else kbTopic.englishSummary
        }
        val prefix = if (isArabic) "بالعودة إلى موضوعنا السابق: " else "Returning to our earlier topic: "
        return ReasoningResult(
          replyText = prefix + baseText,
          topicDetected = kbTopic.id,
          proactiveSuggestions = kbTopic.suggestedFollowUps,
          preparedKnowledgeContext = turnContext
        )
      }
    }

    // 2. Multi-turn Follow-up Resolution (evaluated BEFORE updating currentTopic to a new topic)
    if (isFollowUpQuery(normalized, session, turnContext)) {
      val resolvedFollowUp = resolveFollowUp(normalized, session, isArabic, turnContext)
      if (resolvedFollowUp != null) {
        session.updateTopic(resolvedFollowUp.topicDetected)
        return resolvedFollowUp.copy(preparedKnowledgeContext = turnContext)
      }
    }

    // 3. User Identity Check: "من انا؟"
    if (isUserIdentityQuery(normalized)) {
      val identityText = deviceInfoProvider?.getUserIdentityDescription(isArabic)
        ?: if (isArabic) {
          "أنت مستخدم هذا الجهاز. لم يتم تسجيل اسم شخصي أو ملف تعريف خاص بك في WEYKY بعد، وذلك حفاظاً على خصوصيتك."
        } else {
          "You are the user of this device. No personal identity is currently registered in WEYKY, respecting your privacy."
        }
      session.updateTopic("identity")
      return ReasoningResult(
        replyText = identityText,
        topicDetected = "identity",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "ما هو هاتفي؟" else "Phone model?", "ما هو هاتفي؟"),
          ActionableOption(if (isArabic) "ماذا تعرف عن جهازي؟" else "About my phone?", "ماذا تعرف عن هاتفي؟")
        ),
        preparedKnowledgeContext = turnContext
      )
    }

    // 4. Phone Model & Detailed Specs Check
    if (isPhoneModelQuery(normalized)) {
      val modelSummary = deviceInfoProvider?.getPhoneModelSummary(isArabic)
        ?: if (isArabic) {
          "هاتفك يعمل بنظام Android."
        } else {
          "Your phone is running Android."
        }
      session.updateTopic("device_info", "phone")
      return ReasoningResult(
        replyText = modelSummary,
        topicDetected = "device_info",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "وماذا تعرف عنه؟" else "What else?", "وماذا تعرف عنه؟"),
          ActionableOption(if (isArabic) "البطارية" else "Battery", "كم نسبة البطارية؟")
        ),
        preparedKnowledgeContext = turnContext
      )
    }

    if (isPhoneDetailedSpecsQuery(normalized)) {
      val detailedSpecs = deviceInfoProvider?.getDetailedTechnicalSpecs(isArabic)
        ?: if (isArabic) {
          "مواصفات الجهاز غير متاحة حالياً."
        } else {
          "Device specifications are currently unavailable from system APIs."
        }
      session.updateTopic("device_info", "phone")
      return ReasoningResult(
        replyText = detailedSpecs,
        topicDetected = "device_info",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "البطارية" else "Battery", "البطارية"),
          ActionableOption(if (isArabic) "الذاكرة" else "RAM usage", "الذاكرة")
        ),
        preparedKnowledgeContext = turnContext
      )
    }

    // 5. Greetings & Chitchat
    val greetingReply = handleGreetingsAndChitchat(normalized, isArabic)
    if (greetingReply != null) {
      return greetingReply.copy(preparedKnowledgeContext = turnContext)
    }

    // 6. Emotional Empathy & Mood Support
    val empathyReply = handleEmotionalSupport(normalized, isArabic)
    if (empathyReply != null) {
      return empathyReply.copy(preparedKnowledgeContext = turnContext)
    }

    // 7. Direct Curated Knowledge Base Match via PreparedTurnKnowledgeContext
    val kbTopic = turnContext.retrievedTopic
    val matchedResponseText = turnContext.resolveMatchedResponseText()
    if (kbTopic != null &&
      turnContext.availabilityStatus == KnowledgeAvailabilityStatus.MATCHED_USABLE_KNOWLEDGE &&
      !matchedResponseText.isNullOrBlank()
    ) {
      session.updateTopic(kbTopic.id, kbTopic.id)
      return ReasoningResult(
        replyText = matchedResponseText,
        topicDetected = kbTopic.id,
        proactiveSuggestions = kbTopic.suggestedFollowUps,
        preparedKnowledgeContext = turnContext
      )
    }

    // 8. Principled Decision & Honest Fallback without Canned Deception
    return generatePrincipledResponse(query, normalized, isArabic).copy(
      preparedKnowledgeContext = turnContext
    )
  }

  private fun isUserIdentityQuery(query: String): Boolean {
    return query == "من انا" || query == "من اكون" || query == "مين انا" || query == "هل تعرفني" ||
      query == "ما اسمي" || query == "شنو اسمي" || query == "شو اسمي" || query == "من المستخدم" ||
      query == "who am i" || query == "do you know me" || query == "what is my name"
  }

  private fun isPhoneModelQuery(query: String): Boolean {
    return query.contains("ما هو هاتفي") || query.contains("ما هاتفي") || query.contains("نوع هاتفي") ||
      query.contains("موديل هاتفي") || query.contains("طراز هاتفي") || query.contains("ما نوع الجهاز") ||
      query.contains("what is my phone") || query.contains("phone model")
  }

  private fun isPhoneDetailedSpecsQuery(query: String): Boolean {
    return query.contains("ماذا تعرف عن هاتفي") || query.contains("ماذا تعرف عن جهازي") ||
      query.contains("مواصفات هاتفي") || query.contains("معلومات هاتفي") || query.contains("تفاصيل الجهاز") ||
      query.contains("what do you know about my phone") || query.contains("device specs")
  }

  private fun isSameTopicFamily(topicA: String?, topicB: String?): Boolean {
    if (topicA == null || topicB == null) return false
    if (topicA == topicB) return true
    val travelGroup = setOf("travel", "salalah_tourism")
    if (topicA in travelGroup && topicB in travelGroup) return true
    val sleepGroup = setOf("sleep_wellness", "health_wellness")
    if (topicA in sleepGroup && topicB in sleepGroup) return true
    return false
  }

  private fun isFollowUpQuery(
    query: String,
    session: ConversationSession,
    turnContext: PreparedTurnKnowledgeContext
  ): Boolean {
    if (session.lastBatteryScenario != null && (query.contains("ملاحه") || query.contains("خرائط") || query.contains("navigation") || query.contains("gps"))) {
      return true
    }
    if (session.currentTopic == "device_info" || session.lastReferencedSubject == "phone") {
      if (query.contains("عنه") || query.contains("مواصفاته") || query.contains("تفاصيله") || query.contains("about it")) {
        return true
      }
    }

    val activeTopic = session.currentTopic ?: return false

    val hasBackReferenceMarker = query.contains("هناك") || query.contains("فيها") ||
      query.contains("عنه") || query.contains("عنها") || query.contains("ذلك") ||
      query.contains("زيارتها") || query.contains("رويتها") || query.contains("رؤيتها") || query.contains("نراها") ||
      query.contains("تتكون") || query.contains("تتشكل") ||
      query.contains("there") || query.contains("that") || query.contains("about it") || query.contains("see them") ||
      query.split(" ").contains("its")

    // Check if the user is explicitly asking about a DIFFERENT indexed knowledge topic without back-reference pronouns
    val matchedNewTopic = turnContext.unbiasedTopTopic
    if (matchedNewTopic != null && !isSameTopicFamily(activeTopic, matchedNewTopic.id) && !hasBackReferenceMarker) {
      return false
    }

    // Topic-specific sub-questions on the active topic
    if (isSameTopicFamily(activeTopic, "salalah_tourism") &&
      (query.contains("طقس") || query.contains("جو") || query.contains("مطر") || query.contains("ملابس") ||
       query.contains("متي") || query.contains("وقت") || query.contains("شهر") || query.contains("weather") || query.contains("when"))
    ) {
      return true
    }
    if (isSameTopicFamily(activeTopic, "sleep_wellness") &&
      (query.contains("قهوه") || query.contains("كافيين") || query.contains("شاي") || query.contains("coffee") || query.contains("caffeine"))
    ) {
      return true
    }
    if (activeTopic == "productivity" &&
      (query.contains("نصيحه للتركيز") || query.contains("تعدد المهام") || query.contains("اكثر") || query.contains("المزيد"))
    ) {
      return true
    }
    if (knowledgeRepository.hasMatchingFollowUpBranchTrigger(activeTopic, query, turnContext.snapshotUsed)) {
      return true
    }

    val followUpStarters = listOf(
      "وماذا", "وكيف", "ولماذا", "طيب", "وما", "هل هناك", "وهل", "المزيد", "اكثر", "اشرح", "وضح", "حدثني عن",
      "tell me more", "and how", "what about", "elaborate", "can you give more details", "explain"
    )

    return hasBackReferenceMarker || followUpStarters.any { query.startsWith(it) || query.contains(" $it") }
  }

  private fun resolveFollowUp(
    query: String,
    session: ConversationSession,
    isArabic: Boolean,
    turnContext: PreparedTurnKnowledgeContext
  ): ReasoningResult? {
    // 0. Battery scenario follow-up: "وماذا لو استخدمت الملاحة أيضًا؟"
    if (session.lastBatteryScenario != null && (query.contains("ملاحه") || query.contains("خرائط") || query.contains("navigation") || query.contains("gps"))) {
      val prevScenario = session.lastBatteryScenario!!
      val limitation = batteryForecastCalculator.reasonNavigationFollowUp(prevScenario, isArabic)
      session.lastBatteryScenario = limitation.updatedScenario
      return ReasoningResult(
        replyText = limitation.synthesis.synthesizedSpeech,
        topicDetected = "battery",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "فحص البطارية" else "Battery level", "كم نسبة البطارية؟"),
          ActionableOption(if (isArabic) "تفعيل توفير الطاقة" else "Battery saver", "تفعيل توفير الطاقة")
        )
      )
    }

    val topic = session.currentTopic ?: return null

    // Follow-up about phone: "وماذا تعرف عنه؟"
    if (topic == "device_info" || session.lastReferencedSubject == "phone") {
      if (query.contains("عنه") || query.contains("مواصفاته") || query.contains("تفاصيله") || query.contains("about it")) {
        val detailedSpecs = deviceInfoProvider?.getDetailedTechnicalSpecs(isArabic)
          ?: if (isArabic) {
            "مواصفات الهاتف: يعمل بنظام Android."
          } else {
            "Phone specs: Running Android."
          }
        return ReasoningResult(
          replyText = detailedSpecs,
          topicDetected = "device_info",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "البطارية" else "Battery", "كم نسبة البطارية؟"),
            ActionableOption(if (isArabic) "الذاكرة" else "RAM usage", "الذاكرة")
          )
        )
      }
    }

    // Contextual transition when user is in capitals_arab (e.g. Oman capital) and asks "What about its tourism?"
    if (topic == "capitals_arab" && turnContext.retrievedTopicId == "salalah_tourism") {
      val tourismTopic = turnContext.retrievedTopic ?: turnContext.getTopicById("salalah_tourism")
      if (tourismTopic != null) {
        val text = if (isArabic) tourismTopic.arabicSummary else tourismTopic.englishSummary
        if (text.isNotBlank()) {
          return ReasoningResult(
            replyText = text,
            topicDetected = tourismTopic.id,
            proactiveSuggestions = tourismTopic.suggestedFollowUps
          )
        }
      }
    }

    // Resolve follow-ups across all curated KnowledgeBase topics (including travel/salalah_tourism, sleep_wellness, physics_universe, etc.)
    // and fall back to lazy-loaded dynamic follow-up branches / detail sections for external pack topics.
    val elaboration = LocalKnowledgeBase.getTopicFollowUpElaboration(topic, query, isArabic)
      ?: knowledgeRepository.resolveDynamicFollowUpElaboration(
        topicId = topic,
        query = query,
        isArabic = isArabic,
        snapshotOverride = turnContext.snapshotUsed
      )
    if (elaboration != null) {
      val canonicalTopic = when (topic) {
        "travel" -> "salalah_tourism"
        "health_wellness" -> "sleep_wellness"
        else -> topic
      }
      val kbTopic = turnContext.getTopicById(canonicalTopic)
        ?: knowledgeRepository.getTopicById(canonicalTopic)
      val suggestions = elaboration.second.ifEmpty { kbTopic?.suggestedFollowUps ?: emptyList() }

      if (session.hasDeliveredElaboration(canonicalTopic, elaboration.first)) {
        // Avoid mechanically repeating the exact same elaboration text if it adds no new value.
        // State the boundary of locally indexed details and invite the user to pick a specific angle.
        val availableAngles = suggestions.joinToString("، ") { it.title }
        val nonRepetitiveReply = if (isArabic) {
          if (availableAngles.isNotBlank()) {
            "لقد استعرضنا بالفعل التفاصيل الإضافية المسجلة محلياً في قاعدة المعرفة حول هذا الجانب. يمكنك تحديد زاوية فرعية محددة ترغب في التركيز عليها (مثل: $availableAngles) أو الانتقال إلى موضوع آخر."
          } else {
            "لقد استعرضنا بالفعل جميع التفاصيل المسجلة محلياً في قاعدة المعرفة حول هذا الموضوع. يرجى تحديد الجانب الذي تود الاستفسار عنه أو طرح سؤال في موضوع جديد."
          }
        } else {
          if (availableAngles.isNotBlank()) {
            "We have already covered the locally indexed details for this aspect. Please specify which angle you would like to focus on (e.g., $availableAngles) or switch to another topic."
          } else {
            "We have already covered all locally indexed details for this topic. Please specify a particular aspect or ask about another topic."
          }
        }
        return ReasoningResult(
          replyText = nonRepetitiveReply,
          topicDetected = canonicalTopic,
          proactiveSuggestions = suggestions
        )
      }

      session.markElaborationDelivered(canonicalTopic, elaboration.first)
      return ReasoningResult(
        replyText = elaboration.first,
        topicDetected = canonicalTopic,
        proactiveSuggestions = suggestions
      )
    }

    return null
  }

  private fun containsStandalonePhrase(words: List<String>, phrase: String): Boolean {
    val phraseTokens = phrase.split(" ").filter { it.isNotBlank() }
    if (phraseTokens.isEmpty() || words.size < phraseTokens.size) return false
    val maxStart = words.size - phraseTokens.size
    for (i in 0..maxStart) {
      var allMatch = true
      for (j in phraseTokens.indices) {
        if (words[i + j] != phraseTokens[j]) {
          allMatch = false
          break
        }
      }
      if (allMatch) return true
    }
    return false
  }

  private fun isStandaloneGreetingQuery(words: List<String>): Boolean {
    if (words.isEmpty()) return false
    val standaloneGreetingWords = setOf(
      "مرحبا", "مرحب", "اهلا", "اهلين", "هلا", "ياهلا", "هاي",
      "hello", "hi", "hey"
    )
    val standaloneGreetingPhrases = listOf(
      "السلام عليكم", "سلام عليكم", "يا هلا", "يا مرحبا", "اهلا وسهلا", "اهلا بك"
    )
    val hasGreetingTokenOrPhrase = words.any { it in standaloneGreetingWords } ||
      standaloneGreetingPhrases.any { containsStandalonePhrase(words, it) }
    if (!hasGreetingTokenOrPhrase) return false

    // Prevent an introductory greeting followed by a substantive factual/actionable question
    // from being swallowed as pure chitchat.
    val questionMarkers = setOf(
      "كيف", "لماذا", "ليش", "ما", "ماذا", "متي", "اين", "كم", "هل",
      "what", "why", "how", "where", "when"
    )
    val isHowAreYou = containsStandalonePhrase(words, "كيف حالك") ||
      containsStandalonePhrase(words, "كيف الحال") ||
      containsStandalonePhrase(words, "how are you")
    if (!isHowAreYou && words.any { it in questionMarkers } && words.size >= 3) {
      return false
    }
    return true
  }

  private fun handleGreetingsAndChitchat(
    query: String,
    isArabic: Boolean
  ): ReasoningResult? {
    val words = query.split(" ").filter { it.isNotBlank() }
    if (words.isEmpty()) return null

    when {
      isStandaloneGreetingQuery(words) -> {
        val reply = if (isArabic) {
          "وعليكم السلام ورحمة الله! أهلاً بك، أنا WEYKY، كيف يمكنني مساعدتك أو التفكير معك اليوم؟"
        } else {
          "Hello! I am WEYKY, at your service. How can I assist you today?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "ماذا أفعل الآن؟" else "Current situation", "ماذا افعل الان؟"),
            ActionableOption(if (isArabic) "حالة الطقس" else "Weather forecast", "ما حالة الطقس؟"),
            ActionableOption(if (isArabic) "نسبة البطارية" else "Battery level", "كم نسبة البطارية؟")
          )
        )
      }
      containsStandalonePhrase(words, "كيف حالك") || containsStandalonePhrase(words, "كيف الحال") ||
        words.contains("اخبارك") || words.contains("شلونك") || containsStandalonePhrase(words, "how are you") -> {
        val reply = if (isArabic) {
          "أنا بخير وفي كامل الجاهزية لخدمتك! كيف حالك أنت وكيف يسير يومك؟"
        } else {
          "I'm doing great and fully ready to assist you! How are things with you?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "احكِ لي قصة" else "Tell a story", "احك لي قصة قصيرة"),
            ActionableOption(if (isArabic) "هل أخرج الآن؟" else "Should I go out?", "هل اخرج الان؟")
          )
        )
      }
      (words.contains("شكرا") && !containsStandalonePhrase(words, "لا شكرا")) ||
        words.contains("مشكور") || containsStandalonePhrase(words, "يعطيك العافيه") ||
        containsStandalonePhrase(words, "يعطيك العافية") || words.contains("تسلم") ||
        words.contains("thanks") || containsStandalonePhrase(words, "thank you") -> {
        val reply = if (isArabic) {
          "على الرحب والسعة دائماً! يسعدني مساعدتك في أي وقت."
        } else {
          "You're very welcome! I'm always here whenever you need assistance or quick reasoning."
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "ملخص الجهاز" else "Device overview", "حالة الجهاز"),
            ActionableOption(if (isArabic) "تشغيل الموسيقى" else "Play music", "شغل الموسيقى")
          )
        )
      }
      containsStandalonePhrase(words, "صباح الخير") || containsStandalonePhrase(words, "صباح النور") ||
        containsStandalonePhrase(words, "good morning") -> {
        val reply = if (isArabic) {
          "صباح النور والسرور! أتمنى لك يوماً موفقاً ومليئاً بالإنجاز. هل نلقي نظرة على طقس اليوم ومواعيدك؟"
        } else {
          "Good morning! Wishing you an energized and productive day. Would you like a quick overview of today's weather and schedule?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "طقس اليوم" else "Weather today", "ما حالة الطقس؟"),
            ActionableOption(if (isArabic) "مواعيدي" else "Check schedule", "هل سأصل في الموعد؟"),
            ActionableOption(if (isArabic) "البطارية" else "Battery", "البطارية")
          )
        )
      }
      containsStandalonePhrase(words, "مساء الخير") || containsStandalonePhrase(words, "مساء النور") ||
        containsStandalonePhrase(words, "تصبح علي خير") || containsStandalonePhrase(words, "تصبح على خير") ||
        containsStandalonePhrase(words, "good evening") || containsStandalonePhrase(words, "good night") -> {
        val reply = if (isArabic) {
          "مساء الأنوار! أتمنى أن يكون يومك قد مرّ بسلام. هل ترغب في نصائح للاسترخاء أو ضبط الهاتف للراحة؟"
        } else {
          "Good evening! I hope you had a fruitful day. Would you like help preparing for rest or setting your alarms?"
        }
        return ReasoningResult(
          replyText = reply,
          topicDetected = "chitchat",
          proactiveSuggestions = listOf(
            ActionableOption(if (isArabic) "نصائح للنوم" else "Sleep tips", "نصائح للنوم"),
            ActionableOption(if (isArabic) "إطفاء الكشاف" else "Turn off torch", "اطفئ الكشاف")
          )
        )
      }
    }
    return null
  }

  private fun handleEmotionalSupport(
    query: String,
    isArabic: Boolean
  ): ReasoningResult? {
    if (query.contains("تعبان") || query.contains("مرهق") || query.contains("متضايق") || query.contains("حزين") || query.contains("ملل") || query.contains("زهقان") || query.contains("قلق") || query.contains("sad") || query.contains("tired") || query.contains("stressed")) {
      val reply = if (isArabic) {
        "أشعر بك، الإرهاق والضغوط جزء طبيعي من الحياة. خذ نفساً عميقاً واسترح قليلاً بعيداً عن الشاشات، أو استمع لشيء هادئ تحبه. أنا هنا إذا أردت التحدث أو سماع قصة ملهمة."
      } else {
        "I'm sorry you're feeling that way. Stress and fatigue happen to everyone. Taking a short break, breathing deeply, or a brief walk can help refresh your perspective. I'm here to support you."
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "emotional_support",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "تشغيل موسيقى" else "Play music", "شغل الموسيقى"),
          ActionableOption(if (isArabic) "قصة ملهمة" else "Short story", "احك لي قصة قصيرة"),
          ActionableOption(if (isArabic) "هل أخرج للمشي؟" else "Go outside?", "هل اخرج الان؟")
        )
      )
    }
    return null
  }

  /**
   * Principled handling of un-indexed queries:
   * 1. Distinguishes what is missing rather than inventing answers.
   * 2. Explains clearly if offline capabilities cannot access live external data.
   * 3. Solicits specific clarification for ambiguous requests.
   * 4. Completely avoids misleading canned templates.
   */
  private fun generatePrincipledResponse(
    query: String,
    normalized: String,
    isArabic: Boolean
  ): ReasoningResult {
    // 1. External live web data inquiries (Stocks, currency rates, breaking news, sports scores)
    val isExternalLiveQuery = normalized.contains("سعر الدولار") || normalized.contains("سعر الذهب") ||
      normalized.contains("اسعار الاسهم") || normalized.contains("نتيجة مباراة") || normalized.contains("الاخبار العاجلة") ||
      normalized.contains("بورصة") || normalized.contains("بيتكوين") || normalized.contains("عملات") ||
      normalized.contains("stock price") || normalized.contains("exchange rate") || normalized.contains("breaking news")

    if (isExternalLiveQuery) {
      val reply = if (isArabic) {
        "هذا السؤال يتطلب اتصالاً بمصادر بيانات خارجية حية (كأسعار الأسواق أو الأخبار اللحظية)، وهو غير متوفر في قاعدة المعرفة المحلية على الجهاز. يمكنني مساعدتك في إدارة هاتفك، تحليل الطقس والمواعيد، أو الإجابة عن الأسئلة العلمية والعامة."
      } else {
        "This query requires a live internet connection to external real-time data sources, which is not available locally offline. I operate entirely on-device to manage your phone, analyze daily context, and assist with logical inquiries."
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "unavailable_external_data",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "حالة الجهاز" else "Device overview", "حالة الجهاز"),
          ActionableOption(if (isArabic) "طقس منطقتي" else "Local weather", "ما حالة الطقس؟")
        )
      )
    }

    // 2. Ambiguous or overly brief queries
    if (query.trim().split("\\s+".toRegex()).size <= 2 && query.length < 10) {
      val reply = if (isArabic) {
        "طلبك بخصوص '$query' غير محدد بما يكفي. هل تقصد الاستفسار عن معلومة معينة، أم تنفيذ إجراء في الهاتف مثل فتح تطبيق أو فحص الإعدادات؟"
      } else {
        "Your request regarding '$query' is ambiguous. Would you like information, or to execute a device action such as opening an app or checking battery?"
      }
      return ReasoningResult(
        replyText = reply,
        topicDetected = "ambiguous_clarification",
        proactiveSuggestions = listOf(
          ActionableOption(if (isArabic) "ماذا أفعل الآن؟" else "What's my status?", "ماذا افعل الان؟"),
          ActionableOption(if (isArabic) "فتح تطبيق" else "Open app", "افتح الاعدادات")
        )
      )
    }

    // 3. Thoughtful direct reasoning on unindexed queries
    val reply = if (isArabic) {
      "بخصوص سؤالك عن '$query': هذه المعلومة التفصيلية غير مسجلة في قاعدة المعرفة المحلية الحالية على الجهاز. يمكنني مساعدتك في التحكم بالهاتف، متابعة السياق اليومي (الطقس، البطارية، المواعيد)، العمليات الحسابية، أو النصائح العلمية والعملية."
    } else {
      "Regarding your inquiry about '$query', this specific detail is not stored in my on-device knowledge base. I can assist with device controls, contextual tracking (weather, battery, calendar), calculations, and lifestyle advice."
    }

    return ReasoningResult(
      replyText = reply,
      topicDetected = "unindexed_query",
      proactiveSuggestions = listOf(
        ActionableOption(if (isArabic) "ماذا تستطيع أن تفعل؟" else "What can you do?", "ماذا تستطيع ان تفعل؟"),
        ActionableOption(if (isArabic) "فحص حالة الهاتف" else "Check device", "حالة الجهاز"),
        ActionableOption(if (isArabic) "هل أخرج الآن؟" else "Should I leave?", "هل اخرج الان؟")
      )
    )
  }
}
