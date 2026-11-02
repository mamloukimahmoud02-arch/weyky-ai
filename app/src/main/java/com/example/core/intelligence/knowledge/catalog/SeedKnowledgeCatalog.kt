package com.example.core.intelligence.knowledge.catalog

import com.example.core.intelligence.conversation.ActionableOption
import com.example.core.intelligence.conversation.LocalKnowledgeBase
import com.example.core.intelligence.knowledge.db.converter.KnowledgeTypeConverters
import com.example.core.intelligence.knowledge.db.dao.KeywordWithTokensBundle
import com.example.core.intelligence.knowledge.db.dao.TopicStorageBundle
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeKeywordTokenEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgePackMetadataEntity
import com.example.core.intelligence.knowledge.db.entity.KnowledgeTopicHeaderEntity
import java.security.MessageDigest

/**
 * Immutable catalog entry representing one of WEYKY's 11 built-in core topics.
 *
 * Preserves the exact fields, order, keywords, summaries, and suggested follow-ups from
 * [LocalKnowledgeBase] without renaming or merging any topic.
 */
data class SeedTopicCatalogEntry(
    val topicId: String,
    val topicOrderIndex: Int,
    val keywords: List<String>,
    val arabicSummary: String,
    val englishSummary: String,
    val suggestedFollowUps: List<ActionableOption>
) {
    /**
     * Converts this seed entry into the existing 5-field [LocalKnowledgeBase.KnowledgeTopic].
     */
    fun toLegacyKnowledgeTopic(): LocalKnowledgeBase.KnowledgeTopic =
        LocalKnowledgeBase.KnowledgeTopic(
            id = topicId,
            keywords = keywords,
            arabicSummary = arabicSummary,
            englishSummary = englishSummary,
            suggestedFollowUps = suggestedFollowUps
        )
}

/**
 * Canonical built-in seed catalog for WEYKY's 11 core topics and 37 country-capital entries.
 *
 * The single source of truth for this catalog's content is [LocalKnowledgeBase] (`lines 22–215`).
 * Never modifies or replaces [LocalKnowledgeBase].
 */
object SeedKnowledgeCatalog {

    const val CORE_SEED_PACK_ID: String = "core_seed_pack"
    const val CORE_SEED_SOURCE_TYPE: String = "SEED_BUILTIN"
    const val CORE_SEED_PACK_VERSION: Int = 1
    const val CORE_SEED_SCHEMA_VERSION: Int = 1
    const val CORE_SEED_PACK_PRIORITY: Int = 0
    const val CORE_SEED_CONTENT_VERSION: Int = 1

    /**
     * Exact stop-word set from [LocalKnowledgeBase] (`lines 170–175`).
     */
    val STOP_WORDS: Set<String> = setOf(
        "ما", "ماذا", "هو", "هي", "هم", "هل", "كيف", "متي", "اين", "لماذا", "كم",
        "عن", "في", "من", "الي", "علي", "على", "مع", "لي", "لك", "ان", "إن",
        "حدثني", "اخبرني", "اعطني", "اريد", "ابغى", "ممكن", "لو", "سمحت", "بعض",
        "what", "is", "are", "the", "a", "an", "about", "tell", "me", "how", "to", "of", "in", "for", "on", "can", "you"
    )

    /**
     * Exact 37-entry country-to-capital map from [LocalKnowledgeBase] (`lines 177–215`).
     */
    val COUNTRY_CAPITALS_MAP: Map<String, Pair<String, String>> = linkedMapOf(
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

    /**
     * Exact 11 core topics in their exact declaration order (`0..10`) from
     * [LocalKnowledgeBase] (`lines 22–164`).
     */
    val coreTopics: List<SeedTopicCatalogEntry> = listOf(
        // [0] Identity & WEYKY purpose
        SeedTopicCatalogEntry(
            topicId = "identity",
            topicOrderIndex = 0,
            keywords = listOf("من انت", "من أنت", "عرفني بنفسك", "ما هو ويكي", "ما هو وايكي", "who are you", "what are you", "اسمك", "ما هي weyky", "ما هو weyky", "حدثني عن weyky"),
            arabicSummary = "أنا وايكي، النواة الذكية الحية لهاتفك. أعمل محلياً بالكامل لفهم سياقك اليومي، التحكم في وظائف الجهاز، مساعدتك في اتخاذ القرارات اليومية، والتحاور معك بذكاء وخصوصية تامة.",
            englishSummary = "I am Weyky, your contextual intelligent assistant on this device. I work locally to help you understand your daily context, manage your device, reason together, and provide helpful insights.",
            suggestedFollowUps = listOf(
                ActionableOption("ماذا تستطيع أن تفعل؟", "ماذا تستطيع ان تفعل؟"),
                ActionableOption("ما حالة هاتفي الآن؟", "حالة الجهاز"),
                ActionableOption("هل أخرج الآن؟", "هل اخرج الان؟")
            )
        ),
        // [1] Capabilities
        SeedTopicCatalogEntry(
            topicId = "capabilities",
            topicOrderIndex = 1,
            keywords = listOf("ماذا تستطيع", "قدراتك", "مميزاتك", "شو بتعرف", "what can you do", "features", "كيف تساعدني", "مهامك"),
            arabicSummary = "يمكنني مساعدتك في عدة مجالات:\n1. التحكم الفوري بالهاتف (فتح التطبيقات، الكشاف، الصوت، الموسيقى، المكالمات).\n2. الذكاء السياقي (تحليل الطقس، البطارية، المواعيد، ونصيحة الخروج).\n3. الحوار المعرفي والمنطقي (العلوم، الجغرافيا، الحساب، تنظيم الوقت، والنصائح اليومية) بدون إنترنت.",
            englishSummary = "I can assist you with:\n1. Device controls (apps, flashlight, audio volume, media, calls).\n2. Real-time situational analysis (weather, battery, appointments, departure advice).\n3. Conversational reasoning, calculations, productivity habits, and answering diverse knowledge questions completely offline.",
            suggestedFollowUps = listOf(
                ActionableOption("فحص الطقس", "ما حالة الطقس؟"),
                ActionableOption("فحص البطارية", "كم نسبة البطارية؟"),
                ActionableOption("احسب 25 * 4", "احسب 25 * 4")
            )
        ),
        // [2] Sleep & Health
        SeedTopicCatalogEntry(
            topicId = "sleep_wellness",
            topicOrderIndex = 2,
            keywords = listOf("النوم", "نوم", "ارق", "أرق", "كيف انام", "نصائح للنوم", "sleep", "insomnia", "راحة", "الاستيقاظ"),
            arabicSummary = "للحصول على نوم عميق وصحي ينصح الخبراء بـ:\n1. تثبيت موعد النوم والاستيقاظ يومياً لضبط الساعة البيولوجية.\n2. الابتعاد عن شاشات الهواتف والضوء الأزرق قبل النوم بـ 30 دقيقة.\n3. الحفاظ على برودة الغرفة وهدوئها (~18-20 درجة مئوية).\n4. تجنب الكافيين والوجبات الثقيلة في المساء.",
            englishSummary = "To achieve deep and restorative sleep, experts recommend:\n1. Keeping a consistent bedtime and wake-up schedule.\n2. Avoiding blue light screens at least 30 minutes before sleep.\n3. Keeping your bedroom cool and dark (around 18-20°C).\n4. Avoiding caffeine and heavy meals late in the evening.",
            suggestedFollowUps = listOf(
                ActionableOption("تأثير القهوة على النوم", "تأثير القهوة على النوم"),
                ActionableOption("إطفاء الكشاف", "اطفئ الكشاف"),
                ActionableOption("خفض الصوت للهدوء", "اخفض الصوت")
            )
        ),
        // [3] Productivity & Time Management (Pomodoro etc.)
        SeedTopicCatalogEntry(
            topicId = "productivity",
            topicOrderIndex = 3,
            keywords = listOf("تنظيم الوقت", "إدارة الوقت", "التركيز", "الانتاجية", "الإنتاجية", "productivity", "time management", "pomodoro", "بومودورو", "كسل", "تأجيل", "تسويف", "دراسة", "مذاكرة"),
            arabicSummary = "لرفع الإنتاجية والتركيز وتجنب التسويف:\n1. تقنية بومودورو: اعمل بتركيز تام لمدة 25 دقيقة ثم خذ استراحة 5 دقائق.\n2. قاعدة البدء بالأصعب (Eat the Frog): أنجز المهمة الأهم في بداية يومك.\n3. مصفوفة أيزنهاور: قسّم مهامك حسب الأهمية والاستعجال وتخلص من المشتتات.\n4. تقسيم الأهداف الكبيرة إلى خطوات صغيرة قابلة للتنفيذ فوراً.",
            englishSummary = "To boost your productivity and focus:\n1. The Pomodoro Technique: 25 minutes of deep work followed by a 5-minute break.\n2. Eat That Frog: Tackle your most demanding task first thing in the morning.\n3. Eisenhower Matrix: Distinguish between urgent and important, eliminate distractions.\n4. Break large projects into bite-sized, immediate action steps.",
            suggestedFollowUps = listOf(
                ActionableOption("هل لدي مواعيد اليوم؟", "هل سأصل في الموعد؟"),
                ActionableOption("كم الساعة الآن؟", "كم الساعة؟"),
                ActionableOption("نصيحة إضافية للتركيز", "نصيحة للتركيز")
            )
        ),
        // [4] Artificial Intelligence & Local Processing
        SeedTopicCatalogEntry(
            topicId = "ai_concept",
            topicOrderIndex = 4,
            keywords = listOf("الذكاء الاصطناعي", "ai", "artificial intelligence", "تعلم الآلة", "الشبكات العصبية", "machine learning", "كيف تفكر", "كيف تعمل"),
            arabicSummary = "الذكاء الاصطناعي هو قدرة الأنظمة الحاسوبية على محاكاة القدرات الذهنية البشرية مثل الفهم، الاستنتاج، والتعلم من السياق. في WEYKY، نعتمد على الذكاء المدمج محلياً على الجهاز (On-Device Intelligence) لضمان السرعة الفورية وحماية خصوصية بياناتك بالكامل دون الحاجة لإرسالها إلى خوادم خارجية.",
            englishSummary = "Artificial Intelligence simulates human cognitive capabilities such as learning, deduction, and natural language comprehension. In WEYKY, contextual reasoning and on-device knowledge are integrated locally for maximum privacy and instant speed without relying on external servers.",
            suggestedFollowUps = listOf(
                ActionableOption("فحص ذاكرة الجهاز", "كم الذاكرة المتبقية؟"),
                ActionableOption("حالة السياق الحالي", "ما هو السياق؟")
            )
        ),
        // [5] Geography: Capitals & Countries
        SeedTopicCatalogEntry(
            topicId = "capitals_arab",
            topicOrderIndex = 5,
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
        // [6] Salalah & Tourism / Seasonal Travel
        SeedTopicCatalogEntry(
            topicId = "salalah_tourism",
            topicOrderIndex = 6,
            keywords = listOf("صلالة", "صلاله", "خريف صلالة", "ظفار", "سياحة عمان", "وادي دربات", "المغسيل", "salalah", "dhofar", "khareef"),
            arabicSummary = "صلالة هي حاضرة محافظة ظفار في سلطنة عُمان، وتشتهر بموسم 'الخريف' (من أواخر يونيو إلى سبتمبر) حيث تتحول الجبال والسهول إلى بساط أخضر ساحر مع ضباب ورذاذ منعش ودرجات حرارة معتدلة (~25°م) بينما يكون الصيف حاراً في بقية المنطقة. من أبرز معالمها: وادي دربات، عين أثوم، وشاطئ المغسيل.",
            englishSummary = "Salalah is the jewel of Dhofar, Oman. It is famous for the 'Khareef' monsoon season (late June to September), when the mountains turn lush green under refreshing mist and cool temperatures around 25°C. Top attractions include Wadi Darbat, Ayn Athum, and Mughsail Beach.",
            suggestedFollowUps = listOf(
                ActionableOption("ما أفضل وقت لزيارتها؟", "متى أفضل وقت لزيارة صلالة؟"),
                ActionableOption("طقس منطقتي الآن", "ما حالة الطقس؟")
            )
        ),
        // [7] Science: Speed of light, Solar System, Gravity
        SeedTopicCatalogEntry(
            topicId = "physics_universe",
            topicOrderIndex = 7,
            keywords = listOf(
                "سرعة الضوء", "ضوء الشمس", "السنة الضوئية", "المجموعة الشمسية", "النظام الشمسي",
                "الجاذبية", "الفضاء", "الكواكب", "كواكب", "المجرة", "الكون",
                "speed of light", "solar system", "gravity", "planets", "universe"
            ),
            arabicSummary = "حقائق علمية كونية:\n- سرعة الضوء في الفراغ تبلغ حوالي 300,000 كيلومتر في الثانية (299,792 كم/ث).\n- يستغرق ضوء الشمس حوالي 8 دقائق و20 ثانية للوصول إلى الأرض.\n- تتكون مجموعتنا الشمسية من 8 كواكب، أكبرها المشتري وأقربها للشمس عطارد.\n- الجاذبية هي القوة التي تحفظ الكواكب في مداراتها وتمنح الأجسام أوزانها.",
            englishSummary = "Fascinating cosmic facts:\n- The speed of light in a vacuum is approximately 300,000 km/s (299,792 km/s).\n- Sunlight takes about 8 minutes and 20 seconds to reach Earth.\n- Our solar system contains 8 recognized planets, Jupiter being the largest and Mercury closest to the Sun.\n- Gravity is the fundamental attraction keeping planets in orbit and holding our atmosphere.",
            suggestedFollowUps = listOf(
                ActionableOption("احسب المسافة في دقيقة", "احسب 300000 * 60"),
                ActionableOption("احكِ لي قصة قصيرة", "احك لي قصة قصيرة")
            )
        ),
        // [8] Short Inspirational Story / Wisdom
        SeedTopicCatalogEntry(
            topicId = "short_story",
            topicOrderIndex = 8,
            keywords = listOf("قصة", "احك لي قصة", "قصة قصيرة", "حكمة", "موعظة", "tell me a story", "short story", "طرفة", "الهام", "إلهام"),
            arabicSummary = "قصة قصيرة ملهمة: يُحكى أن مزارعاً كان يفوز دائماً بجائزة أفضل محصول ذرة، وكان يوزع أفضل بذوره على جيرانه المزارعين! وعندما سُئل عن السبب قال: 'الرياح تنقل حبوب اللقاح بين الحقول، فإذا زرع جيراني ذرة رديئة ستؤثر على جودة محصولي. لكي أحصل على أفضل حصاد، يجب أن أساعد جيراني على زراعة الأفضل أيضاً'. الحكمة: نجاحك الحقيقي يزدهر عندما تساعد من حولك على النجاح.",
            englishSummary = "A wise farmer was asked why he always shared his finest seeds with his neighbors. He smiled and said: 'The wind carries pollen from field to field. If my neighbors grow inferior grain, cross-pollination will degrade my own crops. By helping them succeed, I enrich my harvest.' Lesson: True excellence thrives when everyone around us prospers.",
            suggestedFollowUps = listOf(
                ActionableOption("نصائح للإنتاجية", "تنظيم الوقت"),
                ActionableOption("نصائح للنوم الصحي", "نصائح للنوم")
            )
        ),
        // [9] Healthy Habits & Water
        SeedTopicCatalogEntry(
            topicId = "hydration_health",
            topicOrderIndex = 9,
            keywords = listOf("شرب الماء", "فوائد الماء", "كمية الماء", "صحة الجسم", "water intake", "drinking water", "ترطيب الجسم", "الصداع والجفاف"),
            arabicSummary = "يشكل الماء حوالي 60% من جسم الإنسان، وشرب كمية كافية (حوالي 2 إلى 3 لترات يومياً) يساعد على:\n1. تنشيط الذاكرة والتركيز ومنع الصداع الناتج عن الجفاف.\n2. تنظيم درجة حرارة الجسم وتحسين الهضم.\n3. الحفاظ على نضارة البشرة ونشاط العضلات.",
            englishSummary = "Water constitutes about 60% of body weight and is critical for brain function, circulation, and temperature regulation. It is recommended to drink 2 to 3 liters daily (around 8 glasses), increasing during hot weather or physical exercise.",
            suggestedFollowUps = listOf(
                ActionableOption("فحص حرارة الطقس", "ما حالة الطقس؟"),
                ActionableOption("نصائح للنوم", "نصائح للنوم")
            )
        ),
        // [10] Battery & Device Care
        SeedTopicCatalogEntry(
            topicId = "battery_care",
            topicOrderIndex = 10,
            keywords = listOf(
                "الحفاظ على البطارية", "عمر البطارية", "توفير البطارية", "نصائح للبطارية",
                "حماية البطارية", "صحة البطارية", "اطالة عمر البطارية",
                "battery health", "battery tips", "save battery", "battery lifespan", "battery care",
                "شحن الهاتف", "بطارية الهاتف"
            ),
            arabicSummary = "للحفاظ على صحة بطارية هاتفك لأطول فترة:\n1. حاول إبقاء نسبة الشحن بين 20% و80% وتجنب التفريغ الكامل المتكرر.\n2. تجنب تعريض الهاتف للحرارة العالية أثناء الشحن.\n3. خفّض سطوع الشاشة واستخدم الوضع الداكن (Dark Mode).\n4. أغلق البلوتوث والموقع عند عدم الحاجة إليهما.",
            englishSummary = "To maximize your phone's battery lifespan:\n1. Keep charge levels between 20% and 80%, avoiding complete depletion.\n2. Avoid exposing your device to high heat or direct sunlight while charging.\n3. Lower screen brightness and use Dark Mode.\n4. Disable Bluetooth and GPS when not in active use.",
            suggestedFollowUps = listOf(
                ActionableOption("كم نسبة بطاريتي الآن؟", "كم نسبة البطارية؟"),
                ActionableOption("هل البطارية ستكفي؟", "هل البطارية ستكفي؟")
            )
        )
    )

    val coreTopicsById: Map<String, SeedTopicCatalogEntry> =
        coreTopics.associateBy { it.topicId }

    val coreTopicIdsOrdered: List<String> =
        coreTopics.map { it.topicId }

    /**
     * Resolves a specific capital answer using the exact rules of
     * [LocalKnowledgeBase.resolveSpecificCapitalAnswer] (`lines 519–534`).
     */
    fun resolveSpecificCapitalAnswer(query: String, isArabic: Boolean): String? =
        LocalKnowledgeBase.resolveSpecificCapitalAnswer(query, isArabic)

    /**
     * Computes a deterministic SHA-256 checksum over the 11 core topics so idempotent Room seed
     * initialization can verify whether `core_seed_pack` is already up to date.
     */
    val coreSeedChecksum: String by lazy {
        val digest = MessageDigest.getInstance("SHA-256")
        for (entry in coreTopics) {
            digest.update(entry.topicId.toByteArray(Charsets.UTF_8))
            digest.update(entry.topicOrderIndex.toString().toByteArray(Charsets.UTF_8))
            for (kw in entry.keywords) {
                digest.update(kw.toByteArray(Charsets.UTF_8))
            }
            digest.update(entry.arabicSummary.toByteArray(Charsets.UTF_8))
            digest.update(entry.englishSummary.toByteArray(Charsets.UTF_8))
            for (opt in entry.suggestedFollowUps) {
                digest.update(opt.title.toByteArray(Charsets.UTF_8))
                digest.update(opt.queryToExecute.toByteArray(Charsets.UTF_8))
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Builds the [KnowledgePackMetadataEntity] for `core_seed_pack`.
     */
    fun buildCoreSeedPackMetadata(importedAtMillis: Long = 1700000000000L): KnowledgePackMetadataEntity {
        return KnowledgePackMetadataEntity(
            packId = CORE_SEED_PACK_ID,
            sourceType = CORE_SEED_SOURCE_TYPE,
            packVersion = CORE_SEED_PACK_VERSION,
            schemaVersion = CORE_SEED_SCHEMA_VERSION,
            sha256Checksum = coreSeedChecksum,
            packPriority = CORE_SEED_PACK_PRIORITY,
            importedAt = importedAtMillis,
            topicCount = coreTopics.size
        )
    }

    /**
     * Builds the complete list of [TopicStorageBundle]s for persisting the 11 core seed topics,
     * their keywords, and their pre-normalized tokens into Room.
     */
    fun buildCoreSeedTopicStorageBundles(
        converters: KnowledgeTypeConverters = KnowledgeTypeConverters()
    ): List<TopicStorageBundle> {
        return coreTopics.map { entry ->
            val header = KnowledgeTopicHeaderEntity(
                topicId = entry.topicId,
                packId = CORE_SEED_PACK_ID,
                topicOrderIndex = entry.topicOrderIndex,
                arabicSummary = entry.arabicSummary,
                englishSummary = entry.englishSummary,
                suggestedFollowUpsJson = converters.fromActionableOptionList(entry.suggestedFollowUps),
                sourceType = CORE_SEED_SOURCE_TYPE,
                packPriority = CORE_SEED_PACK_PRIORITY,
                contentVersion = CORE_SEED_CONTENT_VERSION,
                isCoreTopic = true,
                isActiveWinner = true
            )

            val keywordsWithTokens = entry.keywords.mapIndexedNotNull { kwOrder, rawKw ->
                val relaxedPhrase = LocalKnowledgeBase.normalizeArabic(rawKw)
                if (relaxedPhrase.isBlank()) return@mapIndexedNotNull null
                val strictPhrase = LocalKnowledgeBase.normalizeArabicStrict(rawKw)
                val strictTokens = strictPhrase.split(" ").filter { it.isNotBlank() }
                val relaxedTokens = relaxedPhrase.split(" ").filter { it.isNotBlank() }
                if (relaxedTokens.isEmpty()) return@mapIndexedNotNull null

                val contentIndices = relaxedTokens.indices
                    .filter { relaxedTokens[it] !in STOP_WORDS }
                    .ifEmpty { relaxedTokens.indices.toList() }

                val hasArabic = rawKw.any { ch -> ch in '\u0600'..'\u06FF' }
                val hasLatin = rawKw.any { ch -> ch in 'a'..'z' || ch in 'A'..'Z' }
                val language = when {
                    hasArabic && hasLatin -> "mixed"
                    hasArabic -> "ar"
                    else -> "en"
                }

                val keywordEntity = KnowledgeKeywordEntity(
                    keywordId = 0L,
                    topicId = entry.topicId,
                    packId = CORE_SEED_PACK_ID,
                    keywordOrder = kwOrder,
                    originalKeyword = rawKw,
                    strictNormalizedPhrase = strictPhrase,
                    relaxedNormalizedPhrase = relaxedPhrase,
                    relaxedCharLength = relaxedPhrase.length,
                    tokenCount = relaxedTokens.size,
                    contentTokenCount = contentIndices.size,
                    matchType = if (relaxedTokens.size > 1) "MULTI_WORD_PHRASE" else "SINGLE_TOKEN",
                    language = language
                )

                val tokenEntities = relaxedTokens.indices.map { pos ->
                    val sTok = strictTokens.getOrElse(pos) { relaxedTokens[pos] }
                    val rTok = relaxedTokens[pos]
                    val sStem = LocalKnowledgeBase.stripArabicPrefixes(sTok)
                    val rStem = LocalKnowledgeBase.stripArabicPrefixes(rTok)
                    KnowledgeKeywordTokenEntity(
                        tokenId = 0L,
                        keywordId = 0L,
                        tokenPosition = pos,
                        strictToken = sTok,
                        strictStem = sStem.ifEmpty { sTok },
                        relaxedToken = rTok,
                        relaxedStem = rStem.ifEmpty { rTok }
                    )
                }

                KeywordWithTokensBundle(
                    keyword = keywordEntity,
                    tokens = tokenEntities
                )
            }

            TopicStorageBundle(
                header = header,
                keywordsWithTokens = keywordsWithTokens,
                detail = null,
                followUpBranches = emptyList()
            )
        }
    }
}
