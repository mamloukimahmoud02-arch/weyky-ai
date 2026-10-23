package com.example.device.calendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import java.util.Calendar

data class CalendarEventItem(
  val id: Long,
  val title: String,
  val description: String?,
  val location: String?,
  val startMillis: Long,
  val endMillis: Long,
  val minutesUntilStart: Long
)

data class CalendarSummary(
  val hasPermission: Boolean,
  val isAvailable: Boolean,
  val nextEvent: CalendarEventItem?,
  val upcomingEventsToday: List<CalendarEventItem> = emptyList(),
  val statusMessage: String? = null
)

class CalendarProvider(private val context: Context) {

  fun hasCalendarPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.READ_CALENDAR
    ) == PackageManager.PERMISSION_GRANTED
  }

  fun queryUpcomingEvents(): CalendarSummary {
    if (!hasCalendarPermission()) {
      return CalendarSummary(
        hasPermission = false,
        isAvailable = false,
        nextEvent = null,
        statusMessage = "صلاحية التقويم غير ممنوحة"
      )
    }

    val now = System.currentTimeMillis()
    val endOfDayCal = Calendar.getInstance().apply {
      set(Calendar.HOUR_OF_DAY, 23)
      set(Calendar.MINUTE, 59)
      set(Calendar.SECOND, 59)
      set(Calendar.MILLISECOND, 999)
    }
    val endOfDay = endOfDayCal.timeInMillis

    var cursor: Cursor? = null
    return try {
      val projection = arrayOf(
        CalendarContract.Events._ID,
        CalendarContract.Events.TITLE,
        CalendarContract.Events.DESCRIPTION,
        CalendarContract.Events.EVENT_LOCATION,
        CalendarContract.Events.DTSTART,
        CalendarContract.Events.DTEND
      )

      val selection = "${CalendarContract.Events.DTSTART} >= ? AND ${CalendarContract.Events.DTSTART} <= ? AND ${CalendarContract.Events.DELETED} = 0"
      val selectionArgs = arrayOf(now.toString(), endOfDay.toString())
      val sortOrder = "${CalendarContract.Events.DTSTART} ASC LIMIT 5"

      cursor = context.contentResolver.query(
        CalendarContract.Events.CONTENT_URI,
        projection,
        selection,
        selectionArgs,
        sortOrder
      )

      val events = mutableListOf<CalendarEventItem>()
      if (cursor != null && cursor.moveToFirst()) {
        val idIdx = cursor.getColumnIndex(CalendarContract.Events._ID)
        val titleIdx = cursor.getColumnIndex(CalendarContract.Events.TITLE)
        val descIdx = cursor.getColumnIndex(CalendarContract.Events.DESCRIPTION)
        val locIdx = cursor.getColumnIndex(CalendarContract.Events.EVENT_LOCATION)
        val startIdx = cursor.getColumnIndex(CalendarContract.Events.DTSTART)
        val endIdx = cursor.getColumnIndex(CalendarContract.Events.DTEND)

        do {
          val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
          val title = if (titleIdx >= 0) cursor.getString(titleIdx) ?: "موعد" else "موعد"
          val desc = if (descIdx >= 0) cursor.getString(descIdx) else null
          val location = if (locIdx >= 0) cursor.getString(locIdx) else null
          val dtStart = if (startIdx >= 0) cursor.getLong(startIdx) else now
          val dtEnd = if (endIdx >= 0) cursor.getLong(endIdx) else dtStart + 3600_000L

          val minsUntil = ((dtStart - now) / 60_000L).coerceAtLeast(0L)
          events.add(
            CalendarEventItem(
              id = id,
              title = title,
              description = desc,
              location = location,
              startMillis = dtStart,
              endMillis = dtEnd,
              minutesUntilStart = minsUntil
            )
          )
        } while (cursor.moveToNext())
      }

      CalendarSummary(
        hasPermission = true,
        isAvailable = true,
        nextEvent = events.firstOrNull(),
        upcomingEventsToday = events,
        statusMessage = if (events.isEmpty()) "لا توجد مواعيد متبقية اليوم" else null
      )
    } catch (e: Exception) {
      CalendarSummary(
        hasPermission = true,
        isAvailable = false,
        nextEvent = null,
        statusMessage = "تعذر قراءة التقويم: ${e.message}"
      )
    } finally {
      cursor?.close()
    }
  }
}
