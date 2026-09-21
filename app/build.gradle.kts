import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.compose.compiler)
}

// 签名配置从 local.properties 读取（该文件已被 .gitignore 忽略，不会进入开源仓库）。
// 这样开源后既不会泄露密钥与口令，别人 clone 下来也能直接构建。
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasReleaseKey = !localProps.getProperty("RELEASE_STORE_FILE").isNullOrBlank()

android {
    namespace = "com.buddy.ncmusic"
    compileSdk = libs.versions.compileSdk.get().toInt()
    // 显式指定本地已安装的 build-tools 版本，避免 AGP 联网下载 35
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.buddy.ncmusic"
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseKey) {
                storeFile = rootProject.file(localProps.getProperty("RELEASE_STORE_FILE"))
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            // 仓库内不含密钥时回退到 debug 签名，保证 clone 后可直接构建
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            // 开启代码与资源收缩，最大限度减小体积与内存占用
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        // MainActivity/PlaybackService 实际继承链正确（ComponentActivity/MediaSessionService），
        // lint 无法解析 AGP 8.13 + Compose 场景下的传递依赖，属误报
        disable += "Instantiatable"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM 统一版本
    implementation(platform(libs.androidx.compose.bom))

    // 基础
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)

    // Compose UI
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // 导航
    implementation(libs.androidx.navigation.compose)

    // 网络 + 序列化（直连模式下仅需 OkHttp，无需 Retrofit）
    implementation(libs.okhttp.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // 图片加载（Coil，比 Glide 更轻、更 Compose 友好）
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)

    // 播放（Media3 ExoPlayer）
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    // 本地存储（DataStore，替代 Room 减依赖）
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.palette)
}
