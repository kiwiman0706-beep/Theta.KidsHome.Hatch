plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

// テプラ（KING JIM TEPRA-Print SDK）：使用許諾 の ため 公開 リポジトリ には 入れない。ある とき だけ くみこむ
val tepraJar = file("libs/TepraPrint.jar")

android {
    namespace = "app.thetahatch.kidshome"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.thetahatch.kidshome"
        minSdk = 29
        targetSdk = 35
        versionCode = 100
        versionName = "1.0.0"
    }

    signingConfigs {
        // シリーズ 共通 の 鍵（Theta.Hatch.Series の secrets/README.md）。CI では Secret の 4 つ から：
        //   ANDROID_KEYSTORE_B64 を ファイル に した パス を ANDROID_KEYSTORE_PATH に、ほか は そのまま 環境変数 に
        // 手元 で 環境変数 が ない とき は デバッグ 用 の 鍵 で ビルド（配る APK には しない）
        val ksPath = System.getenv("ANDROID_KEYSTORE_PATH")
        if (!ksPath.isNullOrBlank() && file(ksPath).exists()) create("series") {
            storeFile = file(ksPath)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {}
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("series") ?: signingConfigs.getByName("debug")
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

android.sourceSets.getByName("main").java.srcDir(if (tepraJar.exists()) "src/tepra/java" else "src/notepra/java")

dependencies {
    if (tepraJar.exists()) implementation(files(tepraJar))
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
    // 絵の印刷
    implementation("androidx.print:print:1.0.0")
    // ちず（OpenStreetMap・地理院タイル）
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    // QR コード（カメラで読む・表示する）
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
    // 📸 プリクラ（インカメラ）
    implementation("androidx.camera:camera-camera2:1.3.4")
    implementation("androidx.camera:camera-lifecycle:1.3.4")
    implementation("androidx.camera:camera-view:1.3.4")
    implementation("androidx.media3:media3-ui:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
