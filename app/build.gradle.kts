import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: localProperties.getProperty(name)?.takeIf { it.isNotBlank() }

val sawnStoreFile = signingValue("SAWN_STORE_FILE")
val sawnManifestUrl = providers.gradleProperty("SAWN_UPDATE_MANIFEST_URL")
    .orElse(providers.environmentVariable("SAWN_UPDATE_MANIFEST_URL"))
    .orElse("")
    .get()
require(sawnManifestUrl.isBlank() || sawnManifestUrl.startsWith("https://")) {
    "SAWN_UPDATE_MANIFEST_URL must be a public HTTPS manifest URL, or omitted to disable update checks."
}
val sawnStorePassword = signingValue("SAWN_STORE_PASSWORD")
val sawnKeyAlias = signingValue("SAWN_KEY_ALIAS")
val sawnKeyPassword = signingValue("SAWN_KEY_PASSWORD")
val sawnSigningValues = listOf(sawnStoreFile, sawnStorePassword, sawnKeyAlias, sawnKeyPassword)
require(sawnSigningValues.all { it == null } || sawnSigningValues.all { it != null }) {
    "Set all SAWN_* signing values in local.properties or the environment, or none of them."
}

android {
    namespace = "com.sawn.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.sawn.app"
        minSdk = 26
        targetSdk = 36
        versionCode = 31
        versionName = "2.2.0"
        buildConfigField("String", "UPDATE_MANIFEST_URL", "\"$sawnManifestUrl\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (sawnSigningValues.all { it != null }) {
            create("release") {
                storeFile = rootProject.file(sawnStoreFile!!)
                storePassword = sawnStorePassword!!
                keyAlias = sawnKeyAlias!!
                keyPassword = sawnKeyPassword!!
            }
        }
    }

    buildTypes {
        release {
            // الإصدار النهائي مضغوط الخفة: R8 بالقواعد الافتراضية + قواعد Sawn الخاصة
            //_KEEP للخدمات المرجعَية من الـManifest فقط. استخدم SAWN_MINIFY_RELEASE=false
            // فقط للتشخيص، فالنسخة المصغرة هي المفترض نُشرها (حجم نزول ملحوظ).
            isMinifyEnabled = providers.gradleProperty("SAWN_MINIFY_RELEASE")
                .orElse("true").get().toBoolean()
            isShrinkResources = isMinifyEnabled
            isDebuggable = false
            if (sawnSigningValues.all { it != null }) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    // APK Signature Scheme v2 + v3. v1 (JAR signing) is requested but AGP omits it for
    // minSdk >= 24 — measured on the release output: v2/v3 verify, v1 reports false, which
    // is expected and still installs on every supported device (minSdk 26).
    // v3 is what lets a future Play key rotation work without breaking updates.
    signingConfigs {
        if (sawnSigningValues.all { it != null }) {
            getByName("release") {
                enableV1Signing = true
                enableV2Signing = true
                enableV3Signing = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true // for BuildConfig.DEBUG (FLAG_SECURE only in release)
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
    }
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.androidx.biometric)

    // On-device NSFW image screening: official TF Lite Task Vision runtime. The model
    // file (MobileNet AutoML, ~5.8MB) ships in assets/nsfw/ so nothing is fetched at
    // runtime and classification never leaves the device.
    implementation("org.tensorflow:tensorflow-lite-task-vision:0.4.4")

    debugImplementation(libs.androidx.ui.tooling)

    testImplementation(project(":core:domain"))
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
