package com.example.device.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WeykyNotificationListenerService : NotificationListenerService() {

  override fun onListenerConnected() {
    super.onListenerConnected()
    NotificationRepository.onServiceConnected()
    refreshActiveNotifications()
  }

  override fun onListenerDisconnected() {
    super.onListenerDisconnected()
    NotificationRepository.onServiceDisconnected()
  }

  override fun onNotificationPosted(sbn: StatusBarNotification?) {
    if (sbn == null) return
    val notif = sbn.notification ?: return
    val extras = notif.extras ?: return
    val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
    val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
    val isOngoing = sbn.isOngoing || ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
    val category = notif.category
    val hasMediaSession = extras.containsKey(Notification.EXTRA_MEDIA_SESSION) ||
      notif.extras?.get(Notification.EXTRA_TEMPLATE)?.toString()?.contains("MediaStyle", ignoreCase = true) == true
    @Suppress("DEPRECATION")
    val priority = notif.priority

    if (!hasMediaSession && category != Notification.CATEGORY_TRANSPORT && (title.isNotBlank() || text.isNotBlank())) {
      NotificationRepository.postNewEvent(
        packageName = sbn.packageName,
        title = title,
        text = text,
        postTimeMillis = sbn.postTime,
        category = category,
        isOngoing = isOngoing,
        priority = priority,
        id = sbn.id
      )
    }
    refreshActiveNotifications()
  }

  override fun onNotificationRemoved(sbn: StatusBarNotification?) {
    refreshActiveNotifications()
  }

  private fun refreshActiveNotifications() {
    try {
      val sbns = activeNotifications ?: return
      val items = sbns.mapNotNull { sbn ->
        val notif = sbn.notification ?: return@mapNotNull null
        val extras = notif.extras ?: return@mapNotNull null
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val isOngoing = sbn.isOngoing || ((notif.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        @Suppress("DEPRECATION")
        val priority = notif.priority
        if (title.isBlank() && text.isBlank()) null
        else NotificationItem(
          id = sbn.id,
          packageName = sbn.packageName,
          title = title,
          text = text,
          postTimeMillis = sbn.postTime,
          category = notif.category,
          isOngoing = isOngoing,
          priority = priority
        )
      }
      NotificationRepository.updateNotifications(items)
    } catch (_: Exception) {}
  }
}
