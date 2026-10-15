-- =============================================================================
-- GymTracker - schemat bazy lokalnej (SQLite / Room)
-- Plik referencyjny. Zrodlo prawdy w kodzie: app/src/main/java/.../data/db/*.
-- Wersja: 1 (2026-10-15)
--
-- Zasady:
--  * Wszystko lokalnie, zero chmury.
--  * Daty kalendarzowe trzymamy jako TEXT 'YYYY-MM-DD' (bez stref -> brak bledow DST).
--  * Znaczniki czasu jako INTEGER (epoch millis, UTC).
--  * Listy (np. miesnie dodatkowe) jako TEXT z JSON - proste i wystarczajace.
--  * Klucze zewnetrzne: external_id = slug ze strony (stabilny klucz naturalny).
-- =============================================================================

PRAGMA foreign_keys = ON;

-- -----------------------------------------------------------------------------
-- 1. KATALOG C WICZE  (rozszerzalny przez uzytkownika)
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
    instructions        TEXT,                   -- JSON: ["krok 1", "krok 2"]
    youtube_url         TEXT,                   -- znormalizowany link (watch?v=ID)
    default_sets        INTEGER,
    default_reps_min    INTEGER,
    default_reps_max    INTEGER,
    default_rest_s      INTEGER,                -- domyslna przerwa w sekundach
    notes               TEXT,                   -- komentarz uzytkownika do cwiczenia
    is_favourite        INTEGER NOT NULL DEFAULT 0,
    is_archived         INTEGER NOT NULL DEFAULT 0,
    created_at          INTEGER NOT NULL,
    updated_at          INTEGER NOT NULL
);
CREATE INDEX idx_exercises_muscle  ON exercises(muscle_group);
CREATE INDEX idx_exercises_ext     ON exercises(external_id);

-- Wyszukiwanie odporne na polskie znaki diakrytyczne.
CREATE VIRTUAL TABLE exercises_fts USING fts5(
    name, muscle_group, equipment,
    content='exercises', content_rowid='id',
    tokenize="unicode61 remove_diacritics 2"
);

-- -----------------------------------------------------------------------------
-- 2. ZALACZNIKI C WICZENIA (zdjecia, filmy, linki)
--    Pliki fizycznie w filesDir/media/, w bazie trzymamy tylko sciezke.
-- -----------------------------------------------------------------------------
CREATE TABLE exercise_media (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    exercise_id     INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    kind            TEXT NOT NULL CHECK (kind IN ('image','video','youtube','link')),
    remote_uri      TEXT,                       -- oryginalny content:// lub URL (tylko do wgladu)
    local_path      TEXT,                       -- sciezka wzgledna w filesDir/media
    thumbnail_path  TEXT,
    caption         TEXT,
    duration_ms     INTEGER,
    size_bytes      INTEGER,
    position        INTEGER NOT NULL DEFAULT 0,
    created_at      INTEGER NOT NULL
);
CREATE INDEX idx_media_exercise ON exercise_media(exercise_id, position);

-- -----------------------------------------------------------------------------
-- 3. PLANY TRENINGOWE I ICH DNI
-- -----------------------------------------------------------------------------
CREATE TABLE plans (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    external_id  TEXT UNIQUE,                   -- np. 'muscle-strength-full-body-workout-routine'
    name         TEXT NOT NULL,
    description  TEXT,
    source_url   TEXT,
    author       TEXT,
    is_active    INTEGER NOT NULL DEFAULT 0,
    is_archived  INTEGER NOT NULL DEFAULT 0,
    created_at   INTEGER NOT NULL,
    updated_at   INTEGER NOT NULL
);

CREATE TABLE plan_days (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id      INTEGER NOT NULL REFERENCES plans(id) ON DELETE CASCADE,
    name         TEXT NOT NULL,                 -- 'Workout A'
    day_of_week  INTEGER,                       -- 0=poniedzialek .. 6=niedziela, NULL = brak stalej
    position     INTEGER NOT NULL,              -- kolejnosc w rotacji
    note         TEXT,
    UNIQUE (plan_id, position)
);

CREATE TABLE plan_day_exercises (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_day_id         INTEGER NOT NULL REFERENCES plan_days(id) ON DELETE CASCADE,
    exercise_id         INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    position            INTEGER NOT NULL,
    target_sets         INTEGER,
    target_reps_min     INTEGER,
    target_reps_max     INTEGER,
    target_reps_unit    TEXT NOT NULL DEFAULT 'reps'
                        CHECK (target_reps_unit IN ('reps','seconds','meters')),
    target_weight_kg    REAL,
    rest_seconds        INTEGER,
    superset_group      INTEGER,                -- te same wartosci = superseria
    alternative_group   TEXT,                   -- 'pull-ups-or-inverted-rows'
    is_default_choice   INTEGER NOT NULL DEFAULT 1,
    note                TEXT
);
CREATE INDEX idx_pde_day ON plan_day_exercises(plan_day_id, position);

-- -----------------------------------------------------------------------------
-- 4. HARMONOGRAM (kalendarz)
--    Regula ("co ile dni") -> jednorazowe rozwini cie do scheduled_sessions.
--    Dzieki temu zapytania kalendarza sa trywialne, a zmiana reguly = przeliczenie.
-- -----------------------------------------------------------------------------
CREATE TABLE recurrences (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_id        INTEGER REFERENCES plans(id) ON DELETE CASCADE,
    plan_day_id    INTEGER REFERENCES plan_days(id) ON DELETE CASCADE,
    rotate_days    INTEGER NOT NULL DEFAULT 1,  -- 1 = ten sam dzien; 0 = rotacja po plan_days
    start_date     TEXT NOT NULL,               -- 'YYYY-MM-DD'
    end_date       TEXT,                        -- NULL = bezterminowo
    interval_days  INTEGER NOT NULL DEFAULT 1 CHECK (interval_days >= 1),  -- "co ile dni"
    weekdays_mask  INTEGER,                     -- bitmaska bit0=pon; NULL = bez ograniczen
    skip_dates     TEXT,                        -- JSON ["2026-11-01"]
    is_active      INTEGER NOT NULL DEFAULT 1,
    created_at     INTEGER NOT NULL
);

CREATE TABLE scheduled_sessions (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    date          TEXT NOT NULL,                -- 'YYYY-MM-DD'
    plan_id       INTEGER REFERENCES plans(id) ON DELETE SET NULL,
    plan_day_id   INTEGER REFERENCES plan_days(id) ON DELETE SET NULL,
    recurrence_id INTEGER REFERENCES recurrences(id) ON DELETE SET NULL,
    title         TEXT,
    status        TEXT NOT NULL DEFAULT 'planned'
                  CHECK (status IN ('planned','done','skipped','moved')),
    session_id    INTEGER,                      -- realizacja (FK dopisane po utworzeniu sessions)
    moved_to      TEXT,
    created_at    INTEGER NOT NULL
);
CREATE INDEX idx_sched_date   ON scheduled_sessions(date);
CREATE INDEX idx_sched_status ON scheduled_sessions(status, date);
CREATE INDEX idx_sched_rec    ON scheduled_sessions(recurrence_id);
-- Jedna zaplanowana sesja danego dnia planu na dany dzien:
CREATE UNIQUE INDEX idx_sched_uniq ON scheduled_sessions(date, plan_day_id)
    WHERE plan_day_id IS NOT NULL;

-- -----------------------------------------------------------------------------
-- 5. TRENING (realizacja) + TIMERY
-- -----------------------------------------------------------------------------
CREATE TABLE sessions (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    title            TEXT,
    plan_id          INTEGER REFERENCES plans(id) ON DELETE SET NULL,
    plan_day_id      INTEGER REFERENCES plan_days(id) ON DELETE SET NULL,
    started_at       INTEGER NOT NULL,          -- epoch ms
    finished_at      INTEGER,                   -- NULL = trening w toku
    duration_s       INTEGER,                   -- finished_at - started_at (cache)
    total_rest_s     INTEGER NOT NULL DEFAULT 0,-- suma przerw (uwzgledniona w czasie)
    active_s         INTEGER,                   -- duration_s - total_rest_s
    note             TEXT,
    created_at       INTEGER NOT NULL,
    updated_at       INTEGER NOT NULL
);
CREATE INDEX idx_sessions_started ON sessions(started_at);

CREATE TABLE session_exercises (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    session_id   INTEGER NOT NULL REFERENCES sessions(id) ON DELETE CASCADE,
    exercise_id  INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    plan_day_exercise_id INTEGER REFERENCES plan_day_exercises(id) ON DELETE SET NULL,
    position     INTEGER NOT NULL,
    note         TEXT
);
CREATE INDEX idx_session_ex ON session_exercises(session_id, position);

CREATE TABLE set_logs (
    id                    INTEGER PRIMARY KEY AUTOINCREMENT,
    session_exercise_id   INTEGER NOT NULL REFERENCES session_exercises(id) ON DELETE CASCADE,
    set_index             INTEGER NOT NULL,     -- 1..n
    weight_kg             REAL,
    reps                  INTEGER,              -- NULL dla serii na czas
    duration_s            INTEGER,              -- dla plank / serii na czas
    rpe                   REAL,                 -- 1..10, opcjonalne
    is_warmup             INTEGER NOT NULL DEFAULT 0,
    is_failure            INTEGER NOT NULL DEFAULT 0,
    rest_after_s          INTEGER,              -- faktyczna przerwa po tej serii
    rest_target_s         INTEGER,              -- ile bylo ustawione
    completed_at          INTEGER NOT NULL,
    UNIQUE (session_exercise_id, set_index)
);

-- Historia przerw: zrodlo podsumowan i srednich per cwiczenie.
CREATE TABLE rest_intervals (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    exercise_id      INTEGER NOT NULL REFERENCES exercises(id) ON DELETE CASCADE,
    session_id       INTEGER REFERENCES sessions(id) ON DELETE CASCADE,
    set_log_id       INTEGER REFERENCES set_logs(id) ON DELETE SET NULL,
    started_at       INTEGER NOT NULL,
    ended_at         INTEGER,
    planned_s        INTEGER,
    actual_s         INTEGER,
    ended_reason     TEXT CHECK (ended_reason IN ('completed','skipped','extended','cancelled')),
    is_auto          INTEGER NOT NULL DEFAULT 0 -- czy odliczanie ruszylo automatycznie po serii
);
CREATE INDEX idx_rest_exercise ON rest_intervals(exercise_id, started_at);

-- -----------------------------------------------------------------------------
-- 6. KARTA POSTACI (waga + customowe wskazniki)
-- -----------------------------------------------------------------------------
CREATE TABLE metric_types (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,                  -- 'Waga', 'Obwod klatki', 'Obwod bicepsa'
    unit        TEXT,                           -- 'kg', 'cm', '%'
    kind        TEXT NOT NULL DEFAULT 'numeric'
                CHECK (kind IN ('numeric','text','boolean')),
    direction   TEXT NOT NULL DEFAULT 'neutral'
                CHECK (direction IN ('up_good','down_good','neutral')),
    target_num  REAL,                           -- opcjonalny cel
    is_builtin  INTEGER NOT NULL DEFAULT 0,
    is_archived INTEGER NOT NULL DEFAULT 0,
    position    INTEGER NOT NULL DEFAULT 0,
    created_at  INTEGER NOT NULL
);

CREATE TABLE body_entries (
    id             INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_type_id INTEGER NOT NULL REFERENCES metric_types(id) ON DELETE CASCADE,
    date           TEXT NOT NULL,               -- 'YYYY-MM-DD'
    measured_at    INTEGER NOT NULL,
    value_num      REAL,
    value_text     TEXT,
    note           TEXT
);
CREATE INDEX idx_body_type_date ON body_entries(metric_type_id, date);
CREATE UNIQUE INDEX idx_body_uniq ON body_entries(metric_type_id, date);

-- -----------------------------------------------------------------------------
-- 7. IMPORT SEEDOW I METADANE
-- -----------------------------------------------------------------------------
CREATE TABLE seed_imports (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    seed_id     TEXT NOT NULL,                  -- 'muscle-strength-full-body-workout-routine'
    version     TEXT,                           -- generated_at ze seeda
    checksum    TEXT,
    imported_at INTEGER NOT NULL,
    stats_json  TEXT
);

CREATE TABLE app_metadata (
    key   TEXT PRIMARY KEY,
    value TEXT
);
