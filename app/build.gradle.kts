plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.chesspilot"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.chesspilot"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2.0"

        testOptions.unitTests.isReturnDefaultValues = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation("androidx.webkit:webkit:1.14.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.10.1")

    // Rules layer for legal move generation and FEN/PGN support.
    implementation("com.github.bhlangonijr:chesslib:1.3.7")

    testImplementation("junit:junit:4.13.2")
}
