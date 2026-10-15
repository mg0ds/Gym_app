# Plan wdrożenia aplikacji **GymTracker** (Android, offline-first)

Dokument opisuje jak zbudować aplikację do śledzenia treningu siłowego, spełniającą
wszystkie punkty z wymagań, oraz jak zaimportować wstępny katalog ćwiczeń i plan
treningowy z `data/seed/` (patrz §7).

| | |
|---|---|
| Platforma | Android (natywnie), `minSdk 26`, `targetSdk 36` |
| Język / UI | Kotlin 2.x + Jetpack Compose (Material 3) |
| Dane | Room (SQLite), DataStore, pliki w `filesDir` — **100% lokalnie** |
| Architektura | Clean-ish: UI → ViewModel → UseCase → Repository → DAO |
| Repo | `moje/Projekty/Gym_app/`: `data/seed/` (dane startowe), `docs/`, `app/` (do utworzenia) |
| Szacowany czas | ~50 dni roboczych dla 1 dewelopera (~10–11 tygodni) + 20% bufora |

---

## 1. Zakres i wymagania

### 1.1 Mapowanie wymagań na moduły

| # | Twoje wymaganie | Moduł | MVP | V2 |
|---|---|---|---|---|
| 1 | Zaznaczanie treningów w kalendarzu | `calendar` | ✔ | — |
| 2 | Planowanie przyszłych: konkretne dni | `calendar` | ✔ | — |
| 3 | Planowanie cykliczne: data od, data do, „co ile dni” | `calendar/schedule` | ✔ | — |
| 4 | Lista ćwiczeń + dodawanie nowych | `exercises` | ✔ | — |
| 5 | Tworzenie planu treningowego (zestawy na dany dzień, różne kombinacje) | `plans` | ✔ | — |
| 6 | Szczegóły ćwiczenia: serie, powtórzenia | `exercises`, `plans` | ✔ | — |
| 7 | Historia wykonania ćwiczenia | `history` | ✔ | — |
| 8 | Obciążenie w kg + historia obciążenia | `tracking`, `history` | ✔ | — |
| 9 | Link do YouTube | `exercises/media` | ✔ | — |
| 10 | Zdjęcie / film z galerii telefonu | `exercises/media` | ✔ | — |
| 11 | Komentarz do ćwiczenia | `exercises` | ✔ | — |
| 12 | Timer całego treningu (start/koniec) | `session/timer` | ✔ | — |
| 13 | Timer przerwy między seriami, wliczany w trening | `session/rest` | ✔ | — |
| 14 | Powiązanie przerwy z ćwiczeniem + podsumowanie / historia / średnia | `session/rest`, `history` | ✔ | — |
| 15 | Panel ustawień | `settings` | ✔ | — |
| 16 | Karta postaci: waga + customowe wskaźniki + wykresy zmian | `body` | ✔ | — |
| 17 | Zapis lokalny (dane + ustawienia) | cała aplikacja | ✔ | — |
| 18 | Wstępny katalog ćwiczeń i plan treningowy | import seedów | ✔ (dane gotowe) | katalog >1 planu |
| 19 | Statystyki (objętość, 1RM, PR) | `history` | częściowo | ✔ |
| 20 | Eksport / backup / przywracanie | `settings/backup` | ✔ (eksport) | import + chmura |
| 21 | Superserie, serie na czas (plank), RPE | `plans`, `tracking` | częściowo | ✔ |
| 22 | Przypomnienia o treningu (notyfikacje) | `notifications` | — | ✔ |
| 23 | Health Connect, Wear OS, widget | integracje | — | ✔ |

### 1.2 Wymagania niefunkcjonalne

| Obszar | Cel |
|---|---|
| Offline | 100% funkcji działa bez internetu. Sieć używana tylko do miniatur YouTube i ewentualnego odświeżenia seedów. |
| Dane użytkownika | Wyłącznie na urządzeniu. Brak kont, brak analityki, brak wysyłki czegokolwiek. |
| Czas zimnego startu | < 1,5 s na urządzeniu klasy średniej; brak blokujących zapytań w `Compose` (Flow + `collectAsStateWithLifecycle`). |
| Odporność timera | Timer treningu przeżywa: obrót ekranu, zwinięcie apki, ubicie procesu przez system, restart telefonu. |
| Rozmiar APK | < 15 MB (bez zależności od ciężkich SDK; wykresy w Compose). |
| Dostępność | TalkBack, skalowanie czcionek, kontrast, obsługa jednej ręki na siłowni (duże przyciski ≥ 48 dp). |
| Języki | PL (domyślny) + EN. |
| Prywatność | Media kopiowane do prywatnego katalogu aplikacji (nie śmiecimy w galerii użytkownika). |

---

## 2. Stack technologiczny

| Warstwa | Wybór | Dlaczego / alternatywa |
|---|---|---|
| UI | Jetpack Compose + Material 3 | Standard, dynamiczne kolory, mniej kodu niż XML. Alternatywa: XML + ViewBinding (jeśli zespół go zna). |
| DI | Hilt | Sprawdzone, dobre wsparcie `ViewModel`/`Service`. Alternatywa: Koin. |
| Baza | Room + SQLite | Typowane zapytania, migracje, `Flow`. SQLite FTS4 (`unicode61`) do wyszukiwania ćwiczeń — działa też bez polskich ogonków. |
| Preferencje | DataStore (Preferences) | Zamiast `SharedPreferences`. |
| Async | Coroutines + Flow | — |
| Daty | `kotlinx-datetime` | `LocalDate` bez pułapek stref czasowych. |
| Nawigacja | Navigation Compose (type-safe routes) | — |
| Wykresy | **Vico** (Compose-native) | Alternatywa: MPAndroidChart (View, owijka) — cięższy i starszy. |
| JSON | `kotlinx.serialization` | Import seedów, backup. |
| Obraz/wideo | Coil (miniatury), ExoPlayer/Media3 (odtwarzanie) | — |
| Galeria | `PickVisualMedia` (Photo Picker) | Od Android 13 bez uprawnień; fallback SAF. |
| YouTube | Intent / Custom Tabs + miniatura z `img.youtube.com` | **YouTube Android Player API jest przestarzałe i wycofywane** — nie używamy. |
| Timer | `ForegroundService` + `startedAt` (epoch) + `SystemClock.elapsedRealtime()` | Monotoniczny licznik odporny na zmianę czasu w systemie. |
| WorkManager | autozapis/dokończenie sesji, przypomnienia | — |
| Testy | JUnit4 + Truth, Turbine, Room in-memory, Compose UI Test, Robolectric | — |
| CI | GitHub Actions: lint, testy jednostkowe, build (docelowo też detekt i assembleRelease) | — |
| Dane startowe | JSON w `data/seed/` → `assets/seed/` | Import jednorazowy, offline. |

> **Decyzja: natywny Kotlin, nie Flutter/React Native.** Powody: timer w tle
> (`ForegroundService`), dostęp do plików i mediów, wykresy, spójny Material 3,
> brak dodatkowej warstwy runtime. Jeśli zależy Ci na iOS w tym samym czasie —
> patrz §11, ryzyko R6.

---

## 3. Architektura

### 3.1 Warstwy

```
┌────────────────────────────────────────────────────────────┐
│ UI (Compose)   ekrany, komponenty, motyw, nawigacja        │
├────────────────────────────────────────────────────────────┤
│ Presentation   ViewModels (StateFlow<UiState>), Events     │
├────────────────────────────────────────────────────────────┤
│ Domain         modele, UseCase (ScheduleGenerator,         │
│                RestStatsCalculator, OneRepMaxCalculator…)  │
├────────────────────────────────────────────────────────────┤
│ Data           Repositories ─┬─ Room (DAO + encje)         │
│                              ├─ DataStore (ustawienia)     │
│                              ├─ FileStorage (media)        │
│                              └─ SeedImporter / BackupCodec │
├────────────────────────────────────────────────────────────┤
│ Platform       ForegroundService (timer), AlarmManager,    │
│                Notifier, WorkManager, MediaResolver        │
└────────────────────────────────────────────────────────────┘
```

Zasady:
1. UI nigdy nie dotyka DAO. Zawsze przez Repository/UseCase.
2. Encje Room nie wychodzą poza warstwę `data` — mapowanie na modele domenowe.
3. Czas wstrzykiwany przez `TimeProvider` (testy timerów bez czekania).
4. Każdy ekran ma `UiState` (sealed/data class) + jednokierunkowy przepływ zdarzeń.

### 3.2 Moduły Gradle (na start wystarczą 3)

```
:app                  # kompozycja, nawigacja, DI, MainActivity
:core                 # baza, repozytoria, modele, utils, design system
:feature:session      # (opcjonalnie, gdy projekt urośnie) timer + logowanie serii
```

Na MVP rekomendacja: **jeden moduł `:app` z pakietami domenowymi**. Wielomodułowość
dopiero gdy czas budowania przekroczy ~40 s.

### 3.3 Struktura pakietów

```
com.gymtracker
├─ data
│  ├─ db (AppDatabase, Converters, daos/, entities/)
│  ├─ prefs (SettingsDataStore)
│  ├─ media (MediaStorage, MediaResolver)
│  ├─ search (ExerciseSearchQuery)          # budowa zapytania FTS z wpisu uzytkownika
│  ├─ seed (SeedImporter, SeedSource/AssetSeedSource, SeedModels)
│  └─ repo (ExerciseRepositoryImpl, PlanRepositoryImpl, SessionRepositoryImpl, BodyRepositoryImpl)
├─ domain
│  ├─ model (Exercise, Plan, PlanDay, ScheduledSession, Session, SetLog, RestInterval, MetricType, BodyEntry)
│  ├─ schedule (ScheduleGenerator, RecurrenceExpander)
│  └─ stats (RestStats, OneRepMax, VolumeStats)
├─ ui
│  ├─ theme, components
│  ├─ calendar, exercises, plans, session, rest, history, body, settings
├─ service (WorkoutTimerService, RestTimerService)
└─ di (DatabaseModule, RepositoryModule, TimeModule)
```

---

## 4. Model danych

Pełny, gotowy do wklejenia DDL: **`docs/SCHEMA.sql`**.

### 4.1 Diagram (uproszczony)

```
exercises 1─┬─* exercise_media
            ├─* plan_day_exercises *─1 plan_days *─1 plans
            ├─* session_exercises *─1 sessions
            └─* rest_intervals

plans 1─* plan_days 1─* plan_day_exercises
plans 1─* recurrences ──> scheduled_sessions (rozwiniete daty)
sessions 1─* session_exercises 1─* set_logs

metric_types 1─* body_entries          (karta postaci)
```

### 4.2 Decyzje projektowe (ważne)

1. **`external_id` = slug** (`barbell-bench-press`, `squat`).
   To stabilny klucz: pozwala re-importować seed bez duplikatów i rozpoznać
   ćwiczenie w planie nawet gdy zmieni się nazwa wyświetlana. Nazwy bywają
   niespójne (tabela: „Seated Overhead Press”, karta ćwiczenia: „Seated Barbell
   Shoulder Press”) — dlatego trzymamy też `name_in_program`.
2. **Serie na czas** (plank 60 s) → `target_reps_unit = 'seconds'`, a w
   `set_logs` mamy `duration_s` obok `reps`.
3. **Alternatywy w planie** („Pull Ups **lub** Inverted Rows”) → osobne wiersze
   `plan_day_exercises` z tym samym `alternative_group`; użytkownik przy starcie
   sesji wybiera jedną, reszta jest oznaczana jako pominięta.
4. **Reguła „co ile dni”** rozwijana do konkretnych dat (`scheduled_sessions`).
   Zaleta: kalendarz to jedno proste zapytanie po `date`, bez liczenia reguł przy
   każdym renderze. Wada: przy „bezterminowo co 3 dni” trzeba rozwinąć horyzont
   (np. +12 miesięcy) i dosypywać przy przewijaniu — patrz §6.1.
5. **Przerwy jako osobna tabela** (`rest_intervals`) — tylko ona daje sensowne
   „średnia przerwa dla ćwiczenia” (rozróżniamy przerwy dokończone od przerwanych).
6. **Media w plikach, nie w bazie.** W bazie ścieżki. Zdjęcie 5 MB w BLOB-ie
   zabiłoby kopie zapasowe i migracje.
7. **FTS4 z tokenizerem `unicode61`** — szukanie „obwod” znajdzie „obwód”,
   a „sztanga” — „sztangą”. Wybór FTS4, nie FTS5: Room nie ma adnotacji `@Fts5`,
   a `@Fts4(contentEntity = …)` sam zakłada triggery synchronizujące indeks
   i działa również w testach Robolectric. Zapytania budujemy jako prefiksowe
   (`sztan*`) i łączymy spójnikiem AND — patrz `ExerciseSearchQuery`.

### 4.3 Migracje

* `Room` z `exportSchema = true`, schematy w `app/schemas/`.
* Migracje pisane ręcznie + test `MigrationTestHelper` (żadnego `fallbackToDestructiveMigration` w produkcji).
* Test: migracja z v1 → vN na kopii bazy z danymi.

---

## 5. Ekrany i nawigacja

### 5.1 Mapa nawigacji (bottom bar: Kalendarz / Ćwiczenia / Historia / Postać / Ustawienia)

```
Kalendarz (start)
 ├─ Dziennik dnia ──> Sesja treningowa (live)
 │                      ├─ Timer przerwy (overlay/sheet)
 │                      └─ Szczegóły ćwiczenia w sesji
 ├─ Dodaj/Edytuj zaplanowany trening
 │   ├─ Wybór dni z kalendarza (multi-select)
 │   └─ Reguła cykliczna (data od / do / co ile dni)
Ćwiczenia
 ├─ Lista (szukaj, filtr po partii, ulubione)
 ├─ Dodaj własne ćwiczenie
 ├─ Szczegóły ćwiczenia (serie/powtórzenia, historia, kg, YouTube, media, komentarz)
 └─ Plany treningowe
      ├─ Lista planów (import z seed)
      ├─ Edycja dnia planu (drag&drop kolejności, superserie, alternatywy)
      └─ Podgląd planu / start z planu
Historia
 ├─ Lista treningów (filtry: plan, ćwiczenie, zakres dat)
 ├─ Szczegóły treningu (czas, przerwy, objętość, serie)
 └─ Statystyki ćwiczenia (postęp ciężaru, objętość, średnia przerwa)
Postać
 ├─ Wskaźniki (waga + custom)
 ├─ Dodaj pomiar
 └─ Wykres zmian (zakres 1M/3M/6M/1R/wszystko)
Ustawienia
 ├─ Jednostki (kg/lb), format czasu, pierwszy dzień tygodnia
 ├─ Dźwięk/wibracja timera, domyślna przerwa globalna
 ├─ Motyw (systemowy/jasny/ciemny), dynamiczne kolory
 ├─ Import/eksport danych (JSON/ZIP), backup
 ├─ Zarządzanie seedami (sprawdź wersję, re-import)
 └─ O aplikacji, licencje
```

### 5.2 Kluczowe ekrany — makiety opisowe

**Kalendarz (miesiąc)**
```
┌───────────────────────────────────────────┐
│  ‹   PAŹDZIERNIK 2026   ›        [Dziś]   │
│  Pn  Wt  Śr  Cz  Pt  So  Nd               │
│              1   2   3   4   5            │
│       ●A      ●B      ●C   ●A             │  ● = zrobiony (zielony)
│   6   7   8   9  10  11  12               │  ○ = zaplanowany (szary)
│   ●B  ○C  ○A  …                           │  ✕ = pominięty
└───────────────────────────────────────────┘
Legenda: ● zrobiony  ○ zaplanowany  ✕ pominięty  ⟳ z reguły cyklicznej
```

**Sesja treningowa (live)**
```
┌───────────────────────────────────────────┐
│ Trening A            ⏱ 01:12:45   [■ Stop]│  <- timer w nagłówku, zawsze widoczny
│ Przerwa: ⏳ 00:47  [−15s][+15s][Pomiń]     │  <- pasek przerwy po zalogowaniu serii
├───────────────────────────────────────────┤
│ 1. Squats                      5 × 5      │
│    ✓ 60 kg ×5   ✓ 80 kg ×5   ✓ 100 kg ×5  │
│    [ + Dodaj serię ]  ⏱ cel przerwy 2:00  │
│    Średnia przerwa: 1:54 (12 treningów)   │
│ 2. Bench Press                 5 × 5      │
└───────────────────────────────────────────┘
```

**Szczegóły ćwiczenia**
```
┌───────────────────────────────────────────┐
│ Barbell Bench Press            [♡][⋮]     │
│ Klatka piersiowa · Sztanga · Złożone      │
├───────────────────────────────────────────┤
│ [Wykres ciężaru 3M]                       │
│ Rekord: 100 kg × 5 (12.10.2026)           │
│ Średnia przerwa: 1:58 · Ostatnio: 90 kg   │
├─ Serie i powtórzenia (domyślne: 5 × 5)    │
├─ Media:  [▶ YouTube] [🖼 2] [🎬 1] [+ Dodaj]│
├─ Komentarz: ........................      │
└───────────────────────────────────────────┘
```

---

## 6. Kluczowe mechanizmy (najtrudniejsze fragmenty)

### 6.1 Kalendarz i generator harmonogramu

**Wejście użytkownika:** tryb „konkretne dni” (multi-select na kalendarzu) albo
„reguła cykliczna”: `data od`, `data do` (opcjonalna), `co ile dni`, opcjonalnie
dni tygodnia, opcjonalnie rotacja dni planu (A → B → C → A…).

**Algorytm rozwijania reguły:**

```kotlin
class RecurrenceExpander(private val clock: Clock) {

    fun expand(rule: Recurrence, horizonMonths: Int = 12): List<LocalDate> {
        require(rule.intervalDays >= 1) { "co ile dni >= 1" }
        val horizon = rule.endDate
            ?: clock.today().plus(horizonMonths, DateTimeUnit.MONTH)
        val dates = mutableListOf<LocalDate>()
        var date = rule.startDate
        while (date <= horizon && dates.size < MAX_ENTRIES) {          // zabezpieczenie
            if (date.allowedBy(rule)) dates += date                    // weekdays_mask + skip_dates
            date = date.plus(rule.intervalDays, DateTimeUnit.DAY)      // zawsze w dniach kalendarzowych
        }
        return dates
    }
}
```

**Reguły i przypadki brzegowe (wymagają testów!):**

| Przypadek | Zachowanie |
|---|---|
| `data do` < `data od` | walidacja formularza, blokada zapisu |
| `co ile dni = 0` / ujemne | walidacja: minimum 1 |
| Rotacja A→B→C przy 3 dniach planu | `plan_day_id` wybierany jako `planDays[(index) % planDays.size]` |
| Zmiana czasu (DST) | liczymy **dni kalendarzowe** (`LocalDate.plus(1, DAY)`), nie sekundy → brak przeskoków |
| Dni pominięte (`skip_dates`) | użytkownik może wykluczyć np. święta; wpis nie jest tworzony |
| Termin „bez końca” | rozwijamy horyzont (12 mies.) + dosypywanie przy przewijaniu kalendarza do przodu |
| Kolizja z istniejącym wpisem | `INSERT OR IGNORE` na unikalnym indeksie `(date, plan_day_id)` |
| Usunięcie planu | kaskada do `plan_days` i `plan_day_exercises`; już rozplanowane daty zostają (status „zaplanowany”, bez planu → „Wolny trening”) |
| Edycja reguły | przeliczamy: usuwamy wpisy `status='planned'` z `recurrence_id`, zostawiamy `done`/`skipped` (historia!) |

**Oznaczanie wykonania:** realizacja sesji (`sessions`) tworzy powiązanie
`scheduled_sessions.session_id` + `status='done'`. Trening „na dziko” (bez planu)
też jest dozwolony — tworzy sesję bez `scheduled_session_id`.

### 6.2 Timer całego treningu

Wymagania: widoczny w nagłówku, liczy czas, zapisuje start i koniec, wlicza przerwy,
jest odporny na ubicie procesu.

**Model:** nie tykamy licznika w UI. Trzymamy:
* `startedAt: Long` (wall clock, epoch) — **do wyświetlania i zapisu**,
* `baseElapsedRealtime: Long` (`SystemClock.elapsedRealtime()`) — **monotoniczny**,
* `pausedTotalMs`.

Wyświetlany czas = `if (running) (elapsedRealtime - baseElapsedRealtime) - pausedTotal else lastElapsed`.
Dzięki temu zmiana godziny w telefonie nie psuje pomiaru, a po restarcie procesu
czas odtwarzamy z `startedAt`.

**Implementacja:**
* `WorkoutTimerService` (`foregroundServiceType="specialUse"` / `dataSync`) z
  notyfikacją ongoing (akcje: „Pauza”, „Zakończ”, „Pokaż przerwę”). Service
  publikuje stan do `StateFlow` konsumowanego przez UI.
* `Session` zapisywana w bazie **od razu** przy starcie (`finished_at = NULL`).
  To jest też mechanizm odzyskiwania: apka po restarcie widzi sesję bez końca
  i proponuje „Kontynuuj / Zakończ”.
* Autozapis: `WorkManager` co 60 s aktualizuje `duration_s` (żeby po nagłej śmierci
  procesu dane nie zniknęły).
* Po zakończeniu: `finished_at`, `duration_s = finished - started`,
  `total_rest_s` (suma z `rest_intervals`), `active_s = duration_s - total_rest_s`.
* `WorkoutTimerService` używa `Handler`/`ticker` tylko do odświeżania UI raz na
  sekundę — stan nigdy nie zależy od tykania.

### 6.3 Timer przerwy

**Przepływ:** użytkownik loguje serię → startuje odliczanie przerwy (`rest_intervals`
row) → koniec: dźwięk + wibracja + notyfikacja → użytkownik loguje kolejną serię
albo „Pomiń”/„+15 s”.

* Cel przerwy brany z `plan_day_exercises.rest_seconds` → `exercises.default_rest_s`
  → ustawienie globalne (np. 90 s).
* **Uwzględniany w czasie treningu**: suma `actual_s` ląduje w `sessions.total_rest_s`,
  a ekran treningu pokazuje dwie liczby: *czas całkowity* i *czas pod sztangą*.
* Powiązanie z ćwiczeniem: `rest_intervals.exercise_id` (+ `set_log_id`).
* „+15 s” → `ended_reason='extended'`; przerwanie → `'cancelled'` (nie liczone do średniej).
* Ekran przerwy pokazuje na żywo: `Średnia przerwa: 1:54 (12 treningów)`,
  `Ostatnio: 2:05`, `Dziś: 3 × 1:50`.
* Alarm końca przerwy: `AlarmManager.setExactAndAllowWhileIdle` (potrzebne
  uprawnienie `SCHEDULE_EXACT_ALARM` — z fallbackiem na `set()` gdy odmówione)
  + notyfikacja full-screen-nie, wystarczy heads-up z dźwiękiem.

**Statystyki przerw (jedno zapytanie do bazy):**

```sql
SELECT COUNT(*)                                       AS samples,
       AVG(actual_s)                                  AS avg_s,
       MIN(actual_s)                                  AS min_s,
       MAX(actual_s)                                  AS max_s
FROM rest_intervals
WHERE exercise_id = :exerciseId
  AND ended_reason IN ('completed','extended')          -- pomijamy przerwane
  AND actual_s BETWEEN 10 AND 900                        -- odsiewamy artefakty
  AND started_at >= :from;
```
Mediana i percentyle liczone w Kotlinie z posortowanej listy (SQLite nie ma mediany).
Dodatkowo „średnia z ostatnich 5 treningów” — bardziej użyteczna niż średnia
z całej historii (odzwierciedla aktualną formę).

### 6.4 Historia i wykresy

Zakładka „Historia ćwiczenia” pokazuje:
1. **Postęp ciężaru** — max `weight_kg` na sesję (linia) + szacowany 1RM.
2. **Objętość** — `Σ(sets × reps × kg)` na sesję (słupek).
3. **Rekordy (PR)** — najcięższa seria, największa objętość, najlepszy 1RM.
4. **Historia przerw** — średnia na sesję.
5. **Tabela wykonania** — data | serie | ciężary | powtórzenia | przerwy.

Szacowany 1RM (Epley): `1RM = w × (1 + reps / 30)`; dla `reps > 12` oznaczamy
jako przybliżenie (nie wyświetlamy jako rekordu).

Zapytanie bazowe (uproszczone):
```sql
SELECT s.started_at AS day,
       MAX(l.weight_kg) AS top_set,
       SUM(l.weight_kg * l.reps) AS volume,
       SUM(l.reps) AS total_reps,
       COUNT(*) AS sets
FROM set_logs l
JOIN session_exercises se ON se.id = l.session_exercise_id
JOIN sessions s ON s.id = se.session_id
WHERE se.exercise_id = :exerciseId AND l.is_warmup = 0 AND s.finished_at IS NOT NULL
GROUP BY date(s.started_at / 1000, 'unixepoch', 'localtime')
ORDER BY day;
```

Wykresy: Vico — `LineCartesianLayer` (ciężar) + `ColumnCartesianLayer` (objętość),
zakresy 1M/3M/6M/1R/wszystko, zoom/pan, „empty state” gdy brak danych.

### 6.5 Załączniki: zdjęcia i filmy z galerii

1. Wybór: `ActivityResultContracts.PickVisualMedia` (Android 13+ bez uprawnień;
   na starszych SAF / `READ_EXTERNAL_STORAGE` z uzasadnieniem).
2. **Kopiowanie** do prywatnego katalogu: `filesDir/media/<exerciseId>/<uuid>.<ext>`
   — nie trzymamy `content://` (URI traci ważność po restarcie/czyszczeniu).
3. Ograniczenia i higiena: limit rozmiaru (np. 50 MB na film), kompresja zdjęć
   (`ImageDecoder` + skalowanie do 2048 px, JPEG q=85), dla filmów brak transkodowania
   (kopiujemy oryginał), `MediaMetadataRetriever` → `duration_ms`, miniatura przez
   `ThumbnailUtils.createVideoThumbnail`.
4. Odtwarzanie: Media3 ExoPlayer w dedykowanym ekranie/galerii; zdjęcia: Coil + zoom.
5. Sprzątanie: `WorkManager` usuwa pliki osierocone (bez wiersza w `exercise_media`).
6. Uprawnienia: `CAMERA` **nie jest potrzebne** (bierzemy z galerii); Notifications
   (+ `POST_NOTIFICATIONS` na 13+), `SCHEDULE_EXACT_ALARM` (dla przerwy), opcjonalnie
   `VIBRATE`.

### 6.6 YouTube

* Wejście: dowolny format linku (`youtu.be/ID`, `watch?v=ID`, `embed/ID`, `shorts/ID`).
* Normalizacja do `https://www.youtube.com/watch?v=ID` w `exercises.youtube_url`.
* Walidacja: ID = 11 znaków `[A-Za-z0-9_-]{11}`; inny link → traktowany jako zwykły link.
* Miniatura: `https://img.youtube.com/vi/ID/hqdefault.jpg` przez Coil (cache offline).
* Odtwarzanie: `Intent(ACTION_VIEW)` → aplikacja YouTube, a gdy jej nie ma —
  Custom Tab. (Jeśli kiedyś potrzebny odtwarzacz w apce: WebView + IFrame Player API.)
* Z seeda dostajemy `youtube_id` dla 16 z 23 ćwiczeń — pozostałe nie mają filmu.

### 6.7 Karta postaci

* `metric_types` startowo: Waga (kg), Obwód klatki, Obwód bicepsa, Obwód pasa,
  Obwód uda, Obwód łydki, % tkanki tłuszczowej, Szyja, Barki, Przedramię.
* Użytkownik dodaje własne: nazwa, jednostka, kierunek (`up_good`/`down_good`/`neutral`),
  opcjonalny cel.
* Pomiar: data + wartość + notatka; **unikalny indeks (typ, data)** → jeden pomiar
  dziennie, edycja zamiast duplikatu.
* Wykres: linia + opcjonalna linia celu; przełącznik kafelków (mini-wykres per wskaźnik).
* Bonus: `Δ` względem poprzedniego pomiaru, 30 dni i 90 dni + „streak” mierzenia.
* Archiwizacja typu (nie usuwamy danych historycznych).

### 6.8 Import danych startowych

* `data/seed/exercises.json` + `data/seed/programs/*.json` to **jedyna** kopia w repo.
  Zadanie Gradle `syncSeedAssets` kopiuje je do assetów na czas budowania
  (`app/build/generated/…/seed/`), więc apka czyta `assets/seed/…`, a drugiej
  kopii plików w repo nie ma.
* Format: `schema_version`, `generated_at`, `exercises[]` / `program`.
  Pełny kontrakt pól — §7.3.
* `SeedImporter` przy pierwszym uruchomieniu (i na żądanie w Ustawieniach):
  1. czyta JSON (`kotlinx.serialization`) przez `SeedSource` (`AssetSeedSource`),
  2. `INSERT OR IGNORE` po `external_id` (nigdy nie nadpisuje edycji użytkownika),
  3. zapisuje wersję danych w `app_meta` (`exercises_seed_version`),
  4. oddaje podsumowanie „dodano N, pominięto M”, które Ustawienia pokazują wprost.
* M1 importuje wyłącznie katalog ćwiczeń. Plan + dni + ćwiczenia dnia (z
  `alternative_group` dla „lub”) dochodzą w M2, razem z tabelą `seed_imports`.
* Re-import po aktualizacji seeda: dopisuje nowe ćwiczenia, **nie rusza** istniejących
  rekordów ani historii. Usunięte w źródle → `is_archived = 1` (M2).
* Dodatkowo w Ustawieniach: „wczytaj plik JSON z pamięci urządzenia”
  (ten sam format, walidacja + ręczne potwierdzenie) — M9.

### 6.9 Backup / eksport

* Eksport: ZIP zapisany przez SAF (`CreateDocument`) zawierający `gymtracker.json`
  (wszystkie tabele) + katalog `media/`.
* Import: walidacja `schemaVersion`, tryb „dopisz” (domyślnie) lub „zastąp”.
* Eksport CSV dla arkusza (ćwiczenia × serie) — opcjonalnie w V2.

---

## 7. Dane startowe

### 7.1 Co jest w `data/seed/`

| Plik | Zawartość |
|---|---|
| `data/seed/exercises.json` | 23 ćwiczenia: nazwa, `id` (slug), partia mięśniowa + mięśnie dodatkowe, sprzęt, typ, mechanika, poziom, `youtube_id`, `source_url`, `name_in_program` |
| `data/seed/programs/muscle-strength-full-body-workout-routine.json` | plan: metadane, `summary`, harmonogram (pon/śr/pt), 3 dni × (8, 8, 9) pozycji = 25, modyfikatory serii, alternatywy |

Statystyki:

```
ćwiczenia:              23  (16 z filmem YouTube)
partie mięśniowe:       11  (Abs, Biceps, Calves, Chest, Hamstrings, Lats,
                             Quads, Shoulders, Traps, Triceps, Upper Back)
sprzęt:                  6  (Barbell, Bodyweight, Cable, Dumbbell, EZ Bar, Machine)
dni treningowe:          3  (Workout A, B, C)
pozycje w planach:      25
wybory alternatywne:     1  („Pull Ups lub Inverted Rows”)
```

Katalog jest zamknięty — aplikacja niczego nie pobiera w czasie działania,
a wszystkie brakujące ćwiczenia użytkownik dodaje sam (§3.4).

### 7.2 Zawartość danych i prawa

Dane obejmują wyłącznie **fakty**: nazwy ćwiczeń, partie mięśniowe, sprzęt,
parametry serii i powtórzeń oraz odnośniki. Nie zawierają tekstów opisowych
ani instrukcji wykonania z żadnej publikacji; pole `source_url` przy ćwiczeniu
i planie służy jako odnośnik „zobacz źródło” w ekranie szczegółów.

Jeśli kiedyś dodamy opisy ćwiczeń, piszemy je od zera albo pozyskujemy na
własnej licencji — plan tego nie zakłada.

### 7.3 Kontrakt plików JSON

`exercises.json`

```jsonc
{
  "schema_version": 1,
  "generated_at": "2026-10-15",
  "source": { "site": "...", "program_url": "..." },
  "exercises": [
    {
      "id": "barbell-bench-press",        // = slug, klucz naturalny (external_id w Room)
      "name": "Barbell Bench Press",
      "source_url": "https://...",
      "muscle_group": "Chest",
      "muscle_group_slug": "chest",
      "secondary_muscles": ["Shoulders", "Triceps"],
      "equipment": "Barbell",
      "exercise_type": "Strength",
      "mechanics": "Compound",
      "force": "Push (Bilateral)",
      "level": "Intermediate",
      "youtube_id": "tuwHzzPdaGc",
      "name_in_program": "Bench Press"   // gdy nazwa w planie różni się od nagłówka karty
    }
  ]
}
```

`programs/<slug>.json`

```jsonc
{
  "schema_version": 1,
  "generated_at": "2026-10-15",
  "program": {
    "id": "muscle-strength-full-body-workout-routine",
    "name": "Muscle & Strength Full Body Workout Routine",
    "source_url": "...", "source_site": "...",
    "author": "Steve Shaw", "published": "2020-03-09", "updated": "2021-04-01",
    "categories": ["Workouts", "Muscle Building", "Increase Strength", "Full Body"],
    "content_policy": "facts-only",
    "summary": {
      "main_goal": "Build Muscle", "workout_type": "Full Body",
      "training_level": "Beginner", "program_duration": "12 weeks",
      "days_per_week": 3, "time_per_workout": "45-60 minutes",
      "equipment_required": ["Barbell", "Bodyweight", "Dumbbells"],
      "target_gender": "Male & Female",
      "recommended_supps": ["Multivitamin", "Pre-Workout", "Creatine", "Whey Protein", "Casein Protein"]
    },
    "schedule": [
      { "day_label": "Monday", "day_of_week": 0, "workout_id": "...-a" }
    ],
    "key_exercises": [ { "name": "Deadlift", "exercise_id": "deadlifts" } ],
    "workouts": [
      {
        "id": "...-a", "name": "Workout A", "order": 1,
        "exercises": [
          { "order": 1, "exercise_id": "squat", "name": "Squats",
            "sets": 5, "reps_min": 5, "reps_max": 5, "reps_raw": "5",
            "modifier": "Ramped",
            "alternatives": [ { "exercise_id": "…", "name": "…" } ],  // opcjonalne
            "choice_note": "Pull Ups lub Inverted Rows",               // opcjonalne
            "tracking": { "reps": "time", "unit": "seconds" }          // opcjonalne (plank)
          }
        ]
      }
    ]
  }
}
```

### 7.4 Mapowanie na bazę (Room)

| JSON | Tabela |
|---|---|
| `exercises[].id` | `exercises.external_id` |
| `exercises[].muscle_group`, `secondary_muscles`, `equipment` | odpowiednie kolumny (listy jako JSON TEXT) |
| `exercises[].youtube_id` | `exercises.youtube_id` |
| `program.id` | `plans.external_id` |
| `program.workouts[]` | `plan_days` (`position` = `order`) |
| `program.workouts[].exercises[]` | `plan_day_exercises` (`target_reps_unit` z `tracking`) |
| `exercises[].alternatives` | dodatkowe wiersze z tym samym `alternative_group` |
| `program.schedule[]` | podpowiedź do utworzenia `recurrences` (co 7 dni, `weekdays_mask` z dnia) |

### 7.5 Znane pułapki danych (obsłużone w seedzie)

| Problem | Przykład | Jak rozwiązane |
|---|---|---|
| Nazwa rozbita na dwa linki | „U” + „pright Row” | scalone w `name = "Upright Row"` |
| Wiersz z wyborem | „Pull Ups **lub** Inverted Rows” | pierwszy = ćwiczenie, reszta w `alternatives` (+ `choice_note`) |
| Serie na czas | plank `60 seconds` | `reps_min/max = 60`, `tracking.reps = "time"` |
| Modyfikator w nawiasie | „Squats (Ramped)” | `modifier = "Ramped"` |
| Ten sam dzień, dwa wiersze | Workout C: „Squats” ×2 (5×5 i 1×20) | dwie pozycje planu, druga `modifier = "Back-down set"` |
| Niespójne nazwy | „Seated Overhead Press” w planie ↔ „Seated Barbell Shoulder Press” na karcie | `name` + `name_in_program` + `external_id` jako klucz |
| Ćwiczenie tylko w tekście artykułu | „Military Press” | dodane do katalogu jako 23. ćwiczenie |
| Lista suplementów przez `<br>` | „Multivitamin<br>Creatine…” | w seedzie jako tablica `recommended_supps` |

---

## 8. Plan realizacji (kamienie milowe)

Założenie: 1 deweloper, praca pełnoetatowa, estymacje w dniach roboczych.

| ID | Kamień milowy | Zakres | Dni | Kryterium ukończenia |
|---|---|---|---|---|
| **M0** | Fundament | projekt Gradle, Compose, Hilt, Room + migracje, motyw PL/EN, CI (GitHub Actions), struktura pakietów | 3 | pusta apka buduje się i przechodzi testy w CI |
| **M1** | Ćwiczenia | CRUD, lista, szukanie (FTS + polskie znaki), filtr po partii, ulubione, import seeda 23 ćwiczeń, ekran szczegółów (bez historii) | 5 | mogę dodać własne ćwiczenie i znaleźć je w wyszukiwaniu |
| **M2** | Plany | plany, dni, kolejność (drag & drop), serie/powtórzenia/czas, superserie, alternatywy, start sesji z planu | 6 | mogę zbudować własną kombinację dni i rozpocząć z niej trening |
| **M3** | Kalendarz + harmonogram | widok miesiąca/tygodnia, oznaczanie zrobionych, multi-select dni, reguła (od/do/co ile dni), rotacja, pomijanie, edycja reguły | 6 | cykl „co 3 dni, od 1.10 do 31.12, rotacja A/B/C” generuje poprawne daty |
| **M4** | Sesja + timer treningu | ForegroundService, start/pauza/stop, odzyskiwanie po restarcie, logowanie serii, notatki | 8 | timer działa przy zgaszonym ekranie i po ubiciu apki |
| **M5** | Timer przerwy | cel przerwy per ćwiczenie, alarm, dźwięk/wibracja, pomiń/+15 s, wliczanie w czas treningu, średnia/historia | 4 | po sesji widzę średnią przerwę dla każdego ćwiczenia |
| **M6** | Historia i statystyki | lista treningów, filtry, szczegóły, wykres ciężaru/objętości, 1RM, PR | 5 | wykres pokazuje postęp z 3 miesięcy |
| **M7** | Media | Photo Picker, kopiowanie do `filesDir`, miniatury, ExoPlayer, link YouTube + miniatura, komentarze | 5 | zdjęcie i film z galerii są widoczne po restarcie urządzenia |
| **M8** | Karta postaci | typy wskaźników, pomiary, edycja, wykresy, delta, cel | 5 | dodaję „obwód bicepsa” i widzę wykres zmian |
| **M9** | Ustawienia, backup, szlify | jednostki, motyw, dźwięki, eksport/import ZIP, i18n, dostępność, R8, baseline profile, release + podpis | 6 | podpisany AAB gotowy do wysłania |
| | | **Razem** | **53** | |

Plus **20% bufora** (≈11 dni) na niespodzianki → realistycznie **~12 tygodni** do
wersji 1.0 w Google Play (testy zamknięte od M6, produkcja po M9).

**Kolejność ma znaczenie:** M3 (kalendarz) zależy od M2 (plany), M6 zależy od M4/M5.
M7 i M8 są niezależne — można je zrównoleglić albo przesunąć za release.

### 8.1 Stan realizacji

**M0 ✔ (fundament)** — w repo działa:

* szkielet Gradle `:app` (Kotlin + Compose + Hilt + Room + DataStore); wszystkie
  wersje w `gradle/libs.versions.toml`, wrapper Gradle 9.8.1,
* **AGP 9.4.1 z wbudowanym Kotlinem** — plugin `org.jetbrains.kotlin.android` nie
  jest już potrzebny ani dozwolony; realną wersję Kotlina (2.3.21) wnosi plugin
  Compose (`org.jetbrains.kotlin.plugin.compose`), a KSP 2.3.12 od wersji 2.3.0
  ma własne numerowanie, niezależne od Kotlina,
* `compileSdk`/`targetSdk` = 36, `minSdk` = 26. **Uwaga przy aktualizacjach:**
  wydania androidx z drugiej połowy 2026 (compose 1.12.x, core-ktx 1.19.x,
  lifecycle 2.11.x, navigation 2.10.x, hilt-navigation-compose 1.4.x) wymagają
  `compileSdk 37` (Android 17) — dlatego M0 stoi na ostatnich wersjach
  wspierających 36 (`minCompileSdk <= 36`), a podniesienie progu to osobny,
  świadomy krok (`compileSdk = 37` + `compileSdkMinor`),
* Room w wersji 1 z eksportem schematów do `app/schemas/` i zasadami migracji
  opisanymi w `DatabaseMigrations` (bez `fallbackToDestructiveMigration`),
* motyw Material 3 (dynamiczne kolory od Androida 12) sterowany ustawieniem
  z DataStore, teksty PL (domyślne) + EN,
* nawigacja Compose z dwoma ekranami (start, ustawienia) — kolejne dochodzą
  wraz z katalogiem ćwiczeń i planami,
* CI (GitHub Actions): `lintDebug`, `testDebugUnitTest`, `assembleDebug` na JDK 21
  (sandbox Robolectric dla SDK 36 wymaga Javy 21, choć AGP wystarcza 17),
* testy JVM: DAO Room (Robolectric), DataStore, model.

Świadome odroczenia z M0:

* `detekt`/`ktlint` w CI — najpierw stabilny build, narzędzia stylu dochodzą
  razem z M9 (szlify), gdy kod urośnie,
* `assembleRelease` w CI — podpis i R8 są zakresem M9,
* wersje bibliotek z PL/pytań zależności — aktualizacja wtedy, gdy dana
  biblioteka wchodzi do użytku (Vico w M6, Coil/Media3 w M7).

**M1 ✔ (katalog ćwiczeń)** — cały zakres kryterium działa na urządzeniu:
dodaję własne ćwiczenie i znajduję je w wyszukiwaniu.

* **Baza v2**: `exercises` (indeksy `idx_exercises_ext` UNIQUE i `idx_exercises_muscle`)
  oraz `exercises_fts` (FTS4 `unicode61`, `contentEntity`) — migracja 1→2 z testem
  `SchemaMigrationTest`; migracja przetestowana też na urządzeniu z danymi z M0,
* **Wyszukiwanie**: `ExerciseSearchQuery` zamienia wpis użytkownika na zapytanie
  prefiksowe (`sztan*`, spójnik AND), więc „sztanga”, „sztangą” i „sztan” znajdują
  to samo ćwiczenie. Sprawdzone również na bazie w telefonie (`sqlite3` + prawdziwe
  triggery Room, nie tylko w Robolectric),
* **Dane startowe**: 23 ćwiczenia wchodzą przy pierwszym starcie (`MainViewModel`)
  i przyciskiem „Wczytaj katalog startowy” w Ustawieniach. Import jest idempotentny
  (`INSERT OR IGNORE` po `external_id`) i **nigdy nie nadpisuje** zmian użytkownika —
  zmieniona nazwa i ulubione przetrwały ponowny import (na urządzeniu: „dodano 0,
  pominięto 23”),
* **UI**: katalog z szukaniem, filtrem partii, ulubionymi i „+”, szczegóły ćwiczenia
  (informacje, domyślne parametry, film/źródło, komentarz, edycja, usuwanie
  z potwierdzeniem), formularz dodawania/edycji, dolny pasek (Dziś / Ćwiczenia /
  Ustawienia). Teksty PL (domyślne) + EN; słownik katalogu tłumaczony w UI,
  nieznane wartości pokazywane bez zmian,
* linki: `youtu.be/ID`, `watch?v=ID`, `shorts/ID` normalizowane do
  `https://www.youtube.com/watch?v=ID`, każdy inny zapisywany dosłownie,
* **testy JVM: 43** (DAO i FTS z polskimi znakami, migracja 1→2, repozytorium,
  importer seeda, linki YouTube, DataStore, model).

Świadome odstępstwa i odroczenia z M1:

* **FTS4 zamiast FTS5** z §4.2 — Room nie ma adnotacji `@Fts5`, a `@Fts4`
  z `contentEntity` sam zakłada triggery i działa w Robolectric,
* kolumny `instructions` (M7) i tabeli `seed_imports` (M2) jeszcze nie ma;
  `docs/SCHEMA.sql` opisuje je jako przyszłe,
* historia wykonań w szczegółach ćwiczenia → M6, miniatury YouTube i media
  z galerii → M7,
* `hiltViewModel` z `androidx.hilt.navigation.compose` jest przestarzałe
  (tylko ostrzeżenie) — wymiana razem z podbiciem wersji Hilt.

---

## 9. Testy i jakość

| Poziom | Co testujemy | Narzędzia |
|---|---|---|
| Jednostkowe | `RecurrenceExpander` (DST, granice, rotacja), statystyki przerw (mediana, filtry), 1RM, walidacja linków YouTube, importer seedów | JUnit5 + `Clock` wstrzykiwany |
| Bazy | DAO, migracje, kaskady, unikalne indeksy, FTS | Room in-memory, `MigrationTestHelper` |
| Repozytoria | mapowanie encja↔model, `Flow` | Turbine |
| UI | krytyczne ścieżki: start treningu → zalogowanie serii → koniec; dodanie ćwiczenia; reguła cykliczna | Compose UI Test + Robolectric |
| Ręczne | timer przy zgaszonym ekranie 30 min, ubiciu apki, zmianie strefy czasowej, restarcie | lista scenariuszy w `docs/QA.md` |
| Statyczne | ktlint, detekt, Compose lint | CI na każdym PR |
| Wydajność | baseline profile, makrobenchmark startu, brak zapytań na głównym wątku | Macrobenchmark |

**Definicja ukończenia (DoD) dla zadania:** kod + testy + brak ostrzeżeń lintera +
dokumentacja w `docs/` jeśli zmienia model danych + migracja Room jeśli zmienia schemat.

---

## 10. Ryzyka

| # | Ryzyko | Wpływ | Mitygacja |
|---|---|---|---|
| R1 | Timer ubijany przez system (agresywne zarządzanie energią na Xiomi/Huawei) | wysoki | ForegroundService + odtwarzanie z `startedAt` + autozapis WorkManager + ekran „Poproś o wyłączenie optymalizacji baterii” |
| R2 | Utrata danych (reinstalacja, awaria telefonu) | wysoki | eksport ZIP od M9, przypomnienie o backupie, „auto-backup do Documents” co tydzień (SAF) |
| R3 | Złożoność reguł cyklicznych rośnie | średni | trzymamy prosty model (od/do/co ile dni + opcjonalna rotacja), rozwijamy do dat; `RRULE` dopiero jeśli naprawdę potrzebny |
| R4 | Duże filmy zapychają pamięć | średni | limit rozmiaru, licznik zajętości, kompresja zdjęć, sprzątanie osieroconych plików |
| R5 | Danych startowych nie da się „odświeżyć” automatycznie | niski | katalog jest wersjonowany w repo i importowany raz do `assets/`; użytkownik może w każdej chwili dodać własne ćwiczenia |
| R6 | Chęć na iOS w przyszłości | średni | jeśli to realne — Kotlin Multiplatform (logika + Room w `:shared`), UI natywne. Decyzję trzeba podjąć **przed M1**, bo później to kosztowny refaktor |
| R7 | Uprawnienie `SCHEDULE_EXACT_ALARM` odmówione | niski | fallback `set()` (z tolerancją kilku sekund) + informacja w Ustawieniach, że przerwa może się spóźnić |
| R8 | Użytkownik „przeskakuje” ćwiczenia w planie podczas treningu | niski | sesja jest kopią planu, można dodać/pominąć/zmienić kolejność bez wpływu na plan |

---

## 11. Roadmapa po 1.0

1. **v1.1** — przypomnienia o treningu, eksport CSV, wykresy: heatmapa aktywności (streak).
2. **v1.2** — superserie/giant sety z automatycznym timerem, RPE i autoregulacja ciężaru.
3. **v1.3** — Health Connect (waga, trening), widget na ekran główny, skróty.
4. **v1.4** — Wear OS: logowanie serii z zegarka.
5. **v2.0** — opcjonalna synchronizacja (self-hosted / E2E), udostępnianie planów plikiem JSON.
6. **Backlog** — TTS/liczenie powtórzeń, kalkulatory (talie, 1RM), katalog >1 planu startowego.

---

## 12. Załączniki

| Plik | Zawartość |
|---|---|
| `docs/SCHEMA.sql` | pełny DDL bazy (gotowy do wklejenia w Room) |
| `docs/QA.md` | scenariusze testów ręcznych (timer, przerwy, kalendarz, media, backup) |
| `data/seed/exercises.json` | 23 ćwiczenia: partie, sprzęt, mechanika, filmy, źródło |
| `data/seed/programs/muscle-strength-full-body-workout-routine.json` | plan + 3 dni treningowe |
| `README.md` | szybki start i struktura repo |
