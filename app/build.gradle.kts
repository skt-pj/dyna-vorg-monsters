import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}

val releaseKeystoreFile = rootProject.file("ci/skt-common-signing.jks")

android {
    namespace = "com.sktpj.dynavorgmonsters"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sktpj.dynavorgmonsters"
        minSdk = 23
        targetSdk = 36
        versionCode = versionProperties.getProperty("VERSION_CODE").toInt()
        versionName = versionProperties.getProperty("VERSION_NAME")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    signingConfigs {
        create("commonStable") {
            storeFile = releaseKeystoreFile
            storePassword = "2048td-release"
            keyAlias = "2048td-release"
            keyPassword = "2048td-release"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("commonStable")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
