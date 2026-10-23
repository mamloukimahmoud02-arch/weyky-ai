package com.example.device.calls

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.os.Build
import android.provider.CallLog
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CallType(val displayNameAr: String, val displayNameEn: String) {
  MISSED("فائتة", "Missed"),
  INCOMING("واردة", "Incoming"),
  OUTGOING("صادرة", "Outgoing"),
  REJECTED("مرفوضة", "Rejected"),
  UNKNOWN("غير معروفة", "Unknown")
}

data class CallLogItem(
  val id: Long,
  val number: String,
  val callerName: String?,
  val type: CallType,
  val dateMillis: Long,
  val durationSeconds: Long,
  val isRead: Boolean,
  val isNew: Boolean
) {
  val formattedTime: String
    get() {
      val now = System.currentTimeMillis()
      val diffHours = (now - dateMillis) / (1000 * 60 * 60)
      return if (diffHours < 24) {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(dateMillis))
      } else {
        val sdf = SimpleDateFormat("d MMMM HH:mm", Locale.getDefault())
        sdf.format(Date(dateMillis))
      }
    }

  val displayLabel: String
    get() = callerName?.ifBlank { null } ?: number.ifBlank { "رقم غير معروف" }
}

data class CallSummary(
  val missedCallsCount: Int,
  val unreadMissedCallsCount: Int,
  val latestMissedCall: CallLogItem?,
  val latestIncomingCall: CallLogItem?,
  val latestOutgoingCall: CallLogItem?,
  val recentCalls: List<CallLogItem> = emptyList(),
  val hasPermission: Boolean,
  val statusMessage: String? = null
)

class CallLogProvider(
  private val context: Context,
  private val contactLookupProvider: ContactLookupProvider? = null
) {

  @Volatile
  private var lastCheckedMissedTimestamp: Long = 0L

  fun hasCallLogPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CALL_LOG
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun setLastCheckedMissedTimestamp(timestamp: Long) {
    lastCheckedMissedTimestamp = timestamp
  }

  fun markMissedCallsAsSeen() {
    lastCheckedMissedTimestamp = System.currentTimeMillis()
  }

  /**
   * Queries full call history, correctly separating missed, incoming, outgoing, and rejected calls.
   * Deduplicates by CallLog._ID and enriches contact names.
   */
  fun queryCallsSummary(): CallSummary {
    if (!hasCallLogPermission()) {
      return CallSummary(
        missedCallsCount = 0,
        unreadMissedCallsCount = 0,
        latestMissedCall = null,
        latestIncomingCall = null,
        latestOutgoingCall = null,
        recentCalls = emptyList(),
        hasPermission = false,
        statusMessage = "يلزم منح صلاحية سجل المكالمات"
      )
    }

    var cursor: Cursor? = null
    val items = mutableListOf<CallLogItem>()

    try {
      val projection = mutableListOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.CACHED_NAME,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.TYPE
      )
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
        projection.add(CallLog.Calls.IS_READ)
      }
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        projection.add(CallLog.Calls.NEW)
      }

      val sortOrder = "${CallLog.Calls.DATE} DESC LIMIT 50"

      cursor = context.contentResolver.query(
        CallLog.Calls.CONTENT_URI,
        projection.toTypedArray(),
        null,
        null,
        sortOrder
      )

      if (cursor != null && cursor.moveToFirst()) {
        val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
        val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
        val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
        val durIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
        val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
        val isReadIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
          cursor.getColumnIndex(CallLog.Calls.IS_READ)
        } else -1
        val newIdx = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
          cursor.getColumnIndex(CallLog.Calls.NEW)
        } else -1

        val seenIds = mutableSetOf<Long>()

        do {
          val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
          if (id != 0L && !seenIds.add(id)) {
            continue // Deduplicate
          }

          val number = if (numIdx >= 0) cursor.getString(numIdx) ?: "رقم خاص" else "رقم خاص"
          var name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
          val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else System.currentTimeMillis()
          val duration = if (durIdx >= 0) cursor.getLong(durIdx) else 0L
          val rawType = if (typeIdx >= 0) cursor.getInt(typeIdx) else -1
          val isRead = if (isReadIdx >= 0) cursor.getInt(isReadIdx) == 1 else true
          val isNew = if (newIdx >= 0) cursor.getInt(newIdx) == 1 else false

          // Resolve contact name if cached name is missing
          if (name.isNullOrBlank() && number.isNotBlank() && contactLookupProvider?.hasContactsPermission() == true) {
            val contactMatch = contactLookupProvider.lookupContact(number)
            if (contactMatch is ContactLookupResult.FoundSingle) {
              name = contactMatch.contact.displayName
            }
          }

          val callType = when (rawType) {
            CallLog.Calls.MISSED_TYPE -> CallType.MISSED
            CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
            CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
            else -> {
              if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && rawType == CallLog.Calls.REJECTED_TYPE) {
                CallType.REJECTED
              } else {
                CallType.UNKNOWN
              }
            }
          }

          items.add(
            CallLogItem(
              id = id,
              number = number,
              callerName = name,
              type = callType,
              dateMillis = date,
              durationSeconds = duration,
              isRead = isRead,
              isNew = isNew
            )
          )
        } while (cursor.moveToNext())
      }
    } catch (e: Exception) {
      return CallSummary(
        missedCallsCount = 0,
        unreadMissedCallsCount = 0,
        latestMissedCall = null,
        latestIncomingCall = null,
        latestOutgoingCall = null,
        recentCalls = emptyList(),
        hasPermission = true,
        statusMessage = "تعذر قراءة سجل المكالمات: ${e.message}"
      )
    } finally {
      cursor?.close()
    }

    val missedList = items.filter { it.type == CallType.MISSED }
    // Only count unread or occurred after last checked timestamp
    val unreadMissed = missedList.filter { call ->
      (!call.isRead || call.isNew) && (lastCheckedMissedTimestamp == 0L || call.dateMillis > lastCheckedMissedTimestamp)
    }

    val latestMissed = missedList.firstOrNull()
    val latestIncoming = items.firstOrNull { it.type == CallType.INCOMING }
    val latestOutgoing = items.firstOrNull { it.type == CallType.OUTGOING }

    return CallSummary(
      missedCallsCount = missedList.size,
      unreadMissedCallsCount = unreadMissed.size,
      latestMissedCall = latestMissed,
      latestIncomingCall = latestIncoming,
      latestOutgoingCall = latestOutgoing,
      recentCalls = items,
      hasPermission = true
    )
  }

  // Backward compatibility alias
  fun queryMissedCalls(): CallSummary = queryCallsSummary()

  fun getLatestOutgoingCall(): CallLogItem? {
    val summary = queryCallsSummary()
    return summary.latestOutgoingCall
  }

  fun getLatestIncomingCall(): CallLogItem? {
    val summary = queryCallsSummary()
    return summary.latestIncomingCall
  }
}
