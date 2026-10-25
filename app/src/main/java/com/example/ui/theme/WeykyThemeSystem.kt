package com.example.ui.theme

import androidx.annotation.DrawableRes
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.R

/**
 * WEYKY Integrated Three-Theme System Modes:
 * - TACTICAL: Pure pitch-black background, HUD wireframes, double chamfered frames.
 * - MODERN: Cinematic luxury, smooth radial glow centered behind Core, translucent glass cards.
 * - MINIMAL: Calm, clean, minimalist with full background customization.
 */
enum class WeykyThemeMode(
  val id: String,
  val titleAr: String,
  val titleEn: String,
  val descriptionAr: String
) {
  TACTICAL(
    id = "tactical",
    titleAr = "تكتيكي (Tactical)",
    titleEn = "Tactical",
    descriptionAr = "هوية تقنية مستوحاة من واجهات HUD العسكرية والفضائية بخلفية سوداء ثابتة"
  ),
  MODERN(
    id = "modern",
    titleAr = "عصري (Modern)",
    titleEn = "Modern",
    descriptionAr = "هوية سينمائية فاخرة بإضاءة لونية ناعمة متمركزة خلف النواة وبطاقات زجاجية عصرية"
  ),
  MINIMAL(
    id = "minimal",
    titleAr = "مبسط (Minimal)",
    titleEn = "Minimal",
    descriptionAr = "هوية هادئة ونقية مع إمكانية التخصيص الكامل للخلفية والصور والتدرجات"
  );

  companion object {
    fun fromId(id: String): WeykyThemeMode = entries.firstOrNull { it.id == id } ?: TACTICAL
  }
}

/**
 * Day/Night mode selector:
 * AUTO: System configuration with local device hour fallback (19:00 - 06:00 is night).
 * DARK: Force dark theme.
 * LIGHT: Force light theme.
 */
enum class DayNightMode(val id: String, val titleAr: String) {
  AUTO("auto", "تلقائي (حسب النظام / الوقت)"),
  DARK("dark", "ليلي داكن (Dark)"),
  LIGHT("light", "نهاري فاتح (Light)");

  companion object {
    fun fromId(id: String): DayNightMode = entries.firstOrNull { it.id == id } ?: AUTO
  }
}

/**
 * Type of background applied when [WeykyThemeMode.MINIMAL] is active.
 */
enum class MinimalBackgroundType(val id: String, val titleAr: String) {
  DEFAULT("default", "الافتراضي الهادئ"),
  IMAGE("image", "صورة من الجهاز"),
  WALLPAPER_PRESET("preset", "مكتبة الخلفيات المدمجة"),
  SOLID_COLOR("solid", "لون ثابت"),
  GRADIENT("gradient", "تدرج لوني مخصص");

  companion object {
    fun fromId(id: String): MinimalBackgroundType = entries.firstOrNull { it.id == id } ?: DEFAULT
  }
}

/**
 * Dimming preset opacity levels for Minimal background.
 */
object MinimalDimmingPresets {
  const val LIGHT = 0.20f
  const val MEDIUM = 0.45f
  const val DARK = 0.70f
}

/**
 * Categories for the built-in offline wallpapers catalog.
 */
enum class WallpaperCategory(val id: String, val titleAr: String) {
  COSMIC("cosmic", "فضائي"),
  ABSTRACT("abstract", "تجريدي"),
  NATURE("nature", "طبيعي"),
  ARCHITECTURAL("arch", "معماري"),
  DARK("dark", "داكن");

  companion object {
    fun fromId(id: String): WallpaperCategory = entries.firstOrNull { it.id == id } ?: COSMIC
  }
}

/**
 * Specification for an offline, procedurally drawn wallpaper.
 * Works 100% offline, zero network requests, perfectly crisp at any display density.
 */
data class OfflineWallpaperPreset(
  val id: String,
  val titleAr: String,
  val category: WallpaperCategory,
  val previewGradient: List<Color>,
  @DrawableRes val drawableResId: Int? = null,
  val drawBackground: DrawScope.() -> Unit = {}
)

/**
 * Comprehensive offline wallpapers catalog categorized per requirement 4.
 */
object OfflineWallpaperCatalog {
  val presets: List<OfflineWallpaperPreset> = listOf(
    // 0. VIBRANT 4K COLORFUL MASTERPIECES (خلفيات ملونة بدقة عالية 4K)
    OfflineWallpaperPreset(
      id = "neon_fluid_4k",
      titleAr = "أمواج النيون السائلة 4K",
      category = WallpaperCategory.ABSTRACT,
      previewGradient = listOf(Color(0xFFE94057), Color(0xFF8A2387), Color(0xFF00D2FF), Color(0xFFF72585)),
      drawableResId = R.drawable.img_wallpaper_neon_fluid
    ),
    OfflineWallpaperPreset(
      id = "cosmic_nebula_4k",
      titleAr = "سديم نيون كوني 4K",
      category = WallpaperCategory.COSMIC,
      previewGradient = listOf(Color(0xFF3C096C), Color(0xFF7209B7), Color(0xFF00ADB5), Color(0xFFFFD166)),
      drawableResId = R.drawable.img_wallpaper_cosmic_nebula
    ),
    OfflineWallpaperPreset(
      id = "prismatic_waves_4k",
      titleAr = "أطياف موشورية ملونة 4K",
      category = WallpaperCategory.ABSTRACT,
      previewGradient = listOf(Color(0xFF00ADB5), Color(0xFFF72585), Color(0xFFFFB703), Color(0xFF7209B7)),
      drawableResId = R.drawable.img_wallpaper_prismatic_waves
    ),
    OfflineWallpaperPreset(
      id = "chromatic_smoke_4k",
      titleAr = "انفجار لوني حبري 4K",
      category = WallpaperCategory.DARK,
      previewGradient = listOf(Color(0xFF00ADB5), Color(0xFFF72585), Color(0xFFFF5722), Color(0xFF030712)),
      drawableResId = R.drawable.img_wallpaper_chromatic_smoke
    ),
    OfflineWallpaperPreset(
      id = "neon_cyberpunk_4k",
      titleAr = "بلورات سايبربانك 4K",
      category = WallpaperCategory.ARCHITECTURAL,
      previewGradient = listOf(Color(0xFF7209B7), Color(0xFF00F0FF), Color(0xFFFF007F), Color(0xFF020617)),
      drawableResId = R.drawable.img_wallpaper_neon_cyberpunk
    ),

    // 1. COSMIC (فضائي)
    OfflineWallpaperPreset(
      id = "cosmic_nebula",
      titleAr = "سديم كوني عميق",
      category = WallpaperCategory.COSMIC,
      previewGradient = listOf(Color(0xFF0D0221), Color(0xFF190061), Color(0xFF240046), Color(0xFF030712)),
      drawBackground = {
        drawRect(
          brush = Brush.radialGradient(
            colors = listOf(Color(0xFF3C096C), Color(0xFF10002B), Color(0xFF030712)),
            center = Offset(size.width * 0.4f, size.height * 0.35f),
            radius = size.width * 0.85f
          )
        )
        drawCircle(
          color = Color(0x337B2CBF),
          center = Offset(size.width * 0.75f, size.height * 0.65f),
          radius = size.width * 0.4f
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "cosmic_event_horizon",
      titleAr = "أفق الحدث الفضائي",
      category = WallpaperCategory.COSMIC,
      previewGradient = listOf(Color(0xFF02040A), Color(0xFF051923), Color(0xFF003554), Color(0xFF006494)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF02040A), Color(0xFF051923), Color(0xFF001E2D))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x4000A8E8), Color(0x15006494), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.45f),
            radius = size.width * 0.7f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "cosmic_stellar",
      titleAr = "الفراغ النجمي",
      category = WallpaperCategory.COSMIC,
      previewGradient = listOf(Color(0xFF03071E), Color(0xFF370617), Color(0xFF6A040F)),
      drawBackground = {
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color(0xFF03071E), Color(0xFF140826), Color(0xFF020510)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x309D4EDD), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height * 0.2f),
            radius = size.width * 0.5f
          )
        )
      }
    ),

    // 2. ABSTRACT (تجريدي)
    OfflineWallpaperPreset(
      id = "abstract_cyber_wave",
      titleAr = "أمواج سيبرانية",
      category = WallpaperCategory.ABSTRACT,
      previewGradient = listOf(Color(0xFF0B091A), Color(0xFF14122E), Color(0xFF1D1B48)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF080711), Color(0xFF100F22), Color(0xFF06050D))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x3500ADB5), Color(0x103F37C9), Color.Transparent),
            center = Offset(size.width * 0.8f, size.height * 0.3f),
            radius = size.width * 0.65f
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x28F72585), Color.Transparent),
            center = Offset(size.width * 0.2f, size.height * 0.75f),
            radius = size.width * 0.6f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "abstract_prismatic",
      titleAr = "شبكة موشورية",
      category = WallpaperCategory.ABSTRACT,
      previewGradient = listOf(Color(0xFF0F172A), Color(0xFF1E293B), Color(0xFF334155)),
      drawBackground = {
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color(0xFF0F172A), Color(0xFF1E1E38), Color(0xFF0B0F19)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x2538BDF8), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            radius = size.width * 0.7f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "abstract_liquid",
      titleAr = "هندسة مائعة",
      category = WallpaperCategory.ABSTRACT,
      previewGradient = listOf(Color(0xFF061A23), Color(0xFF0A2E36), Color(0xFF0F4C5C)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF041017), Color(0xFF082029), Color(0xFF030C10))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x3014B8A6), Color.Transparent),
            center = Offset(size.width * 0.35f, size.height * 0.4f),
            radius = size.width * 0.6f
          )
        )
      }
    ),

    // 3. NATURE (طبيعي)
    OfflineWallpaperPreset(
      id = "nature_midnight_forest",
      titleAr = "غابة منتصف الليل",
      category = WallpaperCategory.NATURE,
      previewGradient = listOf(Color(0xFF021B1A), Color(0xFF052F2D), Color(0xFF0A4441)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF010E0E), Color(0xFF032221), Color(0xFF000808))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x2E10B981), Color(0x10065F46), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.3f),
            radius = size.width * 0.65f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "nature_dunes",
      titleAr = "كثبان شفقية",
      category = WallpaperCategory.NATURE,
      previewGradient = listOf(Color(0xFF1B0E07), Color(0xFF2E190E), Color(0xFF452615)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF140905), Color(0xFF221108), Color(0xFF0B0402))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x30F59E0B), Color(0x10B45309), Color.Transparent),
            center = Offset(size.width * 0.6f, size.height * 0.45f),
            radius = size.width * 0.65f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "nature_ocean",
      titleAr = "أعماق المحيط",
      category = WallpaperCategory.NATURE,
      previewGradient = listOf(Color(0xFF030D1A), Color(0xFF071E36), Color(0xFF0B335A)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF020912), Color(0xFF05172A), Color(0xFF01050A))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x330284C7), Color(0x120369A1), Color.Transparent),
            center = Offset(size.width * 0.45f, size.height * 0.35f),
            radius = size.width * 0.7f
          )
        )
      }
    ),

    // 4. ARCHITECTURAL (معماري)
    OfflineWallpaperPreset(
      id = "arch_monolith",
      titleAr = "صرح الأوبسيديان",
      category = WallpaperCategory.ARCHITECTURAL,
      previewGradient = listOf(Color(0xFF111827), Color(0xFF1F2937), Color(0xFF374151)),
      drawBackground = {
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color(0xFF0A0E17), Color(0xFF161F2E), Color(0xFF080B12)),
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height)
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x2864748B), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.5f),
            radius = size.width * 0.65f
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "arch_metropolis",
      titleAr = "أفق المدينة المستقبلي",
      category = WallpaperCategory.ARCHITECTURAL,
      previewGradient = listOf(Color(0xFF0E0B1F), Color(0xFF1B163B), Color(0xFF2E245E)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF090714), Color(0xFF141029), Color(0xFF05040B))
          )
        )
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x308B5CF6), Color(0x10EC4899), Color.Transparent),
            center = Offset(size.width * 0.7f, size.height * 0.4f),
            radius = size.width * 0.6f
          )
        )
      }
    ),

    // 5. DARK (داكن)
    OfflineWallpaperPreset(
      id = "dark_carbon",
      titleAr = "ألياف الكربون",
      category = WallpaperCategory.DARK,
      previewGradient = listOf(Color(0xFF0F1115), Color(0xFF16191E), Color(0xFF1F232B)),
      drawBackground = {
        drawRect(
          brush = Brush.verticalGradient(
            colors = listOf(Color(0xFF0A0C0E), Color(0xFF121519), Color(0xFF070809))
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "dark_slate",
      titleAr = "تدرج الشيست الهادئ",
      category = WallpaperCategory.DARK,
      previewGradient = listOf(Color(0xFF0B0F19), Color(0xFF111827), Color(0xFF1F2937)),
      drawBackground = {
        drawRect(
          brush = Brush.linearGradient(
            colors = listOf(Color(0xFF080B12), Color(0xFF0F172A), Color(0xFF04060A)),
            start = Offset(0f, 0f),
            end = Offset(0f, size.height)
          )
        )
      }
    ),
    OfflineWallpaperPreset(
      id = "dark_obsidian",
      titleAr = "أوبسيديان نقي هادئ",
      category = WallpaperCategory.DARK,
      previewGradient = listOf(Color(0xFF050505), Color(0xFF0D0D0D), Color(0xFF171717)),
      drawBackground = {
        drawRect(Color(0xFF0A0A0A))
        drawCircle(
          brush = Brush.radialGradient(
            colors = listOf(Color(0x18FFFFFF), Color.Transparent),
            center = Offset(size.width * 0.5f, size.height * 0.4f),
            radius = size.width * 0.7f
          )
        )
      }
    )
  )

  fun findById(id: String): OfflineWallpaperPreset =
    presets.firstOrNull { it.id == id } ?: presets.first()
}

/**
 * Curated minimal solid color palette for [MinimalBackgroundType.SOLID_COLOR].
 */
object MinimalSolidColorPresets {
  val options: List<Pair<String, Long>> = listOf(
    "أوبسيديان نقي" to 0xFF0A0A0AL,
    "رمادي شيست عميق" to 0xFF0F172AL,
    "فحمي غامق" to 0xFF121418L,
    "كحلي منتصف الليل" to 0xFF081220L,
    "زيتي هادئ" to 0xFF0A1813L,
    "قهوة ليلية" to 0xFF160E08L,
    "بنفسجي غامق" to 0xFF140A1EL,
    "تيتانيوم هادئ" to 0xFF1C1F26L,
    "أبيض ثلجي هادئ" to 0xFFF3F4F6L
  )
}

/**
 * Curated gradient starter pairs for [MinimalBackgroundType.GRADIENT].
 */
object MinimalGradientPresets {
  val pairs: List<Triple<String, Long, Long>> = listOf(
    Triple("أفق الشفق الفضائي", 0xFF0F2027L, 0xFF2C5364L),
    Triple("بنفسجي سيبراني عميق", 0xFF1A0B2EL, 0xFF3D195BL),
    Triple("زمرد أعماق الغابة", 0xFF061A14L, 0xFF134E3AL),
    Triple("كثبان ذهبية ليلية", 0xFF1F1300L, 0xFF4A2F08L),
    Triple("تيتانيوم أسمنتي نقي", 0xFF141E30L, 0xFF243B55L)
  )
}
