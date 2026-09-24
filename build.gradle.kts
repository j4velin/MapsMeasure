import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val hasOwnKeys = project.file("key.properties").exists()

val keyProperties = Properties().apply {
    if (!hasOwnKeys) logger.warn("Using sample keystore!!")
    project.file(if (hasOwnKeys) "key.properties" else "key.properties.sample")
        .inputStream().use { load(it) }
}

android {
    namespace = "de.j4velin.mapsmeasure"
    compileSdk = 37

    buildFeatures {
        buildConfig = true
        resValues = true
        compose = true
    }

    defaultConfig {
        applicationId = "de.j4velin.mapsmeasure"
        minSdk = 23
        targetSdk = 37
        if (!hasOwnKeys) {
            resValue("string", "maps_api_key", "0000")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    signingConfigs {
        create("release") {
            storeFile = project.file(keyProperties.getProperty("keyStore"))
            storePassword = keyProperties.getProperty("keyStorePassword")
            keyAlias = keyProperties.getProperty("keyAlias")
            keyPassword = keyProperties.getProperty("keyAliasPassword")
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-project.txt"
            )
        }
        debug {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            isDebuggable = true
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.drawerlayout)
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.play.services.location)
    implementation(libs.play.services.maps)

    implementation(platform(libs.compose.bom))
    implementation(libs.maps.compose)

    testImplementation(libs.junit)
}
