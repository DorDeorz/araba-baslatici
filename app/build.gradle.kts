plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.dordeorz.arababaslatici"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dordeorz.arababaslatici"
        minSdk = 33
        targetSdk = 36
        versionCode = 14
        versionName = "2.0.2"
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
}
