import java.io.File
import java.util.Properties
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose)
    alias(libs.plugins.serialization)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// ---------------------------------------------------------------------------
// Dane startowe (seed) trzymamy w jednym miejscu w repo: `data/seed/`.
// W czasie budowania kopiujemy je do assetów, dzięki czemu nie ma drugiej,
// ręcznie synchronizowanej kopii tych plików w `app/src/main/assets/`.
// W apce czytamy je jako `assets/seed/<plik>`.
// ---------------------------------------------------------------------------
val syncSeedAssets = tasks.register<Sync>("syncSeedAssets") {
    description = "Kopiuje data/seed/ do assetów aplikacji (assets/seed/)."
    // Zawartość data/seed/ ląduje pod `seed/`, więc w apce czytamy `assets/seed/<plik>`.
    from(rootProject.layout.projectDirectory.dir("data/seed")) { into("seed") }
    into(layout.buildDirectory.dir("generated/seedAssets"))
}

androidComponents {
    onVariants { variant ->
        // Variant API sam pilnuje kolejności: assety są scalane po tym zadaniu.
        variant.sources.assets?.addGeneratedSourceDirectory(syncSeedAssets) { it.destinationDirectory }
    }
}

// ---------------------------------------------------------------------------
// Podpis wydania. Klucz **nie leży w repo**: czytamy `keystore.properties`
// z katalogu głównego projektu (albo zmienne środowiskowe - tak buduje CI).
// Gdy nic nie ma, release powstaje **niesignowany** i to jest w porządku:
// taki plik jest kontrolą konfiguracji R8, a nie gotowym wydaniem. Instrukcja
// (keytool, wysyłka AAB): README.
// ---------------------------------------------------------------------------
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use { stream -> load(stream) }
    }
}

fun releaseSetting(key: String, environment: String): String? =
    keystoreProperties.getProperty(key) ?: System.getenv(environment)

val releaseStorePath = releaseSetting("storeFile", "GYMTRACKER_STORE_FILE")
val releaseStorePassword = releaseSetting("storePassword", "GYMTRACKER_STORE_PASSWORD")
val releaseKeyAlias = releaseSetting("keyAlias", "GYMTRACKER_KEY_ALIAS")
val releaseKeyPassword = releaseSetting("keyPassword", "GYMTRACKER_KEY_PASSWORD")
val releaseSigningReady = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
).all { value -> !value.isNullOrBlank() }

android {
    namespace = "com.gymtracker"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gymtracker"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    signingConfigs {
        if (releaseSigningReady) {
            create("release") {
                // Ścieżka absolutna albo względna wobec katalogu głównego repo.
                val path = requireNotNull(releaseStorePath)
                storeFile = if (File(path).isAbsolute) File(path) else rootProject.file(path)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            // R8 i shrinkingResources (M9). Reguły dopisane w proguard-rules.pro.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (releaseSigningReady) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        // AGP 9 ma wbudowany Kotlin: jvmTarget bierze się z targetCompatibility.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests {
            // Robolectric + Compose potrzebują zasobów Androida w testach JVM.
            isIncludeAndroidResources = true
        }
    }
}

// Schematy Room w app/schemas/<klasa bazy>/<wersja>.json - commitujemy je,
// bo są podstawą testów migracji i auto-migracji.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.compose.ui)
    implementation(libs.compose.graphics)
    implementation(libs.compose.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    debugImplementation(libs.compose.tooling)

    implementation(libs.hilt.android)
    implementation(libs.androidx.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.vico.compose.m3)

    implementation(libs.coil.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)

    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.kotlinx.coroutines.test)
}

// Testy na JVM (Robolectric).
tasks.withType<Test>().configureEach {
    // Robolectric dla nowych SDK sięga po wewnętrzne API Javy (jdk.internal.access),
    // którego JDK 17+ nie odsłania domyślnie. Bez tego każdy test z Androidem wywala
    // się na "Failed to interact with raw FileDescriptor internals".
    jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")

    testLogging {
        events("failed")
        exceptionFormat = TestExceptionFormat.FULL
    }
}
