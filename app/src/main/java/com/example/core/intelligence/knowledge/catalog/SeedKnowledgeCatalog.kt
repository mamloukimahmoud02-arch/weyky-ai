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

data class SeedTopicAspect(
    val aspectId: String,
    val priorityOrder: Int,
    val anchorKeywords: List<String>,
    val supportingKeywords: List<String> = emptyList(),
    val arabicResponse: String,
    val englishResponse: String
)

data class ResolvedTopicAspect(
    val topicId: String,
    val aspectId: String,
    val responseText: String,
    val score: Int
)

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
    val suggestedFollowUps: List<ActionableOption>,
    val aspects: List<SeedTopicAspect> = emptyList()
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
    const val CORE_SEED_PACK_VERSION: Int = 2
    const val CORE_SEED_SCHEMA_VERSION: Int = 1
    const val CORE_SEED_PACK_PRIORITY: Int = 0
    const val CORE_SEED_CONTENT_VERSION: Int = 2

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
        // [7] Science: Speed of light, Solar System, Gravity, Stars
        SeedTopicCatalogEntry(
            topicId = "physics_universe",
            topicOrderIndex = 7,
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
            ),
            aspects = listOf(
                SeedTopicAspect(
                    aspectId = "gravity_solar_system",
                    priorityOrder = 1,
                    anchorKeywords = listOf(
                        "دور الجاذبية في النظام الشمسي", "دور الجاذبية في المجموعة الشمسية",
                        "الجاذبية في النظام الشمسي", "الجاذبية في المجموعة الشمسية",
                        "جاذبية الشمس", "مدارات الكواكب حول الشمس", "مدارات الكواكب",
                        "كيف تحفظ الجاذبية الكواكب", "لماذا تدور الكواكب حول الشمس",
                        "دور الجاذبية", "الجاذبية النظام الشمسي", "الجاذبية المجموعة الشمسية",
                        "الجاذبية الكواكب", "gravity in the solar system",
                        "role of gravity in the solar system", "sun gravity", "planetary orbits"
                    ),
                    supportingKeywords = listOf(
                        "النظام الشمسي", "المجموعة الشمسية", "الكواكب", "كواكب",
                        "مدارات", "مدار", "مداراتها", "الشمس", "الأقمار", "الاقمار",
                        "الانفلات", "الكويكبات", "المذنبات",
                        "solar system", "planets", "orbits", "sun", "moons"
                    ),
                    arabicResponse = "تؤدي الجاذبية الدور الأساسي في تماسك النظام الشمسي وتنظيم حركة جميع أجرامه السماوية:\n" +
                        "1. جذب الشمس للكواكب وحفظ مداراتها: تمتلك الشمس نحو 99.8% من كتلة النظام الشمسي، ولذلك تولّد قوة جذب مركزية هائلة تُبقي الكواكب الثمانية في مدارات إهليلجية مستقرة حولها وتمنعها من الانفلات في الفضاء السحيق.\n" +
                        "2. التوازن بين الجاذبية والسرعة المدارية: يستمر كل كوكب في الدوران حول الشمس بفضل التوازن الدقيق بين قوة جذب الشمس التي تشده نحو الداخل وسرعته المدارية (القصور الذاتي) التي تدفعه للتحرك على مساره.\n" +
                        "3. تنظيم حركة الأقمار والكويكبات والمذنبات: تحكم الجاذبية المتبادلة دوران الأقمار حول كواكبها (مثل دوران القمر حول الأرض)، وتضبط مسارات الكواكب القزمة وحزام الكويكبات والمذنبات، كما تنشئ ظواهر المدّ والجزر والتأثيرات المدارية المتبادلة بين الأجرام السماوية.",
                    englishResponse = "Gravity is the primary governing force that holds the Solar System together and orchestrates the motion of its celestial bodies:\n" +
                        "1. The Sun's Gravitational Hold on Planetary Orbits: Holding about 99.8% of the Solar System's mass, the Sun exerts an immense inward gravitational pull that keeps all 8 planets in stable elliptical orbits and prevents them from drifting into deep space.\n" +
                        "2. Balance of Gravity and Orbital Velocity: Stable planetary orbits arise from the precise balance between the Sun's inward gravitational attraction and each planet's forward orbital velocity.\n" +
                        "3. Governing Moons, Asteroids, Comets, and Celestial Interactions: Mutual gravitational attraction keeps moons orbiting their host planets, shapes the trajectories of dwarf planets, asteroids, and comets, and drives tidal and orbital interactions across the Solar System."
                ),
                SeedTopicAspect(
                    aspectId = "gravity",
                    priorityOrder = 2,
                    anchorKeywords = listOf(
                        "الجاذبية", "جاذبية", "قوة الجاذبية",
                        "كيف تعمل الجاذبية", "ما هي الجاذبية", "تجاذب الكتل",
                        "gravity", "gravitational"
                    ),
                    supportingKeywords = listOf(
                        "وزن", "الاوزان", "اوزانها", "الكتل", "الكتلة", "الارض", "الغلاف الجوي",
                        "weight", "mass", "earth", "atmosphere"
                    ),
                    arabicResponse = "الجاذبية هي ظاهرة فيزيائية كونية تنشأ عنها قوة تجاذب طبيعية بين الكتل، حيث تنجذب الأجسام ذات الكتلة نحو بعضها البعض وتزداد قوتها بزيادة الكتلة وتقل بزيادة المسافة:\n" +
                        "1. قرب سطح الأرض: تمنح الجاذبية الأجسام أوزانها وتشدّها نحو مركز الأرض، كما تحافظ على تماسك الغلاف الجوي المحيط بالكوكب.\n" +
                        "2. في النظام الشمسي: تمارس الشمس قوة جاذبية هائلة تُبقي الكواكب والأقمار في مداراتها المنتظمة حولها وتمنعها من الانفلات في الفضاء.\n" +
                        "3. شموليتها الكونية: الجاذبية ليست مقتصرة على الأرض، بل هي ظاهرة كونية شاملة تعمل في جميع أنحاء الكون، فتربط الأقمار بالكواكب، وتجمع النجوم داخل المجرات، وتقود تشكّل النجوم والكواكب.",
                    englishResponse = "Gravity is a universal physical phenomenon of mutual attraction between masses, growing stronger with greater mass and weakening with distance:\n" +
                        "1. Near Earth's Surface: Gravity gives objects their weight and holds Earth's atmosphere in place.\n" +
                        "2. In the Solar System: The Sun's gravitational pull keeps the planets in stable orbits around it.\n" +
                        "3. Universal Scope: Gravity is not limited to Earth—it acts across the entire cosmos, binding moons to planets, stars into galaxies, and shaping cosmic structures."
                ),
                SeedTopicAspect(
                    aspectId = "stars",
                    priorityOrder = 3,
                    anchorKeywords = listOf(
                        "النجوم", "نجوم", "نجم", "كيف تعمل النجوم",
                        "كيف تنتج النجوم الطاقة", "طاقة النجوم", "الاندماج النووي",
                        "stars", "how do stars work", "nuclear fusion"
                    ),
                    supportingKeywords = listOf(
                        "الطاقة", "تنتج", "الضوء", "الحرارة", "البلازما",
                        "الهيدروجين", "الهيليوم", "نواة", "energy", "plasma", "hydrogen", "helium"
                    ),
                    arabicResponse = "النجوم هي كرات غازية ضخمة ومتوهجة تتكون من البلازما شديدة الحرارة، وتعمل وتنتج طاقتها وفق الآلية الفيزيائية التالية:\n" +
                        "1. الاندماج النووي في النواة: بفعل الكتلة الهائلة للنجم، يتولد في نواته (قلبه) ضغط وحرارة شديدان يطلقان تفاعلات الاندماج النووي.\n" +
                        "2. اندماج الهيدروجين إلى هيليوم: في النجوم المشابهة للشمس، تندمج نوى ذرات الهيدروجين معاً لتكوين الهيليوم داخل نواة النجم.\n" +
                        "3. انطلاق الطاقة كإشعاع وضوء وحرارة: يحرر هذا الاندماج النووي طاقة هائلة تنتقل إلى سطح النجم وتنطلق في الفضاء لتصل إلينا على شكل إشعاع وضوء وحرارة.\n" +
                        "4. توازن الجاذبية والضغط الداخلي: يحافظ النجم على استقراره بفضل التوازن الدقيق بين قوة الجاذبية التي تضغط مادة النجم نحو الداخل، والضغط الحراري والإشعاعي الداخلي الناتج عن الاندماج النووي الذي يدفع نحو الخارج.",
                    englishResponse = "Stars are massive, luminous spheres of hot plasma that generate energy primarily through core nuclear fusion:\n" +
                        "1. Core Nuclear Fusion: Extreme temperature and pressure in the star's core drive nuclear fusion reactions.\n" +
                        "2. Hydrogen into Helium: In Sun-like stars, hydrogen nuclei fuse together to form helium.\n" +
                        "3. Radiation, Light, and Heat: Energy released by fusion travels to the surface and radiates into space, reaching us as light, heat, and electromagnetic radiation.\n" +
                        "4. Gravitational Balance: A star remains stable because inward gravitational pull is balanced by the outward thermal and radiation pressure generated by fusion."
                ),
                SeedTopicAspect(
                    aspectId = "speed_of_light",
                    priorityOrder = 4,
                    anchorKeywords = listOf(
                        "سرعة الضوء", "ضوء الشمس", "السنة الضوئية",
                        "speed of light", "light year", "sunlight"
                    ),
                    supportingKeywords = listOf(
                        "الضوء", "الفراغ", "الفضاء", "ثانية", "دقائق", "كيلومتر", "light", "vacuum"
                    ),
                    arabicResponse = "سرعة الضوء في الفراغ تبلغ حوالي 300,000 كيلومتر في الثانية (وتحديداً 299,792 كم/ث)، وهي الحد الأقصى للسرعة الكونية:\n" +
                        "- وصول ضوء الشمس: يبعد كوكب الأرض عن الشمس نحو 149.6 مليون كم، لذلك يستغرق ضوء الشمس حوالي 8 دقائق و20 ثانية للوصول إلى الأرض.\n" +
                        "- السنة الضوئية: هي المسافة التي يقطعها الضوء خلال سنة كاملة وتساوي نحو 9.46 تريليون كيلومتر.",
                    englishResponse = "The speed of light in a vacuum is approximately 300,000 km/s (299,792 km/s):\n" +
                        "- Sunlight takes about 8 minutes and 20 seconds to travel the 149.6 million km from the Sun to Earth.\n" +
                        "- A light-year is the distance light travels in one year, roughly 9.46 trillion kilometers."
                ),
                SeedTopicAspect(
                    aspectId = "solar_system",
                    priorityOrder = 5,
                    anchorKeywords = listOf(
                        "المجموعة الشمسية", "النظام الشمسي", "كواكب المجموعة الشمسية",
                        "عدد الكواكب", "solar system"
                    ),
                    supportingKeywords = listOf(
                        "الكواكب", "كواكب", "عطارد", "المشتري", "زحل", "المريخ", "الزهرة", "planets"
                    ),
                    arabicResponse = "تتكون مجموعتنا الشمسية (النظام الشمسي) من نجم مركزي هو الشمس و8 كواكب تدور حولها في مدارات منتظمة:\n" +
                        "- الكواكب الثمانية: أقربها للشمس عطارد، يليه الزهرة، الأرض، المريخ، ثم الكواكب العملاقة: المشتري (وهو أكبر كواكب المجموعة الشمسية)، زحل، أورانوس، ونبتون.\n" +
                        "- تحفظ جاذبية الشمس الكواكب في مداراتها، ويستغرق ضوء الشمس حوالي 8 دقائق و20 ثانية للوصول إلى الأرض بسرعة 300,000 كم/ث.",
                    englishResponse = "Our Solar System consists of the Sun and 8 recognized planets orbiting it:\n" +
                        "- The 8 planets ordered from the Sun outward are Mercury (closest), Venus, Earth, Mars, Jupiter (the largest), Saturn, Uranus, and Neptune.\n" +
                        "- The Sun's gravity keeps the planets in orbit, while sunlight travels at 300,000 km/s to reach Earth in 8 minutes and 20 seconds."
                )
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
            ),
            aspects = listOf(
                SeedTopicAspect(
                    aspectId = "battery_drain",
                    priorityOrder = 1,
                    anchorKeywords = listOf(
                        "تنفد البطارية", "نفاد البطارية", "استنزاف البطارية",
                        "لماذا تنفد البطارية", "لماذا تنفد البطارية بسرعة",
                        "تنفذ البطارية", "نفاذ البطارية", "لماذا تنفذ البطارية", "لماذا تنفذ البطارية بسرعة",
                        "اسباب استهلاك البطارية", "سبب استهلاك البطارية",
                        "اسباب استهلاك الطاقة", "اسباب نفاد البطارية", "اسباب نفاذ البطارية", "استهلاك البطارية",
                        "تفرغ البطارية", "تفرغ البطارية بسرعة", "تخلص البطارية", "تهبط البطارية",
                        "تنفد بطاريتي", "تنفذ بطاريتي",
                        "battery drain", "why does battery drain", "draining fast"
                    ),
                    supportingKeywords = listOf(
                        "تنفد", "نفاد", "تنفذ", "نفاذ", "استنزاف", "بسرعة", "اسباب", "لماذا", "تفرغ", "تخلص", "تهبط"
                    ),
                    arabicResponse = "ينقسم نفاد البطارية بسرعة إلى قسمين رئيسيين: أسباب الاستهلاك العالي اللحظي، وتراجع السعة الكيميائية للبطارية مع الزمن:\n" +
                        "أولاً — أسباب الاستهلاك العالي للطاقة:\n" +
                        "1. الشاشة: رفع سطوع الشاشة إلى مستويات عالية وتشغيل معدل التحديث المرتفع (90Hz/120Hz) باستمرار.\n" +
                        "2. التطبيقات في الخلفية: عمل تطبيقات تستهلك المعالج والمزامنة والإشعارات في الخلفية دون توقف.\n" +
                        "3. ضعف إشارة الشبكة: عند ضعف التغطية والبحث المستمر عن الاتصال، يرفع الهاتف قدرة الهوائي مما يستنزف البطارية بسرعة.\n" +
                        "4. خدمات الموقع والاتصالات اللاسلكية: تشغيل GPS والملاحة والبلوتوث ونقطة الاتصال (Hotspot) بصورة متواصلة.\n" +
                        "5. الحرارة المرتفعة: ارتفاع حرارة الجهاز أو المحيط يزيد الفقد الحراري ويرفع سرعة تفريغ الشحن.\n" +
                        "ثانياً — تراجع السعة الكيميائية مع الزمن:\n" +
                        "مع تزايد دورات الشحن وتقادم البطارية كيميائياً، تنخفض السعة الفعلية القابلة للتخزين مقارنة بالبطارية الجديدة، فتفرغ البطارية أسرع حتى مع نفس نمط الاستخدام المعتاد.",
                    englishResponse = "Rapid battery drain stems from two distinct categories—high active power consumption versus chemical capacity aging over time:\n" +
                        "1. High Power Consumption Causes:\n" +
                        "- High screen brightness and continuous high refresh rate.\n" +
                        "- Background apps actively using CPU and network sync.\n" +
                        "- Weak network signal forcing constant searching for cellular connection.\n" +
                        "- Continuous GPS location services and wireless connections.\n" +
                        "- High device or ambient temperature.\n" +
                        "2. Chemical Capacity Degradation Over Time:\n" +
                        "- As lithium-ion batteries age across charge cycles, their maximum chemical capacity decreases, causing the phone to run out faster even under normal usage."
                ),
                SeedTopicAspect(
                    aspectId = "overnight_charging",
                    priorityOrder = 2,
                    anchorKeywords = listOf(
                        "الشحن طوال الليل", "الشحن الليلي", "طوال الليل", "اثناء النوم",
                        "يضر البطارية", "تضر البطارية", "اضرار الشحن",
                        "ترك الهاتف في الشاحن", "overnight charging", "charge overnight"
                    ),
                    supportingKeywords = listOf(
                        "الليل", "النوم", "يضر", "ضرر", "الشحن", "100%", "حرارة"
                    ),
                    arabicResponse = "الشحن طوال الليل لا يُتلف البطارية بشكل فوري في الهواتف الحديثة، لكن تأثيره التراكمي يرتبط بالحرارة وطريقة إدارة الشحن:\n" +
                        "1. إيقاف الشحن عند الامتلاء: الهواتف الذكية الحديثة مزودة بمنظومة إدارة طاقة توقف الشحن الفعلي تلقائياً أو تديره عند الوصول إلى 100% لمنع الشحن الزائد.\n" +
                        "2. تأثير البقاء طويلاً عند شحن مرتفع مع الحرارة: بقاء البطارية لساعات طويلة عند مستوى شحن مرتفع (100%) وخاصة مع ارتفاع الحرارة قد يسرّع الشيخوخة الكيميائية للخلايا تدريجياً على المدى الطويل.\n" +
                        "3. ميزة الشحن المحسّن (Optimized Charging): يُنصح بتفعيل الشحن المحسّن أو التكيّفي عند دعمه في هاتفك، حيث يوقف الشحن مؤقتاً قرب 80% أثناء الليل ويكمله قبيل موعد استيقاظك.\n" +
                        "4. تجنب الحرارة أثناء الشحن: ضع الهاتف على سطح جيد التهوية وتجنب تغطيته بالوسادة أو الأغطية السميكة أثناء الشحن؛ علماً أن قاعدة 20%–80% هي عادة مساعدة اختيارية لتقليل الإجهاد الكيميائي وليست شرطاً إلزامياً لكل مستخدم.",
                    englishResponse = "Overnight charging does not cause immediate damage on modern smartphones, though long-term wear depends on heat and charge management:\n" +
                        "1. Automatic Cutoff at Full Charge: Modern phones manage and stop active charging once reaching 100%.\n" +
                        "2. Heat & Prolonged High Charge: Staying at high charge levels for long periods—especially combined with heat—can accelerate chemical aging over time.\n" +
                        "3. Optimized / Adaptive Charging: Enable Optimized Charging when supported so the device pauses near 80% overnight and completes charging shortly before you wake up.\n" +
                        "4. Avoid High Heat While Charging: Keep the phone ventilated while charging; the 20%–80% practice is a helpful optional guideline rather than a mandatory requirement."
                ),
                SeedTopicAspect(
                    aspectId = "energy_saving",
                    priorityOrder = 3,
                    anchorKeywords = listOf(
                        "اقلل استهلاك الطاقة", "تقليل استهلاك الطاقة", "اقلل استهلاك البطارية",
                        "تقليل استهلاك البطارية", "استهلاك الطاقة", "توفير الطاقة",
                        "توفير البطارية", "كيف اوفر الطاقة", "كيف اوفر البطارية",
                        "save power", "save energy", "power saving", "save battery"
                    ),
                    supportingKeywords = listOf(
                        "اقلل", "تقليل", "توفير", "اوفر", "الطاقة", "سطوع", "الوضع الداكن"
                    ),
                    arabicResponse = "لتقليل استهلاك الطاقة وتوفير شحن البطارية خلال الاستخدام اليومي، اتبع الخطوات العملية التالية:\n" +
                        "1. تفعيل وضع توفير الطاقة (Battery Saver): شغّله عند الحاجة لتقليل استهلاك المعالج والنشاط الخلفي تلقائياً.\n" +
                        "2. خفض سطوع الشاشة وتقليل معدل التحديث: قلّل السطوع أو فعّل السطوع التكيّفي، واخفض معدل التحديث (Refresh Rate) إلى المستوى القياسي (60Hz) إذا كان هاتفك يدعم ذلك.\n" +
                        "3. مراجعة التطبيقات الأعلى استهلاكاً وتقييد نشاط الخلفية: افحص قائمة استهلاك البطارية في الإعدادات وقيّد نشاط الخلفية غير الضروري للتطبيقات المستنزفة.\n" +
                        "4. إدارة GPS والبلوتوث والاتصالات اللاسلكية: أوقف الموقع (GPS) والبلوتوث ونقطة الاتصال اللاسلكية عند عدم استخدامها فعلياً.\n" +
                        "5. استخدام الوضع الداكن (Dark Mode): يوفّر الوضع الداكن قدراً ملموساً من الطاقة في شاشات OLED وAMOLED لأن البكسلات الداكنة تستهلك طاقة أقل أو تنطفئ، مع الانتباه إلى أن الفائدة الفعلية تعتمد على نوع الشاشة وتكون محدودة في شاشات LCD.",
                    englishResponse = "To reduce power consumption and extend battery runtime:\n" +
                        "1. Enable Battery Saver when needed.\n" +
                        "2. Lower screen brightness and reduce screen refresh rate if supported.\n" +
                        "3. Review top battery-consuming apps in settings and restrict unnecessary background activity.\n" +
                        "4. Manage GPS, Bluetooth, and wireless connections according to actual usage.\n" +
                        "5. Use Dark Mode on OLED/AMOLED screens, noting that the actual power savings depend on the display panel technology."
                ),
                SeedTopicAspect(
                    aspectId = "battery_lifespan",
                    priorityOrder = 4,
                    anchorKeywords = listOf(
                        "عمر البطارية", "الحفاظ على البطارية", "احافظ على عمر البطارية",
                        "اطالة عمر البطارية", "صحة البطارية", "حماية البطارية",
                        "battery lifespan", "battery health", "battery care"
                    ),
                    supportingKeywords = listOf(
                        "عمر", "الحفاظ", "احافظ", "اطالة", "صحة", "حماية", "دورات الشحن"
                    ),
                    arabicResponse = "للحفاظ على عمر البطارية وإبطاء تآكلها الكيميائي على المدى الطويل، يُنصح باتباع هذه الإرشادات العملية المتوازنة:\n" +
                        "1. تجنب الحرارة المرتفعة: تُعد الحرارة العالية أثناء الشحن أو الاستخدام تحت الشمس العامل الأخطر على صحة خلايا البطارية.\n" +
                        "2. عادات شحن متوازنة وتجنب التفريغ العميق: تجنب ترك البطارية تهبط إلى 0% بشكل متكرر، ويفضّل كعادة عملية مفيدة (وغير إلزامية) إبقاء الشحن غالباً بين 20% و80% عند الإمكان.\n" +
                        "3. تفعيل ميزات الشحن المحسّن: فعّل الشحن المحسّن أو التكيّفي في إعدادات الهاتف لتقليل فترات بقاء البطارية عند أقصى جهد (100%).\n" +
                        "4. استخدام شواحن معتمدة وتخفيف الحمل أثناء الشحن: اعتمد على شواحن موثوقة وتجنب تشغيل الألعاب أو المهام الثقيلة أثناء الشحن السريع لمنع تراكم الحرارة.",
                    englishResponse = "To preserve long-term battery health and lifespan, follow these balanced guidelines:\n" +
                        "1. Avoid High Heat: Protect the phone from overheating during charging or direct sunlight exposure.\n" +
                        "2. Balanced Charging Habits & Avoiding Deep Discharge: Avoid frequent 0% deep discharges; keeping charge mostly between 20% and 80% when convenient is a helpful practice.\n" +
                        "3. Enable Optimized Charging: Use smart or adaptive charging features to reduce time spent at 100% voltage.\n" +
                        "4. Use Certified Chargers: Charge with reliable adapters and avoid heavy gaming while fast charging."
                )
            )
        )
    )

    val coreTopicsById: Map<String, SeedTopicCatalogEntry> =
        coreTopics.associateBy { it.topicId }

    val coreTopicIdsOrdered: List<String> =
        coreTopics.map { it.topicId }

    /**
     * Immutable set of the 11 protected core seed topic IDs that external knowledge packs
     * must never override or claim ownership of.
     */
    val PROTECTED_CORE_TOPIC_IDS: Set<String> =
        coreTopicsById.keys

    fun isProtectedCoreTopicId(topicId: String): Boolean =
        topicId in PROTECTED_CORE_TOPIC_IDS

    /**
     * Resolves a specific capital answer using the exact rules of
     * [LocalKnowledgeBase.resolveSpecificCapitalAnswer] (`lines 519–534`).
     */
    fun resolveSpecificCapitalAnswer(query: String, isArabic: Boolean): String? =
        LocalKnowledgeBase.resolveSpecificCapitalAnswer(query, isArabic)

    /**
     * Unified aspect/sub-concept resolver for retrieved topics.
     *
     * Given a retrieved [topicId] and [query], determines whether the user is asking about a
     * specific sub-concept (`aspectId`) inside that topic rather than the general topic summary.
     * Requires at least one distinctive anchor phrase/token match so a single generic word never
     * triggers an unintended sub-answer.
     */
    fun resolveTopicAspect(
        topicId: String,
        query: String,
        isArabic: Boolean
    ): ResolvedTopicAspect? {
        if (query.isBlank()) return null

        if (topicId == "capitals_arab") {
            val specificCapital = resolveSpecificCapitalAnswer(query, isArabic)
            if (!specificCapital.isNullOrBlank()) {
                return ResolvedTopicAspect(
                    topicId = topicId,
                    aspectId = "specific_capital",
                    responseText = specificCapital,
                    score = 200
                )
            }
            return null
        }

        val entry = coreTopicsById[topicId] ?: return null
        if (entry.aspects.isEmpty()) return null

        val strictQuery = LocalKnowledgeBase.normalizeArabicStrict(query)
        val relaxedQuery = LocalKnowledgeBase.normalizeArabic(query)
        if (relaxedQuery.isBlank()) return null

        val strictQueryTokens = strictQuery.split(" ").filter { it.isNotBlank() }
        val relaxedQueryTokens = relaxedQuery.split(" ").filter { it.isNotBlank() }
        if (relaxedQueryTokens.isEmpty()) return null

        var bestAspect: SeedTopicAspect? = null
        var bestScore = 0

        for (aspect in entry.aspects) {
            var maxAnchorScore = 0
            var matchedAnchorsCount = 0

            for (rawAnchor in aspect.anchorKeywords) {
                val anchorScore = scoreAspectKeywordMatch(
                    rawKeyword = rawAnchor,
                    strictQuery = strictQuery,
                    relaxedQuery = relaxedQuery,
                    strictQueryTokens = strictQueryTokens,
                    relaxedQueryTokens = relaxedQueryTokens,
                    isAnchor = true
                )
                if (anchorScore > 0) {
                    matchedAnchorsCount++
                    if (anchorScore > maxAnchorScore) {
                        maxAnchorScore = anchorScore
                    }
                }
            }

            if (maxAnchorScore == 0) continue

            var supportingBonus = 0
            for (rawSupport in aspect.supportingKeywords) {
                val supScore = scoreAspectKeywordMatch(
                    rawKeyword = rawSupport,
                    strictQuery = strictQuery,
                    relaxedQuery = relaxedQuery,
                    strictQueryTokens = strictQueryTokens,
                    relaxedQueryTokens = relaxedQueryTokens,
                    isAnchor = false
                )
                if (supScore > 0) {
                    supportingBonus += 20
                }
            }

            val multiAnchorBonus = if (matchedAnchorsCount > 1) {
                minOf((matchedAnchorsCount - 1) * 15, 30)
            } else {
                0
            }

            val totalAspectScore = maxAnchorScore + minOf(supportingBonus, 60) + multiAnchorBonus
            val isBetter = totalAspectScore > bestScore ||
                (totalAspectScore == bestScore && bestAspect != null && aspect.priorityOrder < bestAspect.priorityOrder)
            if (isBetter) {
                bestScore = totalAspectScore
                bestAspect = aspect
            }
        }

        val winningAspect = bestAspect ?: return null
        val text = if (isArabic) winningAspect.arabicResponse else winningAspect.englishResponse
        if (text.isBlank()) return null

        return ResolvedTopicAspect(
            topicId = topicId,
            aspectId = winningAspect.aspectId,
            responseText = text,
            score = bestScore
        )
    }

    private fun scoreAspectKeywordMatch(
        rawKeyword: String,
        strictQuery: String,
        relaxedQuery: String,
        strictQueryTokens: List<String>,
        relaxedQueryTokens: List<String>,
        isAnchor: Boolean
    ): Int {
        val relaxedKw = LocalKnowledgeBase.normalizeArabic(rawKeyword)
        if (relaxedKw.isBlank()) return 0
        val strictKw = LocalKnowledgeBase.normalizeArabicStrict(rawKeyword)
        val strictKwTokens = strictKw.split(" ").filter { it.isNotBlank() }
        val relaxedKwTokens = relaxedKw.split(" ").filter { it.isNotBlank() }
        if (relaxedKwTokens.isEmpty()) return 0

        if (strictQuery == strictKw || relaxedQuery == relaxedKw) {
            return if (isAnchor) 180 + relaxedKwTokens.size * 25 else 25
        }

        // Contiguous word-boundary match
        if (containsTokensOnWordBoundary(strictQueryTokens, relaxedQueryTokens, strictKwTokens, relaxedKwTokens)) {
            return if (isAnchor) {
                if (relaxedKwTokens.size >= 2) {
                    120 + relaxedKwTokens.size * 25 + relaxedKw.length
                } else {
                    85 + relaxedKw.length
                }
            } else {
                20
            }
        }

        // Order-independent multi-token match for multi-word anchor phrases
        val contentKwIndices = relaxedKwTokens.indices
            .filter { relaxedKwTokens[it] !in STOP_WORDS }
            .ifEmpty { relaxedKwTokens.indices.toList() }

        if (isAnchor && contentKwIndices.size >= 2) {
            val allContentMatched = contentKwIndices.all { kIdx ->
                val kwTok = relaxedKwTokens[kIdx]
                relaxedQueryTokens.any { qTok -> LocalKnowledgeBase.tokensMatch(qTok, kwTok) }
            }
            if (allContentMatched) {
                return 105 + contentKwIndices.size * 20
            }
        }

        return 0
    }

    private fun containsTokensOnWordBoundary(
        strictQueryTokens: List<String>,
        relaxedQueryTokens: List<String>,
        strictKwTokens: List<String>,
        relaxedKwTokens: List<String>
    ): Boolean {
        if (relaxedKwTokens.isEmpty() || relaxedQueryTokens.size < relaxedKwTokens.size) return false
        for (i in 0..(relaxedQueryTokens.size - relaxedKwTokens.size)) {
            var allMatched = true
            for (j in relaxedKwTokens.indices) {
                val qTok = strictQueryTokens.getOrElse(i + j) { relaxedQueryTokens[i + j] }
                val kTok = strictKwTokens.getOrElse(j) { relaxedKwTokens[j] }
                if (!LocalKnowledgeBase.tokensMatch(qTok, kTok)) {
                    allMatched = false
                    break
                }
            }
            if (allMatched) return true
        }
        return false
    }

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
            for (aspect in entry.aspects) {
                digest.update(aspect.aspectId.toByteArray(Charsets.UTF_8))
                digest.update(aspect.priorityOrder.toString().toByteArray(Charsets.UTF_8))
                for (anchor in aspect.anchorKeywords) {
                    digest.update(anchor.toByteArray(Charsets.UTF_8))
                }
                for (support in aspect.supportingKeywords) {
                    digest.update(support.toByteArray(Charsets.UTF_8))
                }
                digest.update(aspect.arabicResponse.toByteArray(Charsets.UTF_8))
                digest.update(aspect.englishResponse.toByteArray(Charsets.UTF_8))
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
