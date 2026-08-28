plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.bdalxy.edencourt"
    compileSdk = 36
    buildToolsVersion = "36.1.0"

    defaultConfig {
        applicationId = "com.bdalxy.edencourt"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        // androidTest 使用标准 runner（方案 G-③ Phase2：Room 迁移测试用）
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    sourceSets {
        // 方案 G-③ Phase2：把导出的 Room schema JSON 作为 androidTest 资产，
        // 供 MigrationTestHelper 校验迁移后的 schema（与导出版本逐一比对）
        getByName("androidTest").assets.srcDirs(files("$projectDir/schemas"))
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

ksp {
    // Room schema 导出路径：数据库版本升级/迁移的基础（企划书 4.1.5）
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.navigation.compose)

    // 阶段 2：Room 本地存储
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // 阶段 3：网络 + JSON 序列化（kotlinx.serialization）
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.ui.tooling)

    // 方案 G-③ 自动化回归集 Phase1：核心纯逻辑 JVM 单元测试（好感度分档/记忆排序/冲突判定等）
    testImplementation(libs.junit)
    testImplementation(libs.mockk)

    // 方案 G-③ Phase2：Room 迁移升级测试（MigrationTestHelper + runner）
    androidTestImplementation(libs.androidx.room.testing)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)

    // 方案 G-③ Phase3：核心链路 UI 冒烟（Compose 可达性 smoke）
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.test.manifest)
}