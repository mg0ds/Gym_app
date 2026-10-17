# GymTracker

[![CI](https://github.com/mg0ds/Gym_app/actions/workflows/ci.yml/badge.svg)](https://github.com/mg0ds/Gym_app/actions/workflows/ci.yml)

Aplikacja Android do śledzenia treningu siłowego: kalendarz treningów, katalog
ćwiczeń, plany, timery (trening + przerwy), historia i karta pomiarów ciała.
Wszystkie dane trzymamy **lokalnie na urządzeniu** — bez kont, chmury i analityki.

Stan: **M0–M6 ukończone** (fundament, katalog ćwiczeń z wyszukiwaniem, plany
treningowe z edytorem dni i start treningu z planu, kalendarz z harmonogramem
„co N dni”, trening z timerem w notyfikacji, pauzą, logowaniem serii i notatką,
timer przerwy z alarmem, celem na ćwiczenie i średnimi, historia treningów
z filtrami i statystyki ćwiczenia z wykresami).
Kolejny krok: załączniki z galerii i filmy na karcie ćwiczenia (M7).

## Wymagania

* **JDK 21** — AGP 9 wymaga min. 17, ale sandbox Robolectric dla SDK 36 wymaga Javy 21,
* Android Studio (dowolna wersja obsługująca AGP 9) lub samo Android SDK,
* **Android SDK Platform 36** + Build Tools 36.x.

> Android Studio 2026.2 wnosi własne JDK w wersji **25**. Do zbudowania apki wystarczy,
> ale testy uruchamiamy na Javie 21 (tak samo jak w CI), więc w Studiu ustaw
> *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK* na JDK 21.

## Budowanie i testy

```bash
./gradlew assembleDebug        # apka debug
./gradlew testDebugUnitTest    # testy jednostkowe (JUnit + Robolectric)
./gradlew lintDebug            # lint
```

Wszystkie trzy kroki uruchamia CI na GitHub Actions dla każdego pusha i PR-a
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

### Środowisko lokalne (macOS) — konfiguracja od zera

```bash
brew install --cask android-studio                  # IDE + SDK Manager + Device Manager
brew install --cask android-commandlinetools        # sdkmanager, avdmanager
brew install openjdk@21                             # JDK 21 (formuła — bez sudo)

export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"

yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "cmdline-tools;latest" "platform-tools" \
           "platforms;android-36" "build-tools;36.1.0" \
           "emulator" "system-images;android-36;google_apis;arm64-v8a"
```

> Uwaga: `sdkmanager`/`avdmanager` z Homebrew celują we **własny** katalog SDK, nie w ten
> z `ANDROID_HOME`. Dlatego instalujemy `cmdline-tools` w naszym SDK i w `PATH` stawiamy
> `$ANDROID_HOME/cmdline-tools/latest/bin` przed `/opt/homebrew/bin`. Oba te narzędzia
> przyjmują też `--sdk_root="$ANDROID_HOME"`.
>
> `adb` i `fastboot` bierzemy z tego samego `platform-tools` w SDK — **nie** z caska
> `android-platform-tools`. Emulator po starcie podnosi serwer adb, szukając go w `PATH`,
> a pliki z caska Homebrew są znaczone przez macOS jako „pobrane z internetu”, więc system
> pyta wtedy o zgodę na ich uruchomienie. Jedna kopia = brak pytania (i brak ryzyka, że
> Gradle i emulator użyją dwóch różnych wersji adb).
>
> Wygodny skrót do `~/.zshrc` (JDK 21 zgodny z CI + narzędzia Androida):
>
> ```bash
> export JAVA_HOME="/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home"
> export ANDROID_HOME="$HOME/Library/Android/sdk"
> export PATH="/opt/homebrew/opt/openjdk@21/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$PATH"
> ```

`local.properties` (ignorowany przez git) musi wskazywać SDK:

```properties
sdk.dir=/Users/<użytkownik>/Library/Android/sdk
```

Emulator (AVD) i instalacja apki:

```bash
avdmanager create avd -n gym_api36 -d pixel_7 \
  -k "system-images;android-36;google_apis;arm64-v8a"
~/Library/Android/sdk/emulator/emulator -avd gym_api36 &
./gradlew installDebug
```

Telefon po USB: włącz *debugowanie USB*, podłącz kabel, sprawdź `adb devices`,
potem `./gradlew installDebug`.

Po **pierwszym lokalnym** buildzie warto zacommitować wyeksportowane schematy bazy
(`app/schemas/com.gymtracker.data.db.AppDatabase/*.json`) — Room używa ich do testów
migracji i auto-migracji, a w CI powstają tylko na czas budowania.

## Zawartość repozytorium

| Ścieżka | Co to |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | główny plan: wymagania, stack, architektura, model danych, ekrany, algorytmy timerów i harmonogramu, kamienie milowe, testy, ryzyka |
| [`docs/SCHEMA.sql`](docs/SCHEMA.sql) | docelowy DDL bazy lokalnej (Room/SQLite) — realizowany krok po kroku |
| [`docs/QA.md`](docs/QA.md) | scenariusze testów ręcznych (timer, odtwarzanie po ubiciu apki, kalendarz i reguły, zmiana czasu, historia i statystyki) |
| [`data/seed/`](data/seed) | dane startowe do importu: `exercises.json` + `programs/*.json` |
| `app/` | kod aplikacji (Kotlin + Compose, Room, Hilt) |

```
Gym_app/
├── app/                               # moduł aplikacji
│   ├── schemas/                       # wyeksportowane schematy Room (1.json … 6.json)
│   └── src/
│       ├── main/java/com/gymtracker/  # data/ (db, search, seed, prefs, repo), domain/, di/, ui/
│       ├── main/res/                  # teksty PL (domyślne) + EN, motyw, ikony
│       └── test/                      # testy jednostkowe (Robolectric)
├── docs/
│   ├── PLAN.md            # plan wdrożenia aplikacji
│   ├── SCHEMA.sql         # schemat bazy (stan obecny + tabele docelowe)
│   └── QA.md              # scenariusze testów ręcznych
├── gradle/libs.versions.toml          # wszystkie wersje zależności
└── data/
    └── seed/                          # dane startowe (kopiowane do assetów na czas budowania)
        ├── exercises.json            # 23 ćwiczenia
        └── programs/muscle-strength-full-body-workout-routine.json
```

> Plików z `data/seed/` nie ma drugiej kopii w `app/src/main/assets/` — zadanie
> Gradle `syncSeedAssets` przenosi je do generowanych assetów na czas budowania,
> więc źródło prawdy jest jedno. Po zmianie zawartości seeda zrób
> `./gradlew :app:clean`, żeby odświeżyć paczkę assetów.

## Stack

| Element | Wybór |
|---|---|
| Język / UI | Kotlin 2.3 + Jetpack Compose (Material 3) |
| Budowanie | AGP 9.4, Gradle 9.8, wersje w `gradle/libs.versions.toml` |
| Baza | Room (SQLite) + migracje, eksport schematów do `app/schemas/` (obecnie wersja 6) |
| Wyszukiwanie | SQLite FTS4 (`unicode61`) na tabeli `exercises` — znajduje też bez polskich ogonków |
| Usuwanie ćwiczenia | Archiwizacja (`is_archived = 1`), nie kasowanie wiersza — plany i historia treningów zostają czytelne |
| Plany | plany → dni → ćwiczenia dnia: kolejność (przeciąganie + menu), superserie, alternatywy („albo”); start treningu kopiuje dzień do sesji |
| Kalendarz | miesiąc z wpisami (odbyty / zaplanowany / pominięty); reguła „co N dni” z rotacją dni A/B/C rozwijana do dat, horyzont **20 tygodni** w przód; start treningu z wpisu |
| Trening | timer jako usługa pierwszoplanowa (notyfikacja z chronometrem, akcje „Pauza” / „Wznów” / „Zakończ”), czas i pauzy zapisane w bazie od razu, logowanie serii (kg × powtórzenia albo czas), notatka do treningu |
| Przerwy | przerwa rusza po zalogowanej serii: cel na ćwiczenie (sesja → karta ćwiczenia → ustawienie globalne 90 s), pasek z odliczaniem i akcjami „-15 s” / „+15 s” / „Pomiń”, notyfikacja odliczająca w dół, alarm z dźwiękiem i wibracją na końcu, historia i średnia per ćwiczenie; przerwa liczy się do czasu treningu, a obok widać „pod sztangą” |
| Historia i statystyki | zakładka „Historia”: zakończone treningi od najnowszych (data, czas bez pauz, serie, objętość, średnia przerwa) z filtrami zakresu (1M/3M/6M/1R/wszystko), ćwiczenia i nazwy dnia; statystyki ćwiczenia: rekordy z całej historii (najcięższa seria, 1RM Epleya, największa objętość, przerwy) oraz wykresy postępu ciężaru i objętości na **Vico 3.2.3** — jednostką wykresu jest **dzień** (dwa treningi jednego dnia to jeden punkt) |
| Daty | `java.time` (`LocalDate`, `YearMonth`) na poziomie API 26 — bez dodatkowych zależności; w bazie ISO `YYYY-MM-DD` |
| Ustawienia | DataStore (Preferences) |
| DI | Hilt (KSP) |
| Testy | JUnit4 + Robolectric (testy JVM), CI na GitHub Actions |
| minSdk / targetSdk | 26 / 36 |

> **Wersje bibliotek:** stoimy na ostatnich wydaniach androidx wspierających
> `compileSdk 36`. Nowsze (compose 1.12.x, core-ktx 1.19.x, lifecycle 2.11.x,
> navigation 2.10.x) wymagają już `compileSdk 37` — podniesienie progu razem
> z nimi to osobny krok, szczegóły w [`docs/PLAN.md`](docs/PLAN.md) §8.1.

## Co jest w danych

* **23 ćwiczenia** — nazwa, partia mięśniowa + mięśnie dodatkowe, sprzęt, mechanika
  (złożone/izolowane), poziom, 16 linków do filmów na YouTube.
* **1 plan treningowy** — 3 dni (A / B / C), 25 pozycji, z modyfikatorami serii
  „ramped 5×5” i wyborem „Pull Ups lub Inverted Rows”.
* Zalogowane serie trafiają do `set_logs` (ciężar, powtórzenia albo czas,
  znacznik rozgrzewki) i są widoczne na ekranie treningu; przerwy między nimi —
  do `rest_intervals` (z powodem zakończenia), a ich skrót do wiersza serii
  (`rest_after_s`), więc karta ćwiczenia pokazuje średnią z tego treningu
  i z historii.
* **Klucz naturalny** `id` (np. `barbell-bench-press`, `squat`) — trafia do kolumny
  `external_id` w bazie i pozwala bezpiecznie re-importować dane bez duplikatów
  oraz bez nadpisywania zmian użytkownika.
* Kontrakt plików JSON (pola, mapowanie na tabele) opisany w
  [`docs/PLAN.md`](docs/PLAN.md) §6.8 i §7.

## Format danych

* `exercises.json` — `schema_version`, `generated_at`, `source`, `exercises[]`.
* `programs/<slug>.json` — `schema_version`, `generated_at`, `program`
  (metadane, `summary`, `schedule`, `workouts[]` z listą pozycji).
* Każde ćwiczenie i plan mają pole `source_url` — w aplikacji posłuży jako
  odnośnik „zobacz źródło”.
* Listy (mięśnie dodatkowe, wyposażenie planu) zapisujemy w bazie jako JSON TEXT.

## Stan realizacji

| Krok | Zakres | Status |
|---|---|---|
| **M0** | projekt Gradle, Compose, Hilt, Room + migracje, motyw PL/EN, CI | ✔ |
| **M1** | katalog ćwiczeń: CRUD, szukanie FTS z polskimi znakami, filtr partii, ulubione, import 23 ćwiczeń | ✔ |
| **M2** | plany: dni i kolejność (przeciąganie), serie/powtórzenia/czas, superserie, alternatywy, import planu startowego, start i zakończenie treningu z planu, usuwanie ćwiczenia przez archiwizację | ✔ |
| **M3** | kalendarz miesiąca z wpisami i statusami, harmonogram planu (reguła „co N dni” albo wybrane dni), rotacja dni A/B/C, horyzont 20 tygodni, podgląd dat przed zapisem, start treningu z wpisu i odhaczenie po zakończeniu | ✔ |
| **M4** | sesja i timer treningu: usługa pierwszoplanowa z notyfikacją, start/pauza/stop, odtwarzanie po ubiciu aplikacji, logowanie serii, notatka do treningu, wiązanie wpisu w kalendarzu przy starcie z planu | ✔ |
| **M5** | timer przerwy: start po zalogowanej serii, cel przerwy na ćwiczenie (sesja → karta ćwiczenia → 90 s z ustawień), alarm z dźwiękiem i wibracją przy końcu (także po ubiciu aplikacji), „-15 s” / „+15 s” / „Pomiń”, wliczanie w czas treningu i „pod sztangą”, średnia oraz historia przerw per ćwiczenie | ✔ |
| **M6** | historia i statystyki: zakładka „Historia” z listą zakończonych treningów i filtrami (zakres, ćwiczenie, nazwa dnia), podgląd treningu do czytania, karta historii na ćwiczeniu, statystyki ćwiczenia (rekordy z całej historii + wykresy ciężaru i objętości oraz tabela wykonań w zakresie) na Vico 3.2.3; bez zmian schematu bazy | ✔ |

Szczegóły i kolejne kamienie milowe: [`docs/PLAN.md`](docs/PLAN.md) §8.

