import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val dotEnv = Properties().apply {
    val envFile = rootProject.file(".env")
    if (envFile.exists()) {
        envFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "io.github.corebonts.muxgrid"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        applicationId = "io.github.corebonts.muxgrid"
        minSdk = 28
        targetSdk = 36
        versionCode = 2
        versionName = "0.2.0-m1"

        buildConfigField("String", "SPIKE_TVH_HOST", "\"${dotEnv.getProperty("TVH_HOST", "192.168.1.5")}\"")
        buildConfigField("String", "SPIKE_TVH_USER", "\"${dotEnv.getProperty("TVH_USER", "")}\"")
        buildConfigField("String", "SPIKE_TVH_PASS", "\"${dotEnv.getProperty("TVH_PASS", "")}\"")
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    androidResources {
        // mpv-android-lib bundles unused DroidSansFallback (subfont.ttf) and cacert.pem;
        // MuxGrid never calls Utils.copyAssets() and streams through LocalMediaProxy.
        ignoreAssetsPattern = listOfNotNull(
            ignoreAssetsPattern?.takeIf { it.isNotBlank() },
            "!subfont.ttf",
            "!cacert.pem",
        ).joinToString(":")
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

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:data"))
    implementation(project(":core:player"))
    implementation(project(":core:designsystem"))
    implementation(project(":feature:mobile"))
    implementation(project(":feature:tv"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.mpv.android.lib)
    implementation(libs.androidx.tv.material)
}
