import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystoreProps = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}

val releaseStorePath = keystoreProps.getProperty("storeFile")
    ?: System.getenv("MUXGRID_KEYSTORE_PATH")
val releaseStorePassword = keystoreProps.getProperty("storePassword")
    ?: System.getenv("MUXGRID_KEYSTORE_PASSWORD")
val releaseKeyAlias = keystoreProps.getProperty("keyAlias")
    ?: System.getenv("MUXGRID_KEY_ALIAS")
val releaseKeyPassword = keystoreProps.getProperty("keyPassword")
    ?: System.getenv("MUXGRID_KEY_PASSWORD")
val releaseStoreFile = releaseStorePath
    ?.takeIf { it.isNotBlank() }
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }
val hasReleaseSigning = releaseStoreFile != null &&
    !releaseStorePassword.isNullOrBlank() &&
    !releaseKeyAlias.isNullOrBlank() &&
    !releaseKeyPassword.isNullOrBlank()

val gitSha = runCatching {
    val exec = providers.exec {
        workingDir = rootProject.projectDir
        commandLine("git", "rev-parse", "--short=7", "HEAD")
        isIgnoreExitValue = true
    }
    if (exec.result.get().exitValue == 0) {
        exec.standardOutput.asText.get().trim()
    } else {
        ""
    }
}.getOrDefault("")
    .ifEmpty { System.getenv("GITHUB_SHA")?.take(7).orEmpty() }
    .filter { it.isLetterOrDigit() }
    .take(7)
    .ifEmpty { "unknown" }

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
        versionCode = 8
        versionName = "0.8.1"
        buildConfigField("String", "GIT_SHA", "\"$gitSha\"")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
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
        debug {
            isPseudoLocalesEnabled = true
        }
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        create("noDebug") {
            initWith(getByName("debug"))
            isDebuggable = false
            isMinifyEnabled = false
            isShrinkResources = false
            isPseudoLocalesEnabled = false
            matchingFallbacks += listOf("release")
        }
    }

    androidResources {
        generateLocaleConfig = true
        // mpv-android-lib bundles unused DroidSansFallback (subfont.ttf) and cacert.pem;
        // MuxGrid never calls Utils.copyAssets() and streams through LocalMediaProxy.
        ignoreAssetsPattern = listOfNotNull(
            ignoreAssetsPattern?.takeIf { it.isNotBlank() },
            "!subfont.ttf",
            "!cacert.pem",
        ).joinToString(":")
    }

    lint {
        warning += setOf("MissingTranslation")
        error += setOf("StringFormatInvalid", "StringFormatMatches", "StringFormatCount", "ExtraTranslation")
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

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_compiler_config.conf"))
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

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.kotlinx.coroutines.test)
}

tasks.withType<Test> {
    useJUnitPlatform()
}
