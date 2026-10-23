package com.example.device.media

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent
import com.example.device.notifications.NotificationRepository
import com.example.device.notifications.WeykyNotificationListenerService
import android.graphics.Bitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

data class ActiveMediaMetadata(
  val title: String? = null,
  val artist: String? = null,
  val album: String? = null,
  val isPlaying: Boolean = false,
  val packageName: String? = null,
  val artworkBitmap: Bitmap? = null
)

sealed interface MediaControlResult {
  data class Success(val message: String, val isPlaying: Boolean) : MediaControlResult
  object NoActiveSession : MediaControlResult
  data class Error(val message: String) : MediaControlResult
}

class MediaPlaybackController(private val context: Context) {
  private val audioManager =
    context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
  private val mediaSessionManager =
    context.getSystemService(Context.MEDIA_SESSION_SERVICE) as? MediaSessionManager

  private val _mediaState = MutableStateFlow(ActiveMediaMetadata())
  val mediaState: StateFlow<ActiveMediaMetadata> = _mediaState.asStateFlow()

  private var activeController: MediaController? = null

  private val controllerCallback = object : MediaController.Callback() {
    override fun onPlaybackStateChanged(state: PlaybackState?) {
      updateMediaState()
    }

    override fun onMetadataChanged(metadata: MediaMetadata?) {
      updateMediaState()
    }

    override fun onSessionDestroyed() {
      activeController = null
      _mediaState.value = ActiveMediaMetadata()
      Log.d("WEYKY_MEDIA", "Session destroyed")
    }
  }

  fun updateMediaState() {
    val controller = getActiveMediaController()
    if (controller != null) {
      val state = controller.playbackState?.state
      val isPlaying = state == PlaybackState.STATE_PLAYING
      val meta = controller.metadata
      val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        ?: controller.metadata?.description?.title?.toString()
      val artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST)
        ?: controller.metadata?.description?.subtitle?.toString()
      val album = meta?.getString(MediaMetadata.METADATA_KEY_ALBUM)

      val artworkBitmap = meta?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
        ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_ART)
        ?: meta?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)
        ?: controller.metadata?.description?.iconBitmap
        ?: try {
          controller.packageName?.let { pkg ->
            val pm = context.packageManager
            val d = pm.getApplicationIcon(pkg)
            val density = context.resources.displayMetrics.density
            val sizePx = (52 * density).toInt().coerceAtLeast(48)
            d.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
          }
        } catch (_: Exception) {
          null
        }

      _mediaState.value = ActiveMediaMetadata(
        title = title,
        artist = artist,
        album = album,
        isPlaying = isPlaying,
        packageName = controller.packageName,
        artworkBitmap = artworkBitmap
      )
      Log.d("WEYKY_MEDIA", "playing=$isPlaying, title=$title, artist=$artist, artwork=${artworkBitmap != null}")
    } else {
      val isPlaying = audioManager?.isMusicActive == true
      // Fallback: check active ongoing/media notifications for track title & artist
      val mediaNotif = NotificationRepository.activeNotifications.value.firstOrNull { item ->
        val pkg = item.packageName.lowercase(Locale.ROOT)
        item.category == android.app.Notification.CATEGORY_TRANSPORT ||
          pkg.contains("spotify") || pkg.contains("music") || pkg.contains("soundcloud") ||
          pkg.contains("anghami") || pkg.contains("deezer") || pkg.contains("youtube") ||
          pkg.contains("vlc") || pkg.contains("audio") || pkg.contains("player")
      }
      val notifArtwork = mediaNotif?.packageName?.let { pkg ->
        try {
          val pm = context.packageManager
          val d = pm.getApplicationIcon(pkg)
          val density = context.resources.displayMetrics.density
          val sizePx = (52 * density).toInt().coerceAtLeast(48)
          d.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        } catch (_: Exception) {
          null
        }
      }
      _mediaState.value = ActiveMediaMetadata(
        title = mediaNotif?.title?.ifBlank { null },
        artist = mediaNotif?.text?.ifBlank { null },
        isPlaying = isPlaying,
        packageName = mediaNotif?.packageName,
        artworkBitmap = notifArtwork
      )
    }
  }

  fun isMediaActive(): Boolean {
    val musicActive = audioManager?.isMusicActive == true
    if (musicActive) return true
    val active = getActiveMediaController()
    return active?.playbackState?.state == PlaybackState.STATE_PLAYING
  }

  fun getActiveMediaController(): MediaController? {
    if (mediaSessionManager == null) return null
    try {
      val notificationListenerComponent = ComponentName(
        context,
        WeykyNotificationListenerService::class.java
      )
      val sessions = mediaSessionManager.getActiveSessions(notificationListenerComponent)
      if (!sessions.isNullOrEmpty()) {
        val found = sessions.firstOrNull { controller ->
          val state = controller.playbackState?.state
          state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_PAUSED
        } ?: sessions.firstOrNull()

        if (found != null && found != activeController) {
          try {
            activeController?.unregisterCallback(controllerCallback)
          } catch (_: Exception) {}
          activeController = found
          try {
            found.registerCallback(controllerCallback)
          } catch (_: Exception) {}
        }
        return found
      }
    } catch (_: SecurityException) {
      // Notification listener permission not granted
    } catch (_: Exception) {}
    return null
  }

  fun play(): MediaControlResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val controller = getActiveMediaController()
    if (controller != null) {
      return try {
        controller.transportControls.play()
        updateMediaState()
        val title = controller.metadata?.description?.title?.toString()
        val label = if (!title.isNullOrBlank()) {
          if (isArabic) "جاري تشغيل $title." else "Playing $title."
        } else {
          if (isArabic) "تم استئناف تشغيل الموسيقى." else "Resumed playback."
        }
        Log.d("WEYKY_MEDIA", "play command executed on controller")
        MediaControlResult.Success(label, isPlaying = true)
      } catch (e: Exception) {
        MediaControlResult.Error(if (isArabic) "تعذر تشغيل الموسيقى: ${e.message}" else "Playback error: ${e.message}")
      }
    }

    val am = audioManager ?: return MediaControlResult.NoActiveSession
    if (am.isMusicActive) {
      val label = if (isArabic) "الموسيقى قيد التشغيل بالفعل." else "Music is already playing."
      return MediaControlResult.Success(label, isPlaying = true)
    }

    return try {
      val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
      val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY)
      am.dispatchMediaKeyEvent(down)
      am.dispatchMediaKeyEvent(up)
      Log.d("WEYKY_MEDIA", "dispatched play key event")
      val label = if (isArabic) "تم إرسال أمر تشغيل الموسيقى." else "Sent play command."
      MediaControlResult.Success(label, isPlaying = true)
    } catch (_: Exception) {
      MediaControlResult.NoActiveSession
    }
  }

  fun pause(): MediaControlResult {
    Log.d("WEYKY_MEDIA", "[VOICE-AUDIO] explicit media pause() invoked")
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val controller = getActiveMediaController()
    if (controller != null) {
      return try {
        controller.transportControls.pause()
        updateMediaState()
        Log.d("WEYKY_MEDIA", "pause command executed on controller")
        val label = if (isArabic) "تم إيقاف الموسيقى مؤقتاً." else "Music paused."
        MediaControlResult.Success(label, isPlaying = false)
      } catch (e: Exception) {
        MediaControlResult.Error(if (isArabic) "تعذر إيقاف الموسيقى: ${e.message}" else "Pause error: ${e.message}")
      }
    }

    val am = audioManager ?: return MediaControlResult.NoActiveSession
    if (!am.isMusicActive) {
      return MediaControlResult.NoActiveSession
    }

    return try {
      val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
      val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE)
      am.dispatchMediaKeyEvent(down)
      am.dispatchMediaKeyEvent(up)
      Log.d("WEYKY_MEDIA", "dispatched pause key event")
      val label = if (isArabic) "تم إيقاف الموسيقى مؤقتاً." else "Music paused."
      MediaControlResult.Success(label, isPlaying = false)
    } catch (_: Exception) {
      MediaControlResult.NoActiveSession
    }
  }

  fun skipToNext(): MediaControlResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val controller = getActiveMediaController()
    if (controller != null) {
      return try {
        controller.transportControls.skipToNext()
        updateMediaState()
        Log.d("WEYKY_MEDIA", "skipToNext executed on controller")
        val label = if (isArabic) "تم الانتقال إلى المقطع التالي." else "Skipped to next track."
        MediaControlResult.Success(label, isPlaying = true)
      } catch (e: Exception) {
        MediaControlResult.Error(if (isArabic) "تعذر تخطي المقطع: ${e.message}" else "Skip error: ${e.message}")
      }
    }

    val am = audioManager ?: return MediaControlResult.NoActiveSession
    if (!am.isMusicActive) {
      return MediaControlResult.NoActiveSession
    }

    return try {
      val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_NEXT)
      val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_NEXT)
      am.dispatchMediaKeyEvent(down)
      am.dispatchMediaKeyEvent(up)
      Log.d("WEYKY_MEDIA", "dispatched next key event")
      val label = if (isArabic) "تم الانتقال للأغنية التالية." else "Skipped to next song."
      MediaControlResult.Success(label, isPlaying = true)
    } catch (_: Exception) {
      MediaControlResult.NoActiveSession
    }
  }

  fun skipToPrevious(): MediaControlResult {
    val isArabic = Locale.getDefault().language.startsWith("ar")
    val controller = getActiveMediaController()
    if (controller != null) {
      return try {
        controller.transportControls.skipToPrevious()
        updateMediaState()
        Log.d("WEYKY_MEDIA", "skipToPrevious executed on controller")
        val label = if (isArabic) "تم الرجوع إلى المقطع السابق." else "Skipped to previous track."
        MediaControlResult.Success(label, isPlaying = true)
      } catch (e: Exception) {
        MediaControlResult.Error(if (isArabic) "تعذر الرجوع للمقطع السابق: ${e.message}" else "Previous error: ${e.message}")
      }
    }

    val am = audioManager ?: return MediaControlResult.NoActiveSession
    if (!am.isMusicActive) {
      return MediaControlResult.NoActiveSession
    }

    return try {
      val down = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
      val up = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
      am.dispatchMediaKeyEvent(down)
      am.dispatchMediaKeyEvent(up)
      Log.d("WEYKY_MEDIA", "dispatched previous key event")
      val label = if (isArabic) "تم الرجوع للأغنية السابقة." else "Skipped to previous song."
      MediaControlResult.Success(label, isPlaying = true)
    } catch (_: Exception) {
      MediaControlResult.NoActiveSession
    }
  }

  fun toggle(): MediaControlResult {
    val isPlaying = isMediaActive()
    return if (isPlaying) pause() else play()
  }
}
