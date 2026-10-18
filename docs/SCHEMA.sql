-- =============================================================================
-- GymTracker - schemat bazy lokalnej (SQLite / Room)
-- Plik referencyjny. Zrodlo prawdy w kodzie: app/src/main/java/.../data/db/*.
-- Wersja: 9 (2026-10-18)
--
-- Stan implementacji (szczegoly: docs/PLAN.md 8.1):
--  * v1 (M0): app_meta                                            [WDROZONE]
--  * v2 (M1): exercises + exercises_fts + indeksy                  [WDROZONE]
--  * v3 (M2): plans, plan_days, plan_day_exercises, sessions,
--             session_exercises                                   [WDROZONE]
--  * v4 (M3): recurrences, scheduled_sessions                      [WDROZONE]
--  * v5 (M4): set_logs + kolumny pauzy w sessions                  [WDROZONE]
--  * v6 (M5): rest_intervals + indeksy                              [WDROZONE]
--  * v7 (M7): exercises.instructions + exercise_media              [WDROZONE]
--  * v8 (M8): metric_types + body_entries (karta postaci)          [WDROZONE]
--  * v9 (M9): seed_imports (dziennik importow katalogow)           [WDROZONE]
--  * reszta tabel oznaczone [Mx]: docelowe, dochodza w pozniejszych
--    kamieniach milowych. DDL trzymamy z gory, zeby caly model byl w jednym
--    miejscu - ale to jeszcze nie jest kod.
--
-- Uwaga dla wersji 1-9: DDL ponizszy dla tabel [WDROZONE] jest przepisany
-- znak w znak z SQL, ktory generuje Room (app/schemas/...). Migracje w
-- DatabaseMigrations musza byc identyczne - inaczej Room zglasza
-- "Migration didn't properly handle ...".
--
-- Zasady:
--  * Wszystko lokalnie, zero chmury.
--  * Daty kalendarzowe trzymamy jako TEXT 'YYYY-MM-DD' (bez stref -> brak bledow DST).
--  * Znaczniki czasu jako INTEGER (epoch millis, UTC).
--  * Listy (np. miesnie dodatkowe) jako TEXT z JSON - proste i wystarczajace.
--  * Klucze zewnetrzne: external_id = slug ze strony (stabilny klucz naturalny).
--  * Wartosci "enum" (jednostka powtorzen, status sesji) walidujemy w warstwie
--    domeny (RepsUnit.fromWire, SessionStatus), a nie CHECK-iem w bazie: latwiej
--    dodac nowa wartosc bez migracji, a baza i tak nie jest dostepna z zewnatrz.
--    Wyjatki: rest_intervals.ended_reason, exercise_media.kind oraz
--    metric_types.direction - powod przerwy decyduje o tym, czy wpis wchodzi do
--    statystyk, rodzaj zalacznika o tym, jak plik jest pokazywany, a kierunek
--    wskaznika o kolorze zmiany, wiec literowka nie moze przejsc po cichu. Room sam takiego CHECK-a nie generuje, ale
--    TableInfo go ignoruje, wiec walidacja schematu po migracji przechodzi.
-- =============================================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- 1. KATALOG C WICZE  (rozszerzalny przez uzytkownika)          [WDROZONE M1]
-- -----------------------------------------------------------------------------
CREATE TABLE exercises (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id         TEXT UNIQUE,            -- np. 'barbell-bench-press'; NULL dla wlasnych
    name                TEXT NOT NULL,
    name_in_program     TEXT,                   -- jak nazwano cwiczenie w planie zrodlowym
    source_url          TEXT,
    muscle_group        TEXT,                   -- 'Chest', 'Quads', ...
    muscle_group_slug   TEXT,
    secondary_muscles   TEXT,                   -- JSON: ["Shoulders","Triceps"]
    equipment           TEXT,                   -- 'Barbell', 'Dumbbell', 'Machine', 'Bodyweight'
    mechanics           TEXT,                   -- 'Compound' | 'Isolation'
    force_type          TEXT,
    level               TEXT,
    instructions        TEXT,                   -- JSON: ["krok 1", "krok 2"]   [M7]
    youtube_url         TEXT,                   -- znormalizowany link (watch?v=ID)
    default_sets        INTEGER,
    default_reps_min    INTEGER,
    default_reps_max    INTEGER,
    default_rest_s      INTEGER,                -- domyslna przerwa w sekundach
    notes               TEXT,                   -- komentarz uzytkownika do cwiczenia
    -- Bez DEFAULT w DDL: wartosci domyslne ustawia model Kotlina (@ColumnInfo(defaultValue)).
    is_favourite        INTEGER NOT NULL,
    -- 1 = ukryte w katalogu. "Usun" NIE kasuje wiersza: kaskady z plan_day_exercises
    -- i session_exercises zabralyby ze soba historie treningow (docs/PLAN.md 6.11).
    is_archived         INTEGER NOT NULL,
    created_at          INTEGER NOT NULL,
    updated_at          INTEGER NOT NULL
);
CREATE INDEX idx_exercises_muscle  ON exercises(muscle_group);
-- UNIQUE: na tym opiera sie idempotentny import seeda (INSERT OR IGNORE po external_id).
CREATE UNIQUE INDEX idx_exercises_ext ON exercises(external_id);

-- Wyszukiwanie odporne na polskie znaki diakrytyczne.
-- FTS4, nie FTS5: Room nie ma adnotacji @Fts5, a @Fts4 z contentEntity dziala
-- takze w Robolectric. Tokenizer unicode61 domyslnie usuwa diakrytyki, wiec
-- "sztanga" znajdzie "sztangą", a "obwod" - "obwód".
-- Triggery ponizej zaklada sam Room (@Fts4(contentEntity = ExerciseEntity::class));
-- migracja musi tworzyc je identycznie, inaczej Room zglosi
-- "Migration didn't properly handle ...".
CREATE VIRTUAL TABLE exercises_fts USING FTS4(
    name, muscle_group, equipment,
    tokenize=unicode61, content='exercises'
);

CREATE TRIGGER room_fts_content_sync_exercises_fts_BEFORE_UPDATE
    BEFORE UPDATE ON exercises BEGIN
    DELETE FROM exercises_fts WHERE docid = OLD.rowid;
END;
CREATE TRIGGER room_fts_content_sync_exercises_fts_BEFORE_DELETE
    BEFORE DELETE ON exercises BEGIN
    DELETE FROM exercises_fts WHERE docid = OLD.rowid;
END;
CREATE TRIGGER room_fts_content_sync_exercises_fts_AFTER_UPDATE
    AFTER UPDATE ON exercises BEGIN
    INSERT INTO exercises_fts (docid, name, muscle_group, equipment)
    VALUES (NEW.rowid, NEW.name, NEW.muscle_group, NEW.equipment);
END;
CREATE TRIGGER room_fts_content_sync_exercises_fts_AFTER_INSERT
    AFTER INSERT ON exercises BEGIN
    INSERT INTO exercises_fts (docid, name, muscle_group, equipment)
    VALUES (NEW.rowid, NEW.name, NEW.muscle_group, NEW.equipment);
END;

-- -----------------------------------------------------------------------------
-- 2. ZALACZNIKI C WICZENIA (zdjecia, filmy, linki)              [WDROZONE M7]
--    Zalacznik to WSKAZANIE, nie kopia: trzymamy URI pliku z galerii, a nie
--    jego tresc. Aplikacja nie zarzadza cudzymi plikami - jedyny jej plik to
--    wlasna miniatura (~200 px, filesDir/thumbs/<uuid>.jpg), kasowana razem
--    z wierszem. Gdy plik zniknie z galerii, wiersz zostaje, a UI pokazuje
--    miniature i informacje, ze oryginal jest niedostepny (PLAN.md 6.5).
-- -----------------------------------------------------------------------------
CREATE TABLE exercise_media (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    exercise_id     INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('image','video','youtube','link')),
    remote_uri      TEXT,                       -- wskazanie pliku: content:// (galeria) lub URL
    thumbnail_path  TEXT,                       -- wzgledna sciezka NASZEJ miniatury w filesDir/thumbs
    caption         TEXT,                       -- opis zalacznika (nie komentarz cwiczenia)
    duration_ms     INTEGER,                    -- filmy: dlugosc z metadanych
    size_bytes      INTEGER,                    -- rozmiar oryginalu (do wgladu)
    position        INTEGER NOT NULL,           -- kolejnosc w siatce; bez DEFAULT: wartosc ustawia model Kotlina
    created_at      INTEGER NOT NULL
);
CREATE INDEX idx_media_exercise ON exercise_media(exercise_id, position);

-- -----------------------------------------------------------------------------
-- 3. PLANY TRENINGOWE I ICH DNI                                  [WDROZONE M2]
--
-- Plan jest "przepisem": dni w kolejnosci rotacji, a w dniu pozycje z celem
-- (serie/powtorzenia/ciezar/przerwa). Sesja treningowa to KOPIA dnia z chwili
-- startu (patrz sekcja 5) - edycja planu nie przepisuje historii.
-- -----------------------------------------------------------------------------
CREATE TABLE plans (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id  TEXT UNIQUE,                   -- np. 'muscle-strength-full-body-workout-routine'
    name         TEXT NOT NULL,
    description  TEXT,
    source_url   TEXT,
    author       TEXT,
    is_active    INTEGER NOT NULL,              -- plan "na teraz" (uzywany przez Dzien)
    is_archived  INTEGER NOT NULL,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
);
CREATE UNIQUE INDEX idx_plans_ext ON plans(external_id);

CREATE TABLE plan_days (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id      INTEGER NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,                 -- 'Workout A'
    day_of_week  INTEGER,                       -- 0=poniedzialek .. 6=niedziela, NULL = brak stalej
    position     INTEGER NOT NULL,              -- kolejnosc w rotacji
    note         TEXT
);
-- UNIQUE(plan_id, position): przestawianie dni idzie w transakcji dwoma
-- przebiegami (wartosci ujemne -> docelowe), patrz PlanRepositoryImpl.
CREATE UNIQUE INDEX idx_plan_days_position ON plan_days(plan_id, position);

CREATE TABLE plan_day_exercises (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_day_id         INTEGER NOT NULL REFERENCES plan_days(id) ON DELETE CASCADE,
    exercise_id         INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    position            INTEGER NOT NULL,
    target_sets         INTEGER,
    target_reps_min     INTEGER,
    target_reps_max     INTEGER,
    target_reps_unit    TEXT NOT NULL,          -- 'reps' | 'seconds' | 'meters' (RepsUnit)
    target_weight_kg    REAL,
    rest_seconds        INTEGER,
    superset_group      INTEGER,                -- ta sama liczba w obrebie dnia = superseria
    alternative_group   TEXT,                   -- '<id podstawowego>-or-<id alternatywy>'
    is_default_choice   INTEGER NOT NULL,       -- dokladnie jedno na alternative_group
    note                TEXT
);
CREATE INDEX idx_pde_day      ON plan_day_exercises(plan_day_id, position);
CREATE INDEX idx_pde_exercise ON plan_day_exercises(exercise_id);

-- -----------------------------------------------------------------------------
-- 4. HARMONOGRAM (kalendarz)                                              [M3]
--    Regula ("co ile dni") -> jednorazowe rozwiniecie do scheduled_sessions.
--    Dzieki temu zapytania kalendarza sa trywialne, a zmiana reguly = przeliczenie.
--
--    Horyzont planowania: 20 tygodni w przod (ScheduleLimits.MAX_WEEKS_AHEAD).
--    Rotacja dni A/B/C nie ma wlasnej kolumny: rotate = (plan_day_id IS NULL);
--    taka regula przechodzi po plan_days w kolejnosci position.
-- -----------------------------------------------------------------------------
CREATE TABLE recurrences (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id        INTEGER REFERENCES plans(id) ON DELETE CASCADE,
    plan_day_id    INTEGER REFERENCES plan_days(id) ON DELETE CASCADE,
    start_date     TEXT NOT NULL,               -- 'YYYY-MM-DD'
    end_date       TEXT,                        -- NULL = do horyzontu (dzis + 20 tyg.)
    interval_days  INTEGER NOT NULL,            -- "co ile dni" (>= 1 pilnuje domena)
    weekdays_mask  INTEGER,                     -- bitmaska bit0=pon; NULL = kazdy dzien
    created_at     INTEGER NOT NULL
);
CREATE INDEX idx_recurrences_plan ON recurrences(plan_id);
CREATE INDEX idx_recurrences_day  ON recurrences(plan_day_id);

CREATE TABLE scheduled_sessions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    date          TEXT NOT NULL,                -- 'YYYY-MM-DD'
    plan_id       INTEGER REFERENCES plans(id) ON DELETE SET NULL,
    plan_day_id   INTEGER REFERENCES plan_days(id) ON DELETE SET NULL,
    recurrence_id INTEGER REFERENCES recurrences(id) ON DELETE SET NULL,
    title         TEXT NOT NULL,                -- snapshot nazwy dnia z chwili planowania
    status        TEXT NOT NULL,                -- 'planned' | 'done' | 'skipped'
    session_id    INTEGER REFERENCES sessions(id) ON DELETE SET NULL,
    created_at    INTEGER NOT NULL
);
-- Jedna sesja danego dnia planu na dany dzien; INSERT OR IGNORE = idempotencja.
-- Bez WHERE: SQLite traktuje NULL jako rozne wartosci, wiec wpisy bez dnia planu
-- (po usunieciu planu) nie koliduja ze soba i nie gina.
CREATE UNIQUE INDEX idx_sched_uniq ON scheduled_sessions(date, plan_day_id);
CREATE INDEX idx_sched_date_status ON scheduled_sessions(date, status);
CREATE INDEX idx_sched_plan        ON scheduled_sessions(plan_id);
CREATE INDEX idx_sched_day         ON scheduled_sessions(plan_day_id);
CREATE INDEX idx_sched_recurrence  ON scheduled_sessions(recurrence_id);
CREATE INDEX idx_sched_session     ON scheduled_sessions(session_id);

-- -----------------------------------------------------------------------------
-- 5. TRENING (realizacja) + TIMERY
--
-- sessions / session_exercises                                 [WDROZONE M2]
-- set_logs (+ kolumny pauzy w sessions)                        [WDROZONE M4]
-- rest_intervals                                            [WDROZONE M5]
--
-- session_exercises to SNAPSHOT celu z planu: brakuje tu plan_day_exercise_id,
-- bo pozycja w planie moze zniknac, a trening ma zostac czytelny. Ta sama
-- uwaga dotyczy plan_id/plan_day_id w sessions: ON DELETE SET NULL, nigdy
-- kasowanie historii.
-- -----------------------------------------------------------------------------
CREATE TABLE sessions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id          INTEGER REFERENCES plans(id) ON DELETE SET NULL,
    plan_day_id      INTEGER REFERENCES plan_days(id) ON DELETE SET NULL,
    name             TEXT NOT NULL,             -- np. 'Workout A' (skopiowane z dnia planu)
    started_at       INTEGER NOT NULL,          -- epoch ms
    finished_at      INTEGER,                   -- NULL = trening w toku
    status           TEXT NOT NULL,             -- 'active' | 'finished' | 'cancelled'
    note             TEXT,                      -- notatka do treningu (M4)
    -- Pauza jest ZAPISANA, nie tylko trzymana w procesie: inaczej zabicie apki
    -- w trakcie pauzy doliczyloby ja do czasu treningu. Czas treningu =
    -- (paused_at_ms ?: teraz) - started_at - paused_total_ms. Niezmiennik:
    -- zamknieta sesja ma paused_at_ms = NULL (domykamy pauze przy zamknieciu).
    paused_total_ms  INTEGER NOT NULL DEFAULT 0, -- suma zamknietych pauz
    paused_at_ms     INTEGER                    -- poczatek otwartej pauzy
);
CREATE INDEX idx_sessions_plan   ON sessions(plan_id);
CREATE INDEX idx_sessions_day    ON sessions(plan_day_id);
CREATE INDEX idx_sessions_status ON sessions(status);

CREATE TABLE session_exercises (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id          INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    exercise_id         INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    position            INTEGER NOT NULL,
    target_sets         INTEGER,
    target_reps_min     INTEGER,
    target_reps_max     INTEGER,
    target_reps_unit    TEXT NOT NULL,
    rest_seconds        INTEGER,
    superset_group      INTEGER,
    alternative_group   TEXT,                   -- zostaje dla kontekstu ("z czego wybieralismy")
    is_default_choice   INTEGER NOT NULL,       -- do sesji trafiaja tylko wybory domyslne
    note                TEXT
);
CREATE INDEX idx_session_exercises_pos      ON session_exercises(session_id, position);
CREATE INDEX idx_session_exercises_exercise ON session_exercises(exercise_id);

-- Logowane serie - fakt, nie plan: "60 kg x 5", a nie cel z planu. [WDROZONE M4]
-- Jednostka celu decyduje, co jest wartoscia serii: reps dla powtorzen i dystansu,
-- duration_s dla cwiczen na czas. weight_kg moze byc NULL (pompki, plank).
-- Uwaga: kolumn duration_s/total_rest_s/active_s w SESSIONS z pierwotnego
-- projektu (cache czasu) NIE ma: czas liczymy z started_at/finished_at/paused_*,
-- a sume przerw - z rest_intervals. Nie dodajemy pol, ktore moga sie rozjechac
-- z rzeczywistoscia.
CREATE TABLE set_logs (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    session_exercise_id   INTEGER NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE,
    set_index             INTEGER NOT NULL,     -- 1..n, numeracja ciagla (M4)
    weight_kg             REAL,
    reps                  INTEGER,              -- NULL dla serii na czas
    duration_s            INTEGER,              -- dla plank / serii na czas
    rpe                   REAL,                 -- 1..10, opcjonalne (M4: bez UI)
    -- Bez DEFAULT w DDL: wartosci domyslne ustawia model Kotlina (is_warmup = false).
    is_warmup             INTEGER NOT NULL,
    is_failure            INTEGER NOT NULL,
    rest_after_s          INTEGER,              -- faktyczna przerwa po tej serii (M5)
    rest_target_s         INTEGER,              -- ile bylo ustawione (M5)
    completed_at          INTEGER NOT NULL,
    UNIQUE (session_exercise_id, set_index)
);

-- Historia przerw: zrodlo podsumowan i srednich per cwiczenie. [WDROZONE M5]
-- Zrodlo prawdy o przerwach - set_logs.rest_after_s to tylko kopia wygodna dla
-- wiersza serii (dopisuje ja domkniecie przerwy). Dlatego set_log_id jest
-- ON DELETE SET NULL: usuniecie serii nie zabiera statystyk cwiczenia.
-- Powod (ended_reason) decyduje, czy przerwa wchodzi do sredniej: completed
-- i extended tak, skipped i cancelled nie (szczegoly: PLAN.md 4.2 pkt 24).
CREATE TABLE rest_intervals (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    exercise_id      INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    session_id       INTEGER REFERENCES sessions(id) ON DELETE CASCADE,
    set_log_id       INTEGER REFERENCES set_logs(id) ON DELETE SET NULL,
    started_at       INTEGER NOT NULL,
    ended_at         INTEGER,
    planned_s        INTEGER,                   -- cel po korektach ("+15 s" przesuwa to pole)
    actual_s         INTEGER,                   -- faktyczny czas, liczony przy domknieciu
    ended_reason     TEXT CHECK (ended_reason IN ('completed','skipped','extended','cancelled')),
    is_auto          INTEGER NOT NULL           -- bez DEFAULT: wartosc ustawia model Kotlina
);
-- Trzy indeksy, wszystkie po kluczach obcych: tabela rosnie najszybciej w bazie
-- (kilkanascie wierszy na trening), a statystyki pytaja o przerwy jednego
-- cwiczenia i jednej sesji.
CREATE INDEX idx_rest_exercise ON rest_intervals(exercise_id, started_at);
CREATE INDEX idx_rest_session ON rest_intervals(session_id);
CREATE INDEX idx_rest_set ON rest_intervals(set_log_id);

-- -----------------------------------------------------------------------------
-- 6. KARTA POSTACI (waga + customowe wskazniki)                [WDROZONE M8]
-- -----------------------------------------------------------------------------
-- Wskaznik trzyma nazwe w bazie (jest edytowalna), a wbudowany ma dodatkowo
-- builtin_key: dopoki go ma, UI pokazuje nazwe z zasobow ("Waga" / "Weight").
-- Pierwsza wlasna nazwa czysci klucz (PLAN.md 4.2 pkt 47).
-- Karta jest liczbowa - wykresy zmian sa jej sensem, a z tekstu wykresu nie ma
-- (PLAN.md 4.2 pkt 49), dlatego jest samo value_num, bez kind i value_text.
CREATE TABLE metric_types (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,                  -- 'Weight', 'Biceps', albo nazwa uzytkownika
    unit        TEXT,                           -- 'kg', 'cm', '%'
    direction   TEXT NOT NULL
                CHECK (direction IN ('up_good','down_good','neutral')),
    target_num  REAL,                           -- opcjonalny cel
    builtin_key TEXT,                           -- 'weight', 'biceps'; NULL = wlasna nazwa
    is_archived INTEGER NOT NULL,
    position    INTEGER NOT NULL,
    created_at  INTEGER NOT NULL
);
-- Dziesiec wbudowanych wskaznikow (Waga, Obwod klatki, Obwod bicepsa, Obwod
-- pasa, Obwod uda, Obwod lydki, Tluszcz (%), Szyja, Barki, Przedramie) wpisuje
-- migracja 7 -> 8: lista jest stala, wiec import przy starcie jest niepotrzebny.

CREATE TABLE body_entries (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_type_id INTEGER NOT NULL REFERENCES metric_types(id) ON DELETE CASCADE,
    date           TEXT NOT NULL,               -- 'YYYY-MM-DD'
    measured_at    INTEGER NOT NULL,
    value_num      REAL NOT NULL,
    note           TEXT
);
-- Jeden indeks unikalny zamiast dwoch: pilnuje "jeden pomiar na dzien"
-- i obsluguje zwykle zapytania po wskazniku (liczy sie od lewej kolumny).
CREATE UNIQUE INDEX idx_body_uniq ON body_entries(metric_type_id, date);

-- -----------------------------------------------------------------------------
-- 7. IMPORT SEEDOW I METADANE
-- -----------------------------------------------------------------------------
-- [M9, WDROZONE] Dziennik importow katalogow (docs/PLAN.md 6.13):
-- wersje seedow z APK dalej leza w app_meta (tam liczy sie stan: "ktora wersja
-- jest w bazie"), a tutaj jest historia zdarzen - skad przyszly dane, co z tego
-- wyszlo i ktore pliki zostaly odrzucone. Wiersze tylko sie dopisuje.
-- stats_json: {"status":"done","imported":5,"skipped":2,"total":7}
--          albo {"status":"rejected","imported":0,"skipped":0,"total":0,"error":"bad_json"}
CREATE TABLE seed_imports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    seed_id     TEXT NOT NULL,                  -- nazwa pliku uzytkownika albo 'assets/seed/exercises.json'
    version     TEXT,                           -- generated_at z pliku, jesli jest
    checksum    TEXT,                           -- SHA-256 tresci pliku (rozpoznanie "ten sam plik")
    imported_at INTEGER NOT NULL,
    stats_json  TEXT
);
CREATE INDEX idx_seed_imports_key ON seed_imports(seed_id, checksum);

-- WDROZONE (M0). Pary klucz-wartosc na stan wewnetrzny aplikacji:
-- exercises_seed_version, programs_seed_version, motyw, ...
CREATE TABLE app_meta (
    meta_key   TEXT PRIMARY KEY,
    meta_value TEXT NOT NULL,
    updated_at INTEGER NOT NULL
);
