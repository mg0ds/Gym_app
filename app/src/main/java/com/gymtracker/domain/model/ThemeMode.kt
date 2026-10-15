package com.gymtracker.domain.model

/**
 * Wybór motywu z Ustawień. [storageValue] jest tym, co zapisujemy w DataStore,
 * żeby zmiana nazwy stałej w kodzie nie unieważniła zapisanych ustawień.
 */
enum class ThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
    ;

    companion object {
        val DEFAULT: ThemeMode = SYSTEM

        fun fromStorage(value: String?): ThemeMode =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
