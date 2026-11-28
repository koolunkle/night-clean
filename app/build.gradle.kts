import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

// Supabase 접속 정보와 release 서명: local.properties(git 제외)
val local = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use(::load)
}

android {
    namespace = "io.github.koolunkle.alleyglow"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "io.github.koolunkle.alleyglow"
        minSdk = 24
        targetSdk = 37
        versionCode = 14
        versionName = "1.13"


        buildConfigField("String", "SUPABASE_URL", "\"${local.getProperty("supabase.url", "")}\"")
        buildConfigField("String", "SUPABASE_KEY", "\"${local.getProperty("supabase.key", "")}\"")
    }

    signingConfigs {
        local.getProperty("release.storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = local.getProperty("release.storePassword")
                keyAlias = "nightclean"
                keyPassword = local.getProperty("release.storePassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            // shortcut: R8 끔(packageScope 최적화가 kotlin 클래스 접근을 깨 시작 즉시 종료), APK 크기를 줄일 때 keep 규칙과 함께 켬
            optimization {
                enable = false
            }
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
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.supabase.auth)
    implementation(libs.play.services.code.scanner)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
}