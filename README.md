# GymTracker

Aplikacja Android do śledzenia treningu siłowego: kalendarz treningów, katalog
ćwiczeń, plany, timery (trening + przerwy), historia i karta pomiarów ciała.
Wszystkie dane trzymamy **lokalnie na urządzeniu** — bez kont, chmury i analityki.

Stan: **M0 ukończony** (fundament projektu). Kolejny krok to katalog ćwiczeń (M1).

## Wymagania

* **JDK 21** — AGP 9 wymaga min. 17, ale sandbox Robolectric dla SDK 36 wymaga Javy 21
  (Android Studio wnosi własne JDK 21, więc zwykle nic nie trzeba instalować),
* Android Studio (dowolna wersja obsługująca AGP 9) lub samo Android SDK,
* **Android SDK Platform 36** + Build Tools 36.x.

## Budowanie i testy

```bash
./gradlew assembleDebug        # apka debug
./gradlew testDebugUnitTest    # testy jednostkowe (JUnit + Robolectric)
./gradlew lintDebug            # lint
```

Wszystkie trzy kroki uruchamia CI na GitHub Actions dla każdego pusha i PR-a
([`.github/workflows/ci.yml`](.github/workflows/ci.yml)).

## Zawartość repozytorium

| Ścieżka | Co to |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | główny plan: wymagania, stack, architektura, model danych, ekrany, algorytmy timerów i harmonogramu, kamienie milowe, testy, ryzyka |
| [`docs/SCHEMA.sql`](docs/SCHEMA.sql) | docelowy DDL bazy lokalnej (Room/SQLite) — realizowany krok po kroku |
| [`docs/QA.md`](docs/QA.md) | scenariusze testów ręcznych (timer, odtwarzanie po ubiciu apki, DST) |
| [`data/seed/`](data/seed) | dane startowe do importu: `exercises.json` + `programs/*.json` |
| `app/` | kod aplikacji (Kotlin + Compose, Room, Hilt) |

```
Gym_app/
├── app/                               # moduł aplikacji
│   ├── schemas/                       # wyeksportowane schematy Room (migracje)
│   └── src/
│       ├── main/java/com/gymtracker/  # data/ (db, prefs), domain/, di/, ui/
│       ├── main/res/                  # teksty PL (domyślne) + EN, motyw, ikony
│       └── test/                      # testy jednostkowe (Robolectric)
├── docs/
│   ├── PLAN.md            # plan wdrożenia aplikacji
│   ├── SCHEMA.sql         # schemat bazy
│   └── QA.md              # scenariusze testów ręcznych
├── gradle/libs.versions.toml          # wszystkie wersje zależności
└── data/
    └── seed/                          # dane startowe → app/src/main/assets/seed/
        ├── exercises.json            # 23 ćwiczenia
        └── programs/muscle-strength-full-body-workout-routine.json
```

## Stack

| Element | Wybór |
|---|---|
| Język / UI | Kotlin 2.3 + Jetpack Compose (Material 3) |
| Budowanie | AGP 9.4, Gradle 9.8, wersje w `gradle/libs.versions.toml` |
| Baza | Room (SQLite) + migracje, eksport schematów do `app/schemas/` |
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
| M1 | katalog ćwiczeń (CRUD, szukanie, filtry, import seeda) | — |

Szczegóły i kolejne kamienie milowe: [`docs/PLAN.md`](docs/PLAN.md) §8.

