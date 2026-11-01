// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.secrets) apply false
  alias(libs.plugins.google.services) apply false
}

// Automatically generate debug.keystore from debug.keystore.base64 when opening the project locally in Android Studio
val debugKeystoreFile = file("debug.keystore")
val debugKeystoreBase64File = file("debug.keystore.base64")
if (!debugKeystoreFile.exists() && debugKeystoreBase64File.exists()) {
  runCatching {
    val cleanedBase64 = debugKeystoreBase64File.readText().replace("\\s+".toRegex(), "")
    debugKeystoreFile.writeBytes(java.util.Base64.getDecoder().decode(cleanedBase64))
  }
}

