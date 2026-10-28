plugins {
    id("org.jetbrains.kotlin.plugin.serialization") version "1.9.10" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "1.9.10" apply false
    id("com.android.library") version "8.1.1" apply false
    alias(libs.plugins.android).apply(false)
    alias(libs.plugins.kotlinAndroid).apply(false)
    alias(libs.plugins.ksp).apply(false)
}

tasks.register<Delete>("clean") {
    delete {
        rootProject.buildDir
    }
}
