# GymTracker — plan aplikacji + dane startowe

Repozytorium zawiera **plan wdrożenia** aplikacji Android do śledzenia treningu
siłowego oraz **wstępny katalog ćwiczeń i gotowy plan treningowy** (23 ćwiczenia,
3 dni treningowe), który aplikacja zaimportuje przy pierwszym uruchomieniu.

Aplikacja nie jest jeszcze napisana — to etap planowania i przygotowania danych.

## Zawartość repozytorium

| Ścieżka | Co to |
|---|---|
| [`docs/PLAN.md`](docs/PLAN.md) | główny plan: wymagania, stack, architektura, model danych, ekrany, algorytmy timerów i harmonogramu, kamienie milowe, testy, ryzyka |
| [`docs/SCHEMA.sql`](docs/SCHEMA.sql) | pełny DDL bazy lokalnej (Room/SQLite) |
| [`docs/QA.md`](docs/QA.md) | scenariusze testów ręcznych (timer, odtwarzanie po ubiciu apki, DST) |
| [`data/seed/`](data/seed) | dane startowe do importu: `exercises.json` + `programs/*.json` |

```
Gym_app/
├── docs/
│   ├── PLAN.md            # plan wdrożenia aplikacji
│   ├── SCHEMA.sql         # schemat bazy
│   └── QA.md              # scenariusze testów ręcznych
└── data/
    └── seed/                         # dane startowe → app/src/main/assets/seed/
        ├── exercises.json            # 23 ćwiczenia
        └── programs/muscle-strength-full-body-workout-routine.json
```

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

## Jak zacząć pracę nad aplikacją

Plan zakłada własny moduł Gradle `app/` (Kotlin + Jetpack Compose, Room, Hilt).
Kolejne kroki i kamienie milowe: [`docs/PLAN.md`](docs/PLAN.md) §11.
