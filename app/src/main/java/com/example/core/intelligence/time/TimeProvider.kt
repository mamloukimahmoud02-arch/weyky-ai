package com.example.core.intelligence.time

import java.util.Calendar

/**
 * Testable, deterministic time provider interface for WEYKY intelligence.
 */
interface TimeProvider {
  fun now(): Calendar
  fun currentTimeMillis(): Long = now().timeInMillis
}

/**
 * Production implementation using current system time.
 */
class SystemTimeProvider : TimeProvider {
  override fun now(): Calendar = Calendar.getInstance()
}

/**
 * Test implementation allowing deterministic setting of time in unit tests.
 */
class SettableTimeProvider(
  initialCalendar: Calendar = Calendar.getInstance()
) : TimeProvider {
  private var currentCalendar: Calendar = initialCalendar.clone() as Calendar

  override fun now(): Calendar = currentCalendar.clone() as Calendar

  fun setTime(hourOfDay: Int, minute: Int, second: Int = 0) {
    currentCalendar = (currentCalendar.clone() as Calendar).apply {
      set(Calendar.HOUR_OF_DAY, hourOfDay)
      set(Calendar.MINUTE, minute)
      set(Calendar.SECOND, second)
      set(Calendar.MILLISECOND, 0)
    }
  }

  fun setCalendar(calendar: Calendar) {
    currentCalendar = calendar.clone() as Calendar
  }
}
