import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  id("com.google.gms.google-services")
  id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
  id("com.google.firebase.crashlytics")
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
  localPropertiesFile.inputStream().use { localProperties.load(it) }
}

fun getPropOrEnv(key: String): String {
  return localProperties.getProperty(key) ?: System.getenv(key) ?: ""
}

// Load version properties
val versionProps = Properties()
val versionPropsFile = rootProject.file("version.properties")
if (versionPropsFile.exists()) {
  versionPropsFile.inputStream().use { versionProps.load(it) }
}

android {
  namespace = "com.aris.voice"
  compileSdk = 35

  // Common API keys and configuration - extracted to avoid duplication
  val apiKeys = getPropOrEnv("GEMINI_API_KEYS").ifEmpty { getPropOrEnv("GEMINI_API_KEY") }
  val tavilyApiKeys = getPropOrEnv("TAVILY_API")
  val mem0ApiKey = getPropOrEnv("MEM0_API")
  val picovoiceApiKey = getPropOrEnv("PICOVOICE_ACCESS_KEY")
  val googleTtsApiKey = getPropOrEnv("GOOGLE_TTS_API_KEY")
  val googlecloudGatewayPicovoice = getPropOrEnv("GCLOUD_GATEWAY_PICOVOICE_KEY")
  val googlecloudGatewayURL = getPropOrEnv("GCLOUD_GATEWAY_URL")
  val googlecloudProxyURL = getPropOrEnv("GCLOUD_PROXY_URL")
  val googlecloudProxyURLKey = getPropOrEnv("GCLOUD_PROXY_URL_KEY")

  defaultConfig {
    applicationId = "com.aris.voice"
    minSdk = 24
    targetSdk = 35
    versionCode = versionProps.getProperty("VERSION_CODE", "13").toInt()
    versionName = versionProps.getProperty("VERSION_NAME", "1.0.13")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

    // Common build config fields - applies to all build types
    buildConfigField("String", "GEMINI_API_KEYS", "\"$apiKeys\"")
    buildConfigField("String", "TAVILY_API", "\"$tavilyApiKeys\"")
    buildConfigField("String", "MEM0_API", "\"$mem0ApiKey\"")
    buildConfigField("String", "PICOVOICE_ACCESS_KEY", "\"$picovoiceApiKey\"")
    buildConfigField("boolean", "ENABLE_DIRECT_APP_OPENING", "true")
    buildConfigField("boolean", "SPEAK_INSTRUCTIONS", "true")
    buildConfigField("String", "GOOGLE_TTS_API_KEY", "\"$googleTtsApiKey\"")
    buildConfigField("String", "GCLOUD_GATEWAY_PICOVOICE_KEY", "\"$googlecloudGatewayPicovoice\"")
    buildConfigField("String", "GCLOUD_GATEWAY_URL", "\"$googlecloudGatewayURL\"")
    buildConfigField("String", "GCLOUD_PROXY_URL", "\"$googlecloudProxyURL\"")
    buildConfigField("String", "GCLOUD_PROXY_URL_KEY", "\"$googlecloudProxyURLKey\"")
    buildConfigField("boolean", "ENABLE_LOGGING", "false")
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
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
      firebaseCrashlytics {
        nativeSymbolUploadEnabled = true
      }
      isCrunchPngs = false
      isMinifyEnabled = true
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
    viewBinding = true
    buildConfig = true
  }

  testOptions {
    unitTests {
      isIncludeAndroidResources = true
    }
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
}

dependencies {
  implementation(libs.androidx.core.ktx)
  implementation("androidx.security:security-crypto:1.1.0-alpha06")
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.appcompat)
  implementation(libs.generativeai)

  testImplementation(libs.junit)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.ui.test.junit4)
  debugImplementation(libs.androidx.ui.tooling)
  debugImplementation(libs.androidx.ui.test.manifest)

  implementation("com.google.android.material:material:1.11.0")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
  implementation(libs.okhttp)
  implementation("com.squareup.moshi:moshi:1.15.0")
  implementation("com.google.code.gson:gson:2.13.1")
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
  implementation("androidx.test.uiautomator:uiautomator:2.3.0")

  // Porcupine Wake Word Engine
  implementation("ai.picovoice:porcupine-android:3.0.2")

  implementation(platform(libs.firebase.bom))
  implementation(libs.firebase.config)
  implementation(libs.firebase.auth)
  implementation(libs.play.services.auth)

  implementation("com.google.firebase:firebase-analytics")
  implementation("com.google.firebase:firebase-crashlytics-ndk")
  implementation("com.google.firebase:firebase-functions")
  implementation(libs.firebase.firestore)
  implementation("androidx.recyclerview:recyclerview:1.3.2")
  implementation("com.android.billingclient:billing-ktx:7.0.0")

  // Room
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.room.ktx)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)

  // CameraX
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.androidx.camera.core)

  // MediaPipe Tasks-Vision
  implementation("com.google.mediapipe:tasks-vision:0.10.14")
  implementation("com.google.ai.edge.litertlm:litertlm-android:latest.release")
}

// Task to increment version for release builds
tasks.register("incrementVersion") {
  doLast {
    val versionFile = rootProject.file("version.properties")
    val props = Properties()
    versionFile.inputStream().use { props.load(it) }

    val currentVersionCode = props.getProperty("VERSION_CODE").toInt()
    val currentVersionName = props.getProperty("VERSION_NAME")

    // Increment version code
    val newVersionCode = currentVersionCode + 1

    // Increment patch version in semantic versioning (x.y.z -> x.y.z+1)
    val versionParts = currentVersionName.split(".")
    val newPatchVersion = if (versionParts.size >= 3) {
      versionParts[2].toInt() + 1
    } else {
      1
    }
    val newVersionName = if (versionParts.size >= 2) {
      "${versionParts[0]}.${versionParts[1]}.$newPatchVersion"
    } else {
      "1.0.$newPatchVersion"
    }

    // Update properties
    props.setProperty("VERSION_CODE", newVersionCode.toString())
    props.setProperty("VERSION_NAME", newVersionName)

    // Save back to file with comments
    FileOutputStream(versionFile).use { fileOutput ->
      fileOutput.write("# Version configuration for Blurr Android App\n".toByteArray())
      fileOutput.write("# This file is automatically updated during release builds\n".toByteArray())
      fileOutput.write("# Do not modify manually - use Gradle tasks to update versions\n\n".toByteArray())
      fileOutput.write("# Current version code (integer - increments by 1 each release)\n".toByteArray())
      fileOutput.write("VERSION_CODE=$newVersionCode\n\n".toByteArray())
      fileOutput.write("# Current version name (semantic version - increments patch number each release)\n".toByteArray())
      fileOutput.write("VERSION_NAME=$newVersionName".toByteArray())
    }

    println("Version incremented to: versionCode=$newVersionCode, versionName=$newVersionName")
  }
}

// Make release builds automatically increment version
tasks.whenTaskAdded {
  if (name == "assembleRelease" || name == "bundleRelease") {
    dependsOn("incrementVersion")
  }
}
