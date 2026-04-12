import java.io.ByteArrayOutputStream
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun runGit(vararg args: String): String? {
    return try {
        val out = ByteArrayOutputStream()
        val proc = ProcessBuilder(listOf("git") + args.toList())
            .directory(rootDir)
            .redirectErrorStream(false)
            .start()
        proc.inputStream.copyTo(out)
        if (proc.waitFor() == 0) out.toString().trim().ifEmpty { null } else null
    } catch (_: Exception) {
        null
    }
}

fun gitVersionName(): String =
    runGit("describe", "--tags", "--abbrev=0")?.removePrefix("v") ?: "dev"

fun gitVersionCode(): Int =
    runGit("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1

android {
    namespace = "fr.gaetanlhf.dreamflipclock"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.gaetanlhf.dreamflipclock"
        minSdk = 30
        targetSdk = 36
        versionCode = gitVersionCode()
        versionName = gitVersionName()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val storeFilePath = System.getenv("KEYSTORE_PATH")
            if (storeFilePath != null) {
                storeFile = file(storeFilePath)
                storePassword = System.getenv("KEYSTORE_PASSWORD")
                keyAlias = System.getenv("KEY_ALIAS")
                keyPassword = System.getenv("KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (System.getenv("KEYSTORE_PATH") != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
