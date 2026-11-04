package com.example.core.intelligence.conversation

import java.util.Locale

/**
 * Local on-device structured knowledge base and reasoning bank.
 * Provides curated knowledge across science, geography, history, daily life,
 * culture, productivity, mathematics, and language in both Arabic and English.
 */
object LocalKnowledgeBase {

  data class KnowledgeTopic(
    val id: String,
    val keywords: List<String>,
    val arabicSummary: String,
    val englishSummary: String,
    val suggestedFollowUps: List<ActionableOption>
  )

  fun getTopicById(id: String): KnowledgeTopic? = topics.find { it.id == id }

  private val topics = listOf(
    // 1. Identity & WEYKY purpose
    KnowledgeTopic(
      id = "identity",
      keywords = listOf("من انت", "من أنت", "عرفني بنفسك", "ما هو ويكي", "ما هو وايكي", "who are you", "what are you", "اسمك", "ما هي weyky", "ما هو weyky", "حدثني عن weyky"),
      arabicSummary = "أنا وايكي، النواة الذكية الحية لهاتفك. أعمل محلياً بالكامل لفهم سياقك اليومي، التحكم في وظائف الجهاز، مساعدتك في اتخاذ القرارات اليومية، والتحاور معك بذكاء وخصوصية تامة.",
      englishSummary = "I am Weyky, your contextual intelligent assistant on this device. I work locally to help you understand your daily context, manage your device, reason together, and provide helpful insights.",
      suggestedFollowUps = listOf(
        ActionableOption("ماذا تستطيع أن تفعل؟", "ماذا تستطيع ان تفعل؟"),
        ActionableOption("ما حالة هاتفي الآن؟", "حالة الجهاز"),
        ActionableOption("هل أخرج الآن؟", "هل اخرج الان؟")
      )
    ),
    // 2. Capabilities
    KnowledgeTopic(
      id = "capabilities",
      keywords = listOf("ماذا تستطيع", "قدراتك", "مميزاتك", "شو بتعرف", "what can you do", "features", "كيف تساعدني", "مهامك"),
      arabicSummary = "يمكنني مساعدتك في عدة مجالات:\n1. التحكم الفوري بالهاتف (فتح التطبيقات، الكشاف، الصوت، الموسيقى، المكالمات).\n2. الذكاء السياقي (تحليل الطقس، البطارية، المواعيد، ونصيحة الخروج).\n3. الحوار المعرفي والمنطقي (العلوم، الجغرافيا، الحساب، تنظيم الوقت، والنصائح اليومية) بدون إنترنت.",
      englishSummary = "I can assist you with:\n1. Device controls (apps, flashlight, audio volume, media, calls).\n2. Real-time situational analysis (weather, battery, appointments, departure advice).\n3. Conversational reasoning, calculations, productivity habits, and answering diverse knowledge questions completely offline.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص الطقس", "ما حالة الطقس؟"),
        ActionableOption("فحص البطارية", "كم نسبة البطارية؟"),
        ActionableOption("احسب 25 * 4", "احسب 25 * 4")
      )
    ),
    // 3. Sleep & Health
    KnowledgeTopic(
      id = "sleep_wellness",
      keywords = listOf("النوم", "نوم", "ارق", "أرق", "كيف انام", "نصائح للنوم", "sleep", "insomnia", "راحة", "الاستيقاظ"),
      arabicSummary = "للحصول على نوم عميق وصحي ينصح الخبراء بـ:\n1. تثبيت موعد النوم والاستيقاظ يومياً لضبط الساعة البيولوجية.\n2. الابتعاد عن شاشات الهواتف والضوء الأزرق قبل النوم بـ 30 دقيقة.\n3. الحفاظ على برودة الغرفة وهدوئها (~18-20 درجة مئوية).\n4. تجنب الكافيين والوجبات الثقيلة في المساء.",
      englishSummary = "To achieve deep and restorative sleep, experts recommend:\n1. Keeping a consistent bedtime and wake-up schedule.\n2. Avoiding blue light screens at least 30 minutes before sleep.\n3. Keeping your bedroom cool and dark (around 18-20°C).\n4. Avoiding caffeine and heavy meals late in the evening.",
      suggestedFollowUps = listOf(
        ActionableOption("تأثير القهوة على النوم", "تأثير القهوة على النوم"),
        ActionableOption("إطفاء الكشاف", "اطفئ الكشاف"),
        ActionableOption("خفض الصوت للهدوء", "اخفض الصوت")
      )
    ),
    // 4. Productivity & Time Management (Pomodoro etc.)
    KnowledgeTopic(
      id = "productivity",
      keywords = listOf("تنظيم الوقت", "إدارة الوقت", "التركيز", "الانتاجية", "الإنتاجية", "productivity", "time management", "pomodoro", "بومودورو", "كسل", "تأجيل", "تسويف", "دراسة", "مذاكرة"),
      arabicSummary = "لرفع الإنتاجية والتركيز وتجنب التسويف:\n1. تقنية بومودورو: اعمل بتركيز تام لمدة 25 دقيقة ثم خذ استراحة 5 دقائق.\n2. قاعدة البدء بالأصعب (Eat the Frog): أنجز المهمة الأهم في بداية يومك.\n3. مصفوفة أيزنهاور: قسّم مهامك حسب الأهمية والاستعجال وتخلص من المشتتات.\n4. تقسيم الأهداف الكبيرة إلى خطوات صغيرة قابلة للتنفيذ فوراً.",
      englishSummary = "To boost your productivity and focus:\n1. The Pomodoro Technique: 25 minutes of deep work followed by a 5-minute break.\n2. Eat That Frog: Tackle your most demanding task first thing in the morning.\n3. Eisenhower Matrix: Distinguish between urgent and important, eliminate distractions.\n4. Break large projects into bite-sized, immediate action steps.",
      suggestedFollowUps = listOf(
        ActionableOption("هل لدي مواعيد اليوم؟", "هل سأصل في الموعد؟"),
        ActionableOption("كم الساعة الآن؟", "كم الساعة؟"),
        ActionableOption("نصيحة إضافية للتركيز", "نصيحة للتركيز")
      )
    ),
    // 5. Artificial Intelligence & Local Processing
    KnowledgeTopic(
      id = "ai_concept",
      keywords = listOf(
        "الذكاء الاصطناعي", "ai", "artificial intelligence", "تعلم الآلة",
        "الشبكات العصبية", "machine learning", "كيف يعمل الذكاء الاصطناعي", "كيف يفكر الذكاء الاصطناعي"
      ),
      arabicSummary = "الذكاء الاصطناعي هو قدرة الأنظمة الحاسوبية على محاكاة القدرات الذهنية البشرية مثل الفهم، الاستنتاج، والتعلم من السياق. في WEYKY، نعتمد على الذكاء المدمج محلياً على الجهاز (On-Device Intelligence) لضمان السرعة الفورية وحماية خصوصية بياناتك بالكامل دون الحاجة لإرسالها إلى خوادم خارجية.",
      englishSummary = "Artificial Intelligence simulates human cognitive capabilities such as learning, deduction, and natural language comprehension. In WEYKY, contextual reasoning and on-device knowledge are integrated locally for maximum privacy and instant speed without relying on external servers.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص ذاكرة الجهاز", "كم الذاكرة المتبقية؟"),
        ActionableOption("حالة السياق الحالي", "ما هو السياق؟")
      )
    ),
    // 6. Geography: Capitals & Countries
    KnowledgeTopic(
      id = "capitals_arab",
      keywords = listOf(
        "عواصم", "العواصم", "ابرز العواصم", "عواصم الدول", "العواصم العربية", "العواصم العالمية",
        "عاصمة السعودية", "عاصمة مصر", "عاصمة الامارات", "عاصمة عمان", "عاصمة قطر",
        "عاصمة الكويت", "عاصمة الاردن", "عاصمة المغرب", "عاصمة الجزائر", "عاصمة تونس",
        "عاصمة العراق", "عاصمة سوريا", "عاصمة لبنان", "عاصمة فلسطين", "عاصمة اليمن",
        "عاصمة فرنسا", "عاصمة بريطانيا", "عاصمة اليابان",
        "capitals", "world capitals", "arab capitals"
      ),
      arabicSummary = "إليك أبرز العواصم العربية والعالمية:\n- السعودية: الرياض | الإمارات: أبوظبي | عُمان: مسقط\n- قطر: الدوحة | الكويت: مدينة الكويت | مصر: القاهرة\n- الأردن: عمّان | العراق: بغداد | سوريا: دمشق\n- لبنان: بيروت | فلسطين: القدس | اليمن: صنعاء\n- المغرب: الرباط | الجزائر: الجزائر | تونس: تونس\n- فرنسا: باريس | بريطانيا: لندن | اليابان: طوكيو",
      englishSummary = "Key Arab and world capitals:\n- Saudi Arabia: Riyadh | UAE: Abu Dhabi | Oman: Muscat\n- Qatar: Doha | Kuwait: Kuwait City | Egypt: Cairo\n- Jordan: Amman | Iraq: Baghdad | Syria: Damascus\n- Lebanon: Beirut | Palestine: Jerusalem | Yemen: Sana'a\n- Morocco: Rabat | Algeria: Algiers | Tunisia: Tunis\n- France: Paris | United Kingdom: London | Japan: Tokyo",
      suggestedFollowUps = listOf(
        ActionableOption("حدثني عن صلالة", "حدثني عن صلالة"),
        ActionableOption("ما حالة الطقس عندي؟", "ما حالة الطقس؟")
      )
    ),
    // 7. Salalah & Tourism / Seasonal Travel
    KnowledgeTopic(
      id = "salalah_tourism",
      keywords = listOf("صلالة", "صلاله", "خريف صلالة", "ظفار", "سياحة عمان", "وادي دربات", "المغسيل", "salalah", "dhofar", "khareef"),
      arabicSummary = "صلالة هي حاضرة محافظة ظفار في سلطنة عُمان، وتشتهر بموسم 'الخريف' (من أواخر يونيو إلى سبتمبر) حيث تتحول الجبال والسهول إلى بساط أخضر ساحر مع ضباب ورذاذ منعش ودرجات حرارة معتدلة (~25°م) بينما يكون الصيف حاراً في بقية المنطقة. من أبرز معالمها: وادي دربات، عين أثوم، وشاطئ المغسيل.",
      englishSummary = "Salalah is the jewel of Dhofar, Oman. It is famous for the 'Khareef' monsoon season (late June to September), when the mountains turn lush green under refreshing mist and cool temperatures around 25°C. Top attractions include Wadi Darbat, Ayn Athum, and Mughsail Beach.",
      suggestedFollowUps = listOf(
        ActionableOption("ما أفضل وقت لزيارتها؟", "متى أفضل وقت لزيارة صلالة؟"),
        ActionableOption("طقس منطقتي الآن", "ما حالة الطقس؟")
      )
    ),
    // 8. Science: Speed of light, Solar System, Gravity, Stars
    KnowledgeTopic(
      id = "physics_universe",
      keywords = listOf(
        "سرعة الضوء", "ضوء الشمس", "السنة الضوئية", "المجموعة الشمسية", "النظام الشمسي",
        "الجاذبية", "الفضاء", "الكواكب", "كواكب", "المجرة", "الكون",
        "speed of light", "solar system", "gravity", "planets", "universe",
        "النجوم", "نجوم", "نجم", "طاقة النجوم", "الاندماج النووي",
        "قوة الجاذبية", "دور الجاذبية", "جاذبية الشمس", "مدارات الكواكب",
        "دور الجاذبية في النظام الشمسي", "stars", "nuclear fusion"
      ),
      arabicSummary = "حقائق علمية كونية:\n- سرعة الضوء في الفراغ تبلغ حوالي 300,000 كيلومتر في الثانية (299,792 كم/ث).\n- يستغرق ضوء الشمس حوالي 8 دقائق و20 ثانية للوصول إلى الأرض.\n- تتكون مجموعتنا الشمسية من 8 كواكب، أكبرها المشتري وأقربها للشمس عطارد.\n- الجاذبية هي القوة التي تحفظ الكواكب في مداراتها وتمنح الأجسام أوزانها.",
      englishSummary = "Fascinating cosmic facts:\n- The speed of light in a vacuum is approximately 300,000 km/s (299,792 km/s).\n- Sunlight takes about 8 minutes and 20 seconds to reach Earth.\n- Our solar system contains 8 recognized planets, Jupiter being the largest and Mercury closest to the Sun.\n- Gravity is the fundamental attraction keeping planets in orbit and holding our atmosphere.",
      suggestedFollowUps = listOf(
        ActionableOption("احسب المسافة في دقيقة", "احسب 300000 * 60"),
        ActionableOption("احكِ لي قصة قصيرة", "احك لي قصة قصيرة")
      )
    ),
    // 9. Short Inspirational Story / Wisdom
    KnowledgeTopic(
      id = "short_story",
      keywords = listOf("قصة", "احك لي قصة", "قصة قصيرة", "حكمة", "موعظة", "tell me a story", "short story", "طرفة", "الهام", "إلهام"),
      arabicSummary = "قصة قصيرة ملهمة: يُحكى أن مزارعاً كان يفوز دائماً بجائزة أفضل محصول ذرة، وكان يوزع أفضل بذوره على جيرانه المزارعين! وعندما سُئل عن السبب قال: 'الرياح تنقل حبوب اللقاح بين الحقول، فإذا زرع جيراني ذرة رديئة ستؤثر على جودة محصولي. لكي أحصل على أفضل حصاد، يجب أن أساعد جيراني على زراعة الأفضل أيضاً'. الحكمة: نجاحك الحقيقي يزدهر عندما تساعد من حولك على النجاح.",
      englishSummary = "A wise farmer was asked why he always shared his finest seeds with his neighbors. He smiled and said: 'The wind carries pollen from field to field. If my neighbors grow inferior grain, cross-pollination will degrade my own crops. By helping them succeed, I enrich my harvest.' Lesson: True excellence thrives when everyone around us prospers.",
      suggestedFollowUps = listOf(
        ActionableOption("نصائح للإنتاجية", "تنظيم الوقت"),
        ActionableOption("نصائح للنوم الصحي", "نصائح للنوم")
      )
    ),
    // 10. Healthy Habits & Water
    KnowledgeTopic(
      id = "hydration_health",
      keywords = listOf("شرب الماء", "فوائد الماء", "كمية الماء", "صحة الجسم", "water intake", "drinking water", "ترطيب الجسم", "الصداع والجفاف"),
      arabicSummary = "يشكل الماء حوالي 60% من جسم الإنسان، وشرب كمية كافية (حوالي 2 إلى 3 لترات يومياً) يساعد على:\n1. تنشيط الذاكرة والتركيز ومنع الصداع الناتج عن الجفاف.\n2. تنظيم درجة حرارة الجسم وتحسين الهضم.\n3. الحفاظ على نضارة البشرة ونشاط العضلات.",
      englishSummary = "Water constitutes about 60% of body weight and is critical for brain function, circulation, and temperature regulation. It is recommended to drink 2 to 3 liters daily (around 8 glasses), increasing during hot weather or physical exercise.",
      suggestedFollowUps = listOf(
        ActionableOption("فحص حرارة الطقس", "ما حالة الطقس؟"),
        ActionableOption("نصائح للنوم", "نصائح للنوم")
      )
    ),
    // 11. Battery & Device Care
    KnowledgeTopic(
      id = "battery_care",
      keywords = listOf(
        "الحفاظ على البطارية", "عمر البطارية", "توفير البطارية", "نصائح للبطارية",
        "حماية البطارية", "صحة البطارية", "اطالة عمر البطارية",
        "battery health", "battery tips", "save battery", "battery lifespan", "battery care",
        "شحن الهاتف", "بطارية الهاتف",
        "تنفد البطارية", "نفاد البطارية", "استنزاف البطارية", "استهلاك البطارية",
        "لماذا تنفد البطارية", "لماذا تنفد البطارية بسرعة", "تفرغ البطارية", "تفرغ البطارية بسرعة",
        "تنفذ البطارية", "نفاذ البطارية", "لماذا تنفذ البطارية", "لماذا تنفذ البطارية بسرعة",
        "اسباب نفاد البطارية", "اسباب استهلاك البطارية", "سبب استهلاك البطارية", "اسباب استهلاك الطاقة",
        "تخلص البطارية", "تهبط البطارية", "تنفد بطاريتي", "تنفذ بطاريتي",
        "يضر البطارية", "الشحن الليلي", "الشحن طوال الليل",
        "استهلاك الطاقة", "تقليل استهلاك الطاقة", "توفير الطاقة",
        "battery drain", "why does battery drain", "draining fast", "overnight charging", "power saving"
      ),
      arabicSummary = "للحفاظ على صحة بطارية هاتفك لأطول فترة:\n1. حاول إبقاء نسبة الشحن بين 20% و80% وتجنب التفريغ الكامل المتكرر.\n2. تجنب تعريض الهاتف للحرارة العالية أثناء الشحن.\n3. خفّض سطوع الشاشة واستخدم الوضع الداكن (Dark Mode).\n4. أغلق البلوتوث والموقع عند عدم الحاجة إليهما.",
      englishSummary = "To maximize your phone's battery lifespan:\n1. Keep charge levels between 20% and 80%, avoiding complete depletion.\n2. Avoid exposing your device to high heat or direct sunlight while charging.\n3. Lower screen brightness and use Dark Mode.\n4. Disable Bluetooth and GPS when not in active use.",
      suggestedFollowUps = listOf(
        ActionableOption("كم نسبة بطاريتي الآن؟", "كم نسبة البطارية؟"),
        ActionableOption("هل البطارية ستكفي؟", "هل البطارية ستكفي؟")
      )
    )
  )

  private val TASHKEEL_AND_TATWEEL_REGEX = Regex("[\\u064B-\\u065F\\u0670\\u0640]")
  private val PUNCTUATION_REGEX = Regex("[؟?!.,،؛:\"'()«»\\[\\]{}_]")
  private val WHITESPACE_REGEX = Regex("\\s+")

  private val STOP_WORDS = setOf(
    "ما", "ماذا", "هو", "هي", "هم", "هل", "كيف", "متي", "اين", "لماذا", "كم",
    "عن", "في", "من", "الي", "علي", "على", "مع", "لي", "لك", "ان", "إن",
    "حدثني", "اخبرني", "اعطني", "اريد", "ابغى", "ممكن", "لو", "سمحت", "بعض",
    "what", "is", "are", "the", "a", "an", "about", "tell", "me", "how", "to", "of", "in", "for", "on", "can", "you"
  )

  private val COUNTRY_CAPITALS_MAP = mapOf(
    "السعوديه" to Pair("الرياض", "Riyadh (Saudi Arabia)"),
    "مصر" to Pair("القاهرة", "Cairo (Egypt)"),
    "الامارات" to Pair("أبوظبي", "Abu Dhabi (UAE)"),
    "عمان" to Pair("مسقط", "Muscat (Oman)"),
    "قطر" to Pair("الدوحة", "Doha (Qatar)"),
    "الكويت" to Pair("مدينة الكويت", "Kuwait City (Kuwait)"),
    "الاردن" to Pair("عمّان", "Amman (Jordan)"),
    "المغرب" to Pair("الرباط", "Rabat (Morocco)"),
    "الجزائر" to Pair("الجزائر", "Algiers (Algeria)"),
    "تونس" to Pair("تونس", "Tunis (Tunisia)"),
    "العراق" to Pair("بغداد", "Baghdad (Iraq)"),
    "سوريا" to Pair("دمشق", "Damascus (Syria)"),
    "لبنان" to Pair("بيروت", "Beirut (Lebanon)"),
    "فلسطين" to Pair("القدس", "Jerusalem (Palestine)"),
    "اليمن" to Pair("صنعاء", "Sana'a (Yemen)"),
    "فرنسا" to Pair("باريس", "Paris (France)"),
    "بريطانيا" to Pair("لندن", "London (United Kingdom)"),
    "اليابان" to Pair("طوكيو", "Tokyo (Japan)"),
    "saudi" to Pair("الرياض", "Riyadh"),
    "egypt" to Pair("القاهرة", "Cairo"),
    "uae" to Pair("أبوظبي", "Abu Dhabi"),
    "oman" to Pair("مسقط", "Muscat"),
    "qatar" to Pair("الدوحة", "Doha"),
    "kuwait" to Pair("مدينة الكويت", "Kuwait City"),
    "jordan" to Pair("عمّان", "Amman"),
    "morocco" to Pair("الرباط", "Rabat"),
    "algeria" to Pair("الجزائر", "Algiers"),
    "tunisia" to Pair("تونس", "Tunis"),
    "iraq" to Pair("بغداد", "Baghdad"),
    "syria" to Pair("دمشق", "Damascus"),
    "lebanon" to Pair("بيروت", "Beirut"),
    "palestine" to Pair("القدس", "Jerusalem"),
    "yemen" to Pair("صنعاء", "Sana'a"),
    "france" to Pair("باريس", "Paris"),
    "uk" to Pair("لندن", "London"),
    "britain" to Pair("لندن", "London"),
    "japan" to Pair("طوكيو", "Tokyo")
  )

  private val ROOT_AL_WORDS = setOf(
    "الله", "اللهم", "المانيا", "ألمانيا", "البانيا", "ألبانيا",
    "الماس", "ألم", "الم", "الوان", "ألوان", "الهام", "إلهام",
    "اليوم", "الان", "الآن", "الف", "ألف", "اليه", "إليه",
    "اليها", "إليها", "اليهم", "إليهم", "الات", "آلات", "اله", "إله"
  )

  private val PROTECTED_ROOT_WORDS = setOf(
    "باريس", "بغداد", "لبنان", "لندن", "وليد", "برلين", "بروكسل",
    "مدريد", "فرنسا", "تونس", "مسقط", "صلاله", "صلالة", "بومودورو",
    "بطاريه", "بطارية", "بكتيريا", "برمجه", "برمجة", "فيزياء",
    "فلسفه", "فلسفة", "فيتامين", "كافيين", "فواكه", "واشنطن", "وطن"
  )

  private val AMBIGUOUS_HOMOGRAPH_WORDS = setOf(
    "سال", "مهمه", "سنه", "حاله", "عده", "مره", "قوه", "بنه", "حبه"
  )

  private val INITIAL_HAMZA_REGEX = Regex("(?<=^|\\s)[أإآ]")

  /**
   * Strict orthographic normalization: strips diacritics, tatweel, and punctuation,
   * and normalizes word-initial hamzas (أ/إ/آ -> ا) and final ى -> ي,
   * while preserving ة vs ه and medial/final hamzas (e.g., سأل vs سال, مهمة vs مهمه).
   */
  fun normalizeArabicStrict(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(TASHKEEL_AND_TATWEEL_REGEX, "")
      .replace(PUNCTUATION_REGEX, " ")
      .replace(INITIAL_HAMZA_REGEX, "ا")
      .replace('ى', 'ي')
      .replace(WHITESPACE_REGEX, " ")
      .trim()
  }

  fun normalizeArabic(raw: String): String {
    return raw.trim()
      .lowercase(Locale.ROOT)
      .replace(TASHKEEL_AND_TATWEEL_REGEX, "")
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ؤ', 'و')
      .replace('ئ', 'ي')
      .replace(PUNCTUATION_REGEX, " ")
      .replace(WHITESPACE_REGEX, " ")
      .trim()
  }

  /**
   * Conservative Arabic prefix stripping:
   * Strips ONLY definite-article prefixes (وال، بال، كال، فال، لل، ال) when the remaining
   * stem has at least 3 characters and the word is not a root-ال word.
   * Never strips single-letter (و، ب، ل، ف) from bare words, preventing corruption of
   * words like باريس، لبنان، بغداد، لندن، وليد.
   */
  fun stripArabicPrefixes(word: String): String {
    val w = word.trim()
    if (w in ROOT_AL_WORDS) return w
    val definitePrefixes = listOf("وال", "بال", "كال", "فال", "لل", "ال")
    for (p in definitePrefixes) {
      if (w.startsWith(p) && w.length - p.length >= 3) {
        val candidate = w.substring(p.length)
        if (candidate !in ROOT_AL_WORDS || p != "ال") {
          return candidate
        }
      }
    }
    return w
  }

  /**
   * Fast comparison when strict and relaxed tokens are already computed.
   */
  private fun matchPreNormalizedTokens(
    strictQToken: String,
    relQToken: String,
    strictKToken: String,
    relKToken: String
  ): Boolean {
    if (strictQToken == strictKToken) return true
    val strictQ = stripArabicPrefixes(strictQToken)
    val strictK = stripArabicPrefixes(strictKToken)
    if (strictQ.isNotEmpty() && strictQ == strictK) return true

    val relQ = stripArabicPrefixes(relQToken)
    val relK = stripArabicPrefixes(relKToken)
    if (relQ.isEmpty() || relK.isEmpty()) return false

    if (relQ in AMBIGUOUS_HOMOGRAPH_WORDS || relK in AMBIGUOUS_HOMOGRAPH_WORDS) {
      return strictQ == strictK
    }

    if (relQ.length >= 3 && relQ == relK) return true

    if (relQ !in PROTECTED_ROOT_WORDS && strictQ !in PROTECTED_ROOT_WORDS && relK.length >= 4) {
      val firstChar = relQ[0]
      if (relQ.length == relK.length + 1 && (firstChar == 'و' || firstChar == 'ب' || firstChar == 'ل' || firstChar == 'ف')) {
        val afterClitic = relQ.substring(1)
        if (afterClitic == relK && afterClitic !in AMBIGUOUS_HOMOGRAPH_WORDS) {
          return true
        }
      }
    }

    return false
  }

  /**
   * Compares a query token and a keyword token using both strict and relaxed normalization,
   * preserving original words and preventing homograph/prefix false positives.
   */
  fun tokensMatch(queryToken: String, kwToken: String): Boolean {
    val qRaw = queryToken.trim()
    val kRaw = kwToken.trim()
    if (qRaw.isEmpty() || kRaw.isEmpty()) return false
    if (qRaw == kRaw) return true
    return matchPreNormalizedTokens(
      strictQToken = normalizeArabicStrict(qRaw),
      relQToken = normalizeArabic(qRaw),
      strictKToken = normalizeArabicStrict(kRaw),
      relKToken = normalizeArabic(kRaw)
    )
  }

  private fun containsPhraseOnWordBoundary(
    strictQueryTokens: List<String>,
    normQueryTokens: List<String>,
    strictKwTokens: List<String>,
    normKwTokens: List<String>
  ): Boolean {
    if (strictKwTokens.isEmpty() || strictQueryTokens.size < strictKwTokens.size) return false
    if (strictQueryTokens.size != normQueryTokens.size || strictKwTokens.size != normKwTokens.size) {
      return false
    }

    for (i in 0..(strictQueryTokens.size - strictKwTokens.size)) {
      var allMatch = true
      for (j in strictKwTokens.indices) {
        if (!matchPreNormalizedTokens(
            strictQueryTokens[i + j],
            normQueryTokens[i + j],
            strictKwTokens[j],
            normKwTokens[j]
          )
        ) {
          allMatch = false
          break
        }
      }
      if (allMatch) return true
    }
    return false
  }

  /**
   * Detects whether a query carries genuine conversational follow-up / continuity signals.
   */
  fun hasContinuitySignal(rawQuery: String): Boolean {
    val norm = normalizeArabic(rawQuery)
    val tokens = norm.split(" ").filter { it.isNotBlank() }
    val continuityPhrases = listOf(
      "ماذا عن", "وماذا عن", "حدثني اكثر", "اشرح لي", "اشرح اكثر", "تفاصيل اكثر",
      "بالتفصيل", "وضح لي", "اخبرني المزيد", "كيف تتكون", "هل يمكن رؤيتها",
      "افضل وقت لزيارتها", "ولماذا", "وكيف", "ومتي", "ما سببه", "ما سببها",
      "what about", "tell me more", "explain more", "in detail", "about it", "elaborate"
    )
    if (continuityPhrases.any { norm.contains(it) }) return true

    val continuityPronounTokens = setOf(
      "عنه", "عنها", "فيه", "فيها", "هناك", "ذلك", "تلك", "هذا", "هذه",
      "اكثر", "المزيد", "ايضا", "زيارتها", "رؤيتها", "سببه", "سببها",
      "it", "that", "there", "more", "also"
    )
    return tokens.any { it in continuityPronounTokens }
  }

  /**
   * Looks up a topic based on multi-signal scoring (exact phrase match, word-boundary phrase match,
   * order-independent multi-token overlap, multi-keyword specificity, and gated topic continuity).
   */
  fun findMatchingTopic(query: String): KnowledgeTopic? = findMatchingTopic(query, null)

  fun findMatchingTopic(query: String, currentTopicId: String?): KnowledgeTopic? {
    val normQuery = normalizeArabic(query)
    if (normQuery.isBlank()) return null

    val strictQuery = normalizeArabicStrict(query)
    val strictQueryTokens = strictQuery.split(" ").filter { it.isNotBlank() }
    val normQueryTokens = normQuery.split(" ").filter { it.isNotBlank() }
    val contentQueryIndices = normQueryTokens.indices.filter { normQueryTokens[it] !in STOP_WORDS }

    val rawScores = IntArray(topics.size)

    for ((topicIdx, topic) in topics.withIndex()) {
      var topicMaxScore = 0
      var matchedKeywordsCount = 0

      for (kw in topic.keywords) {
        val normKw = normalizeArabic(kw)
        if (normKw.isBlank()) continue
        val strictKw = normalizeArabicStrict(kw)
        val strictKwTokens = strictKw.split(" ").filter { it.isNotBlank() }
        val normKwTokens = normKw.split(" ").filter { it.isNotBlank() }
        val contentKwIndices = normKwTokens.indices.filter { normKwTokens[it] !in STOP_WORDS }.ifEmpty { normKwTokens.indices.toList() }

        var kwScore = 0

        // 1. Exact full query match
        if (strictQuery == strictKw) {
          kwScore = 204 + normKwTokens.size * 30
        } else if (normQuery == normKw && normKw !in AMBIGUOUS_HOMOGRAPH_WORDS) {
          kwScore = 200 + normKwTokens.size * 30
        }
        // 2. Contiguous phrase match on word boundaries (with conservative prefix & homograph awareness)
        else if (containsPhraseOnWordBoundary(strictQueryTokens, normQueryTokens, strictKwTokens, normKwTokens)) {
          val exactStrictBonus = if (strictQuery.contains(strictKw)) 4 else 0
          kwScore = if (normKwTokens.size >= 2) {
            val hasDistinctiveContentToken = contentKwIndices.any { kIdx ->
              val tok = normKwTokens.getOrElse(kIdx) { "" }
              com.example.core.intelligence.knowledge.search.KnowledgeSearchService.isDistinctiveSemanticToken(tok)
            }
            if (hasDistinctiveContentToken) {
              130 + normKwTokens.size * 25 + normKw.length + exactStrictBonus
            } else {
              0
            }
          } else {
            val tokenLen = stripArabicPrefixes(normKwTokens[0]).length
            if (tokenLen <= 2 && normKwTokens[0] != "ai") 40 else 95 + tokenLen + exactStrictBonus
          }
        }
        // 3. Order-independent multi-token match (all content tokens of a multi-word keyword appear in the query)
        else if (contentKwIndices.size >= 2 && contentQueryIndices.isNotEmpty()) {
          val hasDistinctiveContentToken = contentKwIndices.any { kIdx ->
            val tok = normKwTokens.getOrElse(kIdx) { "" }
            com.example.core.intelligence.knowledge.search.KnowledgeSearchService.isDistinctiveSemanticToken(tok)
          }
          val allContentMatched = hasDistinctiveContentToken && contentKwIndices.all { kIdx ->
            val sKwTok = strictKwTokens.getOrElse(kIdx) { normKwTokens[kIdx] }
            val nKwTok = normKwTokens[kIdx]
            contentQueryIndices.any { qIdx ->
              val sQTok = strictQueryTokens.getOrElse(qIdx) { normQueryTokens[qIdx] }
              val nQTok = normQueryTokens[qIdx]
              matchPreNormalizedTokens(sQTok, nQTok, sKwTok, nKwTok)
            }
          }
          if (allContentMatched) {
            kwScore = 115 + contentKwIndices.size * 20
          }
        }

        if (kwScore > 0) {
          matchedKeywordsCount++
          if (kwScore > topicMaxScore) {
            topicMaxScore = kwScore
          }
        }
      }

      // Multi-keyword specificity bonus: topics matching 2+ distinct keywords beat single-word overlaps
      if (topicMaxScore >= 80 && matchedKeywordsCount > 1) {
        topicMaxScore += minOf((matchedKeywordsCount - 1) * 12, 24)
      }

      // Special check for capital city inquiries mentioning any indexed country + "عاصمة"/"capital"
      if (topic.id == "capitals_arab" && (strictQueryTokens.any { tokensMatch(it, "عاصمة") || it == "capital" || it == "capitals" })) {
        val mentionsCountry = COUNTRY_CAPITALS_MAP.keys.any { countryKey ->
          strictQueryTokens.any { qTok -> tokensMatch(qTok, countryKey) }
        }
        if (mentionsCountry && topicMaxScore < 165) {
          topicMaxScore = 165
        }
      }

      rawScores[topicIdx] = topicMaxScore
    }

    // Apply continuity tie-breaker ONLY if the query has a conversational continuity signal
    // AND no competing topic has a clearly stronger match
    val hasContinuity = currentTopicId != null && hasContinuitySignal(query)
    val maxRawScore = rawScores.maxOrNull() ?: 0

    var bestTopic: KnowledgeTopic? = null
    var bestScore = 0

    for ((topicIdx, topic) in topics.withIndex()) {
      var finalScore = rawScores[topicIdx]
      if (finalScore >= 80 && hasContinuity && currentTopicId != null) {
        val isSameTopic = topic.id == currentTopicId ||
          (currentTopicId == "travel" && topic.id == "salalah_tourism") ||
          (currentTopicId == "health_wellness" && topic.id == "sleep_wellness")
        // Only apply tie-breaker when current topic is competitive with the top raw score
        if (isSameTopic && finalScore + 10 >= maxRawScore) {
          finalScore += 12
        }
      }

      if (finalScore > bestScore) {
        bestScore = finalScore
        bestTopic = topic
      }
    }

    return if (bestScore >= 80) bestTopic else null
  }

  /**
   * Returns a specific capital answer if the user asked about a specific country's capital,
   * or null if a general capitals overview is appropriate.
   */
  fun resolveSpecificCapitalAnswer(query: String, isArabic: Boolean): String? {
    val normQuery = normalizeArabic(query)
    val queryTokens = normQuery.split(" ").filter { it.isNotBlank() }
    for ((countryKey, capitalPair) in COUNTRY_CAPITALS_MAP) {
      if (queryTokens.any { tokensMatch(it, countryKey) }) {
        val (arCap, enCap) = capitalPair
        val displayCountry = COUNTRY_CAPITALS_MAP.entries.firstOrNull { it.value == capitalPair && it.key.any { ch -> ch in '\u0600'..'\u06FF' } }?.key ?: countryKey
        return if (isArabic) {
          "عاصمة ${if (displayCountry == "عمان") "سلطنة عُمان" else displayCountry.replace('ه', 'ة')} هي $arCap.\n\nوإليك أبرز العواصم الأخرى:\n- السعودية: الرياض | الإمارات: أبوظبي | مصر: القاهرة | الأردن: عمّان | المغرب: الرباط | فرنسا: باريس | اليابان: طوكيو"
        } else {
          "The capital is $enCap.\n\nOther key capitals:\n- Saudi Arabia: Riyadh | UAE: Abu Dhabi | Egypt: Cairo | Oman: Muscat | France: Paris | Japan: Tokyo"
        }
      }
    }
    return null
  }

  /**
   * Provides contextual follow-up / deep-dive explanations for an active KnowledgeTopic
   * so multi-turn follow-ups ("اشرح لي ذلك", "كيف تتكون؟", "وهل يمكن رؤيتها؟", etc.)
   * receive accurate, non-repetitive answers grounded in the active topic.
   */
  fun getTopicFollowUpElaboration(
    topicId: String,
    normalizedQuery: String,
    isArabic: Boolean
  ): Pair<String, List<ActionableOption>>? {
    val canonicalId = when (topicId) {
      "travel" -> "salalah_tourism"
      "health_wellness" -> "sleep_wellness"
      else -> topicId
    }

    // Check if the follow-up asks about a specific country in capitals_arab
    if (canonicalId == "capitals_arab") {
      val specificCap = resolveSpecificCapitalAnswer(normalizedQuery, isArabic)
      if (specificCap != null) {
        return Pair(
          specificCap,
          listOf(
            ActionableOption(if (isArabic) "حدثني عن صلالة" else "About Salalah", "حدثني عن صلالة"),
            ActionableOption(if (isArabic) "ما حالة الطقس؟" else "Weather", "ما حالة الطقس؟")
          )
        )
      }
    }

    return when (canonicalId) {
      "physics_universe" -> {
        val resolvedAspect = com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog.resolveTopicAspect(
          topicId = "physics_universe",
          query = normalizedQuery,
          isArabic = isArabic
        )
        val text = when {
          normalizedQuery.contains("تتكون") || normalizedQuery.contains("تكونت") || normalizedQuery.contains("تتشكل") || normalizedQuery.contains("نشات") || normalizedQuery.contains("form") -> {
            if (isArabic) {
              "تتكوّن الكواكب والمجموعات الشمسية من سحب عملاقة من الغاز والغبار الكوني تُسمى 'السديم الشمسي'. بفعل الجاذبية، ينهار الجزء الأوسط ليشكّل النجم (مثل الشمس)، بينما تتجمع الذرات والصخور المحيطة في قرص دوّار تتصادم أجزاؤه وتتلاحم عبر ملايين السنين لتكوين الكواكب والأقمار."
            } else {
              "Planets and solar systems form from giant clouds of cosmic gas and dust called a solar nebula. Gravity pulls the center together to ignite a star (our Sun), while surrounding dust and rock accrete over millions of years into orbiting planets and moons."
            }
          }
          normalizedQuery.contains("رويتها") || normalizedQuery.contains("رؤيتها") || normalizedQuery.contains("نراها") || normalizedQuery.contains("مشاهدتها") || normalizedQuery.contains("بالعين") || normalizedQuery.contains("see them") || normalizedQuery.contains("visible") -> {
            if (isArabic) {
              "نعم، يمكن رؤية 5 من كواكب المجموعة الشمسية بالعين المجردة من الأرض في الليالي الصافية دون تلسكوب، وهي: عطارد، الزهرة (ألمعها في السماء بعد القمر)، المريخ (يتميز بلونه المائل للأحمر)، المشتري، وزحل. أما أورانوس ونبتون فيتطلبان تلسكوباً لرؤيتهما."
            } else {
              "Yes, five planets in our solar system can be seen with the naked eye under clear night skies without a telescope: Mercury, Venus (the brightest), Mars (with its reddish hue), Jupiter, and Saturn. Uranus and Neptune require binoculars or a telescope."
            }
          }
          resolvedAspect != null && (resolvedAspect.aspectId == "gravity_solar_system" || resolvedAspect.aspectId == "gravity" || resolvedAspect.aspectId == "stars") -> {
            resolvedAspect.responseText
          }
          normalizedQuery.contains("شمس") || normalizedQuery.contains("دقائق") || normalizedQuery.contains("وصول") || normalizedQuery.contains("sunlight") -> {
            if (isArabic) {
              "يبعد كوكب الأرض عن الشمس حوالي 149.6 مليون كيلومتر، وبما أن الضوء يسير بسرعة 300,000 كم/ثانية، فإن الفوتونات المنبعثة من سطح الشمس تستغرق 8 دقائق و20 ثانية تقريباً لتصل إلينا."
            } else {
              "Earth orbits at an average distance of about 149.6 million kilometers from the Sun. Traveling at 300,000 km/s, sunlight takes roughly 8 minutes and 20 seconds to reach Earth."
            }
          }
          resolvedAspect != null -> {
            resolvedAspect.responseText
          }
          else -> {
            if (isArabic) {
              "توضيح إضافي حول الفيزياء والكون: سرعة الضوء هي الحد الأقصى للسرعة في الكون وفق النسبية الخاصة، ولذلك يقيس العلماء المسافات الكونية بـ 'السنة الضوئية' (وهي المسافة التي يقطعها الضوء في سنة وتساوي نحو 9.46 تريليون كيلومتر). كما أن جاذبية الشمس تشكّل 99.8% من كتلة مجموعتنا الشمسية وهي التي تضبط حركة الكواكب."
            } else {
              "Further cosmic insight: The speed of light is the universal speed limit according to special relativity. Astronomers measure vast cosmic distances in 'light-years' (~9.46 trillion km). Meanwhile, the Sun holds 99.8% of our solar system's mass, governing planetary orbits through gravity."
            }
          }
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "هل يمكن رؤية الكواكب؟" else "Are planets visible?", "وهل يمكن رؤيتها؟"),
            ActionableOption(if (isArabic) "احسب المسافة في دقيقة" else "Distance in 1 min", "احسب 300000 * 60")
          )
        )
      }

      "salalah_tourism" -> {
        val text = when {
          normalizedQuery.contains("طقس") || normalizedQuery.contains("جو") || normalizedQuery.contains("مطر") || normalizedQuery.contains("ملابس") || normalizedQuery.contains("weather") || normalizedQuery.contains("cold") -> {
            if (isArabic) {
              "في صلالة خلال موسم الخريف، يكون الطقس معتدلاً ومائلاً للبرودة في الجبال (~22-25°م) مع رذاذ وضباب مستمر. يُنصح بإحضار ملابس خفيفة مع سترة مقاومة للرطوبة وأحذية مريحة للمشي في الطبيعة."
            } else {
              "In Salalah during the Khareef season, the weather is pleasantly mild and mist-covered (approx 22°C), cooler in mountain areas. Pack light layers and a rain-resistant jacket."
            }
          }
          normalizedQuery.contains("متي") || normalizedQuery.contains("وقت") || normalizedQuery.contains("شهر") || normalizedQuery.contains("when") || normalizedQuery.contains("زيارتها") || normalizedQuery.contains("لزياره") -> {
            if (isArabic) {
              "أفضل وقت لزيارة صلالة للاستمتاع بالخضرة والضباب هو من منتصف يوليو حتى نهاية أغسطس، حيث يكون الخريف في ذروته."
            } else {
              "The best time to visit Salalah for lush greenery is between July and August during the peak of Khareef season."
            }
          }
          normalizedQuery.contains("تتكون") || normalizedQuery.contains("سبب") || normalizedQuery.contains("لماذا") -> {
            if (isArabic) {
              "تتشكل ظاهرة خريف صلالة نتيجة هبوب الرياح الموسمية الجنوبية الغربية القادمة من المحيط الهندي وبحر العرب، فعندما تصطدم بسلسلة جبال ظفار ترتفع وتبرد مكونةً السحب المنخفضة والضباب والرذاذ اليومي الذي يكسو الجبال بالخضرة."
            } else {
              "Salalah's Khareef monsoon forms when moist southwest winds from the Indian Ocean and Arabian Sea hit the Dhofar mountain range, cooling into persistent mist and light drizzle that turns the landscape lush green."
            }
          }
          else -> {
            if (isArabic) {
              "من أبرز التجارب في صلالة: زيارة شلالات وادي دربات والتجول بالقوارب، مشاهدة النوافير الطبيعية في شاطئ المغسيل (كهف المرنيف)، زيارة العيون المائية مثل عين رزات وعين أثوم، وتذوق جوز الهند الطازج والفواكه الاستوائية المحلية."
            } else {
              "Highlights in Salalah include boating near Wadi Darbat waterfalls, seeing the natural blowholes at Mughsail Beach (Marneef Cave), visiting natural springs like Ayn Razat and Ayn Athum, and enjoying fresh local coconut."
            }
          }
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "متى أفضل وقت لزيارتها؟" else "Best time to visit?", "متى أفضل وقت لزيارة صلالة؟"),
            ActionableOption(if (isArabic) "طقس منطقتي الآن" else "Local weather", "ما حالة الطقس؟")
          )
        )
      }

      "sleep_wellness" -> {
        val text = if (normalizedQuery.contains("قهوه") || normalizedQuery.contains("كافيين") || normalizedQuery.contains("شاي") || normalizedQuery.contains("coffee") || normalizedQuery.contains("caffeine")) {
          if (isArabic) {
            "الكافيين يبقى نصف عمره في الجسم لمدة 6 إلى 8 ساعات. لذلك يُفضل التوقف عن شرب القهوة بعد الساعة 2 ظهراً لضمان عدم تأثيره على مراحل النوم العميق."
          } else {
            "Caffeine has a half-life of 6 to 8 hours. It is best to avoid coffee after 2 PM to protect deep sleep stages."
          }
        } else {
          if (isArabic) {
            "يمر النوم الصحي بعدة دورات (كل دورة ~90 دقيقة) تشمل النوم الخفيف، النوم العميق لإصلاح الأنسجة وتقوية المناعة، ومرحلة حركة العين السريعة (REM) لتثبيت الذاكرة. يحتاج البالغون عادةً من 7 إلى 8 ساعات يومياً، والتعرض لضوء الصباح الطبيعي يساعد على ضبط إفراز هرمون الميلاتونين مساءً."
          } else {
            "Healthy sleep cycles through ~90-minute stages of light sleep, deep restorative sleep, and REM sleep for memory consolidation. Adults typically need 7 to 8 hours, and morning daylight helps regulate evening melatonin production."
          }
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "تأثير القهوة على النوم" else "Coffee & sleep", "تأثير القهوة على النوم"),
            ActionableOption(if (isArabic) "خفض الصوت للهدوء" else "Lower volume", "اخفض الصوت")
          )
        )
      }

      "productivity" -> {
        val text = if (isArabic) {
          "نصيحة إضافية للتركيز: تجنب تعدد المهام (Multitasking). أظهرت الدراسات أن التركيز على مهمة واحدة يرفع الكفاءة بنسبة 40% ويقلل الإجهاد الذهني، كما أن تفعيل وضع عدم الإزعاج وإبعاد الهاتف أثناء جلسة بومودورو (25 دقيقة) يضاعف سرعة الإنجاز."
        } else {
          "Additional tip: Avoid multitasking. Research indicates single-task focus boosts efficiency by up to 40% while lowering cognitive strain. Silencing notifications during a 25-minute Pomodoro block dramatically improves deep work."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "هل لدي مواعيد اليوم؟" else "Check schedule", "هل سأصل في الموعد؟"),
            ActionableOption(if (isArabic) "فحص البطارية" else "Check battery", "كم نسبة البطارية؟")
          )
        )
      }

      "ai_concept" -> {
        val text = if (isArabic) {
          "يعمل الذكاء المحلي (On-Device AI) في WEYKY عبر تحليل نصوصك وسياق الهاتف مباشرة داخل معالج جهازك دون إرسال أي بيانات إلى الإنترنت. هذا يمنحك استجابة فورية بالملي ثانية، عمل مستمر حتى في وضع الطيران، وخصوصية مطلقة لمحادثاتك."
        } else {
          "On-Device AI in WEYKY processes your queries and device telemetry directly on your phone's processor without cloud calls. This ensures millisecond latency, full offline availability, and complete data privacy."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "ماذا تستطيع أن تفعل؟" else "What can you do?", "ماذا تستطيع ان تفعل؟"),
            ActionableOption(if (isArabic) "فحص ذاكرة الجهاز" else "Check RAM", "كم الذاكرة المتبقية؟")
          )
        )
      }

      "hydration_health" -> {
        val text = if (isArabic) {
          "لتوزيع شرب الماء بذكاء خلال اليوم: ابدأ بكوب ماء عند الاستيقاظ لتنشيط الدورة الدموية، واشرب كوباً قبل كل وجبة بـ 30 دقيقة، وزد الكمية عند ارتفاع حرارة الطقس أو ممارسة الرياضة لأن فقدان 2% فقط من سوائل الجسم قد يسبب الصداع وضعف التركيز."
        } else {
          "To stay optimally hydrated: start with a glass of water upon waking, drink water 30 minutes before meals, and increase intake in hot weather or during exercise, as even 2% dehydration can trigger headaches and fatigue."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "فحص حرارة الطقس" else "Check weather", "ما حالة الطقس؟"),
            ActionableOption(if (isArabic) "نصائح للنوم" else "Sleep tips", "نصائح للنوم")
          )
        )
      }

      "battery_care" -> {
        val resolvedAspect = com.example.core.intelligence.knowledge.catalog.SeedKnowledgeCatalog.resolveTopicAspect(
          topicId = "battery_care",
          query = normalizedQuery,
          isArabic = isArabic
        )
        val text = resolvedAspect?.responseText ?: if (isArabic) {
          "تعمل بطاريات الليثيوم أيون بكفاءة أعلى عندما لا تتعرض للجهد الأقصى (100%) أو التفريغ التام (0%)، لأن البقاء طويلاً عند 100% مع حرارة مرتفعة يسرّع تآكل الخلايا الكيميائية. لذلك فإن الشحن المتقطع بين 20% و80% واستخدام شاحن موثوق يطيل عمر البطارية لسنوات."
        } else {
          "Lithium-ion batteries experience the least chemical stress between 20% and 80% charge. Prolonged heat combined with 100% voltage accelerates capacity degradation, so avoiding overheating while charging preserves long-term battery health."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "كم نسبة بطاريتي الآن؟" else "Battery level", "كم نسبة البطارية؟"),
            ActionableOption(if (isArabic) "هل البطارية ستكفي؟" else "Will battery last?", "هل البطارية ستكفي؟")
          )
        )
      }

      "short_story" -> {
        val text = if (isArabic) {
          "المغزى العميق من القصة هو أن المعرفة والنجاح ليسا لعبة صفرية؛ فعندما تساهم في رفع جودة بيئتك ومساعدة من حولك، ينعكس ذلك إيجاباً على جودة حياتك ونجاحك الشخصي."
        } else {
          "The deeper takeaway is that success and wisdom are not zero-sum games; elevating the community around you naturally enriches your own environment and growth."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "نصائح للإنتاجية" else "Productivity tips", "تنظيم الوقت"),
            ActionableOption(if (isArabic) "حقائق عن الكون" else "Cosmic facts", "سرعة الضوء")
          )
        )
      }

      "capitals_arab" -> {
        val text = if (isArabic) {
          "تُعد العواصم المراكز الإدارية والثقافية للدول؛ فمثلاً القاهرة هي أكبر عاصمة عربية سكاناً، والرياض تقع في قلب الجزيرة العربية، ومسقط تجمع بين التراث البحري العريق والجبال الساحلية. يمكنك سؤالي عن عاصمة أي دولة عربية أو عالمية محددة!"
        } else {
          "Capitals serve as cultural and administrative hubs—for example, Cairo is the most populous Arab capital, Riyadh sits at the heart of the Arabian Peninsula, and Muscat blends maritime heritage with coastal mountains."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "حدثني عن صلالة" else "About Salalah", "حدثني عن صلالة"),
            ActionableOption(if (isArabic) "ما حالة الطقس؟" else "Weather", "ما حالة الطقس؟")
          )
        )
      }

      "identity", "capabilities" -> {
        val text = if (isArabic) {
          "صُمم WEYKY ليعمل كنواة ذكية متكاملة على جهازك دون الحاجة لأي اتصال سحابي؛ فهو يجمع بين تنفيذ الأوامر الفورية، قراءة سياق الهاتف (البطارية، الطقس، المواعيد)، والإجابة عن أسئلتك المعرفية والحسابية."
        } else {
          "WEYKY is architected as a self-contained on-device intelligence core, combining instant hardware control, situational awareness (battery, weather, calendar), and offline knowledge reasoning."
        }
        Pair(
          text,
          listOf(
            ActionableOption(if (isArabic) "ما حالة هاتفي الآن؟" else "Device status", "حالة الجهاز"),
            ActionableOption(if (isArabic) "سرعة الضوء" else "Speed of light", "ما هي سرعة الضوء؟")
          )
        )
      }

      else -> null
    }
  }

  private fun normalizeDigits(raw: String): String {
    val sb = StringBuilder(raw.length)
    for (ch in raw) {
      when (ch) {
        in '٠'..'٩' -> sb.append(('0'.code + (ch.code - '٠'.code)).toChar())
        in '۰'..'۹' -> sb.append(('0'.code + (ch.code - '۰'.code)).toChar())
        '٫' -> sb.append('.')
        else -> sb.append(ch)
      }
    }
    return sb.toString()
  }

  /**
   * Evaluates arithmetic expressions locally with Arabic/Indic digit support,
   * multi-operator precedence (* and / before + and -), division-by-zero protection,
   * and guards against hijacking non-math sentences containing dates or hyphenated numbers.
   * e.g. "احسب 25 * 4", "كم يساوي 15 + 30", "120 / 6", "10 + 5 * 2", "٢٥ × ٤"
   */
  fun evaluateArithmetic(raw: String): String? {
    val digitNormalized = normalizeDigits(raw).trim()
    if (digitNormalized.isBlank()) return null

    val hasExplicitMathTrigger = digitNormalized.contains("احسب") ||
      digitNormalized.contains("إحسب") ||
      digitNormalized.contains("كم يساوي") ||
      digitNormalized.contains("كم تساوي") ||
      digitNormalized.contains("ما ناتج") ||
      digitNormalized.contains("كم ناتج") ||
      digitNormalized.contains("حاصل") ||
      digitNormalized.contains("زائد") ||
      digitNormalized.contains("ناقص") ||
      digitNormalized.contains("ضرب") ||
      digitNormalized.contains("قسمة") ||
      digitNormalized.contains("قسمه") ||
      digitNormalized.contains("تقسيم") ||
      digitNormalized.contains("+") ||
      digitNormalized.contains("*") ||
      digitNormalized.contains("×") ||
      digitNormalized.contains("÷") ||
      digitNormalized.contains("calculate", ignoreCase = true) ||
      digitNormalized.contains("plus", ignoreCase = true) ||
      digitNormalized.contains("minus", ignoreCase = true) ||
      digitNormalized.contains("multiplied", ignoreCase = true) ||
      digitNormalized.contains("divided", ignoreCase = true)

    val clean = digitNormalized
      .replace(Regex("[\\u064B-\\u065F\\u0670\\u0640]"), "")
      .replace("أحسب", "")
      .replace("إحسب", "")
      .replace("احسب", "")
      .replace("كم يساوي", "")
      .replace("كم تساوي", "")
      .replace("ما هو ناتج", "")
      .replace("ما ناتج", "")
      .replace("كم ناتج", "")
      .replace("حاصل", "")
      .replace("calculate", "", ignoreCase = true)
      .replace("what is", "", ignoreCase = true)
      .replace("math", "", ignoreCase = true)
      .replace("multiplied by", "*", ignoreCase = true)
      .replace("divided by", "/", ignoreCase = true)
      .replace("plus", "+", ignoreCase = true)
      .replace("minus", "-", ignoreCase = true)
      .replace("times", "*", ignoreCase = true)
      .replace("ضرب", "*")
      .replace("في", "*")
      .replace("×", "*")
      .replace("زائد", "+")
      .replace("جمع", "+")
      .replace("ناقص", "-")
      .replace("طرح", "-")
      .replace("قسمة", "/")
      .replace("قسمه", "/")
      .replace("تقسيم", "/")
      .replace("على", "/")
      .replace("علي", "/")
      .replace("÷", "/")
      .replace(Regex("(?<=\\d\\s?)x(?=\\s?\\d)", RegexOption.IGNORE_CASE), "*")
      .replace(Regex("[؟?=!]"), "")
      .trim()

    // Must consist ONLY of numbers, operators, parentheses, and whitespace after stripping math triggers.
    // This prevents sentences like "تاريخ 2024/10" or "قاعدة 20-20-20 للعين" from being hijacked as math!
    val validMathCharsRegex = Regex("""^[\d.\s+\-*/()]+$""")
    if (!validMathCharsRegex.matches(clean)) {
      return null
    }

    val isArabic = raw.any { it in '\u0600'..'\u06FF' } || Locale.getDefault().language.startsWith("ar")

    // Do not strip parentheses silently and risk violating mathematical precedence (e.g. "(10 + 5) * 2").
    // Document and report the boundary honestly if parentheses are present.
    if (clean.contains("(") || clean.contains(")")) {
      return if (hasExplicitMathTrigger) {
        if (isArabic) {
          "المحلل الحسابي المحلي يدعم العمليات المباشرة (+، -، *، /) مع أولوية الضرب والقسمة، ولا يدعم الأقواس المتداخلة حالياً."
        } else {
          "The local arithmetic evaluator supports direct operations (+, -, *, /) with operator precedence, and does not currently support parenthesized expressions."
        }
      } else {
        null
      }
    }

    // Must contain at least one binary operator between two numbers
    val tokenRegex = Regex("""(-?\d+(?:\.\d+)?)|([+\-*/])""")
    val rawTokens = tokenRegex.findAll(clean).map { it.value }.toList()
    if (rawTokens.size < 3) return null

    // Validate alternating number / operator structure
    val numbers = mutableListOf<Double>()
    val operators = mutableListOf<String>()
    val displayParts = mutableListOf<String>()

    var idx = 0
    var expectNumber = true
    while (idx < rawTokens.size) {
      val tok = rawTokens[idx]
      if (expectNumber) {
        val num = tok.toDoubleOrNull() ?: return null
        numbers.add(num)
        displayParts.add(tok)
        expectNumber = false
      } else {
        if (tok !in listOf("+", "-", "*", "/")) return null
        operators.add(tok)
        displayParts.add(tok)
        expectNumber = true
      }
      idx++
    }
    // If expression ends with a trailing operator (e.g. "25 *"), it's invalid
    if (expectNumber || operators.isEmpty() || numbers.size != operators.size + 1) {
      return null
    }

    // Prevent date-like or range-like strings (e.g. "2024/10/15" or "20-20-20") when no math trigger was used
    if (!hasExplicitMathTrigger && operators.size >= 2 && operators.all { it == "-" || it == "/" }) {
      return null
    }

    // Pass 1: Evaluate * and / (left-to-right)
    val addNums = mutableListOf<Double>()
    val addOps = mutableListOf<String>()
    var currentVal = numbers[0]

    for (i in operators.indices) {
      val op = operators[i]
      val nextVal = numbers[i + 1]
      when (op) {
        "*" -> currentVal *= nextVal
        "/" -> {
          if (nextVal == 0.0) {
            return if (isArabic) "لا يمكن القسمة على صفر." else "Cannot divide by zero."
          }
          currentVal /= nextVal
        }
        "+", "-" -> {
          addNums.add(currentVal)
          addOps.add(op)
          currentVal = nextVal
        }
      }
    }
    addNums.add(currentVal)

    // Pass 2: Evaluate + and - (left-to-right)
    var res = addNums[0]
    for (i in addOps.indices) {
      val op = addOps[i]
      val nextVal = addNums[i + 1]
      when (op) {
        "+" -> res += nextVal
        "-" -> res -= nextVal
      }
    }

    val formattedResult = if (res == res.toLong().toDouble()) {
      res.toLong().toString()
    } else {
      String.format(Locale.US, "%.2f", res)
    }

    val expressionStr = displayParts.joinToString(" ")
    return if (isArabic) {
      "الناتج الحسابي: $expressionStr = $formattedResult"
    } else {
      "Result: $expressionStr = $formattedResult"
    }
  }
}

