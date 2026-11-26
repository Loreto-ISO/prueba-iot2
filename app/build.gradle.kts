plugins {
    alias(libs.plugins.android.application)
    id("com.google.gms.google-services")
}

android {
    namespace = "com.example.myapplication"

    // Requerido por androidx.core 1.19.1
    compileSdk {
        version = release(37)
    }

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.example.myapplication"

        // Android 7.0 o superior
        minSdk = 24

        // Android 16 como comportamiento objetivo estable
        targetSdk = 36

        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner =
            "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            isDebuggable = true
        }

        release {
            isMinifyEnabled = false

            proguardFiles(
                getDefaultProguardFile(
                    "proguard-android-optimize.txt"
                ),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility =
            JavaVersion.VERSION_11

        targetCompatibility =
            JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(
        platform(
            "com.google.firebase:firebase-bom:33.7.0"
        )
    )

    implementation(
        "com.google.firebase:firebase-auth"
    )

    implementation(
        "com.google.firebase:firebase-database"
    )

    implementation(
        "androidx.recyclerview:recyclerview:1.4.0"
    )

    implementation(
        libs.androidx.activity.ktx
    )

    implementation(
        libs.androidx.appcompat
    )

    implementation(
        libs.androidx.constraintlayout
    )

    implementation(
        libs.androidx.core.ktx
    )

    implementation(
        libs.material
    )

    testImplementation(
        libs.junit
    )

    androidTestImplementation(
        libs.androidx.espresso.core
    )

    androidTestImplementation(
        libs.androidx.junit
    )
}