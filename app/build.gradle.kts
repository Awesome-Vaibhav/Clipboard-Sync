plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
}

import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.File
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.security.KeyStore
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.Base64

fun unzipUploadedFiles(rootDir: File) {
  val zipFile = rootDir.listFiles()?.find { it.name.endsWith(".zip") }
  if (zipFile != null && zipFile.exists()) {
    try {
      ZipInputStream(FileInputStream(zipFile)).use { zis ->
        var entry = zis.nextEntry
        while (entry != null) {
          val newFile = File(rootDir, entry.name)
          if (entry.isDirectory) {
            newFile.mkdirs()
          } else {
            newFile.parentFile?.mkdirs()
            FileOutputStream(newFile).use { fos ->
              zis.copyTo(fos)
            }
          }
          zis.closeEntry()
          entry = zis.nextEntry
        }
      }
      zipFile.delete()
    } catch (e: Exception) {
      // ignore
    }
  }
}

fun getSigningSha256(): String {
  unzipUploadedFiles(rootDir)

  // Check if any user uploaded .jks signature is there in the rootDir
  val jksFile = rootDir.listFiles()?.find { it.name.endsWith(".jks") }
  if (jksFile != null && jksFile.exists()) {
    try {
      val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
      val storePass = "Vaibhav@8242428"
      FileInputStream(jksFile).use { fis ->
        keystore.load(fis, storePass.toCharArray())
      }
      val alias = if (keystore.aliases().hasMoreElements()) keystore.aliases().nextElement() else "VAIBHAV ( BLACKDEX)"
      val cert = keystore.getCertificate(alias) as? X509Certificate
      if (cert != null) {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString("") { "%02X".format(it) }
      }
    } catch (e: Exception) {
      // ignore and try fallback
    }
  }

  val debugFile = file("${rootDir}/debug.keystore")
  val base64File = file("${rootDir}/debug.keystore.base64")
  if (!debugFile.exists() && base64File.exists()) {
    try {
      val encoded = base64File.readText().trim()
      val cleanedEncoded = encoded.replace("\\s".toRegex(), "")
      val decoded = Base64.getDecoder().decode(cleanedEncoded)
      debugFile.writeBytes(decoded)
    } catch (e: Exception) {
      // ignore
    }
  }

  if (debugFile.exists()) {
    try {
      val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
      FileInputStream(debugFile).use { fis ->
        keystore.load(fis, "android".toCharArray())
      }
      val cert = keystore.getCertificate("androiddebugkey") as? X509Certificate
      if (cert != null) {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString("") { "%02X".format(it) }
      }
    } catch (e: Exception) {
      // ignore and try fallback
    }
  }

  val uploadFile = file("${rootDir}/my-upload-key.jks")
  if (uploadFile.exists()) {
    try {
      val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
      val storePass = System.getenv("STORE_PASSWORD") ?: ""
      FileInputStream(uploadFile).use { fis ->
        keystore.load(fis, storePass.toCharArray())
      }
      val cert = keystore.getCertificate("upload") as? X509Certificate
      if (cert != null) {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(cert.encoded).joinToString("") { "%02X".format(it) }
      }
    } catch (e: Exception) {
      // ignore
    }
  }

  return "UNKNOWN"
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    val calculatedSignature = getSigningSha256()
    buildConfigField("String", "SIGNATURE_SHA256", "\"$calculatedSignature\"")
  }

  signingConfigs {
    create("release") {
      val jksFile = rootDir.listFiles()?.find { it.name.endsWith(".jks") }
      if (jksFile != null && jksFile.exists()) {
        storeFile = jksFile
        storePassword = "Vaibhav@8242428"
        var resolvedAlias = "VAIBHAV ( BLACKDEX)"
        try {
          val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
          FileInputStream(jksFile).use { fis ->
            keystore.load(fis, "Vaibhav@8242428".toCharArray())
          }
          if (keystore.aliases().hasMoreElements()) {
            resolvedAlias = keystore.aliases().nextElement()
          }
        } catch (e: Exception) {
          // ignore
        }
        keyAlias = resolvedAlias
        keyPassword = "Vaibhav@8242428"
      } else {
        val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
        storeFile = file(keystorePath)
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      }
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  // implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}

