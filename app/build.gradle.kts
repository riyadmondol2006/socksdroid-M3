import java.util.Properties

/** Version used until the first v* tag exists. */
val BASE_VERSION = "2.0.0"

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Optional release signing: create keystore.properties in the project root with
// storeFile, storePassword, keyAlias and keyPassword entries.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

// Versions come from git: versionCode is the commit count (always increasing), versionName is
// the latest v* tag, e.g. "2.1.0" on the tag itself or "2.1.0-3-gabc1234" three commits later.
fun git(vararg args: String): String? = runCatching {
    providers.exec {
        commandLine("git", *args)
        isIgnoreExitValue = true
    }.standardOutput.asText.get().trim()
}.getOrNull()?.takeIf { it.isNotEmpty() }

val gitVersionCode = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val gitVersionName = git("describe", "--tags", "--match", "v[0-9]*")?.removePrefix("v")
    ?: "$BASE_VERSION-g${git("rev-parse", "--short=7", "HEAD") ?: "unknown"}"

android {
    namespace = "com.riyadm.socksdroid"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.riyadm.socksdroid"
        minSdk = 26
        targetSdk = 37
        versionCode = gitVersionCode
        versionName = gitVersionName
    }

    signingConfigs {
        if (keystoreProperties.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.findByName("release")?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        // tun2socks and pdnsd are executables shipped as lib*.so; they must be
        // extracted to nativeLibraryDir so they can be exec'd.
        jniLibs.useLegacyPackaging = true
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
