package com.example.core.voice

import com.example.core.voice.engine.EdgeNeuralTtsEngine
import com.example.core.voice.model.ArabicVoiceCatalog
import com.example.core.voice.model.VoiceModelManager
import com.example.core.voice.model.VoiceModelType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicVoiceCatalogTest {

  @Test
  fun `catalog contains exactly the 6 requested arabic voices`() {
    val voices = ArabicVoiceCatalog.VOICES
    assertEquals(6, voices.size)

    val salma = voices.find { it.name == "سلمى" }
    assertNotNull("Salma should exist", salma)
    assertEquals("🇪🇬", salma?.flag)
    assertEquals("مصر", salma?.country)
    assertEquals("أنثى", salma?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_SALMA_EGYPT, salma?.voiceKey)

    val hamed = voices.find { it.name == "حامد" }
    assertNotNull("Hamed should exist", hamed)
    assertEquals("🇸🇦", hamed?.flag)
    assertEquals("السعودية", hamed?.country)
    assertEquals("ذكر", hamed?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_HAMED_SAUDI, hamed?.voiceKey)

    val fatima = voices.find { it.name == "فاطمة" }
    assertNotNull("Fatima should exist", fatima)
    assertEquals("🇦🇪", fatima?.flag)
    assertEquals("الإمارات", fatima?.country)
    assertEquals("أنثى", fatima?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_FATIMA_UAE, fatima?.voiceKey)

    val zariyah = voices.find { it.name == "زارية" }
    assertNotNull("Zariyah should exist", zariyah)
    assertEquals("🇸🇦", zariyah?.flag)
    assertEquals("السعودية", zariyah?.country)
    assertEquals("أنثى", zariyah?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_ZARIYAH_SAUDI, zariyah?.voiceKey)

    val hedi = voices.find { it.name == "الهادي" }
    assertNotNull("Hedi should exist", hedi)
    assertEquals("🇹🇳", hedi?.flag)
    assertEquals("تونس", hedi?.country)
    assertEquals("ذكر", hedi?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_HEDI_TUNISIA, hedi?.voiceKey)

    val reem = voices.find { it.name == "ريم" }
    assertNotNull("Reem should exist", reem)
    assertEquals("🇹🇳", reem?.flag)
    assertEquals("تونس", reem?.country)
    assertEquals("أنثى", reem?.gender)
    assertEquals(EdgeNeuralTtsEngine.VOICE_REEM_TUNISIA, reem?.voiceKey)
  }

  @Test
  fun `findByKey returns correct preset`() {
    val voice = ArabicVoiceCatalog.findByKey(EdgeNeuralTtsEngine.VOICE_ZARIYAH_SAUDI)
    assertNotNull(voice)
    assertEquals("زارية", voice?.name)
    assertEquals("🇸🇦", voice?.flag)
  }

  @Test
  fun `offline models contain piper and vosk models`() {
    val catalog = VoiceModelManager.CATALOG

    val piperModels = catalog.filter { it.type == VoiceModelType.TTS }
    val voskModels = catalog.filter { it.type == VoiceModelType.STT }

    assertTrue("Must have Piper VITS offline TTS models", piperModels.isNotEmpty())
    assertTrue("Must have Vosk offline STT models", voskModels.isNotEmpty())

    assertTrue("Must contain Piper female model", piperModels.any { it.isFemaleVoice })
    assertTrue("Must contain Piper male model", piperModels.any { !it.isFemaleVoice })
    assertTrue("Must contain Vosk Arabic model", voskModels.any { it.id.contains("vosk") })
  }
}
