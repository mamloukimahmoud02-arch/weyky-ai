package com.example.device.apps

import java.util.Locale

/**
 * Universal normalization and cross-script phonetic engine for application discovery.
 * Connects spoken Arabic names and Latin/English application labels dynamically
 * without requiring hardcoded package IDs.
 */
object UniversalAppNormalizer {

  // Phonetic letter mapping for Latin acronyms spoken in Arabic
  private val ARABIC_SPOKEN_LETTER_TO_LATIN = mapOf(
    "اي" to "a", "بي" to "b", "سي" to "c", "دي" to "d", "إي" to "e",
    "اف" to "f", "جي" to "g", "اتش" to "h", "كي" to "k", "ال" to "l",
    "ام" to "m", "ان" to "n", "او" to "o", "پي" to "p", "كيو" to "q",
    "ار" to "r", "اس" to "s", "تي" to "t", "يو" to "u", "في" to "v",
    "دبليو" to "w", "اكس" to "x", "واي" to "y", "زد" to "z"
  )

  private val LATIN_LETTER_TO_ARABIC_SPOKEN = mapOf(
    'a' to "اي", 'b' to "بي", 'c' to "سي", 'd' to "دي", 'e' to "اي",
    'f' to "اف", 'g' to "جي", 'h' to "اتش", 'i' to "اي", 'j' to "جي",
    'k' to "كي", 'l' to "ال", 'm' to "ام", 'n' to "ان", 'o' to "او",
    'p' to "بي", 'q' to "كيو", 'r' to "ار", 's' to "اس", 't' to "تي",
    'u' to "يو", 'v' to "في", 'w' to "دبليو", 'x' to "اكس", 'y' to "واي",
    'z' to "زد"
  )

  // Common cross-script root transliterations (bilingual words)
  private val LATIN_TO_ARABIC_WORDS = mapOf(
    "chat" to listOf("شات", "تشات"),
    "whats" to listOf("واتس"),
    "tube" to listOf("تيوب", "توب"),
    "you" to listOf("يو"),
    "tik" to listOf("تيك"),
    "tok" to listOf("توك"),
    "snap" to listOf("سناب"),
    "face" to listOf("فيس"),
    "book" to listOf("بوك"),
    "tele" to listOf("تيلي", "تلي"),
    "gram" to listOf("جرام", "غرام", "كرام"),
    "play" to listOf("بلاي"),
    "store" to listOf("ستور"),
    "spot" to listOf("سبوت"),
    "net" to listOf("نت"),
    "flix" to listOf("فليكس", "فلكس"),
    "uber" to listOf("اوبر"),
    "zoom" to listOf("زوم", "زووم"),
    "drive" to listOf("درايف"),
    "cloud" to listOf("كلاود"),
    "photo" to listOf("فوتو"),
    "photos" to listOf("صور"),
    "map" to listOf("ماب"),
    "maps" to listOf("خرائط"),
    "music" to listOf("ميوزك"),
    "ai" to listOf("اي اي")
  )

  private val ARABIC_TO_LATIN_WORDS = mapOf(
    "شات" to "chat",
    "تشات" to "chat",
    "واتس" to "whats",
    "يوتيوب" to "youtube",
    "يوتوب" to "youtube",
    "تيك" to "tik",
    "توك" to "tok",
    "سناب" to "snap",
    "فيس" to "face",
    "بوك" to "book",
    "تيلي" to "tele",
    "تلي" to "tele",
    "جرام" to "gram",
    "غرام" to "gram",
    "كرام" to "gram",
    "بلاي" to "play",
    "ستور" to "store",
    "سبوت" to "spot",
    "نت" to "net",
    "فليكس" to "flix",
    "فلكس" to "flix",
    "اوبر" to "uber",
    "زوم" to "zoom",
    "زووم" to "zoom",
    "درايف" to "drive",
    "كلاود" to "cloud",
    "فوتو" to "photo",
    "ميوزك" to "music",
    "كروم" to "chrome",
    "جيميل" to "gmail"
  )

  /**
   * Normalizes text across Unicode, diacritics, case, and Arabic letter variants.
   */
  fun normalize(raw: String): String {
    if (raw.isBlank()) return ""
    var text = raw.trim().lowercase(Locale.ROOT)

    // Remove Arabic diacritics / tashkeel (fatha, damma, kasra, tanween, shadda, sukoon, etc.)
    text = text.replace(Regex("[\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")

    // Remove Tatweel / Kashida
    text = text.replace("ـ", "")

    // Normalize Arabic letter variations
    text = text
      .replace('أ', 'ا')
      .replace('إ', 'ا')
      .replace('آ', 'ا')
      .replace('ٱ', 'ا')
      .replace('ة', 'ه')
      .replace('ى', 'ي')
      .replace('ئ', 'ي')
      .replace('ؤ', 'و')
      .replace('پ', 'ب')
      .replace('چ', 'ج')
      .replace('ڤ', 'ف')
      .replace('گ', 'ك')

    // Remove punctuation and special symbols
    text = text.replace(Regex("[.,/#!$%^&*;:{}=\\-_`~()?\"'!\\[\\]]"), " ")

    // Collapse multiple whitespace
    return text.replace(Regex("\\s+"), " ").trim()
  }

  /**
   * Strips Arabic definite article "ال" or English "al-" if present.
   */
  fun stripDefiniteArticle(normalizedText: String): String {
    val clean = normalizedText.trim()
    return when {
      clean.startsWith("ال") && clean.length > 3 -> clean.substring(2).trim()
      clean.startsWith("al ") && clean.length > 4 -> clean.substring(3).trim()
      clean.startsWith("al-") && clean.length > 4 -> clean.substring(3).trim()
      else -> clean
    }
  }

  /**
   * Splits a CamelCase, PascalCase, or alphanumeric string into distinct tokens.
   * e.g. "ChatGPT" -> ["Chat", "GPT"], "WhatsApp" -> ["Whats", "App"]
   */
  fun splitCamelCase(text: String): List<String> {
    if (text.isBlank()) return emptyList()
    // Split between lower/upper, between letters and digits, and by spaces
    val tokens = mutableListOf<String>()
    val parts = text.split(Regex("[\\s_\\-\\.]+")).filter { it.isNotBlank() }
    for (part in parts) {
      val subparts = part.replace(Regex("(?<=[a-z])(?=[A-Z])"), " ")
        .replace(Regex("(?<=[A-Z])(?=[A-Z][a-z])"), " ")
        .replace(Regex("(?<=[A-Za-z])(?=[0-9])"), " ")
        .replace(Regex("(?<=[0-9])(?=[A-Za-z])"), " ")
        .split(" ")
        .filter { it.isNotBlank() }
      tokens.addAll(subparts)
    }
    return tokens
  }

  /**
   * Converts a Latin acronym like "GPT" into its Arabic spoken letter names "جي بي تي".
   */
  fun acronymToSpokenArabic(acronym: String): String {
    val norm = acronym.lowercase(Locale.ROOT)
    val words = norm.mapNotNull { LATIN_LETTER_TO_ARABIC_SPOKEN[it] }
    return words.joinToString(" ")
  }

  /**
   * Converts consecutive spoken Arabic letter names into a Latin acronym.
   * e.g. ["جي", "بي", "تي"] -> "gpt"
   */
  fun spokenArabicToLatinAcronym(tokens: List<String>): String? {
    if (tokens.isEmpty()) return null
    val sb = StringBuilder()
    for (token in tokens) {
      val letter = ARABIC_SPOKEN_LETTER_TO_LATIN[token] ?: return null
      sb.append(letter)
    }
    return if (sb.isNotEmpty()) sb.toString() else null
  }

  /**
   * Translates an Arabic spoken token phrase that might contain acronyms into Latin text.
   * e.g. "شات جي بي تي" -> "chat gpt", "تشات جي بي تي" -> "chat gpt"
   */
  fun convertSpokenArabicToLatin(query: String): List<String> {
    val norm = normalize(query)
    val tokens = norm.split(" ").filter { it.isNotBlank() }
    if (tokens.isEmpty()) return emptyList()

    val results = mutableListOf<String>()

    // Check if the tail of the tokens consists of spoken letters (acronym)
    for (splitIdx in 0..tokens.size) {
      val wordTokens = tokens.subList(0, splitIdx)
      val letterTokens = tokens.subList(splitIdx, tokens.size)
      val acronym = if (letterTokens.isNotEmpty()) spokenArabicToLatinAcronym(letterTokens) else null
      if (acronym != null) {
        val wordPart = wordTokens.map { ARABIC_TO_LATIN_WORDS[it] ?: it }.joinToString(" ")
        val combined = if (wordPart.isNotBlank()) "$wordPart $acronym" else acronym
        results.add(combined)
        results.add(combined.replace(" ", ""))
      }
    }

    // Direct token word replacement
    val allTranslated = tokens.map { ARABIC_TO_LATIN_WORDS[it] ?: it }.joinToString(" ")
    if (allTranslated != norm) {
      results.add(allTranslated)
      results.add(allTranslated.replace(" ", ""))
    }

    return results
  }

  /**
   * Universal Phonetic Consonant Skeleton.
   * Produces the identical acoustic consonant signature across Arabic and Latin scripts.
   * For example:
   * "ChatGPT" -> "STKBT"
   * "شات جي بي تي" -> "STKBT"
   * "WhatsApp" -> "WTST"
   * "واتساب" -> "WTST"
   */
  fun toPhoneticSkeleton(raw: String): String {
    val norm = normalize(stripDefiniteArticle(raw))
    if (norm.isBlank()) return ""

    val sb = StringBuilder()
    var i = 0
    val len = norm.length
    while (i < len) {
      val c = norm[i]
      // Multi-character checks in Latin
      if (i + 1 < len) {
        val two = norm.substring(i, i + 2)
        when (two) {
          "sh", "ch" -> {
            appendConsonant(sb, 'S')
            i += 2
            continue
          }
          "ph" -> {
            appendConsonant(sb, 'F')
            i += 2
            continue
          }
          "th" -> {
            appendConsonant(sb, 'T')
            i += 2
            continue
          }
          "kh", "gh" -> {
            appendConsonant(sb, 'K')
            i += 2
            continue
          }
        }
      }

      val consonant: Char? = when (c) {
        // Sibilants: S, Z, SH, CH
        's', 'z', 'x', 'س', 'ص', 'ش', 'ز', 'ذ' -> 'S'
        // Dentals: T, D
        't', 'd', 'ت', 'ط', 'د', 'ض', 'ظ', 'ث' -> 'T'
        // Bilabials: B, P
        'b', 'p', 'ب' -> 'B'
        // Velars/Gutturals: K, G, C, Q, J
        'k', 'c', 'q', 'g', 'j', 'ك', 'ق', 'ج', 'غ', 'خ', 'ع' -> 'K'
        // Labiodentals: F, V
        'f', 'v', 'ف' -> 'F'
        // Nasals
        'm', 'م' -> 'M'
        'n', 'ن' -> 'N'
        // Liquids
        'l', 'ل' -> 'L'
        'r', 'ر' -> 'R'
        // Aspirate
        'h', 'ه', 'ح' -> 'H'
        // Semivowels at word start
        'w', 'و' -> if (sb.isEmpty()) 'W' else null
        'y', 'ي' -> if (sb.isEmpty()) 'Y' else null
        else -> null
      }
      if (consonant != null) {
        appendConsonant(sb, consonant)
      }
      i++
    }
    return sb.toString()
  }

  private fun appendConsonant(sb: StringBuilder, c: Char) {
    if (sb.isEmpty() || sb.last() != c) {
      sb.append(c)
    }
  }

  /**
   * Generates dynamic cross-language aliases for an application label.
   * Decomposes CamelCase, expands acronyms, and translates known syllables.
   */
  fun generateCrossLanguageAliases(label: String): Set<String> {
    val aliases = mutableSetOf<String>()
    val norm = normalize(label)
    if (norm.isBlank()) return aliases

    aliases.add(norm)
    aliases.add(norm.replace(" ", ""))

    // CamelCase decomposition
    val tokens = splitCamelCase(label)
    if (tokens.size > 1) {
      val normTokens = tokens.map { normalize(it) }
      aliases.add(normTokens.joinToString(" "))
      aliases.add(normTokens.joinToString(""))

      // Check for acronyms in tokens (e.g. "GPT" in "ChatGPT")
      val arabicTokenVariants = mutableListOf<List<String>>()
      for (tok in tokens) {
        val tokNorm = normalize(tok)
        val variants = mutableListOf<String>()
        variants.add(tokNorm)

        // Check if acronym (all caps and length >= 2)
        if (tok.all { it.isUpperCase() } && tok.length >= 2) {
          val spoken = acronymToSpokenArabic(tok)
          if (spoken.isNotBlank()) {
            variants.add(spoken)
            variants.add(spoken.replace(" ", ""))
          }
        }
        // Check known word translations
        LATIN_TO_ARABIC_WORDS[tokNorm]?.forEach { arWord ->
          variants.add(normalize(arWord))
        }
        arabicTokenVariants.add(variants)
      }

      // Generate cross-product of combinations
      val combinations = cartesianProduct(arabicTokenVariants)
      for (comb in combinations) {
        val joined = comb.joinToString(" ")
        aliases.add(normalize(joined))
        aliases.add(normalize(joined).replace(" ", ""))
      }
    } else {
      // Single token acronym check (e.g. "VLC", "PDF", "VPN")
      if (label.all { it.isUpperCase() } && label.length in 2..5) {
        val spoken = acronymToSpokenArabic(label)
        if (spoken.isNotBlank()) {
          aliases.add(normalize(spoken))
          aliases.add(normalize(spoken).replace(" ", ""))
        }
      }
      LATIN_TO_ARABIC_WORDS[norm]?.forEach { arWord ->
        aliases.add(normalize(arWord))
      }
    }

    // Phonetic skeleton
    val skeleton = toPhoneticSkeleton(label)
    if (skeleton.isNotBlank()) {
      aliases.add(skeleton)
    }

    return aliases
  }

  private fun cartesianProduct(lists: List<List<String>>): List<List<String>> {
    var result = listOf(listOf<String>())
    for (list in lists) {
      val next = mutableListOf<List<String>>()
      for (prev in result) {
        for (item in list) {
          next.add(prev + item)
        }
      }
      result = next
    }
    return result
  }

  /**
   * Calculates Levenshtein edit distance between two strings.
   */
  fun levenshteinDistance(s1: String, s2: String): Int {
    if (s1 == s2) return 0
    if (s1.isEmpty()) return s2.length
    if (s2.isEmpty()) return s1.length

    val d = Array(s1.length + 1) { IntArray(s2.length + 1) }
    for (i in 0..s1.length) d[i][0] = i
    for (j in 0..s2.length) d[0][j] = j

    for (i in 1..s1.length) {
      for (j in 1..s2.length) {
        val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
        d[i][j] = minOf(
          d[i - 1][j] + 1,
          d[i][j - 1] + 1,
          d[i - 1][j - 1] + cost
        )
      }
    }
    return d[s1.length][s2.length]
  }

  /**
   * Computes normalized similarity between 0.0f and 1.0f.
   */
  fun similarity(s1: String, s2: String): Float {
    if (s1 == s2) return 1.0f
    val maxLen = maxOf(s1.length, s2.length)
    if (maxLen == 0) return 1.0f
    val dist = levenshteinDistance(s1, s2)
    return (1.0f - (dist.toFloat() / maxLen.toFloat())).coerceIn(0.0f, 1.0f)
  }
}
