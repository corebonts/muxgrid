plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

// Bundle the canonical legal texts from the repository root as classpath resources so the app can
// surface the full license documents in Settings → App Info without duplicating them in git.
tasks.processResources {
    from(rootProject.file("LICENSE")) {
        into("licenses")
        rename { "apache-2.0.txt" }
    }
    from(rootProject.file("licenses/gpl-3.0.txt")) {
        into("licenses")
    }
}
