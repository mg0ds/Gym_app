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
| Daty | `java.time` (`LocalDate`, `YearMonth`, `DayOfWeek`) | Dostępne od API 26 (nasze `minSdk`), zero dodatkowych zależności, działa w testach JVM i Robolectric. **Odstąpiliśmy od `kotlinx-datetime`** — uzasadnienie w §8.1. |
| Nawigacja | Navigation Compose (type-safe routes) | — |
| Wykresy | **Vico 3.2.3** (Compose-native, `compose-m3`) | Alternatywa: MPAndroidChart (View, owijka) — cięższy i starszy. Linia 3.3+ wymaga `compileSdk 37`, więc stoimy na 3.2.3 (decyzja i ryzyko: §4.2 pkt 35, §10 R9). |
| JSON | `kotlinx.serialization` | Import seedów, backup. |
| Obraz/wideo | Coil (miniatury), ExoPlayer/Media3 (odtwarzanie) | — |
| Galeria | `PickVisualMedia` (Photo Picker) | Od Android 13 bez uprawnień; fallback SAF. |
| YouTube | Intent / Custom Tabs + miniatura z `img.youtube.com` | **YouTube Android Player API jest przestarzałe i wycofywane** — nie używamy. |
| Timer | `ForegroundService` + `startedAt` i pauzy w bazie (epoch) | Licznik wstaje sam po ubiciu aplikacji; notyfikacja z chronometrem systemowym (M4). **Odstąpiliśmy od `elapsedRealtime`** — uzasadnienie w §6.2 i §8.1. |
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
│  ├─ timer (WorkoutTimerState)             # czysty model zegara treningu (M4)
│  └─ stats (RestStats, OneRepMax, VolumeStats)
├─ ui
│  ├─ theme, components
│  ├─ calendar, exercises, plans, session, rest, history, body, settings
├─ timer (WorkoutTimer, WorkoutTimerService)   # zegar + usluga pierwszoplanowa (M4)
└─ di (DatabaseModule, RepositoryModule, TimeModule, TimerModule)
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
   każdym renderze. Wada: trzeba rozwinąć horyzont i dosypywać go w miarę upływu
   dni — horyzont to **20 tygodni** (§6.1).
5. **Przerwy jako osobna tabela** (`rest_intervals`) — tylko ona daje sensowne
   „średnia przerwa dla ćwiczenia” (rozróżniamy przerwy dokończone od przerwanych).
6. **Media w plikach, nie w bazie.** W bazie ścieżki. Zdjęcie 5 MB w BLOB-ie
   zabiłoby kopie zapasowe i migracje.
7. **FTS4 z tokenizerem `unicode61`** — szukanie „obwod” znajdzie „obwód”,
   a „sztanga” — „sztangą”. Wybór FTS4, nie FTS5: Room nie ma adnotacji `@Fts5`,
   a `@Fts4(contentEntity = …)` sam zakłada triggery synchronizujące indeks
   i działa również w testach Robolectric. Zapytania budujemy jako prefiksowe
   (`sztan*`) i łączymy spójnikiem AND — patrz `ExerciseSearchQuery`.
8. **Alternatywa to osobny wiersz** `plan_day_exercises` z tym samym
   `alternative_group`, a `is_default_choice` wskazuje, które ćwiczenie wchodzi do
   treningu (dokładnie jedno na grupę). Klucz grupy to
   `"<id podstawowego>-or-<id alternatywy>"`. Alternatywy nie mają własnej
   kolejności — stoją zawsze pod swoim ćwiczeniem podstawowym, więc plan dnia ma
   jedną, spójną listę pozycji (M2).
9. **Superseria** to liczba w `superset_group` w obrębie dnia (`NULL` = ćwiczenie
   pojedyncze). Numerujemy je od 1 w górę, więc o kolejności decyduje `position`,
   a nie treść kolumny (M2).
10. **Sesja jest kopią, nie referencją.** `session_exercises` zapisuje cel
    i kolejność z chwili startu, więc późniejsza edycja planu nie przepisuje
    rozpoczętego ani zakończonego treningu, a usunięcie planu nie kasuje historii
    (`plan_id`/`plan_day_id` z `ON DELETE SET NULL`) (M2).
11. **`UNIQUE(plan_id, position)`** — przestawianie wierszy robimy w jednej
    transakcji dwoma przebiegami: najpierw wartości ujemne (tymczasowe), potem
    docelowe. Inaczej zapis „w miejscu” łamie unikalność w połowie drogi (M2).
12. **Wiersza ćwiczenia nie kasujemy, tylko archiwizujemy** (`is_archived = 1`).
    Kaskady z `plan_day_exercises` i `session_exercises` są potrzebne (usunięcie
    planu ma posprzątać po sobie), ale przy ćwiczeniu oznaczałyby utratę historii
    treningów — dlatego „usuń z katalogu” to zmiana flagi (M2, szczegóły §6.11).
13. **Wpis w kalendarzu to kopia, nie referencja.** `scheduled_sessions.title`
    zapisuje nazwę dnia z chwili planowania, więc kalendarz zostaje czytelny po
    zmianie nazwy dnia i po usunięciu planu. Usunięcie planu kasuje reguły
    (`CASCADE`), ale wpisy tylko tracą wskazanie (`SET NULL`) — historia i plan
    na najbliższe dni nie mogą zniknąć przez porządek w planach (M3).
14. **Rotacja wynika z `plan_day_id IS NULL`,** nie z osobnej kolumny: reguła
    bez wybranego dnia rotuje po dniach planu w kolejności `position`, a reguła
    z dniem zawsze trafia w ten dzień. Jedna informacja, jeden stan (M3).
15. **Statusy wpisu tylko `planned` / `done` / `skipped`.** Przeniesienie to
    zmiana `date` tego samego wiersza, a nie nowy wiersz ze statusem „moved” —
    inaczej „gdzie jest mój trening” trzeba by składać z dwóch rekordów (M3).
16. **`UNIQUE(date, plan_day_id)`, bez `WHERE`.** Room nie obsługuje częściowych
    indeksów, a SQLite traktuje `NULL` jako różne wartości — dzięki temu wpisy
    bez dnia planu (po jego usunięciu) nie kolidują ze sobą, a powtórzone
    planowanie jest idempotentne przez `INSERT OR IGNORE` (M3).
17. **Zalogowana seria to fakt, nie plan.** `set_logs` zapisuje to, co się
    wydarzyło („60 kg × 5”), a nie cel z planu — cel jest w `session_exercises`
    i oba mogą się różnić bez sprzeczności. Jednostka celu decyduje, co jest
    wartością serii: `reps` dla powtórzeń i dystansu, `duration_s` dla ćwiczeń
    na czas; ciężar jest opcjonalny (pompki, plank) (M4).
18. **Numeracja serii jest ciągła.** `set_index` + `UNIQUE(session_exercise_id,
    set_index)` pilnują, żeby dwa zapisy nie nadpisały się nawzajem, a usunięcie
    serii **przenumerowuje** resztę w jednej transakcji — użytkownik widzi
    „seria 1, 2, 3”, a nie dziury po skasowanych (M4).
19. **Pauza treningu jest zapisana, nie tylko trzymana w pamięci.**
    `sessions.paused_total_ms` to suma zamkniętych pauz, `paused_at_ms` to
    otwarta pauza. Gdyby pauza żyła tylko w procesie, zabicie aplikacji w trakcie
    pauzy doliczyłoby ją do czasu treningu. **Niezmiennik:** sesja zakończona
    lub przerwana nie ma otwartej pauzy (domykamy ją przy zamykaniu sesji) (M4).
20. **Zegar liczy się z zegara ściennego**, nie z `elapsedRealtime` (odstępstwo od
    pierwotnego §6.2): kotwica monotoniczna żyje tylko w procesie, a pauza jest
    zapisana w epochach — dwa wzory na ten sam czas rozjechałyby się przy
    pierwszej pauzie (M4, szczegóły §6.2).
21. **Usługa timera nie ma własnego stanu.** `WorkoutTimerService` pokazuje to, co
    widzi `WorkoutTimer` (`StateFlow`), a czas w notyfikacji odmierza systemowy
    chronometr. Brak tykania w usłudze = brak wybudzeń przy zgaszonym ekranie
    i brak możliwości rozjechania się ekranu z notyfikacją (M4).
22. **Historia jest zamknięta.** Serię dopiszemy i poprawimy tylko w **trwającym**
    treningu; zakończony (i przerwany) jest zapisem faktów, a nie brudnopisem (M4).
23. **`rest_intervals` jest źródłem prawdy o przerwach**, a `set_logs.rest_after_s`
    to tylko kopia wygodna dla wiersza serii (dopisuje ją domknięcie przerwy).
    Dzięki temu usunięcie serii nie zabiera statystyk ćwiczenia (FK `SET NULL`),
    a jedna przerwa może istnieć bez serii (np. przerwana pauzą treningu) (M5).
24. **Powód zakończenia decyduje, czy przerwa coś mówi o formie.** `completed`
    (odbyta) i `extended` (odbyta po „+15 s”) wchodzą do średniej, `skipped`
    („Pomiń”) i `cancelled` (pauza, koniec treningu, nowa przerwa) — nie.
    Słownika pilnuje `CHECK` w bazie, żeby literówka nie wypadła ze statystyk
    po cichu. Statystyki odsiewają też czasy spoza 10–900 s: 3 s to pomyłka
    w dotknięciu, pół godziny to telefon zostawiony na ławce (M5).
25. **Przerwę domyka ten, kto pierwszy: alarm, ekran albo kolejna seria.**
    Domknięcie jest idempotentne (`UPDATE … WHERE ended_at IS NULL`) i liczy
    faktyczny czas w jednym miejscu (`RestRepository`), więc kolejność zdarzeń
    nie ma znaczenia, a wyścig nie zrobi krzywdy (M5).
26. **Alarm końca przerwy idzie przez `AlarmManager`, nie przez `delay`.**
    `delay` liczy czas, który nie płynie, gdy telefon śpi, a budzik systemowy
    wstaje nawet z ubitego procesu. Gdy system pozwala na dokładne alarmy,
    używamy `setExactAndAllowWhileIdle`; gdy nie (a na Androidzie 12+ domyślnie
    nie pozwala), `setAndAllowWhileIdle`, czyli ten sam moment, ale z oknem,
    w którym system może go odłożyć — zmierzone na API 36: do ~22 s.
    Nie nagabujemy o zgodę `SCHEDULE_EXACT_ALARM`: aplikacja fitness nie ma
    prawa wymagać „Alarmów i przypomnień”, a budzik nie jest jedynym
    mechanizmem — przy żywym procesie przerwę domyka ekran (M5, szczegóły §6.3).
27. **Cel przerwy ma trzy poziomy:** `session_exercises.rest_seconds` (korekta na
    dzisiejszy trening) → `exercises.default_rest_s` (karta ćwiczenia) →
    ustawienie globalne z DataStore (domyślnie 90 s). Plan dnia nie ma własnego
    celu, bo przerwa jest cechą ćwiczenia, a nie dnia (M5).
28. **Przerwa liczy się do czasu treningu, ale pokazujemy też „pod sztangą”.**
    Czas treningu zostaje czasem treningu (M4), a obok pojawia się
    `elapsed − Σ przerw`; na ekranie, nie w bazie, bo to jedna linia arytmetyki,
    a nie dana, której ktoś miałby szukać w SQL-u (M5).
29. **Statystyki przerw liczymy w Kotlinie, nie w SQL-u.**
    `RestStatsCalculator` to czysta funkcja (mediana, ostatnie 5 treningów,
    filtry), a DAO zwraca tylko zamknięte przerwy z `LIMIT`. Dzięki temu logikę
    da się przetestować bez bazy, a zapytanie zostaje proste (M5).
30. **Jedna notyfikacja, dwa tryby.** Notyfikacja usługi pokazuje w trakcie
    przerwy odliczanie w dół (systemowy chronometr) z akcjami „+15 s” i „Pomiń”,
    a poza nią — czas treningu. Osobna notyfikacja pojawia się tylko raz:
    jako heads-up z dźwiękiem i wibracją, gdy przerwa się skończy (M5).
31. **Koniec przerwy dzwoni dlatego, że przerwa się skończyła, a nie dlatego,
    że zrobił to alarm.** Domknąć przerwę może alarm, ekran albo kolejna seria;
    dźwięk i wibracja idą z dwóch pierwszych dróg, a „Pomiń” i koniec treningu
    milczą (to nie są przerwy odbyte). Podwójnego sygnału nie ma, bo obie drogi
    domykają ten sam wiersz i anulują ten sam budzik — wygrywa ta pierwsza.
    Bez tej zasady telefon leżący w kieszeni milczałby: gdy proces żyje, ekran
    domyka przerwę szybciej niż budzik — i domykał ją bez słowa (M5).
32. **Rekordy liczymy z całej historii, a wykresy i tabelę z wybranego zakresu.**
    Rekord nie przestaje być rekordem dlatego, że patrzymy na ostatni miesiąc,
    a z drugiej strony wykres ma pokazywać to, co użytkownik wybrał. Rozdzielenie
    jest widoczne w UI: karta rekordów ma na dole sekcję „… w zakresie”, więc dwie
    liczby obok siebie mają jasne źródło (M6).
33. **Jednostką wykresu jest dzień, nie sesja.** Dwa treningi jednego dnia to
    jeden punkt (objętość i serie sumują się, ciężar to najcięższa seria dnia).
    Gdyby punktem była sesja, dzień z dwoma treningami rozjeżdżałby słupki,
    a liczba punktów na osi nie zgadzałaby się z liczbą dni treningowych (M6).
34. **Oś X to kolejne treningi, nie kalendarz.** Na wykresie postępu pytanie
    brzmi „czy rosnę z treningu na trening”; proporcjonalna oś ściskałaby punkty
    przy rzadkich treningach w kupkę przy jednej krawędzi. Data każdego punktu
    jest na osi i w tabeli wykonań (M6).
35. **Vico 3.2.3, nie nowsze.** 3.3+ podnosi `minCompileSdk` do 37, a cały
    projekt stoi na `compileSdk 36` (androidx wybrane świadomie, §2). W 3.2.3
    działa to, czego potrzebujemy: dwie linie na jednym wykresie, słupki, osie
    z własnym formatowaniem i zoom gestem; nie ma natomiast `rememberMarker`,
    dlatego nie ma markera pod palcem (M6).
36. **Agregaty per trening liczy SQL, a reguły — Kotlin.** Lista historii
    potrzebuje sumy serii i objętości dla każdego treningu; to proste sumy po
    `set_logs`, więc liczy je `GROUP BY` w `StatsDao`, a nie pętla w Kotlinie.
    Wszystko, co jest **regułą** (co wchodzi do średniej przerwy, jak liczy się
    `1RM`, jaki jest zakres dat, co pokazuje wiersz) zostaje w czystych
    kalkulatorach z testami bez bazy (M6).
37. **M6 nie zmienia schematu.** Historia czyta istniejące tabele; indeks na
    `sessions.started_at` nie jest potrzebny — zapytania mają `LIMIT`, a wierszy
    są setki, nie miliony. Migracja bez potrzeby to koszt (numer wersji, test
    przejścia, ryzyko przy aktualizacji) bez zysku (M6).
38. **Filtr historii działa w pamięci, a nie w zapytaniu.** Lista zakończonych
    treningów jest krótka (LIMIT-y w `StatsDao`), więc zmiana filtra jest
    natychmiastowa i nie odświeża bazy; filtry to zakres dat, ćwiczenie (lista
    brana z historii, nie z całego katalogu) i nazwa dnia planu (M6).

### 4.3 Migracje

* `Room` z `exportSchema = true`, schematy w `app/schemas/`.
* Migracje pisane ręcznie + test `MigrationTestHelper` (żadnego `fallbackToDestructiveMigration` w produkcji).
* Test: migracja z v1 → vN na kopii bazy z danymi.
* Stan: **v6** (v1 → fundament, v2 → katalog ćwiczeń + FTS4, v3 → plany, dni,
  ćwiczenia dnia i sesje, v4 → reguły harmonogramu i wpisy w kalendarzu,
  v5 → logowanie serii i pauza treningu, v6 → przerwy między seriami).
  `SchemaMigrationTest` przechodzi całą drogę 1 → 6, a migracje sprawdzamy także
  na urządzeniu z bazą z poprzedniego etapu (2 → 3: 24 ćwiczenia, w tym własne,
  zostały na miejscu; 3 → 4: plany, sesje i historia bez zmian; 4 → 5: treningi
  i kalendarz bez zmian, tabela serii pusta; 5 → 6: treningi, serie i kalendarz
  bez zmian, tabela przerw pusta).
* Migracje v4 dodają dwie tabele (`recurrences`, `scheduled_sessions`) i trzy
  indeksy; nie zmieniają istniejących kolumn, więc dane z M2 przechodzą bez
  przepisywania. `MigrationTestHelper` porównuje wynikowy schemat z plikiem
  `app/schemas/…/4.json`, więc rozjazd SQL-u wychwyci test, nie użytkownik.
* Migracje v5 dodają `set_logs` (+ `UNIQUE(session_exercise_id, set_index)`)
  i dwie kolumny w `sessions` (`paused_total_ms` z `DEFAULT 0`, `paused_at_ms`).
  `DEFAULT 0` nie jest ozdobnikiem: `ALTER TABLE` nie umie dodać kolumny
  `NOT NULL` bez wartości domyślnej dla istniejących wierszy, a stare sesje mają
  zero pauz i pustą listę serii — nic nie przeliczamy.
* Migracje v6 dodają jedną tabelę (`rest_intervals`) i trzy indeksy, wszystkie
  na kolumnach kluczy obcych. To nie ozdobnik: `rest_intervals` rośnie najszybciej
  z całej bazy (kilkanaście wierszy na trening), a statystyki pytają o przerwy
  jednego ćwiczenia i jednej sesji. Założenie indeksu od razu w migracji jest
  tańsze niż czekanie, aż zapytanie zwolni u użytkownika z roczną historią.
  Kolumna `ended_reason` ma `CHECK` ze słownikiem powodów — Room sam takiego
  ograniczenia nie generuje, ale `TableInfo` go ignoruje, więc walidacja
  schematu po migracji przechodzi.

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

**Co z tego istnieje po M6** (stan faktyczny, trasy z `Routes.kt`):

```
Kalendarz (home)                 ← dolny pasek
 ├─ karta „Kontynuuj trening” (gdy jest aktywna sesja) → session/{id}
 ├─ akcja „Dziś” w nagłówku (powrót do bieżącego miesiąca)
 └─ dotknięcie wpisu: start / podgląd → session/{id}
Ćwiczenia (exercises)            ← dolny pasek
 ├─ exercise/new, exercise/{id}, exercise/{id}/edit
 ├─ exercise/{id} → „Pełna historia i statystyki” → exercise/{id}/stats
 └─ akcja „plany” w nagłówku → plans
Plany (plans)                    ← bez dolnego paska
 └─ plan/{planId}
      ├─ sekcja „Harmonogram”:
      │    ├─ plan/{planId}/schedule?recurrenceId=0     (nowa reguła albo wybrane dni)
      │    └─ plan/{planId}/schedule?recurrenceId={id}  (zmiana istniejącej reguły)
      └─ planDay/{dayId}
           ├─ planDay/{dayId}/pickExercise                   (dodanie ćwiczenia)
           └─ planDay/{dayId}/pickExercise?anchorId={planExerciseId}  (alternatywa)
                    └─ (start) session/{sessionId}
Historia (history)               ← dolny pasek
 ├─ lista zakończonych treningów (zakres / ćwiczenie / nazwa dnia)
 │    └─ session/{id}            (podgląd do czytania, ten sam co z kalendarza)
 └─ exercise/{id}/stats          (wejście z karty ćwiczenia)
Ustawienia (settings)            ← dolny pasek
```

Dolny pasek pokazuje się tylko na trasach najwyższego poziomu
(`Routes.topLevel`: Kalendarz / Ćwiczenia / Historia / Ustawienia); ekrany
ćwiczeń, planów, statystyk, harmonogramu i treningu są „pełnoekranowe”, żeby nie
kusiło wyjście z formularza reguły albo z wykresu w połowie oglądania.
Argumenty opcjonalne trasy mają wartość `Routes.NO_ID` (`0`) — `recurrenceId = 0`
znaczy „nowa reguła”.

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

**Formularz harmonogramu (M3)**
```
┌───────────────────────────────────────────┐
│ ‹  Zaplanuj treningi            [Zapisz]  │
├───────────────────────────────────────────┤
│ [Reguła powtarzania] [Wybrane dni]        │
│ Dni planu:                                │
│  (Rotacja A → B → C)  (A)  (B)  (C)       │
│ Data od:    czw., 1 paź.                  │
│ Data do:    sob., 31 gru.          [✕]    │
│ Co ile dni: [3]                           │
│ Dni tygodnia: (pn.) (wt.) (śr.) (czw.) …  │
│ ┌ Podgląd ──────────────────────────────┐ │
│ │ 31 treningów                          │ │
│ │ czw., 1 paź.    Workout A             │ │
│ │ niedz., 4 paź.  Workout B             │ │
│ │ …                                     │ │
│ └───────────────────────────────────────┘ │
│ Planowanie tylko do 27.02.2027.           │
└───────────────────────────────────────────┘
```
Tryb „Wybrane dni” zamienia regułę na siatkę miesiąca z wielokrotnym wyborem
(poza oknem 20 tygodni dni są nieaktywne), a rotacja działa tak samo — daty
dostają kolejne dni planu w kolejności chronologicznej.

**Sesja treningowa (live, M4 + przerwy z M5)**
```
┌───────────────────────────────────────────┐
│ ‹  Trening A                              │
├───────────────────────────────────────────┤
│ Trening w toku               [ Pauza ]    │
│ 1:12:45                                   │  <- cały trening, w pauzie stoi
│ Pod sztangą 58:20 · przerwy 14:25         │  <- czas bez przerw (M5)
│ Start o 18:42                             │
├───────────────────────────────────────────┤
│ Przerwa · Squats                          │  <- pasek przerwy, gdy odlicza (M5)
│ 1:12                                      │
│ cel 2:00                                  │
│ [ -15 s ] [ +15 s ] [ Pomiń ]             │
├───────────────────────────────────────────┤
│ Notatka: dodaj notatkę                    │  <- notatka do treningu (M4)
│ 1. Squats                                 │
│    5 × 5 · superset 1                     │
│    [ przerwa 2:00 ]                       │  <- klik = zmień cel (M5)
│    1   60 kg × 5                          │  <- klik = popraw lub usuń
│    2   80 kg × 5  rozgrzewka · przerwa 1:40│
│    Przerwy: 3 · śr. 1:48                  │  <- z tego treningu (M5)
│    Średnia przerwa: 1:45 · ostatnio 2:05 (12 treningów)│  <- historia
│    [ + Dodaj serię ]             2 z 5    │
│ 2. Bench Press                 5 × 5      │
└───────────────────────────────────────────┘
```
Formularz serii startuje z **poprzedniej serii** tego ćwiczenia (albo z celu
z planu), więc powtarzanie tego samego ciężaru to dwa kliknięcia. Po zapisaniu
serii przerwa rusza sama — z celem wziętym z sesji, karty ćwiczenia albo
z ustawień. `rest_intervals` jest źródłem prawdy o przerwach, a „pod sztangą”
to `elapsed − Σ przerw` policzone na ekranie. Historia wykonań ćwiczenia
dochodzi w M6.

**Szczegóły ćwiczenia (M1 + historia z M6)**
```
┌───────────────────────────────────────────┐
│ Barbell Back Squat         [♡][✎][🗑]     │
│ Czworogłowe uda · Sztanga · Złożone       │
├─ Historia ────────────────────────────────┤
│ Najcięższa seria      70 kg × 5 · 17.10.2026│
│ Ostatnio              70 kg × 5 · 17.10.2026│
│ Przerwy     Średnia przerwa: 0:43 · ostatnio│
│                           0:30 (1 trening) │
│ ● Najcięższa seria  ● Szacowany 1RM        │
│ [wykres ciężaru 3M, zoom gestem]           │
│ [Pełna historia i statystyki]              │
├─ Informacje ──────────────────────────────┤
│ Partia mięśniowa · Mięśnie dodatkowe ·     │
│ Sprzęt · Charakter · Poziom · Nazwa w planie│
├─ Domyślne parametry ──────────────────────┤
├─ Film i źródło ───────────────────────────┤
├─ Komentarz ───────────────────────────────┤
└───────────────────────────────────────────┘
```

**Historia (M6)**
```
┌───────────────────────────────────────────┐
│ Historia                                  │
├───────────────────────────────────────────┤
│ [1M] [3M] [6M] [1R] [Wszystko]            │  <- zakres (chip)
│ (Wszystkie ćwiczenia) (Barbell Back Squat)…│  <- filtr po ćwiczeniu
│ (Wszystkie treningi) (Workout A) (Dzień A) │  <- filtr po dniu planu
├───────────────────────────────────────────┤
│ Workout A                                 │
│ 17.10.2026 • 17:55                        │
│ 9 serii • 3 150 kg • śr. przerwa 0:43      │
├───────────────────────────────────────────┤
│ Dzień A                                   │
│ 16.10.2026 • 0:48                         │
│ 3 serie • 900 kg                          │
└───────────────────────────────────────────┘
```
Nazwa dnia, data, czas bez pauz, liczba serii roboczych, objętość i średnia
przerwa; stuknięcie otwiera ten sam ekran sesji co z kalendarza (bez edycji).
Filtry, które nie mają czego zawężać (jedna nazwa, jedno ćwiczenie), się nie
pokazują, a brak wyników mówi wprost, że to filtry — nie brak treningów.

**Statystyki ćwiczenia (M6)**
```
┌───────────────────────────────────────────┐
│ ‹  Barbell Back Squat                     │
├───────────────────────────────────────────┤
│ [1M] [3M] [6M] [1R] [Wszystko]            │
├─ Rekordy i podsumowanie ──────────────────┤
│ Najcięższa seria     70 kg × 5 • 17.10.2026│
│ Najlepszy 1RM                          82 kg│
│ Największa objętość   3 450 kg • 17.10.2026│
│ Przerwy      Średnia przerwa: 0:43 · ostatnio│
│                           0:30 (1 trening) │
│ ─────────────────────────────────────────  │
│ Treningi w zakresie                        3│
│ Serie w zakresie                          16│
│ Objętość w zakresie                  5 100 kg│
├─ Postęp ciężaru ──────────────────────────┤
│ ● Najcięższa seria   ● Szacowany 1RM       │
│ [linie: 50 → 60 → 70 kg]                   │
├─ Objętość treningu ───────────────────────┤
│ [słupki: 750 / 900 / 3 450 kg]             │
├─ Wykonania ───────────────────────────────┤
│ Data      Serie  Ciężar  Powt.  Przerwa   │
│ 17.10.2026   10  70 kg      50     0:43    │
│ 16.10.2026    3  60 kg      15        —    │
│ 1.10.2026     3  50 kg      15        —    │
└───────────────────────────────────────────┘
```

---

## 6. Kluczowe mechanizmy (najtrudniejsze fragmenty)

### 6.1 Kalendarz i generator harmonogramu

**Wejście użytkownika:** tryb „wybrane dni” (multi-select na siatce miesiąca)
albo „reguła powtarzania”: `data od`, `data do` (opcjonalna), `co ile dni`,
opcjonalnie dni tygodnia, opcjonalnie rotacja dni planu (A → B → C → A…).

**Horyzont planowania: 20 tygodni w przód.** Data od i data do muszą mieścić się
w oknie `[dziś, dziś + 20 tygodni]` — bez planowania bezterminowego i bez
planowania w przeszłość. Daty wybieramy z `DatePicker` z zawężonym zakresem
(`SelectableDates`), a `RecurrenceExpander.validate` pilnuje tego samego po
stronie domeny. Horyzont przesuwa się razem z „dziś”, więc przy wejściu
w kalendarz `refreshHorizon()` **dosypuje wpisy tylko w przód** (od `MAX(date)`
danej reguły do nowego horyzontu) — nie odtwarza tego, co użytkownik przeniósł
albo pominął.

**Algorytm rozwijania reguły** (czysta funkcja z jawnym „dziś” — testuje się bez
bazy i bez zegara; daty w `java.time.LocalDate`, więc dzień nie ma godziny
i zmiana czasu go nie przesuwa):

```kotlin
object RecurrenceExpander {

    fun horizon(today: LocalDate, endDate: LocalDate?): LocalDate {
        val limit = ScheduleLimits.horizonEnd(today)      // dziś + 20 tygodni
        return if (endDate == null || endDate.isAfter(limit)) limit else endDate
    }

    fun dates(draft: RecurrenceDraft, today: LocalDate): List<LocalDate> {
        val end = horizon(today, draft.endDate)
        val step = draft.intervalDays.coerceAtLeast(1)
        val first = maxOf(draft.startDate, today)          // nie planujemy w przeszłość
        if (first.isAfter(end)) return emptyList()
        val result = mutableListOf<LocalDate>()
        var date = first
        var offset = ChronoUnit.DAYS.between(draft.startDate, first)  // rytm od pierwotnego startu
        while (!date.isAfter(end)) {
            val onInterval = offset % step == 0L
            val allowedDay = draft.weekdays.isEmpty() || date.dayOfWeek in draft.weekdays
            if (onInterval && allowedDay) result += date
            date = date.plusDays(1)
            offset++
        }
        return result
    }
}
```

Przypisanie dat do dni robi `assignDays(dates, days, startIndex)`: pierwsza data
dostaje pierwszy dzień, druga drugi, po wyczerpaniu listy wracamy do początku.
Jednoelementowa lista = zawsze ten sam dzień; `startIndex` pozwala kontynuować
rotację po dosypaniu horyzontu.

**Reguły i przypadki brzegowe (pokryte testami):**

| Przypadek | Zachowanie |
|---|---|
| `data do` < `data od` | walidacja formularza, blokada zapisu (`END_BEFORE_START`) |
| `data od` / `data do` poza horyzontem | walidacja: `START_BEYOND_HORIZON` / `END_BEYOND_HORIZON` |
| `co ile dni = 0` / ujemne | walidacja: minimum 1 (`INTERVAL_TOO_SMALL`) |
| `data do` puste | horyzont = `dziś + 20 tygodni` |
| `data od` w przeszłości (edycja starej reguły) | nie błąd: daty sprzed „dziś” są pomijane, a rytm „co N dni” liczy się dalej od pierwotnego startu |
| Rotacja A→B→C przy 3 dniach planu | `planDayId = planDays[(startIndex + index) % planDays.size]` |
| Wybrany konkretny dzień | lista dni zwija się do jednego, więc rotacja zawsze trafia w ten dzień |
| Zmiana czasu (DST) | liczymy dni kalendarzowe (`LocalDate.plusDays`), nie sekundy → brak przeskoków i dubli (test na 29.03.2026) |
| Brak dni w planie | walidacja `NO_PLAN_DAY`, nie ma czego rozplanować |
| Kolizja z istniejącym wpisem | `INSERT OR IGNORE` na unikalnym indeksie `(date, plan_day_id)` — istniejący wpis (np. odbyty) wygrywa |
| Ten sam formularz zapisany dwa razy | `findIdentical` znajduje regułę o tych samych parametrach i **prelicza ją**, zamiast dokładać drugą |
| Edycja reguły | usuwamy wpisy `status='planned'`, zmieniamy regułę; `done`/`skipped` zostają jako historia (`recurrence_id → NULL`) |
| Usunięcie reguły | to samo, bez tworzenia nowej reguły |
| Usunięcie planu | kaskada: reguły znikają, wpisy zostają bez wskazania (`SET NULL`) i da się je nadal rozpocząć („wolny trening” z nazwą dnia) |

**Statusy wpisu:** `planned` (zaplanowany), `done` (odbyty, ręcznie albo po
zakończeniu sesji), `skipped` (pominięty). Nie ma osobnego statusu
„przeniesiony” — przeniesienie to zmiana `date` tego samego wiersza.

**Oznaczanie wykonania:** start treningu z wpisu wiąże go z sesją
(`scheduled_sessions.session_id`), a **zakończenie** treningu odhacza wpis jako
`done` — w tej samej transakcji co zamknięcie sesji, żeby nie dało się zakończyć
treningu i nie odhaczyć dnia. Przerwany trening wpisu nie odhacza. Trening bez
planu też jest dozwolony (sesja bez ćwiczeń, z nazwą wpisu).


### 6.2 Timer całego treningu

Wymagania: widoczny w nagłówku, liczy czas, zapisuje start i koniec, wlicza
przerwy między seriami (M5), jest odporny na zgaszenie ekranu i ubicie procesu.

**Model (M4).** Zegar to trzy liczby, które leżą w `sessions` — i to jest cała
pamięć timera:

* `started_at` — wall clock (epoch); **wyświetlanie i zapis**,
* `paused_total_ms` — suma zamkniętych pauz,
* `paused_at_ms` — początek otwartej pauzy (`NULL`, gdy zegar idzie).

Czas treningu `= (paused_at_ms ?: teraz) - started_at - paused_total_ms`,
nigdy poniżej zera. Cała arytmetyka siedzi w jednej czystej klasie
(`WorkoutTimerState`, testy w §9), bo ten sam wzór liczy się w trzech miejscach:
na ekranie, w notyfikacji i — po restarcie procesu — z tego, co jest w bazie.

Czas liczymy z **zegara ściennego**, a nie z `SystemClock.elapsedRealtime()`,
jak zakładał pierwotny plan (odstępstwo w §8.1). Powód jest praktyczny: kotwica
monotoniczna żyje tylko w procesie, więc po restarcie i tak trzeba wrócić do
`started_at`, a **pauza jest zapisana w epochach** — dwa różne wzory na ten sam
czas rozjechałyby się przy pierwszej pauzie. Cena jest jedna i znana: ręczna
zmiana godziny w telefonie przesuwa licznik (o tyle samo myli się zapisany
`started_at`).

**Implementacja (M4):**

* `WorkoutTimer` (`@Singleton`) — jedno źródło prawdy dla ekranu i usługi.
  Trzyma `TrackedWorkout` (id sesji, nazwa, `WorkoutTimerState`) w `StateFlow`,
  a **pauzę zapisuje do bazy od razu po jej rozpoczęciu**, nie dopiero przy końcu
  treningu — inaczej zabicie aplikacji w pauzie doliczyłoby ją do czasu treningu,
* `WorkoutTimerService` — usługa pierwszoplanowa
  (`foregroundServiceType="specialUse"`) z notyfikacją ongoing (akcje „Pauza” /
  „Wznów”, „Zakończ”; kliknięcie otwiera ekran treningu). Usługa **nie ma
  własnego stanu i nie tyka**: pokazuje to, co widzi w `WorkoutTimer`, a czas
  w notyfikacji odmierza systemowy chronometr (w pauzie zamrożony tekst).
  Zgaszony ekran nie kosztuje więc ani jednego wybudzenia, a ekran nie może się
  rozjechać z notyfikacją. Akcje z notyfikacji działają też po ubiciu aplikacji:
  usługa odtwarza wtedy zegar z bazy (`restoreIfNeeded`),
* `WorkoutTimer.attach(lifecycleScope)` w `MainActivity` — jedno miejsce, które
  pilnuje aktywnej sesji: nowy trening, trening odtworzony po ubiciu aplikacji
  i powrót z tła obsługuje ten sam strumień z bazy. Ekrany nie muszą o niczym
  pamiętać, więc nie da się „zapomnieć” o włączeniu timera,
* sesja jest w bazie **od razu** przy starcie (`finished_at = NULL`), a seria
  i notatka zapisują się na bieżąco — dlatego odzyskiwanie nie potrzebuje
  żadnego autozapisu: po ubiciu procesu zegar wstaje z `started_at` i `paused_*`,
  a karta „Kontynuuj trening” czeka w kalendarzu,
* **`duration_s`/`total_rest_s`/`active_s` nie są cache'owane.** Po zakończeniu
  czas to `finished_at - started_at - paused_total_ms`, suma przerw liczy się
  z `rest_intervals` (M5), a „czas pod sztangą” = czas treningu − suma przerw —
  na ekranie. Trzy kolumny, które mogą się rozjechać z faktami, byłyby
  proszeniem się o kłamstwo w historii treningów,
* zakończenie treningu **domyka otwartą pauzę** i czyści `paused_at_ms`
  (niezmiennik z §4.2, punkt 19), a zakończenie z wpisu w kalendarzu odhacza ten
  wpis (§6.1) w tej samej transakcji.

### 6.3 Timer przerwy

Wymagania: cel przerwy per ćwiczenie, alarm, dźwięk/wibracja, „Pomiń”/„+15 s”,
wliczanie w czas treningu, średnia i historia.

**Przepływ:** użytkownik loguje serię → startuje przerwa (`rest_intervals`) →
koniec: dźwięk + wibracja + heads-up → użytkownik loguje kolejną serię, „Pomiń”
albo „+15 s”.

**Model (M5).** Przerwa to **wiersz w `rest_intervals`**, nie pole w serii
(§4.2, punkt 23). Wiersz zawiera wszystko, co trzeba, żeby odtworzyć odliczanie
po restarcie procesu:

* `started_at` — wall clock (epoch); ten sam wybór co przy treningu (§4.2, punkt 20),
* `planned_s` — cel **po korektach**; koniec przerwy jest liczony
  (`started_at + planned_s`), więc „+15 s” nie potrzebuje własnego pola,
* `exercise_id` (+ `session_id` i `set_log_id`) — powiązanie z ćwiczeniem i serią,
* `ended_at`, `actual_s`, `ended_reason` — dopisywane przy domknięciu.

Arytmetyka końca przerwy siedzi w czystej klasie `ActiveRest` (`endsAtEpochMs`,
`remainingMs`, `isOver`), bo liczy ją ekran, notyfikacja i powrót po restarcie.

**Cel przerwy** ma trzy poziomy (§4.2, punkt 27): korekta w sesji → domyślna
z karty ćwiczenia → ustawienie globalne (domyślnie 90 s). Zero i wartości ujemne
znaczą „bez przerwy”, więc efektywny cel nigdy nie jest pusty.

**Kto kończy przerwę.** Każdy, kto pierwszy — i to jest cała odporność mechanizmu:

| Zdarzenie | Powód | Liczy się do średniej | Dzwoni |
|---|---|---|---|
| alarm (proces ubity albo śpiący) | `completed` / `extended` | tak | tak |
| ekran zobaczył zero na liczniku | `completed` / `extended` | tak | tak |
| kolejna seria zalogowana | `completed` / `extended` | tak | nie — użytkownik właśnie wrócił do pracy |
| „Pomiń” | `skipped` | nie | nie |
| pauza, koniec lub przerwanie treningu, nowa przerwa | `cancelled` | nie | nie |

Domknięcie jest **idempotentne** (`UPDATE … WHERE ended_at IS NULL`) i w jednej
transakcji dopisuje `rest_after_s`/`rest_target_s` do wiersza serii — ale tylko dla
przerw, które coś mówią o formie (te pola są kopią wygodną dla UI, nie źródłem
prawdy). Druga próba nic nie zmienia, więc kolejność zdarzeń nie ma znaczenia.

**Dzwoni ten, kto kończy — o ile przerwa się skończyła.** Alarm i ekran wołają
`RestNotifier`, „Pomiń” i przerwanie treningu milczą (§4.2, punkt 31). Podwójnego
sygnału nie ma z dwóch powodów: obie drogi domykają ten sam wiersz (druga
zobaczy `ended_at`), a obie anulują ten sam budzik. Gdyby alarm był jedynym
dzwoniącym, telefon leżący w kieszeni milczałby — bo ekran wygrywa z budzikiem
o ułamki sekundy (patrz niżej).

**Alarm.** `AlarmManager.setExactAndAllowWhileIdle`, gdy system pozwala na
dokładne alarmy, a `setAndAllowWhileIdle`, gdy nie. Uprawnienie
`SCHEDULE_EXACT_ALARM` jest w manifeście, ale **nie nagabujemy o nie**: aplikacja
fitness nie ma prawa wymagać „Alarmów i przypomnień”, a ekran i tak odlicza na
żywo. Budzik jest jeden — każda zmiana celu („+15 s”, „-15 s”, odtworzenie po
restarcie) przepisuje ten sam `PendingIntent`. `RestAlarmReceiver` wstaje nawet
po ubiciu procesu, domyka przerwę i pokazuje heads-up na osobnym kanale
o wysokiej ważności (dźwięk + wibracja).

**Ile to trwa w praktyce (zmierzone na API 36).** Zależy od tego, czy proces
żyje — i dlatego budzik nie jest jedynym mechanizmem:

| Sytuacja | Kto domyka | Ile po zerze |
|---|---|---|
| aplikacja żyje (także ze zgaszonym ekranem) | ekran (tyknięcie licznika) | ~0,4 s |
| proces ubity, bez zgody na dokładne alarmy | budzik systemowy, okno do ~22 s | raz 18,7 s |
| proces ubity, ze zgodą na dokładne alarmy | budzik `setExactAndAllowWhileIdle` | co do sekundy |

Do tego dochodzi przypadek „proces wstał już po czasie”: przerwa jest wtedy
domykana jako `completed`, ale **bez dzwonienia** — dzwonek po fakcie jest gorszy
niż milczenie. Po odtworzeniu przerwy powód jest zawsze `completed`, bo „+15 s”
nie jest zapisane w wierszu; oba powody liczą się do średniej tak samo, więc
różnica jest kosmetyczna.

**Notyfikacja.** Ta sama usługa i ta sama notyfikacja co przy treningu (§6.2):
w trakcie przerwy pokazuje odliczanie **w dół** (systemowy chronometr,
`setChronometerCountDown(true)`) z akcjami „+15 s” i „Pomiń”, a poza przerwą —
czas treningu. Jedna notyfikacja, jeden kanał i nadal zero tykania w procesie.

**Wliczanie w trening.** Suma `actual_s` wszystkich przerw to „przerwy”,
a „pod sztangą” = czas treningu − przerwy. Liczone na ekranie z listy przerw
(bieżąca przerwa liczy się do teraz), bez kolumn cache w `sessions`
(§4.2, punkt 28). Powód: kolumny cache rozjeżdżają się z faktami przy pierwszej
poprawce serii, a różnica to jedna linia arytmetyki.

**Statystyki przerw.** DAO zwraca zamknięte przerwy wskazanych ćwiczeń z `LIMIT`,
a filtry i agregaty liczy czysty `RestStatsCalculator` (§4.2, punkt 29):

* do próby wchodzą tylko `completed`/`extended` i czasy 10–900 s — 3 s to
  przypadkowe dotknięcie, pół godziny to telefon zostawiony na ławce,
* `samples`, `sessions` (liczba różnych treningów), średnia, mediana, min, max,
* `last` (najnowsza przerwa) oraz średnia z **ostatnich 5 treningów** — policzona
  już teraz, ale pokaże ją dopiero ekran statystyk (M6): druga średnia na karcie
  ćwiczenia byłaby zagadką, a nie informacją.

Karta ćwiczenia pokazuje natomiast średnią z całej historii, bo tę użytkownik
zestawia z liczbą treningów: „Przerwy: 3 • śr. 1:48” (ten trening)
i „Średnia przerwa: 1:45 • ostatnio 2:05 (12 treningów)” (historia ćwiczenia),
plus „przerwa 1:40” w wierszu serii, której dotyczyła.

### 6.4 Historia i wykresy

Dwa ekrany, jedna arytmetyka:

* **Historia** (zakładka w dolnym pasku) — lista zakończonych treningów,
  najnowsze pierwsze: nazwa dnia, data, czas bez pauz, liczba serii roboczych,
  objętość i średnia przerwa. Filtry: zakres (1M/3M/6M/1R/wszystko), ćwiczenie
  i nazwa dnia planu. Stuknięcie otwiera ten sam ekran sesji co z kalendarza,
  tylko w trybie do czytania.
* **Statystyki ćwiczenia** (`exercise/{id}/stats`, wejście z karty ćwiczenia) —
  rekordy z całej historii plus wykresy i tabela w wybranym zakresie.

Karta ćwiczenia dostaje skrót historii: rekord, ostatnie wykonanie, średnią
przerwę i mini wykres 3M (ten sam komponent co na pełnym ekranie) oraz wejście
„Pełna historia i statystyki”. Bez zapisanych serii karta mówi wprost, że
historii nie ma, zamiast pokazywać pusty wykres.

**Jednostką wykresu jest dzień, nie sesja** (§4.2 pkt 33). Dwa treningi jednego
dnia to jeden punkt: objętość i serie się sumują, ciężar bierze najcięższą serię
dnia.

**Rekordy z całej historii, wykresy i tabela z zakresu** (§4.2 pkt 32). W UI
widać to wprost: karta rekordów ma na dole sekcję „… w zakresie”.

**Oś X to kolejne treningi, nie kalendarz** (§4.2 pkt 34) — data punktu jest na
osi („17.10”) i w tabeli wykonań.

**1RM (Epley):** `1RM = w × (1 + reps / 30)`. Powyżej 12 powtórzeń liczba
dostaje w UI „~” i przypis, ale nadal jest pokazywana — to jedyna informacja
o postępie w seriach wysokopowtórzeniowych.

Ćwiczenia na czas (`weight_kg = null`) liczą się jako trening, ale nie mają
punktów ciężaru: karta „Postęp ciężaru” i „Objętość treningu” pokazują wtedy
wyjaśnienie, a nie płaską linię na zerze.

Serie rozgrzewkowe odpadają wszędzie (`is_warmup = 0`). Zapytania (uproszczone;
pełne w `StatsDao`):

```sql
-- punkty wykresu: surowe serie jednego ćwiczenia z całej historii
SELECT l.set_index, l.weight_kg, l.reps, l.is_warmup,
       s.id AS session_id, s.started_at AS session_started_at
FROM set_logs l
JOIN session_exercises se ON se.id = l.session_exercise_id
JOIN sessions s ON s.id = se.session_id
WHERE se.exercise_id = :exerciseId AND s.status = 'finished'
ORDER BY s.started_at DESC, l.set_index
LIMIT :limit;

-- jedna linia na trening: serie robocze, objętość, najcięższa seria
SELECT se.session_id,
       SUM(CASE WHEN l.is_warmup = 0 THEN 1 ELSE 0 END) AS working_sets,
       SUM(CASE WHEN l.is_warmup = 0
                THEN COALESCE(l.weight_kg, 0.0) * COALESCE(l.reps, 0)
                ELSE 0.0 END) AS volume_kg,
       MAX(CASE WHEN l.is_warmup = 0 THEN l.weight_kg END) AS best_weight_kg
FROM set_logs l
JOIN session_exercises se ON se.id = l.session_exercise_id
JOIN sessions s ON s.id = se.session_id
WHERE s.status = 'finished'
GROUP BY se.session_id;
```

Podział pracy jest celowy (§4.2 pkt 36): SQL robi to, co umie tanio (złączenia,
sumy per trening, filtr statusu), a **reguły** — co jest serią roboczą, jak liczy
się 1RM, co wchodzi do średniej przerwy, jaki jest zakres dat, co pokazuje wiersz
historii — liczy Kotlin (`ExerciseStatsCalculator`, `SessionSummaryCalculator`,
`RestStatsCalculator`). Reguły mają testy bez bazy, a zapytanie nie wymaga testu
po każdej zmianie progu (10–900 s, 12 powtórzeń).

Zapytania są ograniczone `LIMIT`-em (`StatsLimits`: 3 000 serii jednego ćwiczenia,
1 000 przerw), a wiersze brane są **od najnowszych** — przy kilku latach treningu
interesuje nas postęp, nie archiwum.

**Wykresy:** Vico 3.2.3 — `LineCartesianLayer` (najcięższa seria dnia
i szacowany 1RM), `ColumnCartesianLayer` (objętość dnia), osie z własnym
formatowaniem (data „17.10”, liczby z separatorem tysięcy: „3 500”), zoom
i przewijanie z domyślnego `rememberDefaultVicoZoomState` w `CartesianChartHost`.
Markera pod palcem nie ma — 3.2.3 nie ma go w module Compose (§4.2 pkt 35).

**Tabela wykonania:** data | serie | najcięższa | powtórzenia | przerwa, od
najnowszych („co robiłem ostatnio” to najczęstsze pytanie do tabeli).


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
  3. zapisuje wersję danych w `app_meta` (`exercises_seed_version`,
     `programs_seed_version`),
  4. oddaje podsumowanie „dodano N, pominięto M”, które Ustawienia pokazują wprost.
* **M1** importuje katalog ćwiczeń, **M2** — plan startowy (`programs/*.json`) razem
  z dniami, ćwiczeniami dnia, alternatywami i harmonogramem. Import planów jest
  osobnym krokiem (`importProgramsIfNeeded`), a nie częścią importu ćwiczeń, więc
  zmiana seeda ćwiczeń nie wymusza ponownego wczytania planu i odwrotnie. Kolejność
  jest jednak istotna: plan odwołuje się do ćwiczeń po `external_id`, więc najpierw
  importujemy katalog, a dopiero potem plan (w jednym przebiegu
  `importStartDataIfNeeded`).
* **Świadome odstępstwo: tabeli `seed_imports` (z §4.1) nie ma.** Wersje obu seedów
  trzymamy w `app_meta`. Tabela miała sens, gdy planowane były niezależne źródła
  importu z własnym cyklem życia i stanem „częściowo zaimportowany”; dziś mamy
  jedno źródło (assety) i dwa klucz-wersje, więc dodatkowa tabela byłaby drugim
  mechanizmem opisującym to samo — a drugi mechanizm to drugie miejsce, w którym
  stan może się rozjechać. Wracamy do tematu w M9, gdy dojdzie import pliku
  z pamięci urządzenia i trzeba będzie pamiętać również pliki odrzucone.
* Re-import po aktualizacji seeda: dopisuje nowe ćwiczenia i nowe plany, **nie
  rusza** istniejących rekordów, planów użytkownika ani historii. `INSERT OR IGNORE`
  dotyczy wyłącznie wierszy z `external_id` — plany tworzone ręcznie mają go `NULL`,
  więc są nietykalne.
* **Archiwizacja to droga usunięcia, nie re-importu** (§6.11). Ćwiczenie usunięte
  z katalogu zostaje w bazie z `is_archived = 1`, więc wiersz dalej zajmuje
  `external_id` i import go **nie przywróci** — to była decyzja użytkownika.
  Odwrotnie, pozycje usunięte z samego pliku źródłowego nie są oznaczane: przy
  jednym źródle wersjonowanym w repo nie ma czego „wygaszać”, a dokładanie stanu,
  którego nikt nie czyta, to koszt bez zysku. Wracamy do tego razem z importem
  plików z urządzenia (M9).
* Dodatkowo w Ustawieniach: „wczytaj plik JSON z pamięci urządzenia”
  (ten sam format, walidacja + ręczne potwierdzenie) — M9.

### 6.9 Backup / eksport

* Eksport: ZIP zapisany przez SAF (`CreateDocument`) zawierający `gymtracker.json`
  (wszystkie tabele) + katalog `media/`.
* Import: walidacja `schemaVersion`, tryb „dopisz” (domyślnie) lub „zastąp”.
* Eksport CSV dla arkusza (ćwiczenia × serie) — opcjonalnie w V2.

### 6.10 Edytor planu: kolejność, superserie, alternatywy (M2)

**Kolejność (drag & drop).** Jedna reużywalna kompozycja
`ui/components/ReorderableColumn` obsługuje i dni planu, i ćwiczenia dnia.
To zwykła `Column` z `verticalScroll` (nie `LazyColumn`): listy są krótkie
(3 dni, 8–9 ćwiczeń), a proste `Column` daje przewidywalne liczenie pozycji —
wiersz przesuwa się o „jedną pozycję”, gdy przeciągnięcie przekroczy połowę
wysokości **przeciąganego** wiersza (wysokości mierzymy, bo wiersz z chipsami
jest wyższy).

Nową kolejność oddajemy **raz, po upuszczeniu** — dzięki temu w trakcie
przeciągania baza nie dostaje serii zapisów, a nieudany gest nic nie zmienia.
Repozytorium przyjmuje całą nową listę identyfikatorów
(`reorderDays(planId, orderedDayIds)`, `reorderExercises(dayId, orderedIds)`):
jeśli lista nie zgadza się co do zbioru z tym, co jest w bazie, **nie zmieniamy
nic** (żadnego zapisu połówkowego). Zapis idzie jednym `withTransaction`
w dwóch przebiegach (wartości ujemne, potem docelowe) — patrz §4.2 pkt 11.

W menu przepełnienia wiersza są dodatkowo „przenieś wyżej” / „przenieś niżej”.
To nie duplikat: działa bez gestu (dostępność, TalkBack, emulator bez dotyku)
i jest jedyną drogą, gdy drag jest niewygodny przy długiej liście.

**Alternatywy.** „Pull Ups **lub** Inverted Rows” to dwa wiersze z tym samym
`alternative_group`; pierwszy ma `is_default_choice = 1`. UI grupuje je przez
`List<PlanExercise>.toGroups()` i pokazuje pod ćwiczeniem podstawowym jako
„4a **albo** …”. Przeciąganie przestawia **tylko** ćwiczenia podstawowe, a każda
alternatywa idzie zaraz za swoim (nie da się rozdzielić pary przez przypadek).
Alternatywa, która zostanie sama (bo usunięto jej ćwiczenie podstawowe), traci
grupę i staje się zwykłą pozycją.

**Superserie.** `linkSuperset` nadaje `superset_group = max + 1` w obrębie dnia,
`unlinkSuperset` czyści grupę, gdy zostaje w niej jeden samotny członek (żeby nie
została „superseria jednoosobowa”). Na ekranie widać chip „superseria N”.

**Start treningu.** „Rozpocznij” tworzy `sessions` + `session_exercises` jako
**kopię** dnia (patrz §4.2 pkt 10) i przechodzi do ekranu treningu: lista celów,
czas liczony od `started_at` (ticker w kompozycji, nie w bazie), „Zakończ trening”
i „Przerwij trening”. Do sesji trafiają tylko **wybory domyślne** z grup
alternatyw — trening jest konkretny, a nie „jeden z dwóch”. Jednocześnie może być
aktywny tylko jeden trening: nowy start zamyka poprzedni jako `cancelled`.

Ekran treningu w M2 celowo **nie loguje serii** — to zakres M4 (wtedy dochodzi
`ForegroundService`, licznik przerw i wpisy w `set_logs`). M2 dowozi to, co było
kryterium: mogę zbudować własną kombinację dni i **rozpocząć z niej** trening.

### 6.11 Usuwanie ćwiczenia = archiwizacja (M2)

„Usuń” w karcie ćwiczenia **nie kasuje wiersza** — ustawia `is_archived = 1`.
Powód jest konkretny: `plan_day_exercises.exercise_id` i `session_exercises.exercise_id`
mają `ON DELETE CASCADE`, więc skasowanie wiersza zabrałoby ze sobą pozycje w planach
i **wpisy w historii treningów**. Trening, którego nie da się już odtworzyć, to nie
„porządek w katalogu”.

Co to znaczy w praktyce:

* `is_archived = 0` jest warunkiem w każdym zapytaniu katalogu (`observeExercises`,
  partie mięśniowe, licznik, `observeById`/`getById`) — zarchiwizowanego ćwiczenia
  nie ma na liście, w wyszukiwaniu FTS ani pod jego adresem, więc nie da się go
  otworzyć ani edytować (a `updateExercise` jest wtedy pustym przebiegiem),
* plany i treningi **nadal** pokazują tę pozycję z nazwą (wiersze JOIN zostają),
  więc stary trening czyta się tak samo,
* ponowny import seeda go nie przywróci: wiersz dalej zajmuje `external_id`,
  a `INSERT OR IGNORE` nie nadpisuje zmian użytkownika,
* import planu używa `getActiveByExternalId`, więc plan nie dostanie wiersza
  wskazującego zarchiwizowane ćwiczenie — brak trafia do `missingExercises`,
* potwierdzenie usunięcia pokazuje, ile pozycji zostaje („Zostanie w planach:
  4 pozycje.”, „Zostanie w historii treningów: 1 wpis.”) — to spokojna
  informacja, nie straszenie.

Czego jeszcze nie ma: przeglądania i przywracania z archiwum oraz trwałego
„opróżnij archiwum” (`ExerciseDao.deleteById` czeka na ten ekran). To M9 — razem
z importem plików, bo dopiero wtedy pojawia się pytanie „co leży w archiwum i dlaczego”.

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
| `program.name`, `author`, `source_url`, `summary.*` | kolumny `plans` (`description` z `summary.main_goal`/`workout_type`) |
| `program.workouts[]` | `plan_days` (`position` = `order`, `name` = `workout.name`) |
| `program.schedule[].day_of_week` | `plan_days.day_of_week` (0 = poniedziałek, `NULL` gdy dnia nie ma w harmonogramie) |
| `program.workouts[].exercises[]` | `plan_day_exercises` (`position` = kolejność w dniu) |
| `exercises[].sets`, `reps_min`, `reps_max` | `target_sets`, `target_reps_min`, `target_reps_max` |
| `exercises[].tracking.reps == "time"` | `target_reps_unit = 'seconds'` (z `tracking.unit`), inaczej `'reps'` |
| `exercises[].modifier` | `note` — **tylko** dla wyboru domyślnego (modyfikator opisuje serię, którą faktycznie wykonujemy) |
| `exercises[].alternatives` | dodatkowe wiersze z tym samym `alternative_group` (`is_default_choice = 0`) |
| `exercises[].choice_note` | nie zapisywane — napis „X lub Y” wynika wprost z grupy alternatyw |

Dwie rzeczy, które warto powiedzieć wprost:

* **`workouts[].exercises[].name` nie jest zapisywane.** Wiersz planu pokazuje
  nazwę z **katalogu**, żeby „Bench Press” z programu i „Barbell Bench Press”
  na karcie ćwiczenia nie były dwiema różnymi rzeczami pod tym samym
  `exercise_id` (patrz pułapka z nazwami w §7.5). Nazwa z planu jest tylko kluczem
  użytym przy imporcie; ćwiczenie rozpoznajemy po `exercise_id`.
* **`program.schedule[]` nie tworzy jeszcze reguł cyklicznych.** W M2 zapisujemy
  `day_of_week` przy dniu planu — to podpowiedź dla kalendarza i kolumna do
  sortowania dni. Prawdziwe `recurrences`/`scheduled_sessions` powstają w M3,
  gdy użytkownik będzie mógł je edytować; wtedy `schedule` posłuży jako wartość
  początkową formularza, a nie jako gotowa reguła.

### 7.5 Znane pułapki danych (obsłużone w seedzie)

| Problem | Przykład | Jak rozwiązane |
|---|---|---|
| Nazwa rozbita na dwa linki | „U” + „pright Row” | scalone w `name = "Upright Row"` |
| Wiersz z wyborem | „Pull Ups **lub** Inverted Rows” | pierwszy = ćwiczenie, reszta w `alternatives` (+ `choice_note`) |
| Serie na czas | plank `60 seconds` | `reps_min/max = 60`, `tracking.reps = "time"` |
| Modyfikator w nawiasie | „Squats (Ramped)” | `modifier = "Ramped"` |
| Ten sam dzień, dwa wiersze | Workout C: „Squats” ×2 (3×5 i 1×20) | dwie pozycje planu; pierwsza ma `modifier = "Ramped"`, druga **nie ma modyfikatora** (`null`) — `sets`/`reps` wystarczają, żeby je rozróżnić |
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
* kolumny `instructions` (M7) i tabeli `seed_imports` jeszcze nie ma;
  `docs/SCHEMA.sql` opisuje je jako przyszłe, a wersje seedów trzymamy
  w `app_meta` (§6.8),
* historia wykonań w szczegółach ćwiczenia → M6, miniatury YouTube i media
  z galerii → M7,
* `hiltViewModel` z `androidx.hilt.navigation.compose` jest przestarzałe
  (tylko ostrzeżenie) — wymiana razem z podbiciem wersji Hilt.

**M2 ✔ (plany)** — cały zakres kryterium działa na urządzeniu: buduję własną
kombinację dni i **rozpoczynam z niej trening**.

* **Baza v3**: `plans`, `plan_days`, `plan_day_exercises`, `sessions`,
  `session_exercises` (z `UNIQUE(plan_id, position)` i `CHECK` na status sesji);
  migracja 2 → 3 z testem `SchemaMigrationTest` — sprawdzona też na urządzeniu
  z bazą z M1 (własne ćwiczenie i ulubione zostały na miejscu),
* **Domena i repozytoria**: `PlanRepository` (drafty planu/dnia/pozycji, kolejność,
  superserie, alternatywy, `PlanDetails`), `SessionRepository` (obserwacja aktywnej
  sesji, start z dnia planu, zakończenie, przerwanie), mapowanie w `PlanMappers`,
* **Import planu startowego**: `programs/*.json` → 1 plan, 3 dni (A/B/C),
  25 pozycji + 1 alternatywa (26 wierszy), plank jako `3 × 60 s` (`seconds`),
  dzień tygodnia z `schedule` (pon/śr/pt), wersja w `app_meta`
  (`programs_seed_version`), drugi przycisk w Ustawieniach,
* **UI**: lista planów (etykieta autora i „z danych startowych”), szczegóły planu
  (dni z dniem tygodnia i liczbą ćwiczeń, „Rozpocznij”), ekran dnia (cele
  w formie `5 × 5` / `2 × 8-12` / `3 × 60 s` z przerwą, alternatywy jako
  „4a **albo** …”, etykiety „wybór domyślny” i „superseria N”), formularze
  planu/dnia/celu, wybór ćwiczenia z katalogu (szukanie + filtr partii),
  menu wiersza (edytuj cel, dodaj alternatywę, superseria, wyżej/niżej, usuń),
* **Kolejność**: przeciąganie (przytrzymanie + ruch, zapis **raz po upuszczeniu**)
  oraz „przenieś wyżej/niżej” w menu jako droga bez gestu. Cała nowa kolejność
  idzie do bazy jednym wywołaniem; gdy lista identyfikatorów nie zgadza się ze
  stanem bazy, nie zmieniamy nic,
* **Trening**: sesja = kopia dnia (tylko wybory domyślne alternatyw), jeden aktywny
  trening naraz (nowy start zamyka poprzedni jako `cancelled`), karta „Kontynuuj
  trening” na Dziś, ekran treningu z licznikiem od `started_at` oraz „Zakończ
  trening” / „Przerwij trening” z potwierdzeniem,
* **Usuwanie ćwiczenia archiwizuje, nie kasuje** (§6.11): pozycje w planach
  i historia treningów zostają czytelne, a ćwiczenie znika z katalogu,
  wyszukiwania i spod swojego adresu. Potwierdzenie mówi, ile pozycji zostaje
  („Zostanie w planach: 4 pozycje.”, „Zostanie w historii treningów: 1 wpis.”);
  liczniki obserwujemy na żywo, więc po sprzątnięciu planu zdanie
  znika samo. Przeglądanie archiwum i przywracanie → M9,
* **Testy JVM: 74** (doszły: repozytoria planów i sesji na prawdziwym Room,
  import planu z assetów, grupowanie alternatyw, przestawianie wierszy,
  liczniki użycia ćwiczenia, archiwizacja nie ruszająca planów i historii,
  formatowanie czasu). `:app:lintDebug`
  i `:app:assembleDebug` bez błędów.

Świadome odstępstwa i odroczenia z M2:

* **`seed_imports` odroczone do M9**, wersje seedów w `app_meta` — uzasadnienie
  w §6.8,
* brak archiwum w UI: ćwiczenie zniknięte z katalogu zostaje w bazie
  (`is_archived = 1`), ale nie ma jeszcze ekranu, żeby je obejrzeć i przywrócić;
  pozycje usunięte z samego pliku źródłowego nie są oznaczane (§6.8, §6.11),
* **logowanie serii, licznik przerw i timer w tle → M4**. Ekran treningu w M2
  pokazuje cele i mierzy czas, ale nie zapisuje serii; `RestTimerService`
  i `set_logs` są zakresem M4/M5 — kryterium M2 (rozpoczęcie treningu z własnej
  kombinacji dni) jest nim objęte w całości,
* `plan_days.day_of_week` to na razie etykieta i podpowiedź do sortowania, a nie
  reguła powtarzania; prawdziwy harmonogram i kalendarz → M3,
* źródło planu (`source_url`) i media → M7 (nie mamy jeszcze ekranu, na którym
  miałyby się pokazać).

**M3 ✔ (kalendarz + harmonogram)** — kryterium działa: cykl „co 3 dni, od 1.10 do
31.12, rotacja A/B/C” generuje poprawne daty (31 wpisów, ostatni 30.12.2026),
widać je w podglądzie przed zapisem i w kalendarzu.

* **Baza v4**: `recurrences` (reguła: `start_date`, `end_date`, `interval_days`,
  `weekdays_mask`, `plan_id`, `plan_day_id`) i `scheduled_sessions` (wpis:
  `date`, `status`, `title`, `plan_id`, `plan_day_id`, `recurrence_id`,
  `session_id`) + unikalny indeks `(date, plan_day_id)`; migracja 3 → 4 z testem
  `SchemaMigrationTest` (cała droga 1 → 4),
* **Horyzont 20 tygodni** (§6.1): formularz zawęża wybór dat
  (`SelectableDates`), a domena powtarza walidację (`ScheduleError`). Bez
  planowania bezterminowego, ale z dosypywaniem w przód, gdy horyzont przesuwa
  się z upływem dni (`refreshHorizon()` przy wejściu w kalendarz),
* **`RecurrenceExpander`** — czysta funkcja z jawnym „dziś”: `horizon`, `dates`,
  `assignDays`, `validate`. Liczy dni kalendarzowe (`java.time.LocalDate`), więc
  zmiana czasu nie gubi ani nie dubluje wpisu; daty sprzed „dziś” pomija (rytm
  „co N dni” liczy dalej od pierwotnego startu), a rotacja wraca do początku po
  wyczerpaniu dni i umie kontynuować od `startIndex` po dosypaniu horyzontu,
* **Kalendarz**: siatka miesiąca z nawigacją i akcją „Dziś”, kropki statusu na
  dniach z wpisami (do trzech, resztę zwija „+n”), legenda, podgląd wybranego
  dnia z listą wpisów i menu („rozpocznij”, „przenieś” = zmiana daty tego samego
  wiersza, „oznacz jako odbyty” / „pominięty”, „przywróć do zaplanowanych”,
  „usuń wpis”). Ekran „Dziś” z M2 **stał się kalendarzem**, a karta
  „Kontynuuj trening” została na górze,
* **Formularz harmonogramu** (trasa `plan/{planId}/schedule`): dwa tryby —
  reguła (data od, data do, co ile dni, dni tygodnia) albo wybrane dni
  (multi-select na siatce miesiąca); oba z wyborem konkretnego dnia planu albo
  rotacją i z **podglądem wygenerowanych dat** (z nazwą dnia) przed zapisem,
* **Start z wpisu**: `startFromSchedule` tworzy sesję z dnia planu (albo bez
  ćwiczeń, gdy wpis stracił plan) i wiąże wpis z sesją; **zakończenie** treningu
  odhacza wpis jako `done` w tej samej transakcji, a przerwanie zostawia
  `planned`,
* **Harmonogram pod planem**: sekcja „Harmonogram” w szczegółach planu — lista
  reguł z opisem („co 3 dni • od 1.10.2026 do 31.12.2026 • rotacja dni • pn., śr., pt.”),
  zmiana i usunięcie z potwierdzeniem. Usunięcie reguły kasuje wpisy `planned`,
  a `done`/`skipped` zostają jako historia (`recurrence_id → NULL`); powtórny
  zapis identycznej reguły przelicza istniejącą, zamiast tworzyć drugą,
* **Teksty PL (domyślne) + EN**; daty i dni tygodnia z zasobów `Locale`
  (`CalendarFormat`), więc interfejs po angielsku też pokazuje „Mon, 1 Oct”,
* **testy JVM: 112** (doszły: `RecurrenceExpanderTest` — kryterium M3, dni
  tygodnia, rotacja, DST, walidacja, pomijanie przeszłości;
  `CalendarRepositoryTest` — reguły, idempotencja, dosypywanie horyzontu,
  przenoszenie i statusy wpisów, edycja i usuwanie reguły, start z wpisu,
  `planDates`; migracja 1 → 4; `CalendarFormatTest` — siatka miesiąca
  i konwersje dat z `DatePicker`-a). `:app:lintDebug` i `:app:assembleDebug`
  bez błędów.

Świadome odstępstwa i odroczenia z M3:

* **`java.time` zamiast `kotlinx-datetime`** (§2) — `minSdk 26` ma już
  `LocalDate`, `YearMonth` i `DayOfWeek`, więc nie dokładamy zależności,
  a `YearMonth` daje siatkę miesiąca bez ręcznego liczenia dni. Daty w bazie to
  ISO `YYYY-MM-DD`, więc wymiana biblioteki nie zmienia schematu,
* **`skip_dates` nie ma** — zamiast wykluczać dni w regule, użytkownik pomija
  wpis w kalendarzu (`status = 'skipped'`). Dosypywanie w przód nie wskrzesza
  wpisów pominiętych ani przeniesionych (§6.1),
* **`is_active` na regule nie ma** — reguła żyje od `start_date` do `end_date`,
  a „zawieszenie” to zmiana `end_date` albo usunięcie reguły; wpisy, które się
  odbyły, zostają jako historia,
* **`moved` jako status nie istnieje** — przeniesienie to zmiana `date` tego
  samego wiersza (§4.2, punkt 15),
* **brak widoku tygodnia** — zakres mówi „miesiąc/tydzień”; miesiąc pokrywa
  kryterium, a tydzień (przegląd jak w dniu planu) został świadomie przełożony:
  M6 zrobił historię i statystyki, więc widok tygodnia czeka w §11 (v1.1),
* **brak przypomnień i wpisu w powiadomieniach** — wpisy kalendarza to na razie
  tylko dane i UI; powiadomienia o treningu → M9,
* **kalendarz nie ma „+”** — nowe reguły zakłada się z planu („Zaplanuj
  treningi” w szczegółach planu), bo reguła bez planu nie ma czego planować.
  Planowanie z poziomu kalendarza można dodać później bez zmiany modelu,
* **odhaczenie wpisu tylko przy starcie z kalendarza** — trening rozpoczęty
  z ekranu planu („Rozpocznij” w dniu) nie jest wiązany z wpisem w kalendarzu,
  więc wpis zostaje „zaplanowany” i użytkownik odhacza go ręcznie. Dopasowanie
  po samym dniu planu i dacie mogłoby odhaczyć niewłaściwy wpis (ten sam dzień
  może być rozplanowany kilka razy), a po przeniesieniu wpisu — żaden.
  **Domknięte w M4**: `UNIQUE(date, plan_day_id)` gwarantuje, że wpis tego dnia
  planu na daną datę jest najwyżej jeden, więc dopasowanie „dzień planu + dziś”
  jest jednoznaczne (szczegóły w opisie M4),
* **pominięcie wpisu nie przenosi treningu** — „pominięty” to informacja
  w historii; kolejny wpis z reguły zostaje na swoim miejscu.

**M4 ✔ (sesja + timer treningu)** — kryterium działa: timer liczy czas przy
zgaszonym ekranie (notyfikacja z systemowym chronometrem), a po ubiciu aplikacji
wstaje z zapisanej sesji bez utraty sekundy; serie i notatka zapisują się
w trakcie treningu, a zakończenie wpisu z planu odhacza dzień w kalendarzu.

* **Baza v5**: `set_logs` (ciężar, powtórzenia, czas, RPE, rozgrzewka, upadek,
  przerwa po serii) z `UNIQUE(session_exercise_id, set_index)` i `CASCADE` po
  pozycji treningu oraz `sessions.paused_total_ms` / `paused_at_ms`; migracja
  4 → 5 z testem `SchemaMigrationTest` (cała droga 1 → 5),
* **`WorkoutTimerState`** — czysty model zegara (start + suma pauz + otwarta
  pauza) z jednym wzorem na czas treningu, więc ekran, notyfikacja i odtworzenie
  po restarcie liczą dokładnie to samo (§6.2, decyzja 20 z §4.2),
* **`WorkoutTimer` + `WorkoutTimerService`**: usługa pierwszoplanowa
  (`specialUse`, `stopWithTask="false"`) z notyfikacją „Pauza”/„Wznów”,
  „Zakończ” i chronometrem systemowym. Usługa nie tyka i nie trzyma stanu,
  a akcje z notyfikacji działają też po ubiciu aplikacji — zegar odtwarza się
  wtedy z bazy (`restoreIfNeeded`),
* **`WorkoutTimer.attach`** w `MainActivity` — jedno miejsce, które pilnuje
  aktywnej sesji: nowy trening, trening po ubiciu aplikacji i powrót z tła
  obsługuje ten sam strumień z bazy, więc ekrany nie muszą o timerze pamiętać,
* **Start z dnia planu wiąże wpis w kalendarzu**: gdy na dziś jest zaplanowany
  wpis tego dnia, sesja wiąże się z nim w tej samej transakcji, a jej zakończenie
  odhacza wpis jako `done`. Dopasowanie jest jednoznaczne dzięki
  `UNIQUE(date, plan_day_id)` — odroczenie z M3 zniknęło razem z powodem,
  dla którego istniało,
* **Logowanie serii**: `addSet`/`updateSet`/`deleteSet` z walidacją po jednostce
  celu (powtórzenia i dystans → `reps`, czas → `duration_s`) i przenumerowaniem
  po usunięciu w jednej transakcji; serie dopiszemy wyłącznie do trwającego
  treningu (historia jest zamknięta),
* **Pauza** w nagłówku ekranu i w notyfikacji: zapisywana do bazy od razu,
  domykana przy zakończeniu, a zakończenie w pauzie **nie** dolicza jej do czasu
  treningu,
* **Notatka treningu** — edytowalna, czyszczona, przycinana z brzegów; trafia do
  bazy od razu, więc przeżywa ubicie aplikacji (na ekranie treningu jest kartą),
* **UI ekranu treningu**: nagłówek z zegarem i pauzą, karta notatki, lista
  ćwiczeń z celem i postępem („2 z 5”), wiersze zalogowanych serii (klik =
  popraw albo usuń) oraz formularz serii startujący z poprzedniej serii tego
  ćwiczenia (albo z celu z planu) i z przełącznikiem „rozgrzewka”,
* **Zgoda na powiadomienia** (Android 13+) pytana przy pierwszym treningu,
  nie na starcie aplikacji; odmowa nie psuje pomiaru czasu (zegar liczy się
  z bazy). Po udzieleniu zgody usługa dostaje `ACTION_REFRESH` i od razu pokazuje
  notyfikację - inaczej trzeba by czekać na pierwszą pauzę,
* **testy JVM: 132** (doszły: `WorkoutTimerStateTest` — pauzy, wznowienie,
  odtworzenie z zapisanych liczb i format czasu; `SetLogRepositoryTest` —
  numeracja, przenumerowanie po usunięciu, walidacja po jednostce, zamknięta
  historia; a w istniejących zestawach: wiązanie wpisu z sesją, notatka, zapis
  pauzy i jej domknięcie przy zakończeniu, migracja 1 → 5). `:app:lintDebug`
  i `:app:assembleDebug` bez błędów.

Świadome odstępstwa i odroczenia z M4:

* **zegar ścienny, a nie `elapsedRealtime`** (§4.2 punkt 20, §6.2) — kotwica
  monotoniczna nie przeżyłaby restartu procesu, a pauza jest zapisana w epochach;
  ceną jest wrażliwość na ręczną zmianę godziny w telefonie,
* **brak autozapisu `WorkManager`** z pierwotnego §6.2 — nie ma czego zapisywać:
  start, pauzy, serie i notatka są w bazie od razu, więc nagła śmierć procesu
  nie gubi niczego,
* **`duration_s`/`total_rest_s`/`active_s` nie istnieją** jako kolumny — czas
  liczy się z `started_at`/`finished_at`/`paused_total_ms`, a suma przerw
  z `rest_intervals`, gdy powstanie (M5),
* **`rest_intervals` i timer przerwy dołożył M5** — M4 zbierał serie i ciężary,
  ale nie mierzył przerw między nimi; kolumny `set_logs.rest_after_s`
  i `rest_target_s` były już w schemacie v5, więc M5 nie musiał zmieniać tabeli
  serii (§8.1, M5),
* **RPE i „do upadku” bez UI** — kolumny (i `is_failure` w domenie) są gotowe,
  ale nie zbieramy ich na ekranie: pokażą swoją wartość w statystykach i przy
  rekordach (M6), gdzie trzeba odsiać serie rozgrzewkowe,
* **brak ręcznego odhaczania wpisu z ekranu treningu** — wpis odhacza
  zakończenie treningu, a „pominięty” zostaje akcją kalendarza (M3),
* **usługa nie wstaje sama z tła** po zabiciu procesu (Android 12+ ogranicza
  start usługi z tła) — licznik tego nie potrzebuje, bo dane są w bazie,
  a notyfikacja wraca przy następnym wejściu do aplikacji,
* **historia wykonań, statystyki ciężaru i archiwum → M6/M9**, a wykresy → M6
  (jak w M2/M3). Historia przerw jest już w M5; **widok tygodnia** zostaje
  odłożony do §11 (v1.1).

**M5 ✔ (timer przerwy)** — kryterium działa: po sesji widać średnią przerwę dla
każdego ćwiczenia, a przerwa odlicza się i dzwoni przy zgaszonym ekranie.

* **Baza v6**: `rest_intervals` (ćwiczenie, sesja, seria, start, cel, koniec,
  faktyczny czas, powód, `is_auto`) z `CASCADE` po ćwiczeniu i sesji, `SET NULL`
  po serii i trzema indeksami; `CHECK` na słowniku powodów; migracja 5 → 6
  z testem `SchemaMigrationTest` (cała droga 1 → 6) i testem odrzucenia powodu
  spoza słownika,
* **`RestInterval` + `RestStats`** — modele domenowe i czysta arytmetyka:
  `countsToStats` (czy przerwa wchodzi do średniej), `elapsedMs`,
  `RestStatsCalculator` (średnia, mediana, min/max, ostatnia przerwa, średnia
  z 5 ostatnich treningów) oraz `ActiveRest` (koniec przerwy liczony z początku
  i celu, więc „+15 s” nie potrzebuje własnego pola),
* **`RestTimer` + `RestRepository`**: start przerwy zapisuje wiersz od razu,
  a domknięcie jest idempotentne i liczy faktyczny czas w jednym miejscu; cel
  przerwy: sesja → karta ćwiczenia → ustawienie globalne (90 s),
* **Alarm i notyfikacja**: `RestAlarmScheduler` (dokładny alarm, gdy system
  pozwala) + `RestAlarmReceiver` (`@AndroidEntryPoint`, `goAsync`) domykający
  przerwę także po ubiciu procesu oraz `RestNotifier` z kanałem o wysokiej
  ważności (dźwięk + wibracja). Notyfikacja usługi pokazuje w trakcie przerwy
  odliczanie w dół z akcjami „+15 s” i „Pomiń”,
* **Ekran treningu**: pasek przerwy z odliczaniem i trzema decyzjami, klikalny
  cel przerwy na karcie ćwiczenia (dialog), „przerwa 1:40” w wierszu serii,
  „Przerwy: 3 • śr. 1:48” (ten trening) i „Średnia przerwa: 1:45 • ostatnio 2:05
  (12 treningów)” (historia), a w nagłówku „Pod sztangą 58:20 • przerwy 14:25”,
* **Ustawienia**: domyślna przerwa między seriami (90 s) jako ostatni poziom celu,
* **Pauza treningu anuluje przerwę** (`cancelled`, więc nie liczy się do średniej):
  przerwa jest częścią pracy, a nie przerwy od pracy,
* **testy JVM: 156** (doszły: `RestStatsCalculatorTest` — co wchodzi do próby,
  mediana, ostatnie treningi; `RestTimerStateTest` — koniec przerwy, „+15 s”,
  minimum; `RestRepositoryTest` — start, idempotentne domknięcie, kopia przerwy
  w wierszu serii, statystyki per ćwiczenie, czytelna nazwa po archiwizacji,
  korekta celu w sesji; w istniejących zestawach: migracja 1 → 6 i odrzucenie
  powodu spoza słownika). `:app:lintDebug` i `:app:assembleDebug` bez błędów,
* **Weryfikacja na urządzeniu (emulator API 36)**: migracja 5 → 6 na żywej bazie
  i cała ścieżka przerwy — start po zalogowanej serii, „+15 s”/„-15 s”, „Pomiń”,
  domknięcie kolejną serią z `rest_after_s` w wierszu serii, „Pod sztangą”,
  średnie na karcie i w sesji otwartej z kalendarza, `cancelled` przy końcu
  treningu, notyfikacja w trybie przerwy oraz alert „Koniec przerwy”. Pomiary
  i szczegóły: §9 (weryfikacja po M5).

Świadome odstępstwa i odroczenia z M5:

* **powód po odtworzeniu przerwy to zawsze `completed`** — po restarcie procesu
  nie da się odtworzyć, czy użytkownik użył „+15 s”; oba powody liczą się do
  średniej tak samo, więc różnica jest kosmetyczna,
* **alarm bez wymuszania zgody na dokładne alarmy** (§4.2 punkt 26) — przy braku
  zgody system może przesunąć koniec przerwy w Doze; ekran i tak odlicza na żywo,
* **statystyki przerw bez percentyli** — mediana i średnia mówią o przerwach
  wszystko, czego użytkownik szuka w jednej linii na karcie ćwiczenia,
* **brak ręcznego startu przerwy z karty ćwiczenia** — przerwa startuje po
  zalogowanej serii; kolumna `is_auto` czeka na ten wariant,
* **historia wykonań i wykresy → M6**, archiwum i eksport → M9 (jak w M4).

**M6 ✔ (historia i statystyki)** — kryterium działa: wykres pokazuje postęp
z ostatnich trzech miesięcy, a historia odpowiada na „kiedy i co robiłem”.

* **Baza bez zmian** — historia czyta istniejące tabele; nowy `StatsDao` dokłada
  cztery zapytania: serie jednego ćwiczenia, agregaty per trening, pary
  (trening, ćwiczenie) i zamknięte przerwy z `LIMIT`. Świadomie **bez** indeksu
  na `sessions.started_at` i bez migracji (§4.2 pkt 37),
* **Domena**: `ExerciseStatsCalculator` (punkty per dzień, rekordy z całej
  historii, `OneRepMax` Epleya z flagą przybliżenia, `hasWeightData`),
  `SessionSummaryCalculator` (tylko zakończone treningi, kolejność od
  najnowszych, średnia przerwa per trening, filtr zakres / ćwiczenie / nazwa)
  oraz `RestStatsCalculator.averageSeconds` — jedna definicja próbki przerwy dla
  karty ćwiczenia, historii i wykresu,
* **Warstwa danych**: `StatsRepository` (tylko odczyt) + `StatsLimits` (wspólne
  limity wierszy, żeby karta ćwiczenia i historia patrzyły na tę samą próbkę
  przerw) i `StatsMappers` (data liczona raz, w warstwie danych — dzięki temu
  kalkulatory są czystymi funkcjami),
* **Ekran „Historia”**: lista zakończonych treningów (nazwa dnia, data, czas bez
  pauz, serie robocze, objętość, średnia przerwa) z chipami filtrów (zakres,
  ćwiczenie z historii, nazwa dnia), wejście w sesję w trybie do czytania i dwa
  warianty pustego stanu: „brak treningów” vs „żaden nie pasuje do filtrów”,
* **Ekran „Statystyki ćwiczenia”**: rekordy (najcięższa seria, 1RM z „~” powyżej
  12 powtórzeń, największa objętość, przerwy) i podsumowanie zakresu, wykres
  ciężaru (najcięższa seria + szacowany 1RM), wykres objętości (słupki) oraz
  tabela wykonań — na Vico 3.2.3 (`compose-m3`), z zoomem gestem,
* **Karta ćwiczenia**: sekcja „Historia” z rekordem, ostatnim wykonaniem, średnią
  przerwą i mini wykresem 3M oraz wejściem „Pełna historia i statystyki”; wspólne
  komponenty (`SectionCard`, `InfoRow`, `restHistorySummary`) wyniesione do
  `ui/components`, żeby szczegóły i statystyki nie rozjechały się wizualnie,
* **Nawigacja**: `Routes.HISTORY` w `topLevel` (zakładka „Historia” w dolnym
  pasku) i `Routes.EXERCISE_STATS` (pełny ekran, bez dolnego paska); teksty PL/EN
  1:1 — po M6 **356 kluczy** w obu plikach,
* **testy JVM: 183** (doszły: `ExerciseStatsCalculatorTest` — punkty per dzień,
  rekordy, 1RM i próg przybliżenia, serie rozgrzewkowe, ćwiczenie na czas;
  `SessionSummaryCalculatorTest` — statusy, kolejność, filtr, średnia przerwa
  z sesji; `StatsRepositoryTest` — agregaty z SQL, filtr statusu, filtr po
  ćwiczeniu, rozdzielenie zakresu i rekordów, puste statystyki).
  `:app:lintDebug` i `:app:assembleDebug` bez błędów,
* **Weryfikacja na urządzeniu (emulator API 36)**: patrz §9 (weryfikacja po M6).

Świadome odstępstwa i odroczenia z M6:

* **bez markera pod palcem na wykresie** — `rememberMarker` nie ma w module
  Compose Vico 3.2.3 (§4.2 pkt 35); rolę „sprawdź dokładną wartość” bierze
  tabela wykonań z pełnymi datami,
* **wykres z jednym dniem to punkt bez linii** — przy dwóch i więcej treningach
  rysuje się normalnie (sprawdzone na emulatorze); „sztuczne” dorysowywanie
  punktu zerowego znaczyłoby „podniosłem nic”,
* **filtr historii po nazwie dnia, nie po planie** — sesja zna nazwę dnia
  („Workout A”), a to ona jest widoczna na liście,
* **brak eksportu danych z historii** (CSV/udostępnianie) — archiwum i eksport
  → M9,
* **widok tygodnia w kalendarzu nadal odłożony** — w M6 była kolej na historię
  i statystyki; tydzień to osobny kawałek UI w kalendarzu (§11, v1.1).

---

## 9. Testy i jakość

| Poziom | Co testujemy | Narzędzia |
|---|---|---|
| Jednostkowe | logika bez Androida: formatowanie czasu, grupowanie alternatyw, przestawianie wierszy, walidacja linków YouTube, importer seedów, `RecurrenceExpander` (M3), siatka miesiąca i formaty dat (M3), zegar treningu z pauzami (M4), statystyki i arytmetyka przerw (M5), statystyki ćwiczenia i lista historii (M6) | JUnit4 + `TimeProvider` wstrzykiwany |
| Bazy | DAO, migracje (1 → 2 → 3 → 4 → 5 → 6), kaskady, unikalne indeksy, słownik powodów przerwy, FTS | Room in-memory + `MigrationTestHelper` na Robolectric |
| Repozytoria | mapowanie encja↔model, `Flow`, kolejność i superserie, harmonogram i wpisy kalendarza (M3), serie i pauza treningu (M4), przerwy między seriami (M5), historia i statystyki z SQL (M6) | Room in-memory + `runTest` |
| UI | krytyczne ścieżki: dodanie ćwiczenia, zbudowanie dnia, zaplanowanie treningów, start i koniec treningu | Compose UI Test + Robolectric (M4+), teraz scenariusze w `docs/QA.md` |
| Ręczne | timer przy zgaszonym ekranie 30 min, ubiciu apki, zmianie strefy czasowej, restarcie; przerwa z alarmem przy zgaszonym ekranie; migracja na bazie z poprzedniego wydania | lista scenariuszy w `docs/QA.md` |
| Statyczne | ktlint, detekt, Compose lint | CI na każdym PR (lint Android jest już w CI) |
| Wydajność | baseline profile, makrobenchmark startu, brak zapytań na głównym wątku | Macrobenchmark (M9) |

Stan na M6: **183 testy JVM**, wszystkie zielone, uruchamiane bez emulatora
(Robolectric). Każdy krok, który zmienia schemat, dowozi migrację **i** test
przejścia całej drogi (nie tylko ostatniego skoku) — tak było przy 1 → 2,
2 → 3, 3 → 4, 4 → 5 i 5 → 6. M6 nie zmienił schematu, więc nie doszła ani
migracja, ani nowy plik schematu.

Weryfikacja na urządzeniu (emulator API 36) po M3: migracja bazy z M2 bez utraty
danych i historia treningów bez zmian, kryterium M3 („co 3 dni, od 1.10 do 31.12,
rotacja A/B/C” → 31 poprawnych dat w podglądzie i w kalendarzu), oznaczenie
wpisu jako odbyty, przeniesienie wpisu, edycja i usunięcie reguły, start
i zakończenie treningu z wpisu (wpis odhaczony). Scenariusze: `docs/QA.md`.

Weryfikacja na urządzeniu (emulator API 36) po M4: migracja 4 → 5 na żywej bazie
z M3 (treningi, kalendarz i plany bez zmian, `user_version = 5`), logowanie serii
z ciężarem i na czas, poprawa i usunięcie serii z przenumerowaniem, notatka
przeżywająca zabicie aplikacji, pauza (zamrożony licznik i notyfikacja), timer
przy zgaszonym ekranie i po ubiciu aplikacji oraz odhaczenie wpisu w kalendarzu
po treningu rozpoczętym z planu. Scenariusze: `docs/QA.md`.

Weryfikacja na urządzeniu (emulator API 36) po M5: migracja 5 → 6 na żywej bazie
z M4 (treningi, serie i kalendarz bez zmian, `user_version = 6`, tabela przerw
pusta), przerwa startująca po zalogowanej serii (`planned_s` i `is_auto`
w wierszu), „+15 s” i „-15 s” przesuwające `planned_s`, „Pomiń”
(`ended_reason = 'skipped'`, bez wpisu w wierszu serii), kolejna seria domykająca
poprzednią przerwę (`completed` + `rest_after_s`/`rest_target_s` w `set_logs`),
„Pod sztangą … • przerwy …” i „Przerwy: 7 • śr. 0:43” na karcie ćwiczenia,
zakończenie treningu przy trwającej przerwie (`cancelled`, poza statystykami),
odczyt zakończonej sesji z kalendarza („Otwórz trening”) ze średnimi przerwami,
notyfikacja usługi w trybie przerwy (tytuł = ćwiczenie, chronometr w dół, akcje)
oraz notyfikacja „Koniec przerwy” na kanale `rest_timer`
(`IMPORTANCE_HIGH`, wibracja `[0, 300, 150, 300]`, kategoria `alarm`).
Zmierzone przy okazji: przy żywym procesie przerwę domyka ekran w ~0,4 s od
zera i sama dzwoni, a budzik systemowy jest siatką bezpieczeństwa dla ubitego
procesu — bez zgody na dokładne alarmy ma okno do ~22 s (raz spóźnił się
18,7 s po ubiciu procesu); to cena opisana w §4.2 pkt 26 i §6.3.
Scenariusze: `docs/QA.md`.

Weryfikacja na urządzeniu (emulator API 36) po M6: baza z M5 (bez migracji,
`user_version = 6`) i cała ścieżka historii — zakładka „Historia” w dolnym pasku
z listą zakończonych treningów (sesja `cancelled` **nie** wchodzi), wiersze
z czasem bez pauz, liczbą serii roboczych, objętością i średnią przerwą, filtry
(zakres 1M/3M/6M/1R/wszystko, ćwiczenie z historii, nazwa dnia) i pusty stan
„żaden nie pasuje do filtrów” przy kombinacji bez wyników, wejście w trening
w trybie do czytania. Na karcie ćwiczenia sekcja „Historia” z rekordem, ostatnim
wykonaniem, średnią przerwą i mini wykresem, a pod nią „Pełna historia
i statystyki”: rekordy z całej historii, podsumowanie zakresu, wykres ciężaru
z dwiema liniami (najcięższa seria 50 → 60 → 70 kg, 1RM 58 → 70 → 82 kg), słupki
objętości (750 / 900 / 3 450 kg — dzień z dwoma treningami sumuje się w jeden
słupek), tabela wykonań od najnowszych i przełączenie zakresu. Poza tym:
ćwiczenie na czas (`weight_kg = null`) liczy się jako trening, ale zamiast
wykresu pokazuje wyjaśnienie, a ćwiczenie bez historii — komunikat zamiast
pustych osi. Zmiana w bazie zrobiona poza aplikacją nie odświeża ekranu (Room
nie wie o zapisie z `sqlite3`) — po powrocie na ekran dane są już nowe, co samo
w sobie potwierdziło, że odczyt idzie `Flow`, a nie ze snapshotu.
Scenariusze: `docs/QA.md`.

**Definicja ukończenia (DoD) dla zadania:** kod + testy + brak ostrzeżeń lintera +
dokumentacja w `docs/` jeśli zmienia model danych + migracja Room jeśli zmienia schemat.

---

## 10. Ryzyka

| # | Ryzyko | Wpływ | Mitygacja |
|---|---|---|---|
| R1 | Timer ubijany przez system (agresywne zarządzanie energią na Xiomi/Huawei) | wysoki | usługa pierwszoplanowa (M4) + czas i serie zapisane w bazie na bieżąco, więc licznik wstaje sam (M4); w backlogu: ekran „Poproś o wyłączenie optymalizacji baterii” |
| R2 | Utrata danych (reinstalacja, awaria telefonu) | wysoki | eksport ZIP od M9, przypomnienie o backupie, „auto-backup do Documents” co tydzień (SAF) |
| R3 | Złożoność reguł cyklicznych rośnie | średni | trzymamy prosty model (od/do/co ile dni + opcjonalna rotacja), rozwijamy do dat; `RRULE` dopiero jeśli naprawdę potrzebny |
| R4 | Duże filmy zapychają pamięć | średni | limit rozmiaru, licznik zajętości, kompresja zdjęć, sprzątanie osieroconych plików |
| R5 | Danych startowych nie da się „odświeżyć” automatycznie | niski | katalog jest wersjonowany w repo i importowany raz do `assets/`; użytkownik może w każdej chwili dodać własne ćwiczenia |
| R6 | Chęć na iOS w przyszłości | średni | jeśli to realne — Kotlin Multiplatform (logika + Room w `:shared`), UI natywne. Decyzję trzeba podjąć **przed M1**, bo później to kosztowny refaktor |
| R7 | Uprawnienie `SCHEDULE_EXACT_ALARM` odmówione (domyślnie na Androidzie 12+) | niski | budzik to siatka bezpieczeństwa, a nie jedyny mechanizm: przy żywym procesie przerwę domyka ekran i **też dzwoni** (§6.3); budzik bez zgody ma okno do ~22 s (zmierzone), a z zgodą jest co do sekundy; scenariusz 2.4 w `docs/QA.md` opisuje oba przypadki |
| R8 | Użytkownik „przeskakuje” ćwiczenia w planie podczas treningu | niski | sesja jest kopią planu, można dodać/pominąć/zmienić kolejność bez wpływu na plan |
| R9 | Vico 3.2.3 to ostatnia linia działająca na `compileSdk 36` (3.3+ wymaga 37) | niski | wersja jest przypięta w `libs.versions.toml`; nowsze Vico wejdzie razem z podniesieniem `compileSdk` (razem z androidx, §2). W 3.2.3 działa wszystko, czego potrzebujemy (dwie linie, słupki, osie z formatowaniem, zoom); brakuje tylko markera pod palcem (§4.2 pkt 35) |

---

## 11. Roadmapa po 1.0

1. **v1.1** — przypomnienia o treningu, eksport CSV, wykresy: heatmapa aktywności (streak), **widok tygodnia w kalendarzu** (przegląd dni z podsumowaniem, odłożony z M3/M6).
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
