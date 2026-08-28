plugins {
    alias(libs.plugins.android.application)
}

val configuredApiBaseUrl = providers.gradleProperty("TOURISM_API_BASE_URL")
    .orElse(providers.environmentVariable("TOURISM_API_BASE_URL"))
    .orElse("http://10.0.2.2:3000/")
    .get()
    .let { if (it.endsWith('/')) it else "$it/" }

fun quotedBuildConfig(value: String) = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

android {
    namespace = "com.example.tourismmedia"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.example.tourismmedia"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "API_BASE_URL", quotedBuildConfig(configuredApiBaseUrl))
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
    buildFeatures {
        buildConfig = true
    }
    lint {
        // The student demo intentionally ships only Vietnamese copy. Extracting every
        // programmatic label is deferred until a second locale is introduced.
        disable += setOf("HardcodedText", "SetTextI18n")
        // Keep the tested SDK/AGP/Gradle matrix reproducible instead of following
        // unstable "latest available" suggestions on each developer machine.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "OldTargetApi"
        )
        // These are cosmetic layout suggestions; the current grouped rows and the
        // theme window background are deliberate for this demo UI.
        disable += setOf("UseCompoundDrawables", "Overdraw", "UnusedAttribute")
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation(libs.navigation.fragment)
    implementation(libs.navigation.ui)
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.gson)
    implementation(libs.recyclerview)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.livedata)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
