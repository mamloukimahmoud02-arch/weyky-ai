package com.example.core.voice.model

import com.example.core.voice.engine.EdgeNeuralTtsEngine

/**
 * High-definition Arabic neural voice catalog entry.
 */
data class ArabicVoicePreset(
  val flag: String,
  val name: String,
  val country: String,
  val gender: String,
  val voiceKey: String,
  val previewText: String
) {
  val fullLabel: String
    get() = "$flag $name ($country) - $gender"
}

object ArabicVoiceCatalog {
  val VOICES: List<ArabicVoicePreset> = listOf(
    ArabicVoicePreset(
      flag = "🇪🇬",
      name = "سلمى",
      country = "مصر",
      gender = "أنثى",
      voiceKey = EdgeNeuralTtsEngine.VOICE_SALMA_EGYPT,
      previewText = "أهلاً بك، أنا سلمى من مصر. يسعدني تقديم المساعدة لك دائماً."
    ),
    ArabicVoicePreset(
      flag = "🇸🇦",
      name = "حامد",
      country = "السعودية",
      gender = "ذكر",
      voiceKey = EdgeNeuralTtsEngine.VOICE_HAMED_SAUDI,
      previewText = "أهلاً بك، أنا حامد من السعودية. رهن إشارتك في أي وقت."
    ),
    ArabicVoicePreset(
      flag = "🇦🇪",
      name = "فاطمة",
      country = "الإمارات",
      gender = "أنثى",
      voiceKey = EdgeNeuralTtsEngine.VOICE_FATIMA_UAE,
      previewText = "مرحباً بك، أنا فاطمة من الإمارات. كيف يمكنني مساعدتك اليوم؟"
    ),
    ArabicVoicePreset(
      flag = "🇸🇦",
      name = "زارية",
      country = "السعودية",
      gender = "أنثى",
      voiceKey = EdgeNeuralTtsEngine.VOICE_ZARIYAH_SAUDI,
      previewText = "أهلاً بك، أنا زارية من السعودية. أتمنى لك يوماً سعيداً ومميزاً."
    ),
    ArabicVoicePreset(
      flag = "🇹🇳",
      name = "الهادي",
      country = "تونس",
      gender = "ذكر",
      voiceKey = EdgeNeuralTtsEngine.VOICE_HEDI_TUNISIA,
      previewText = "عسّلامة، أنا الهادي من تونس. حاضر لكل استفساراتك وطلباتك."
    ),
    ArabicVoicePreset(
      flag = "🇹🇳",
      name = "ريم",
      country = "تونس",
      gender = "أنثى",
      voiceKey = EdgeNeuralTtsEngine.VOICE_REEM_TUNISIA,
      previewText = "عسّلامة، أنا ريم من تونس. في خدمتك بكل سرور."
    )
  )

  fun findByKey(key: String): ArabicVoicePreset? {
    return VOICES.firstOrNull { it.voiceKey == key }
  }
}
