package com.example.device.flashlight

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FlashlightController(private val context: Context) {
  private val cameraManager =
    context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager

  private val _isTorchOn = MutableStateFlow(false)
  val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

  private var torchCameraId: String? = null

  init {
    findTorchCameraId()
    registerTorchCallback()
  }

  private fun findTorchCameraId() {
    try {
      val ids = cameraManager?.cameraIdList ?: emptyArray()
      for (id in ids) {
        val chars = cameraManager?.getCameraCharacteristics(id)
        val hasFlash = chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        val facing = chars?.get(CameraCharacteristics.LENS_FACING)
        if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
          torchCameraId = id
          return
        }
      }
      if (torchCameraId == null) {
        for (id in ids) {
          val chars = cameraManager?.getCameraCharacteristics(id)
          if (chars?.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true) {
            torchCameraId = id
            return
          }
        }
      }
    } catch (_: Exception) {
      torchCameraId = null
    }
  }

  private fun registerTorchCallback() {
    try {
      cameraManager?.registerTorchCallback(
        object : CameraManager.TorchCallback() {
          override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchCameraId) {
              _isTorchOn.value = enabled
            }
          }

          override fun onTorchModeUnavailable(cameraId: String) {
            if (cameraId == torchCameraId) {
              _isTorchOn.value = false
            }
          }
        },
        null
      )
    } catch (_: Exception) {}
  }

  fun setTorch(enable: Boolean): Result<Unit> {
    val cm = cameraManager ?: return Result.failure(IllegalStateException("Camera manager unavailable"))
    val id = torchCameraId ?: return Result.failure(IllegalStateException("No camera flash available on this device"))

    return try {
      cm.setTorchMode(id, enable)
      _isTorchOn.value = enable
      Result.success(Unit)
    } catch (e: Exception) {
      Result.failure(e)
    }
  }

  fun toggle(): Result<Boolean> {
    val newState = !_isTorchOn.value
    return setTorch(newState).map { newState }
  }
}
