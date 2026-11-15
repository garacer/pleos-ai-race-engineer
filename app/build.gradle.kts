import java.util.Properties

plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

// Release signing is read from a properties file kept OUTSIDE this repository.
// Path: -PjevSigningProps=<file> or env JEV_SIGNING_PROPS, default ~/.config/jev-signing/keystore.properties
// Keys: storeFile, storePassword, keyAlias, keyPassword. Without the file, release stays unsigned.
val signingPropsFile = file(
    (findProperty("jevSigningProps") as String?)
        ?: System.getenv("JEV_SIGNING_PROPS")
        ?: "${System.getProperty("user.home")}/.config/jev-signing/keystore.properties"
)
val signingProps = Properties().apply { if (signingPropsFile.isFile) signingPropsFile.inputStream().use { load(it) } }

android { useLibrary("android.car"); namespace = "com.jev.drivecoach"; compileSdk = 35
  defaultConfig { applicationId = "com.jev.drivecoach"; minSdk = 28; targetSdk = 34; versionCode = 15; versionName = "0.5.2"; applicationIdSuffix = ".dashboard" }
  signingConfigs {
    if (signingProps.containsKey("storeFile")) create("release") {
      storeFile = file(signingProps.getProperty("storeFile"))
      storePassword = signingProps.getProperty("storePassword")
      keyAlias = signingProps.getProperty("keyAlias")
      keyPassword = signingProps.getProperty("keyPassword")
    }
  }
  buildTypes { getByName("release") { signingConfigs.findByName("release")?.let { signingConfig = it } } }
  compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
  kotlinOptions { jvmTarget = "17" }
}

dependencies { implementation("ai.pleos.playground:Vehicle:2.3.2"); testImplementation("junit:junit:4.13.2") }

dependencies { implementation("ai.pleos.playground:TextToSpeech:3.3.1") }
