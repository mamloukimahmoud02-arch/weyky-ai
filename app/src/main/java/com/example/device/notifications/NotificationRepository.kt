package com.example.device.notifications

import android.app.Notification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class NotificationImportanceCategory {
  PERSONAL_MESSAGE,
  MISSED_CALL,
  REMINDER_EVENT,
  SECURITY_SYSTEM_ALERT,
  UNIMPORTANT
}

data class NotificationItem(
  val id: Int,
  val packageName: String,
  val title: String,
  val text: String,
  val postTimeMillis: Long,
  val category: String? = null,
  val isOngoing: Boolean = false,
  val priority: Int = Notification.PRIORITY_DEFAULT
)

data class NotificationEvent(
  val eventId: String,
  val packageName: String,
  val title: String,
  val text: String,
  val timestampMillis: Long,
  val importanceCategory: NotificationImportanceCategory,
  val isOngoing: Boolean = false,
  val appLabel: String? = null
)

data class NotificationSummary(
  val totalImportantCount: Int,
  val latestImportantEvent: NotificationEvent?,
  val isAccessGranted: Boolean,
  val activeImportantEvents: List<NotificationEvent> = emptyList()
) {
  val totalCount: Int get() = totalImportantCount
  val latestTitle: String? get() = latestImportantEvent?.title
  val latestText: String? get() = latestImportantEvent?.text
  val recentEvents: List<NotificationEvent> get() = activeImportantEvents
}

object NotificationRepository {
  private val _activeNotifications = MutableStateFlow<List<NotificationItem>>(emptyList())
  val activeNotifications: StateFlow<List<NotificationItem>> = _activeNotifications.asStateFlow()

  private val _importantEvents = MutableStateFlow<List<NotificationEvent>>(emptyList())
  val importantEvents: StateFlow<List<NotificationEvent>> = _importantEvents.asStateFlow()

  private val _isServiceConnected = MutableStateFlow(false)
  val isServiceConnected: StateFlow<Boolean> = _isServiceConnected.asStateFlow()

  // Track dismissed event IDs so user can dismiss an alert from the home screen
  private val dismissedEventIds = mutableSetOf<String>()

  // Privacy mode: show app label only without sensitive message text
  @Volatile
  var isPrivacyModeEnabled: Boolean = false

  // Deduplication tracker: packageName + title + text -> timestamp
  private val recentPostedHistory = mutableMapOf<String, Long>()

  private val PROMOTIONAL_KEYWORDS = listOf(
    "خصم", "تخفيض", "تخفيضات", "عرض", "عروض", "تسوق", "اشتر", "قسيمة", "كوبون", "كود خصم",
    "توصيل مجاني", "اعلان", "إعلان", "ممول", "اشترك الآن", "سحب", "جائزة", "ربح",
    "promo", "discount", "sale", "offer", "deal", "coupon", "cashback", "newsletter",
    "sponsored", "50% off", "free delivery", "limited time", "flash sale"
  )

  fun onServiceConnected() {
    _isServiceConnected.value = true
  }

  fun onServiceDisconnected() {
    _isServiceConnected.value = false
    _activeNotifications.value = emptyList()
    _importantEvents.value = emptyList()
  }

  /**
   * Evaluates if a notification qualifies as an important, actionable alert.
   * Drops promotions, ongoing background bars, and silent low-priority updates.
   */
  fun classifyNotification(
    packageName: String,
    title: String,
    text: String,
    category: String?,
    isOngoing: Boolean,
    priority: Int
  ): NotificationImportanceCategory {
    // 1. Ongoing notifications or Media/Transport notifications (music player, persistent foreground service) -> Dropped
    if (isOngoing || category == Notification.CATEGORY_TRANSPORT) return NotificationImportanceCategory.UNIMPORTANT

    // Drop notifications from music/media players so music never appears in the notifications card
    val pkgLower = packageName.lowercase(Locale.ROOT)
    val isMusicOrMediaApp = pkgLower.contains("spotify") ||
      pkgLower.contains("music") ||
      pkgLower.contains("soundcloud") ||
      pkgLower.contains("anghami") ||
      pkgLower.contains("deezer") ||
      pkgLower.contains("audiomack") ||
      pkgLower.contains("shazam") ||
      pkgLower.contains("podcast") ||
      pkgLower.contains("mediaplayer") ||
      pkgLower.contains("vlc") ||
      pkgLower.contains("mp3") ||
      pkgLower.contains("audio") ||
      pkgLower.contains("player")
    if (isMusicOrMediaApp) return NotificationImportanceCategory.UNIMPORTANT

    // 2. Silent or Min Priority -> Dropped
    if (priority <= Notification.PRIORITY_LOW) return NotificationImportanceCategory.UNIMPORTANT

    val combined = "$title $text".lowercase(Locale.ROOT)

    // 3. Promotional filter -> Dropped
    for (kw in PROMOTIONAL_KEYWORDS) {
      if (combined.contains(kw)) {
        return NotificationImportanceCategory.UNIMPORTANT
      }
    }

    // 4. Missed Call category
    if (category == Notification.CATEGORY_CALL ||
      combined.contains("مكالمة فائتة") || combined.contains("مكالمات فائتة") ||
      combined.contains("missed call")
    ) {
      return NotificationImportanceCategory.MISSED_CALL
    }

    // 5. Personal Messaging, Social Media & Direct Communications
    val isSocialOrMessagingPackage = packageName.contains("whatsapp", ignoreCase = true) ||
      packageName.contains("telegram", ignoreCase = true) ||
      packageName.contains("instagram", ignoreCase = true) ||
      packageName.contains("facebook", ignoreCase = true) ||
      packageName.contains("orca", ignoreCase = true) || // Facebook Messenger
      packageName.contains("messenger", ignoreCase = true) ||
      packageName.contains("snapchat", ignoreCase = true) ||
      packageName.contains("twitter", ignoreCase = true) ||
      packageName.contains("com.x.", ignoreCase = true) ||
      packageName.contains("tiktok", ignoreCase = true) ||
      packageName.contains("zhiliaoapp", ignoreCase = true) || // TikTok
      packageName.contains("musically", ignoreCase = true) ||
      packageName.contains("discord", ignoreCase = true) ||
      packageName.contains("linkedin", ignoreCase = true) ||
      packageName.contains("threads", ignoreCase = true) ||
      packageName.contains("barcelona", ignoreCase = true) || // Instagram Threads
      packageName.contains("viber", ignoreCase = true) ||
      packageName.contains("imo", ignoreCase = true) ||
      packageName.contains("skype", ignoreCase = true) ||
      packageName.contains("wechat", ignoreCase = true) ||
      packageName.contains("line", ignoreCase = true) ||
      packageName.contains("reddit", ignoreCase = true) ||
      packageName.contains("gmail", ignoreCase = true) ||
      packageName.contains("outlook", ignoreCase = true) ||
      packageName.contains("messaging", ignoreCase = true) ||
      packageName.contains("mms", ignoreCase = true) ||
      packageName.contains("signal", ignoreCase = true) ||
      packageName.contains("chat", ignoreCase = true)

    if (category == Notification.CATEGORY_MESSAGE ||
      category == Notification.CATEGORY_SOCIAL ||
      category == Notification.CATEGORY_EMAIL ||
      isSocialOrMessagingPackage
    ) {
      if (title.isNotBlank() || text.isNotBlank()) {
        return NotificationImportanceCategory.PERSONAL_MESSAGE
      }
    }

    // 6. Alarms, Reminders, Calendar Events
    if (category == Notification.CATEGORY_ALARM ||
      category == Notification.CATEGORY_REMINDER ||
      category == Notification.CATEGORY_EVENT
    ) {
      return NotificationImportanceCategory.REMINDER_EVENT
    }

    // 7. System Security & Urgent Errors
    if (category == Notification.CATEGORY_SYSTEM ||
      category == Notification.CATEGORY_ERROR ||
      category == Notification.CATEGORY_ALARM ||
      combined.contains("تحذير أمني") || combined.contains("security alert") || combined.contains("طارئ")
    ) {
      return NotificationImportanceCategory.SECURITY_SYSTEM_ALERT
    }

    return NotificationImportanceCategory.UNIMPORTANT
  }

  fun postNewEvent(
    packageName: String,
    title: String,
    text: String,
    postTimeMillis: Long,
    category: String?,
    isOngoing: Boolean,
    priority: Int,
    id: Int
  ) {
    val importance = classifyNotification(packageName, title, text, category, isOngoing, priority)
    if (importance == NotificationImportanceCategory.UNIMPORTANT) {
      return // Purely ignore unimportant notifications
    }

    val eventId = "${packageName}_${id}_${postTimeMillis}"
    if (dismissedEventIds.contains(eventId)) {
      return
    }

    // Deduplication key
    val dedupKey = "${packageName}|${title.trim()}|${text.trim()}"
    val lastTime = recentPostedHistory[dedupKey] ?: 0L
    if (System.currentTimeMillis() - lastTime < 10000L) {
      return // Ignore duplicate within 10 seconds
    }
    recentPostedHistory[dedupKey] = System.currentTimeMillis()

    val appFriendly = resolveAppFriendlyName(packageName)
    val displayTitle = if (isPrivacyModeEnabled) appFriendly else title
    val displayText = if (isPrivacyModeEnabled) "تنبيه جديد (مخفي للخصوصية)" else text

    val event = NotificationEvent(
      eventId = eventId,
      packageName = packageName,
      title = displayTitle,
      text = displayText,
      timestampMillis = postTimeMillis,
      importanceCategory = importance,
      isOngoing = isOngoing,
      appLabel = appFriendly
    )

    val current = _importantEvents.value.toMutableList()
    // Replace if same eventId or prepend
    current.removeAll { it.eventId == eventId }
    current.add(0, event)

    // Keep up to 5 important events
    if (current.size > 5) {
      _importantEvents.value = current.take(5)
    } else {
      _importantEvents.value = current
    }
  }

  fun updateNotifications(notifications: List<NotificationItem>) {
    _activeNotifications.value = notifications
  }

  fun dismissEvent(eventId: String) {
    dismissedEventIds.add(eventId)
    val current = _importantEvents.value.toMutableList()
    current.removeAll { it.eventId == eventId }
    _importantEvents.value = current
  }

  fun clearAllEvents() {
    _importantEvents.value.forEach { dismissedEventIds.add(it.eventId) }
    _importantEvents.value = emptyList()
  }

  fun getSummary(hasPermission: Boolean): NotificationSummary {
    val events = _importantEvents.value
    return NotificationSummary(
      totalImportantCount = events.size,
      latestImportantEvent = events.firstOrNull(),
      isAccessGranted = hasPermission,
      activeImportantEvents = events
    )
  }

  private fun resolveAppFriendlyName(packageName: String): String {
    val pkg = packageName.lowercase(Locale.ROOT)
    return when {
      pkg.contains("whatsapp") -> "WHATSAPP"
      pkg.contains("telegram") -> "TELEGRAM"
      pkg.contains("instagram") -> "INSTAGRAM"
      pkg.contains("orca") || pkg.contains("messenger") -> "MESSENGER"
      pkg.contains("facebook") -> "FACEBOOK"
      pkg.contains("snapchat") -> "SNAPCHAT"
      pkg.contains("twitter") || pkg.contains("com.x.") -> "X / TWITTER"
      pkg.contains("tiktok") || pkg.contains("zhiliaoapp") || pkg.contains("musically") -> "TIKTOK"
      pkg.contains("discord") -> "DISCORD"
      pkg.contains("linkedin") -> "LINKEDIN"
      pkg.contains("threads") || pkg.contains("barcelona") -> "THREADS"
      pkg.contains("viber") -> "VIBER"
      pkg.contains("imo") -> "IMO"
      pkg.contains("signal") -> "SIGNAL"
      pkg.contains("reddit") -> "REDDIT"
      pkg.contains("youtube") -> "YOUTUBE"
      pkg.contains("gmail") || pkg.contains("outlook") -> "EMAIL"
      pkg.contains("dialer") || pkg.contains("telecom") || pkg.contains("phone") -> "PHONE"
      pkg.contains("messaging") || pkg.contains("mms") -> "SMS"
      pkg.contains("calendar") -> "CALENDAR"
      else -> "ALERT"
    }
  }
}
