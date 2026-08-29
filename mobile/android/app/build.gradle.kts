plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val releaseSigningVariables = mapOf(
    "ANDROID_RELEASE_STORE_FILE" to providers.environmentVariable("ANDROID_RELEASE_STORE_FILE"),
    "ANDROID_RELEASE_STORE_PASSWORD" to providers.environmentVariable("ANDROID_RELEASE_STORE_PASSWORD"),
    "ANDROID_RELEASE_KEY_ALIAS" to providers.environmentVariable("ANDROID_RELEASE_KEY_ALIAS"),
    "ANDROID_RELEASE_KEY_PASSWORD" to providers.environmentVariable("ANDROID_RELEASE_KEY_PASSWORD"),
)

android {
    namespace = "com.antiscroll.mobile"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.antiscroll.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 7
        versionName = "0.3.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            releaseSigningVariables.getValue("ANDROID_RELEASE_STORE_FILE")
                .orNull
                ?.takeIf(String::isNotBlank)
                ?.let { storeFile = file(it) }
            storePassword = releaseSigningVariables
                .getValue("ANDROID_RELEASE_STORE_PASSWORD")
                .orNull
            keyAlias = releaseSigningVariables
                .getValue("ANDROID_RELEASE_KEY_ALIAS")
                .orNull
            keyPassword = releaseSigningVariables
                .getValue("ANDROID_RELEASE_KEY_PASSWORD")
                .orNull
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")

    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}

val validateReleaseSigning by tasks.registering {
    group = "verification"
    description = "Checks that all release-signing environment variables are configured."

    doLast {
        val missingVariables = releaseSigningVariables
            .filterValues { provider -> provider.orNull.isNullOrBlank() }
            .keys

        if (missingVariables.isNotEmpty()) {
            throw GradleException(
                "Missing release-signing environment variables: " +
                    missingVariables.sorted().joinToString(", "),
            )
        }

        val storeFilePath = releaseSigningVariables
            .getValue("ANDROID_RELEASE_STORE_FILE")
            .get()
        if (!file(storeFilePath).isFile) {
            throw GradleException("Release keystore not found: $storeFilePath")
        }
    }
}

tasks.configureEach {
    if (name == "packageRelease" || name == "bundleRelease") {
        dependsOn(validateReleaseSigning)
    }
}
