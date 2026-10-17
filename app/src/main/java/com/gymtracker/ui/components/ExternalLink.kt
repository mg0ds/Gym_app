package com.gymtracker.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Otwiera link poza aplikacją (YouTube, przeglądarka).
 *
 * Aplikacja nie chodzi do sieci - nie ma uprawnienia `INTERNET` - więc film
 * oglądamy tam, gdzie użytkownik ma do tego narzędzie. Gdy nie ma czym
 * obsłużyć linku, nic się nie dzieje: brak odbiorcy to nie błąd aplikacji.
 */
fun openExternalLink(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    runCatching { context.startActivity(intent) }
}
