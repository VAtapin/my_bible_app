import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.zip.GZIPInputStream

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

val releaseSecrets = listOf("BIBLE_RELEASE_STORE_FILE", "BIBLE_RELEASE_STORE_PASSWORD", "BIBLE_RELEASE_KEY_ALIAS", "BIBLE_RELEASE_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = releaseSecrets.values.all { !it.isNullOrBlank() }
val requestedVersionCode = providers.gradleProperty("bibleVersionCode").orNull
val requestedVersionName = providers.gradleProperty("bibleVersionName").orNull
val releaseVersionCode = requestedVersionCode?.let {
    it.toIntOrNull()?.takeIf { code -> code in 1..2_100_000_000 } ?: error("bibleVersionCode must be a positive Play version code")
} ?: 4
val releaseVersionName = requestedVersionName?.also {
    require(Regex("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-.][A-Za-z0-9.-]+)?").matches(it)) { "bibleVersionName must be a semantic version" }
} ?: "0.1.3"

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }

    dependencies {
        implementation(projects.sharedLogic)
        implementation(libs.androidx.activity.compose)
        implementation(libs.androidx.core.ktx)
        implementation(platform(libs.androidx.compose.bom))
        implementation(libs.androidx.compose.foundation)
        implementation(libs.androidx.compose.material.icons.extended)
        implementation(libs.androidx.compose.material3)
        implementation(libs.androidx.compose.ui)
        implementation(libs.androidx.compose.ui.tooling.preview)
        implementation(libs.coil.compose)
        implementation(libs.coil.network.okhttp)
        implementation(libs.coil.svg)
        implementation(libs.androidx.work.runtime)
        implementation(libs.kotlinx.serialization.json)
        implementation(libs.kotlinx.coroutines.android)
        implementation(libs.snowball.stemmer)
        debugImplementation(libs.androidx.compose.ui.tooling)
        debugImplementation(libs.androidx.compose.ui.test.manifest)
        androidTestImplementation(platform(libs.androidx.compose.bom))
        androidTestImplementation(libs.androidx.compose.ui.test.junit4)
        androidTestImplementation(libs.androidx.test.runner)
        androidTestImplementation(libs.androidx.test.ext.junit)
        androidTestImplementation(libs.androidx.test.espresso.core)
        androidTestImplementation(libs.androidx.work.testing)
    }
}

android {
    namespace = "com.bibledesktop.myapp"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.bibledesktop.myapp"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = releaseVersionCode
        versionName = releaseVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildFeatures {
        compose = true
    }

    // The in-app language picker must work offline, independently of the device language.
    bundle {
        language { enableSplit = false }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    signingConfigs {
        if (hasReleaseSigning) create("releaseUpload") {
            storeFile = file(releaseSecrets.getValue("BIBLE_RELEASE_STORE_FILE")!!)
            storePassword = releaseSecrets.getValue("BIBLE_RELEASE_STORE_PASSWORD")
            keyAlias = releaseSecrets.getValue("BIBLE_RELEASE_KEY_ALIAS")
            keyPassword = releaseSecrets.getValue("BIBLE_RELEASE_KEY_PASSWORD")
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        getByName("release") {
            isDebuggable = false
            isMinifyEnabled = false
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("releaseUpload")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
    group = "verification"
    description = "Prevent unsigned releases and accidental use of debug signing."
    doLast {
        check(hasReleaseSigning) { "Release signing is not configured. Use mobile/scripts/Build-Release.ps1; do not put passwords in Gradle arguments." }
        val key = file(releaseSecrets.getValue("BIBLE_RELEASE_STORE_FILE")!!).canonicalFile
        check(key.isFile && !key.toPath().startsWith(rootProject.projectDir.parentFile.canonicalFile.toPath())) {
            "The release keystore must exist outside the repository."
        }
        check(key.name != "debug.keystore" && releaseSecrets.getValue("BIBLE_RELEASE_KEY_ALIAS") != "androiddebugkey") {
            "Debug signing is not permitted for a release."
        }
    }
}
tasks.matching { it.name in setOf("bundleRelease", "assembleRelease", "packageRelease", "packageReleaseBundle", "validateSigningRelease") }
    .configureEach { dependsOn(verifyReleaseSigning) }

val verifyBundledBible = tasks.register("verifyBundledBible") {
    group = "verification"
    val asset = layout.projectDirectory.file("src/main/assets/bibles/synodal.bundle")
    inputs.file(asset)
    doLast {
        check(asset.asFile.isFile && asset.asFile.length() > 1_000_000) { "The bundled Synodal Bible is missing. Run mobile/scripts/build-bundled-bible.mjs explicitly." }
        GZIPInputStream(asset.asFile.inputStream()).bufferedReader(Charsets.UTF_8).use { reader ->
            check(reader.readLine().contains("BQ_RUSSIAN_RST_STRONG")) { "Invalid bundled Bible manifest" }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyBundledBible) }
