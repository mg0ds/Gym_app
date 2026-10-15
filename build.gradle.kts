// Wersje wtyczek deklarujemy w gradle/libs.versions.toml.
// `apply false` = wersja jest tu tylko zarejestrowana; moduł :app włącza ją u siebie.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
}
