plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.jumeong.animalrace"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.jumeong.animalrace"
        minSdk = 24
        targetSdk = 36
        versionCode = 10
        versionName = "0.7"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation("com.google.android.gms:play-services-ads:23.0.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
}