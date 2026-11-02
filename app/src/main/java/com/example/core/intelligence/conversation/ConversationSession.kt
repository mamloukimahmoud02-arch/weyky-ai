package com.example.core.intelligence.conversation

import com.example.core.intelligence.knowledge.context.PreparedTurnKnowledgeContext
import com.example.core.intelligence.knowledge.repository.KnowledgeRepository

/**
 * Indicates if the assistant is awaiting specific follow-up information from the user.
 */
enum class PendingExpectation {
  NONE,
  AWAITING_SONG_TITLE,
  AWAITING_CLARIFICATION
}

/**
 * Manages in-memory dialogue session history, tracking user/assistant turns,
 * current focus topic, mentioned entities, pending expectations, and context for multi-turn reasoning.
 */
class ConversationSession(
  private val maxTurns: Int = 20,
  val knowledgeRepository: KnowledgeRepository = KnowledgeRepository.getDefaultInstance()
) {
  private val _turns = mutableListOf<ConversationTurn>()
  val turns: List<ConversationTurn> get() = _turns.toList()

  private val _topicHistory = ArrayDeque<String>()
  val topicHistory: List<String> get() = _topicHistory.toList()

  var currentTopic: String? = null
  var previousTopic: String? = null
  var pendingExpectation: PendingExpectation = PendingExpectation.NONE
  var lastReferencedSubject: String? = null
  var lastBatteryScenario: com.example.core.intelligence.battery.BatteryForecastScenario? = null
  private val entitiesMentioned = mutableSetOf<String>()
  private val deliveredElaborations = mutableMapOf<String, MutableSet<String>>()

  fun recordUserTurn(text: String, deferTopicUpdate: Boolean = false): ConversationTurn {
    val cleanText = text.trim()
    val last = _turns.lastOrNull()
    if (last != null && last.role == ParticipantRole.USER && last.text == cleanText) {
      if (!deferTopicUpdate) {
        detectAndSetTopic(cleanText)
      }
      extractEntities(cleanText)
      return last
    }
    val turn = ConversationTurn(
      role = ParticipantRole.USER,
      text = cleanText,
      topic = currentTopic
    )
    addTurn(turn)
    if (!deferTopicUpdate) {
      detectAndSetTopic(cleanText)
    }
    extractEntities(cleanText)
    return turn
  }

  fun updateTopic(newTopic: String?, referencedSubject: String? = null) {
    if (!newTopic.isNullOrBlank()) {
      val nonStickyTopics = setOf("unindexed_query", "ambiguous_clarification", "unavailable_external_data")
      if (newTopic !in nonStickyTopics) {
        if (currentTopic != null && currentTopic != newTopic) {
          previousTopic = currentTopic
        }
        if (newTopic != "battery" && newTopic != "battery_forecast" && currentTopic != newTopic) {
          lastBatteryScenario = null
        }
        currentTopic = newTopic
        if (referencedSubject != null) {
          lastReferencedSubject = referencedSubject
        }
        if (_topicHistory.lastOrNull() != newTopic) {
          _topicHistory.addLast(newTopic)
          while (_topicHistory.size > 10) {
            _topicHistory.removeFirst()
          }
        }
      }
    }
  }

  fun recordAssistantTurn(
    text: String,
    suggestions: List<ActionableOption> = emptyList(),
    newTopic: String? = null,
    expectation: PendingExpectation = PendingExpectation.NONE
  ): ConversationTurn {
    val cleanText = text.trim()
    updateTopic(newTopic)
    pendingExpectation = expectation
    val last = _turns.lastOrNull()
    if (last != null && last.role == ParticipantRole.ASSISTANT && last.text == cleanText) {
      return last
    }
    val turn = ConversationTurn(
      role = ParticipantRole.ASSISTANT,
      text = cleanText,
      topic = currentTopic,
      actionableSuggestions = suggestions
    )
    addTurn(turn)
    return turn
  }

  fun markElaborationDelivered(topicId: String, elaborationText: String) {
    val set = deliveredElaborations.getOrPut(topicId) { mutableSetOf() }
    set.add(elaborationText.trim())
  }

  fun hasDeliveredElaboration(topicId: String, elaborationText: String): Boolean {
    return deliveredElaborations[topicId]?.contains(elaborationText.trim()) == true
  }

  fun getDeliveredElaborationCount(topicId: String): Int {
    return deliveredElaborations[topicId]?.size ?: 0
  }

  /**
   * Resolves an explicit user request to return to an earlier topic in the session history.
   * When [preparedContext] is supplied, reuses its normalized query, unbiased match, and
   * pinned snapshot without performing redundant normalizations or searches.
   */
  fun resolveExplicitTopicReturn(
    rawQuery: String,
    preparedContext: PreparedTurnKnowledgeContext? = null
  ): String? {
    val norm = preparedContext?.relaxedNormalizedQuery ?: LocalKnowledgeBase.normalizeArabic(rawQuery)
    val hasReturnMarker = norm.contains("نعود") || norm.contains("عوده") || norm.contains("العوده") ||
      norm.contains("نرجع") || norm.contains("ارجع") || norm.contains("رجوع") ||
      norm.contains("بالعوده") || norm.contains("عد الي") || norm.contains("عد لموضوع") ||
      norm.contains("الموضوع السابق") || norm.contains("الموضوع الاول") ||
      norm.contains("سابقا") || norm.contains("قبل قليل") ||
      norm.contains("back to") || norm.contains("return to") || norm.contains("previous topic") ||
      norm.contains("earlier topic")

    if (!hasReturnMarker) return null

    // 1. Check if the query names a specific topic that exists in topicHistory or previousTopic
    val matchedTopic = if (preparedContext != null) {
      preparedContext.unbiasedTopTopicId ?: preparedContext.retrievedTopicId
    } else {
      knowledgeRepository.findMatchingTopic(rawQuery, null)?.id
    }
    if (matchedTopic != null && (_topicHistory.contains(matchedTopic) || previousTopic == matchedTopic)) {
      return matchedTopic
    }

    // 2. Check category/domain aliases in topicHistory (e.g. "نعود إلى الفيزياء" -> "physics_universe")
    for (historyTopicId in _topicHistory.reversed()) {
      if (historyTopicId == currentTopic && _topicHistory.size > 1) continue
      val kbTopic = preparedContext?.getTopicById(historyTopicId)
        ?: knowledgeRepository.getTopicById(historyTopicId)
      if (kbTopic != null) {
        val matchesAlias = (kbTopic.id == "physics_universe" && (norm.contains("فيزياء") || norm.contains("الفيزياء") || norm.contains("فضاء") || norm.contains("الفضاء") || norm.contains("كون") || norm.contains("physics") || norm.contains("space"))) ||
          (kbTopic.id == "capitals_arab" && (norm.contains("جغرافيا") || norm.contains("الجغرافيا") || norm.contains("عواصم") || norm.contains("العواصم") || norm.contains("geography") || norm.contains("capitals"))) ||
          (kbTopic.id == "salalah_tourism" && (norm.contains("سياحه") || norm.contains("السياحه") || norm.contains("سفر") || norm.contains("صلاله") || norm.contains("salalah"))) ||
          (kbTopic.id == "sleep_wellness" && (norm.contains("نوم") || norm.contains("النوم") || norm.contains("sleep"))) ||
          (kbTopic.id == "productivity" && (norm.contains("تركيز") || norm.contains("التركيز") || norm.contains("انتاجيه") || norm.contains("وقت"))) ||
          (kbTopic.id == "ai_concept" && (norm.contains("ذكاء") || norm.contains("الذكاء") || norm.contains("ai"))) ||
          kbTopic.keywords.any { kw ->
            val normKw = LocalKnowledgeBase.normalizeArabic(kw)
            normKw.length >= 4 && norm.contains(normKw)
          }
        if (matchesAlias) {
          return historyTopicId
        }
      }
    }

    // 3. Generic "previous topic" / "الموضوع السابق" / "الموضوع الأول"
    if (norm.contains("الموضوع الاول") || norm.contains("earlier topic") || norm.contains("first topic")) {
      return _topicHistory.firstOrNull { it != currentTopic } ?: previousTopic
    }
    if (norm.contains("الموضوع السابق") || norm.contains("previous topic")) {
      return previousTopic ?: _topicHistory.lastOrNull { it != currentTopic }
    }

    // 4. If user explicitly named a KB topic with a return marker
    if (matchedTopic != null) {
      return matchedTopic
    }

    return null
  }

  fun getLastTurn(): ConversationTurn? = _turns.lastOrNull()
  fun getLastUserTurn(): ConversationTurn? = _turns.lastOrNull { it.role == ParticipantRole.USER }
  fun getLastAssistantTurn(): ConversationTurn? = _turns.lastOrNull { it.role == ParticipantRole.ASSISTANT }

  fun getRecentTurns(count: Int = 6): List<ConversationTurn> {
    return _turns.takeLast(count)
  }

  fun clear() {
    _turns.clear()
    _topicHistory.clear()
    deliveredElaborations.clear()
    currentTopic = null
    previousTopic = null
    pendingExpectation = PendingExpectation.NONE
    lastReferencedSubject = null
    lastBatteryScenario = null
    entitiesMentioned.clear()
  }

  private fun addTurn(turn: ConversationTurn) {
    _turns.add(turn)
    if (_turns.size > maxTurns) {
      _turns.removeAt(0)
    }
  }

  private fun detectAndSetTopic(text: String) {
    val norm = LocalKnowledgeBase.normalizeArabic(text)
    val words = norm.split(" ").filter { it.isNotBlank() }

    // 0. Check explicit return to an earlier topic first
    val returnedTopic = resolveExplicitTopicReturn(text)
    if (returnedTopic != null) {
      updateTopic(returnedTopic, returnedTopic)
      return
    }

    val isSalalahOrTravelContext = (currentTopic == "salalah_tourism" || currentTopic == "travel") &&
      (norm.contains("هناك") || norm.contains("فيها") || norm.contains("صلاله") || norm.contains("زيارتها") || norm.contains("there") || norm.contains("salalah"))

    when {
      lastBatteryScenario != null && (norm.contains("ملاحه") || norm.contains("خرائط") || norm.contains("navigation") || norm.contains("gps")) -> {
        updateTopic("battery", "battery")
      }
      isSalalahOrTravelContext -> {
        updateTopic(currentTopic ?: "salalah_tourism")
      }
      norm.contains("صلاله") || norm.contains("ظفار") || norm.contains("salalah") -> {
        updateTopic("salalah_tourism", "salalah")
      }
      (norm.contains("بطاريه") || norm.contains("battery")) &&
        (norm.contains("حفاظ") || norm.contains("عمر البطاريه") || norm.contains("نصائح") || norm.contains("توفير البطاريه") || norm.contains("battery health") || norm.contains("battery tips")) -> {
        updateTopic("battery_care")
      }
      norm.contains("هاتف") || norm.contains("جهاز") || norm.contains("تلفون") || norm.contains("phone") || norm.contains("device") -> {
        updateTopic("device_info", "phone")
      }
      norm.contains("اغنيه") || norm.contains("موسيقي") || norm.contains("موسيق") || norm.contains("song") || norm.contains("music") -> {
        if (!norm.contains("بطاريه") && !norm.contains("battery") && !norm.contains("شحن")) {
          updateTopic("music", "music")
        }
      }
      norm.contains("بطاريه") || norm.contains("شحن") || norm.contains("battery") -> {
        updateTopic("battery", "battery")
      }
      norm.contains("موعد") || norm.contains("اجتماع") || norm.contains("جدول") || norm.contains("meeting") || norm.contains("schedule") -> {
        updateTopic("schedule", "schedule")
      }
      norm.contains("طقس") || words.contains("جو") || words.contains("الجو") || norm.contains("مطر") || norm.contains("امطار") || norm.contains("weather") -> {
        updateTopic("weather", "weather")
      }
      norm.contains("سفر") || norm.contains("سياحه") || norm.contains("رحله") || norm.contains("travel") -> {
        updateTopic("travel")
      }
      norm.contains("نوم") || norm.contains("ارق") || norm.contains("sleep") || norm.contains("insomnia") -> {
        updateTopic("sleep_wellness")
      }
      norm.contains("صحه") || norm.contains("رياضه") || norm.contains("health") -> {
        updateTopic("health_wellness")
      }
      norm.contains("حساب") || norm.contains("رياضيات") || norm.contains("معادله") || norm.contains("math") -> {
        updateTopic("calculation")
      }
      else -> {
        val kbMatch = knowledgeRepository.findMatchingTopic(text, null)
        if (kbMatch != null) {
          updateTopic(kbMatch.id, kbMatch.id)
        }
      }
    }
  }

  private fun extractEntities(text: String) {
    val keywords = listOf(
      "صلالة", "مسقط", "الرياض", "دبي", "القاهرة", "مكة", "المدينة", "باريس", "لندن", "طوكيو",
      "الذكاء الاصطناعي", "الفيزياء", "الفضاء", "التاريخ", "الفلسفة", "البرمجة",
      "البطارية", "الشبكة", "الصوت", "الإشعارات"
    )
    for (k in keywords) {
      if (text.contains(k, ignoreCase = true)) {
        entitiesMentioned.add(k)
      }
    }
  }

  fun getMentionedEntities(): Set<String> = entitiesMentioned.toSet()
}
