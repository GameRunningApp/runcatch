import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// 간편 로그인 키는 GitHub에 올리지 않도록 local.properties에서 읽음 (없으면 빈 값 → 앱에서 "설정 필요" 안내)
val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val kakaoNativeAppKey: String = localProps.getProperty("KAKAO_NATIVE_APP_KEY", "")
val googleWebClientId: String = localProps.getProperty("GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "com.cookandroid.runcatch"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.cookandroid.runcatch"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoNativeAppKey\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        // 카카오계정(웹) 로그인 후 앱으로 돌아오는 주소 : kakao{네이티브 앱 키}://oauth
        manifestPlaceholders["kakaoScheme"] = "kakao$kakaoNativeAppKey"
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = false
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    lint {
        disable += "MissingPermission"
    }
}

dependencies {
    implementation(project(":core"))
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.emoji.picker)
    // 간편 로그인 (카카오 · 구글)
    implementation(libs.kakao.user)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("org.maplibre.gl:android-sdk:13.6.1")
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}