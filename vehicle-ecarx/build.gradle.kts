plugins {
    id("com.android.library")
}

android {
    namespace = "com.shilapi.xcertplay.vehicle.ecarx"
    compileSdk = 37

    defaultConfig {
        minSdk = 25
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":shared"))
    testImplementation(libs.junit)
    testImplementation("org.robolectric:robolectric:4.17")
}
