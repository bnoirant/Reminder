plugins { id("com.android.library") }
android {
    namespace = "com.duolingo.open.rtlviewpager"
    compileSdk = 34
    defaultConfig { minSdk = 26 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
dependencies {
    api("androidx.viewpager:viewpager:1.0.0")
    implementation("androidx.core:core:1.9.0")
}
