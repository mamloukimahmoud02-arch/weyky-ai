package com.example.device.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.util.Log

data class AppMatch(
  val label: String,
  val packageName: String,
  val launchIntent: Intent,
  val normalizedLabel: String = "",
  val aliases: Set<String> = emptySet(),
  val phoneticSkeleton: String = ""
)

sealed interface AppResolveResult {
  data class FoundSingle(val app: AppMatch) : AppResolveResult
  data class FoundMultiple(val query: String, val apps: List<AppMatch>) : AppResolveResult
  data class NotFound(val query: String) : AppResolveResult
  data class LaunchFailed(val packageName: String, val error: String) : AppResolveResult
}

/**
 * Universal App Discovery and Resolution Engine for WEYKY.
 * Discovers any installed launchable app via Android PackageManager, maintains an in-memory index,
 * and matches queries using bilingual normalization, cross-script phonetic matching, and confidence scoring.
 * Zero hardcoded package IDs.
 */
class AppResolver(private val context: Context) {
  private val packageManager: PackageManager = context.packageManager

  private val indexLock = Any()
  @Volatile
  private var cachedApps: List<AppMatch> = emptyList()
  private var lastIndexTimestamp: Long = 0L

  @Volatile
  private var testAppsOverride: List<AppMatch>? = null

  @androidx.annotation.VisibleForTesting
  fun setTestAppsOverride(apps: List<AppMatch>?) {
    synchronized(indexLock) {
      testAppsOverride = apps
      if (apps != null) {
        cachedApps = apps
        lastIndexTimestamp = System.currentTimeMillis()
      }
    }
  }

  companion object {
    private const val TAG = "WEYKY_APP"
    private const val CACHE_EXPIRATION_MS = 120_000L // 2 minutes cache duration

    // Secondary semantic bridge for common platform apps (labels only, no package IDs)
    private val LABEL_EQUIVALENTS = mapOf(
      "واتساب" to listOf("whatsapp", "whats app", "واتس", "الواتس"),
      "واتس" to listOf("whatsapp", "whats app", "واتساب", "الواتساب"),
      "الواتساب" to listOf("whatsapp", "واتساب"),
      "whatsapp" to listOf("واتساب", "واتس"),
      "يوتيوب" to listOf("youtube", "you tube", "اليوتيوب"),
      "اليوتيوب" to listOf("youtube", "يوتيوب"),
      "youtube" to listOf("يوتيوب", "اليوتيوب"),
      "تيليجرام" to listOf("telegram", "تليجرام", "تلكرام"),
      "تليجرام" to listOf("telegram", "تيليجرام", "تلكرام"),
      "تلكرام" to listOf("telegram", "تيليجرام", "تليجرام"),
      "telegram" to listOf("تيليجرام", "تليجرام"),
      "فيسبوك" to listOf("facebook", "فيس بوك", "الفيس"),
      "فيس بوك" to listOf("facebook", "فيسبوك"),
      "الفيسبوك" to listOf("facebook", "فيسبوك"),
      "facebook" to listOf("فيسبوك", "فيس بوك"),
      "انستجرام" to listOf("instagram", "انستغرام"),
      "انستغرام" to listOf("instagram", "انستجرام"),
      "instagram" to listOf("انستجرام", "انستغرام"),
      "تيك توك" to listOf("tiktok", "تيكتوك"),
      "تيكتوك" to listOf("tiktok", "تيك توك"),
      "tiktok" to listOf("تيك توك", "تيكتوك"),
      "سناب شات" to listOf("snapchat", "سناب"),
      "سناب" to listOf("snapchat", "سناب شات"),
      "snapchat" to listOf("سناب شات", "سناب"),
      "الكاميرا" to listOf("camera", "كاميرا"),
      "camera" to listOf("الكاميرا", "كاميرا"),
      "المعرض" to listOf("gallery", "photos", "معرض", "الصور", "الاستوديو"),
      "معرض" to listOf("gallery", "photos", "المعرض"),
      "الصور" to listOf("photos", "gallery", "المعرض"),
      "gallery" to listOf("المعرض", "معرض", "الصور"),
      "photos" to listOf("الصور", "المعرض"),
      "الخرائط" to listOf("maps", "google maps", "خرائط"),
      "maps" to listOf("الخرائط", "خرائط"),
      "الاعدادات" to listOf("settings", "الإعدادات", "الضبط", "اعدادات"),
      "الضبط" to listOf("settings", "الاعدادات", "الإعدادات"),
      "settings" to listOf("الاعدادات", "الإعدادات", "الضبط"),
      "الهاتف" to listOf("phone", "dialer", "تلفون", "مكالمات"),
      "تلفون" to listOf("phone", "dialer", "الهاتف"),
      "phone" to listOf("الهاتف", "تلفون"),
      "الرسائل" to listOf("messages", "messaging", "رسائل", "sms"),
      "messages" to listOf("الرسائل", "رسائل"),
      "الساعه" to listOf("clock", "الساعة", "المنبه", "منبه"),
      "المنبه" to listOf("clock", "alarm", "الساعة"),
      "clock" to listOf("الساعة", "المنبه"),
      "الحاسبه" to listOf("calculator", "الحاسبة", "حاسبة"),
      "calculator" to listOf("الحاسبة", "حاسبة"),
      "كروم" to listOf("chrome", "browser", "المتصفح"),
      "المتصفح" to listOf("browser", "chrome", "متصفح"),
      "chrome" to listOf("كروم", "المتصفح"),
      "جيميل" to listOf("gmail", "email", "البريد"),
      "البريد" to listOf("email", "gmail", "جيميل"),
      "gmail" to listOf("جيميل", "البريد"),
      "سبوتيفاي" to listOf("spotify", "موسيقى"),
      "spotify" to listOf("سبوتيفاي"),
      "نتفلكس" to listOf("netflix", "نتفليكس"),
      "netflix" to listOf("نتفلكس", "نتفليكس"),
      "المتجر" to listOf("play store", "store", "google play", "متجر", "سوق بلاي"),
      "play store" to listOf("المتجر", "سوق بلاي"),
      "الملاحظات" to listOf("notes", "keep", "ملاحظات"),
      "notes" to listOf("الملاحظات", "ملاحظات"),
      "الملفات" to listOf("files", "file manager", "ملفات"),
      "files" to listOf("الملفات", "ملفات")
    )
  }

  /**
   * Builds an AppMatch entry from an application label, package name, and launch intent.
   * Automatically generates cross-language aliases, decomposed tokens, and phonetic skeleton.
   */
  fun createAppMatch(label: String, packageName: String, launchIntent: Intent): AppMatch {
    val normLabel = UniversalAppNormalizer.normalize(label)
    val aliases = mutableSetOf<String>()

    // Cross-language aliases generated via UniversalAppNormalizer
    aliases.addAll(UniversalAppNormalizer.generateCrossLanguageAliases(label))

    // Spaceless label
    val spaceless = normLabel.replace(" ", "")
    if (spaceless.isNotEmpty()) aliases.add(spaceless)

    // Tokenized words
    val tokens = normLabel.split(" ").filter { it.isNotBlank() }
    aliases.addAll(tokens)

    // Definite article stripped
    val strippedArticle = UniversalAppNormalizer.stripDefiniteArticle(normLabel)
    if (strippedArticle != normLabel) {
      aliases.add(strippedArticle)
      aliases.add(strippedArticle.replace(" ", ""))
    }

    // Strip common vendor prefixes (e.g. "Google Chrome" -> "chrome")
    val vendorPrefixes = listOf("google ", "samsung ", "microsoft ", "xiaomi ", "huawei ", "android ")
    for (prefix in vendorPrefixes) {
      if (normLabel.startsWith(prefix)) {
        val remainder = normLabel.substring(prefix.length).trim()
        if (remainder.isNotEmpty()) {
          aliases.add(remainder)
          aliases.add(remainder.replace(" ", ""))
        }
      }
    }

    // Package name suffix segments
    val pkgSegments = packageName.split(".").filter {
      it !in listOf("com", "org", "net", "android", "google", "app", "apps", "mobile") && it.length > 2
    }
    aliases.addAll(pkgSegments)

    // Secondary label equivalents
    LABEL_EQUIVALENTS[normLabel]?.forEach { eq ->
      aliases.add(UniversalAppNormalizer.normalize(eq))
      aliases.add(UniversalAppNormalizer.normalize(eq).replace(" ", ""))
    }
    tokens.forEach { token ->
      LABEL_EQUIVALENTS[token]?.forEach { eq ->
        aliases.add(UniversalAppNormalizer.normalize(eq))
      }
    }

    val skeleton = UniversalAppNormalizer.toPhoneticSkeleton(label)

    return AppMatch(
      label = label,
      packageName = packageName,
      launchIntent = launchIntent,
      normalizedLabel = normLabel,
      aliases = aliases,
      phoneticSkeleton = skeleton
    )
  }

  /**
   * Retrieves all user-launchable apps from the device using PackageManager.
   * Caches the result in memory to maintain maximum performance.
   */
  fun getInstalledLaunchableApps(forceRefresh: Boolean = false): List<AppMatch> {
    synchronized(indexLock) {
      testAppsOverride?.let { return it }

      val now = System.currentTimeMillis()
      if (!forceRefresh && cachedApps.isNotEmpty() && (now - lastIndexTimestamp < CACHE_EXPIRATION_MS)) {
        return cachedApps
      }

      val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
        addCategory(Intent.CATEGORY_LAUNCHER)
      }

      val resolveInfos: List<ResolveInfo> = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.queryIntentActivities(
            mainIntent,
            PackageManager.ResolveInfoFlags.of(0L)
          )
        } else {
          @Suppress("DEPRECATION")
          packageManager.queryIntentActivities(mainIntent, 0)
        }
      } catch (e: Exception) {
        Log.e(TAG, "Error querying launcher activities via PackageManager", e)
        emptyList()
      }

      val discoveredMap = mutableMapOf<String, AppMatch>()

      for (info in resolveInfos) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (pkg.isBlank() || discoveredMap.containsKey(pkg)) continue
        val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
        val label = try {
          info.loadLabel(packageManager).toString()
        } catch (_: Exception) {
          pkg
        }
        discoveredMap[pkg] = createAppMatch(label, pkg, launchIntent)
      }

      // Supplementary scan via getInstalledApplications in case any launcher activity was filtered
      try {
        val installedApps = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
          packageManager.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
          @Suppress("DEPRECATION")
          packageManager.getInstalledApplications(0)
        }
        for (appInfo in installedApps) {
          val pkg = appInfo.packageName ?: continue
          if (pkg.isBlank() || discoveredMap.containsKey(pkg)) continue
          val launchIntent = packageManager.getLaunchIntentForPackage(pkg) ?: continue
          val label = try {
            appInfo.loadLabel(packageManager).toString()
          } catch (_: Exception) {
            pkg
          }
          discoveredMap[pkg] = createAppMatch(label, pkg, launchIntent)
        }
      } catch (e: Exception) {
        Log.w(TAG, "Supplementary app enumeration skipped", e)
      }

      cachedApps = discoveredMap.values.toList()
      lastIndexTimestamp = now
      Log.d(TAG, "Discovered and indexed ${cachedApps.size} launchable applications")
      return cachedApps
    }
  }

  fun warmUpIndex() {
    getInstalledLaunchableApps(forceRefresh = false)
  }

  fun refreshIndex(): List<AppMatch> {
    return getInstalledLaunchableApps(forceRefresh = true)
  }

  /**
   * Resolves a spoken or typed app query against installed applications.
   * Instruments matching with APP_QUERY, APP_NORMALIZED_QUERY, APP_CANDIDATES,
   * APP_MATCH_SCORE, APP_RESOLVED, and APP_NOT_FOUND.
   */
  fun resolveApp(rawQuery: String): AppResolveResult {
    val clean = rawQuery.trim()
    if (clean.isBlank()) {
      Log.d(TAG, "APP_NOT_FOUND: $rawQuery")
      return AppResolveResult.NotFound(rawQuery)
    }

    var result = matchAgainstApps(clean, getInstalledLaunchableApps(forceRefresh = false))

    // Fallback refresh if initial search failed
    if (result is AppResolveResult.NotFound) {
      val freshApps = getInstalledLaunchableApps(forceRefresh = true)
      result = matchAgainstApps(clean, freshApps)
    }

    return result
  }

  /**
   * Core scoring and resolution pipeline, testable with any list of installed apps.
   */
  fun matchAgainstApps(rawQuery: String, installedApps: List<AppMatch>): AppResolveResult {
    Log.d(TAG, "APP_QUERY: $rawQuery")
    if (installedApps.isEmpty()) {
      Log.d(TAG, "APP_NOT_FOUND: $rawQuery")
      return AppResolveResult.NotFound(rawQuery)
    }

    val normQuery = UniversalAppNormalizer.normalize(rawQuery)
    Log.d(TAG, "APP_NORMALIZED_QUERY: $normQuery")

    val candidateQueries = generateCandidateQueries(normQuery)
    Log.d(TAG, "APP_CANDIDATES: $candidateQueries")

    val querySkeleton = UniversalAppNormalizer.toPhoneticSkeleton(normQuery)

    // Score every installed app
    data class ScoredApp(val app: AppMatch, val score: Float, val reason: String)
    val scoredList = mutableListOf<ScoredApp>()

    for (app in installedApps) {
      val (score, reason) = computeAppScore(app, normQuery, candidateQueries, querySkeleton)
      if (score > 0.0f) {
        scoredList.add(ScoredApp(app, score, reason))
        Log.d(TAG, "APP_MATCH_SCORE: ${app.label} = $score ($reason)")
      }
    }

    scoredList.sortByDescending { it.score }

    if (scoredList.isEmpty() || scoredList.first().score < 0.65f) {
      Log.d(TAG, "APP_NOT_FOUND: $rawQuery")
      return AppResolveResult.NotFound(rawQuery)
    }

    val best = scoredList.first()

    // Ambiguity and confidence check
    if (scoredList.size > 1) {
      val second = scoredList[1]
      val scoreDiff = best.score - second.score
      // If top score is high (>= 0.90) and clearly better than second, resolve directly
      if (best.score >= 0.90f && scoreDiff >= 0.10f) {
        Log.d(TAG, "APP_RESOLVED: ${best.app.label} (${best.app.packageName})")
        return AppResolveResult.FoundSingle(best.app)
      }
      // If scores are too close, trigger disambiguation (NeedsConfirmation)
      if (scoreDiff < 0.10f && second.score >= 0.70f) {
        val ambiguousList = scoredList.filter { (best.score - it.score) < 0.10f }.map { it.app }
        Log.d(TAG, "APP_AMBIGUOUS: ${ambiguousList.map { it.label }}")
        return AppResolveResult.FoundMultiple(rawQuery, ambiguousList)
      }
    }

    Log.d(TAG, "APP_RESOLVED: ${best.app.label} (${best.app.packageName})")
    return AppResolveResult.FoundSingle(best.app)
  }

  /**
   * Computes matching confidence score between an application and candidate queries.
   */
  private fun computeAppScore(
    app: AppMatch,
    normQuery: String,
    candidateQueries: Set<String>,
    querySkeleton: String
  ): Pair<Float, String> {
    val normLabel = app.normalizedLabel
    val spacelessLabel = normLabel.replace(" ", "")

    // 1. Exact Match on normalized label or spaceless label
    for (cq in candidateQueries) {
      if (normLabel == cq || spacelessLabel == cq.replace(" ", "")) {
        return 1.0f to "EXACT_LABEL_MATCH"
      }
      if (app.packageName.equals(cq, ignoreCase = true)) {
        return 1.0f to "EXACT_PACKAGE_MATCH"
      }
      if (app.aliases.contains(cq) || app.aliases.contains(cq.replace(" ", ""))) {
        return 0.98f to "EXACT_ALIAS_MATCH"
      }
    }

    // 2. Token Match (e.g. "chat gpt" matches "Chat GPT", "whatsapp" matches "WhatsApp Messenger")
    val labelTokens = normLabel.split(" ").filter { it.isNotBlank() }
    for (cq in candidateQueries) {
      val cqTokens = cq.split(" ").filter { it.isNotBlank() }
      if (labelTokens.size > 1 && cqTokens.size > 1 && labelTokens == cqTokens) {
        return 0.95f to "EXACT_TOKEN_SEQUENCE"
      }
      if (labelTokens.any { t -> t == cq }) {
        return 0.88f to "PRIMARY_TOKEN_MATCH"
      }
    }

    // 3. Phonetic Skeleton Match
    if (querySkeleton.isNotBlank() && app.phoneticSkeleton.isNotBlank()) {
      if (querySkeleton == app.phoneticSkeleton) {
        return 0.92f to "PHONETIC_SKELETON_MATCH"
      }
      // Check prefix of skeleton for compound queries
      if (querySkeleton.length >= 4 && app.phoneticSkeleton.length >= 4) {
        if (app.phoneticSkeleton.startsWith(querySkeleton) || querySkeleton.startsWith(app.phoneticSkeleton)) {
          return 0.82f to "PHONETIC_SKELETON_PREFIX"
        }
      }
    }

    // 4. Prefix / Substring Match
    for (cq in candidateQueries) {
      val cqSpaceless = cq.replace(" ", "")
      if (cqSpaceless.length >= 4 && (spacelessLabel.startsWith(cqSpaceless) || cqSpaceless.startsWith(spacelessLabel))) {
        return 0.80f to "PREFIX_MATCH"
      }
    }

    // 5. Controlled Levenshtein Fuzzy Match
    var maxSim = 0.0f
    val appTerms = mutableListOf(normLabel, spacelessLabel)
    appTerms.addAll(app.aliases)

    for (term in appTerms) {
      if (term.length < 4) continue
      for (cq in candidateQueries) {
        if (cq.length < 4) continue
        if (kotlin.math.abs(term.length - cq.length) <= 3) {
          val sim = UniversalAppNormalizer.similarity(term, cq)
          if (sim > maxSim) {
            maxSim = sim
          }
        }
      }
    }

    if (maxSim >= 0.78f) {
      val scaledScore = 0.65f + (maxSim - 0.78f) * 0.8f
      return scaledScore.coerceAtMost(0.85f) to "FUZZY_MATCH_${String.format("%.2f", maxSim)}"
    }

    return 0.0f to "NO_MATCH"
  }

  /**
   * Generates candidate variations for a normalized query.
   */
  fun generateCandidateQueries(normQuery: String): Set<String> {
    val candidates = mutableSetOf<String>()
    candidates.add(normQuery)
    val spaceless = normQuery.replace(" ", "")
    if (spaceless.isNotEmpty()) candidates.add(spaceless)

    // Strip Arabic definite article "ال"
    val strippedArticle = UniversalAppNormalizer.stripDefiniteArticle(normQuery)
    if (strippedArticle != normQuery) {
      candidates.add(strippedArticle)
      candidates.add(strippedArticle.replace(" ", ""))
    }

    // Cross-script spoken Arabic to Latin translation (e.g. "شات جي بي تي" -> "chat gpt", "chatgpt")
    val crossScriptVariants = UniversalAppNormalizer.convertSpokenArabicToLatin(normQuery)
    candidates.addAll(crossScriptVariants)
    if (strippedArticle != normQuery) {
      val crossScriptStripped = UniversalAppNormalizer.convertSpokenArabicToLatin(strippedArticle)
      candidates.addAll(crossScriptStripped)
    }

    // Secondary mapped equivalents
    LABEL_EQUIVALENTS[normQuery]?.forEach { eq ->
      val n = UniversalAppNormalizer.normalize(eq)
      candidates.add(n)
      candidates.add(n.replace(" ", ""))
    }
    if (strippedArticle != normQuery) {
      LABEL_EQUIVALENTS[strippedArticle]?.forEach { eq ->
        val n = UniversalAppNormalizer.normalize(eq)
        candidates.add(n)
        candidates.add(n.replace(" ", ""))
      }
    }

    return candidates
  }

  /**
   * Safely launches an application match.
   */
  fun launchApp(app: AppMatch): Boolean {
    return try {
      val intent = (app.launchIntent.clone() as Intent).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
      }
      context.startActivity(intent)
      Log.d(TAG, "Successfully launched ${app.label} (${app.packageName})")
      true
    } catch (e: Exception) {
      Log.e(TAG, "Failed to launch app ${app.packageName}", e)
      false
    }
  }
}
