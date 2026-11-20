plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "io.github.corebonts.muxgrid.core.player"
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }

    defaultConfig {
        minSdk = 28
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:htsp"))
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.mpv.android.lib)
    implementation(libs.androidx.media3.session)
}
