plugins { id("com.android.application") }
android {
    namespace = "com.minova.cloud"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.minova.cloud"
        minSdk = 26
        targetSdk = 37
        versionCode = 5
        versionName = "1.4.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CLOUD_ORIGIN", "\"https://cloud.project-minova.com\"")
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        create("release") {
            storeFile = file(System.getenv("MINOVA_KEYSTORE") ?: "../../.signing/release.jks")
            storePassword = System.getenv("MINOVA_KEYSTORE_PASSWORD")
            keyAlias = "minova-cloud"
            keyPassword = System.getenv("MINOVA_KEYSTORE_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
        getByName("debug") { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }
    }
}
dependencies {
    implementation("androidx.work:work-runtime:2.12.0")
    implementation("androidx.core:core:1.15.0")
    implementation("androidx.exifinterface:exifinterface:1.4.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.work:work-testing:2.12.0")
}
