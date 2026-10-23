package com.example.rendering.visual_state

import androidx.compose.ui.graphics.Color

/**
 * Central visual states for WEYKY Core.
 * IDLE, LISTENING, PROCESSING, RESPONDING, SUCCESS, ERROR, CALL, MUSIC, NOTIFICATION, CHARGING
 */
enum class CoreVisualState(
  val displayName: String,
  val primaryColor: Color,
  val secondaryColor: Color,
  val accentColor: Color,
  val speedMultiplier: Float,
  val glowIntensity: Float
) {
  IDLE(
    displayName = "Ambient Idle",
    primaryColor = Color(0xFFF59E0B), // Golden amber sun
    secondaryColor = Color(0xFFD97706), // Deep warm solar gold
    accentColor = Color(0xFFFDE047), // Radiant golden-yellow sun
    speedMultiplier = 0.5f,
    glowIntensity = 0.88f
  ),
  CHARGING(
    displayName = "Power Charging",
    primaryColor = Color(0xFF00B4D8), // Electric cyan-teal
    secondaryColor = Color(0xFF06D6A0), // Emerald energy
    accentColor = Color(0xFF39FF14), // Neon volt green
    speedMultiplier = 1.15f,
    glowIntensity = 0.85f
  ),
  LISTENING(
    displayName = "Listening",
    primaryColor = Color(0xFF00ADB5), // Cyan
    secondaryColor = Color(0xFF0077B6), // Azure
    accentColor = Color(0xFF90E0EF), // Pale cyan highlight
    speedMultiplier = 1.6f,
    glowIntensity = 0.85f
  ),
  PROCESSING(
    displayName = "Processing",
    primaryColor = Color(0xFF7209B7), // Purple
    secondaryColor = Color(0xFF3F37C9), // Indigo
    accentColor = Color(0xFFF72585), // Magenta / Pink
    speedMultiplier = 2.2f,
    glowIntensity = 0.9f
  ),
  RESPONDING(
    displayName = "Responding",
    primaryColor = Color(0xFF06D6A0), // Emerald turquoise
    secondaryColor = Color(0xFF118AB2), // Teal
    accentColor = Color(0xFFFFD166), // Soft warm amber
    speedMultiplier = 1.0f,
    glowIntensity = 0.75f
  ),
  SUCCESS(
    displayName = "Success",
    primaryColor = Color(0xFF06D6A0), // Emerald
    secondaryColor = Color(0xFFFFD166), // Amber gold
    accentColor = Color(0xFFFFFFFF), // Radiant white
    speedMultiplier = 1.4f,
    glowIntensity = 1.0f
  ),
  ERROR(
    displayName = "Error",
    primaryColor = Color(0xFFD90429), // Crimson
    secondaryColor = Color(0xFFEF233C), // Coral red
    accentColor = Color(0xFFFFB703), // Amber
    speedMultiplier = 1.2f,
    glowIntensity = 0.65f
  ),
  CALL(
    displayName = "Call Active",
    primaryColor = Color(0xFF2A9D8F), // Deep teal
    secondaryColor = Color(0xFF264653), // Slate teal
    accentColor = Color(0xFFE76F51), // Coral
    speedMultiplier = 1.3f,
    glowIntensity = 0.8f
  ),
  MUSIC(
    displayName = "Media Playback",
    primaryColor = Color(0xFF8338EC), // Vivid purple
    secondaryColor = Color(0xFFFF006E), // Vivid pink
    accentColor = Color(0xFFFFBE0B), // Warm yellow
    speedMultiplier = 1.5f,
    glowIntensity = 0.85f
  ),
  NOTIFICATION(
    displayName = "Notification Alert",
    primaryColor = Color(0xFFFB8500), // Amber orange
    secondaryColor = Color(0xFFFFB703), // Gold
    accentColor = Color(0xFF023047), // Deep navy
    speedMultiplier = 1.8f,
    glowIntensity = 0.9f
  )
}
