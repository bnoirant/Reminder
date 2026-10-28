plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

android {
    compileSdk = commonsLibs.versions.app.build.compileSDKVersion.get().toInt()

    defaultConfig {
        minSdk = commonsLibs.versions.app.build.minimumSDK.get().toInt()
        vectorDrawables.useSupportLibrary = true
        ksp {
            arg("room.schemaLocation", "$projectDir/schemas")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            consumerProguardFiles("proguard-rules.pro")
        }
    }

    buildFeatures {
        viewBinding = true
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = commonsLibs.versions.composeCompiler.get()
    }

    compileOptions {
        val currentJavaVersionFromLibs = JavaVersion.valueOf(commonsLibs.versions.app.build.javaVersion.get().toString())
        sourceCompatibility = currentJavaVersionFromLibs
        targetCompatibility = currentJavaVersionFromLibs
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = project.commonsLibs.versions.app.build.kotlinJVMTarget.get()
        kotlinOptions.freeCompilerArgs = listOf(
            "-opt-in=kotlin.RequiresOptIn",
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api",
            "-opt-in=androidx.compose.material.ExperimentalMaterialApi",
            "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi",
            "-Xcontext-receivers"
        )
    }

    sourceSets {
        getByName("main").java.srcDirs("src/main/kotlin")
    }
    namespace = commonsLibs.versions.app.version.groupId.get()
}


dependencies {
    implementation(commonsLibs.kotlinx.serialization.json)
    api(commonsLibs.kotlin.immutable.collections)
    implementation(commonsLibs.androidx.constraintlayout)
    implementation(commonsLibs.androidx.documentfile)
    implementation(commonsLibs.androidx.swiperefreshlayout)
    implementation(commonsLibs.androidx.exifinterface)
    implementation(commonsLibs.androidx.biometric.ktx)
    implementation(commonsLibs.ez.vcard)


    implementation(commonsLibs.bundles.lifecycle)
    implementation(commonsLibs.bundles.compose)
    implementation(commonsLibs.compose.view.binding)
    debugImplementation(commonsLibs.bundles.compose.preview)

    api(commonsLibs.joda.time)
    api(commonsLibs.recyclerView.fastScroller)
    api(commonsLibs.reprint)
    api(project(":rtl-viewpager"))
    api(commonsLibs.patternLockView)
    api(commonsLibs.androidx.core.ktx)
    api(commonsLibs.androidx.appcompat)
    api(commonsLibs.material)
    api(commonsLibs.gson)

    api(commonsLibs.glide)
    ksp(commonsLibs.glide.compiler)

    api(commonsLibs.bundles.room)
    ksp(commonsLibs.androidx.room.compiler)
}
