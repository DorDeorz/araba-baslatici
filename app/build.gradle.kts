plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.dordeorz.arababaslatici"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dordeorz.arababaslatici"
        minSdk = 33
        targetSdk = 36
        versionCode = 18
        versionName = "2.1.2"
    }

    // İmza anahtarı GitHub Secret'larından gelir (bkz. .github/workflows/android.yml).
    // Yerelde: ARABA_KEYSTORE_FILE ve ARABA_KEYSTORE_PASSWORD ortam değişkenlerini ayarla.
    val releaseKeystore = System.getenv("ARABA_KEYSTORE_FILE")

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storeType = "pkcs12"
                storePassword = System.getenv("ARABA_KEYSTORE_PASSWORD")
                keyAlias = "arababaslatici"
                keyPassword = System.getenv("ARABA_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // AGP 9 Kotlin kütüphanesini kendiliğinden ekliyor; R8 kullanılmayanı atar.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            signingConfig = signingConfigs.getByName(if (releaseKeystore != null) "release" else "debug")
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
}

// Ana ekran ve ayarlar Compose + Material 3 (Kronometre ve Adımsayar ile aynı tasarım dili).
// Android Auto ekranı (EkranActivity) hız için düz View olarak kalır.
dependencies {
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
