plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.theta.kidshome"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.theta.kidshome"
        minSdk = 29
        targetSdk = 35
        versionCode = 8
        versionName = "0.8.0"
    }

    signingConfigs {
        // どのPC・GitHub Actionsでビルドしても同じ署名になるよう、固定の鍵を使う
        // (個人の端末にサイドロードする用途なので、鍵をリポジトリに同梱している)
        create("shared") {
            storeFile = file("kidshome.jks")
            storePassword = "kidshome"
            keyAlias = "kidshome"
            keyPassword = "kidshome"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("shared")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("shared")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    // ローカルの動画・音楽・オーディオブックの再生
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    // くるくる思考パッド（内蔵の Web アプリ）を WebView で開く
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    testImplementation("junit:junit:4.13.2")
}
