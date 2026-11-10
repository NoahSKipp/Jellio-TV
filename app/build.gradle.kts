import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

// Each release needs a higher versionCode than every build before it, and CI run
// numbers differ per workflow, so the code comes from versionName instead:
// major * 100000 + minor * 1000 + patch (0.85.0 is 85000, 1.0.0 is 100000).
fun versionCodeOf(name: String): Int {
    val parts = Regex("""(\d{1,5})\.(\d{1,5})\.(\d{1,5})""").matchEntire(name)
        ?.groupValues?.drop(1)?.map { it.toInt() }
        ?: throw GradleException("versionName \"$name\" must be major.minor.patch, like 1.2.3")
    val (major, minor, patch) = parts
    if (major > 20000 || minor > 99 || patch > 999) {
        throw GradleException("versionName \"$name\" is out of range: major <= 20000, minor <= 99, patch <= 999")
    }
    return major * 100000 + minor * 1000 + patch
}

android {
    namespace = "com.jellio.tv"
    // activity-compose 1.11 needs compileSdk 36.
    compileSdk = 36

    defaultConfig {
        applicationId = "com.jellio.tv"
        minSdk = 24
        targetSdk = 36
        versionName = "1.0.0"
        versionCode = versionCodeOf(versionName!!)
    }

    val signingKeystore = (System.getenv("SIGNING_KEYSTORE_FILE") ?: System.getenv("SIGNING_KEYSTORE_PATH"))
        ?.takeIf { it.isNotBlank() }?.let { file(it) }
        ?: file("/home/noahsk/jellio-tv.keystore").takeIf { it.exists() }
    val storePassword = System.getenv("SIGNING_STORE_PASSWORD")

    signingConfigs {
        if (signingKeystore != null && signingKeystore.exists() && !storePassword.isNullOrBlank()) {
            create("sideload") {
                storeFile = signingKeystore
                this.storePassword = storePassword
                keyAlias = System.getenv("SIGNING_KEY_ALIAS") ?: "jellio"
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD") ?: storePassword
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("sideload")?.let { signingConfig = it }
        }
        // The published build. Same key as debug builds so it installs over them.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // A published APK must carry the real key, or it won't install over
            // existing ones; CI builds without the secrets may still use debug.
            if (System.getenv("REQUIRE_RELEASE_SIGNING") == "true" && signingConfigs.findByName("sideload") == null) {
                throw GradleException("Release signing key missing: set SIGNING_KEYSTORE_FILE and SIGNING_STORE_PASSWORD.")
            }
            signingConfig = signingConfigs.findByName("sideload") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        // Settings' About section reads BuildConfig.VERSION_NAME.
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

composeCompiler {
    stabilityConfigurationFiles.add(rootProject.layout.projectDirectory.file("compose_stability_config.conf"))
    // ./gradlew assembleRelease -PcomposeReports writes which composables still can't skip.
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_reports")
        metricsDestination = layout.buildDirectory.dir("compose_metrics")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
        freeCompilerArgs.add("-opt-in=androidx.compose.foundation.ExperimentalFoundationApi")
        freeCompilerArgs.add("-opt-in=androidx.tv.material3.ExperimentalTvMaterial3Api")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation("junit:junit:4.13.2")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation("androidx.compose.material:material-icons-extended")
    debugImplementation(libs.androidx.compose.ui.tooling)
    implementation(libs.androidx.tv.material)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.tv.provider)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.retrofit)
    implementation(libs.retrofit.moshi)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.moshi)
    ksp(libs.moshi.codegen)

    implementation(libs.coroutines.core)
    implementation(libs.coroutines.android)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.coil.gif)
    implementation(libs.coil.svg)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.media3.common)
    implementation(libs.media3.session)
    implementation(libs.media3.exoplayer.hls)
    implementation(libs.media3.datasource.okhttp)

    implementation(libs.zxing.core)
}
