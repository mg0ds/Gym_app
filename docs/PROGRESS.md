# Stan prac

## 27.10.2026 — po M10h

Zrobione i wypchnięte: commit `aa990af` (M10h) na `main`, CI zielone
(run `39349646759`), drzewo czyste. Baza w wersji **11**.

- typy zapisu serii (`ExerciseTracking`: ciężar + powtórzenia, powtórzenia,
  czas, czas + powtórzenia, dystans) z migracją `10 → 11`,
- „Ostatnio” na karcie ćwiczenia (poprzedni zakończony trening, także rampa),
- kolor kafelka po wykonaniu planu i wejście w szczegóły ćwiczenia z treningu,
- przerwa domykana następną serią (dźwięk już jej nie zamyka),
- wykres ze znacznikami punktów i linią najlżejszej serii, tabela wykonań
  zależna od typu ćwiczenia.
- 322 testy JVM, lint: 0 błędów / 46 ostrzeżeń,
- podpisany APK: `app/build/outputs/apk/release/app-release.apk`
  (klucz `gymtracker`, alias `gymtracker`).

## Do zrobienia

1. Ręczny przebieg `docs/QA.md` rozdział 14 na telefonie (M10h) — w tej rundzie
   nie było podłączonego emulatora.
2. Domknąć to, co wyjdzie z testu na siłowni.
3. Starsze punkty, świadomie odłożone:
   - off-by-one w `observeWorkoutsBetween`,
   - ekran prośby o `ACTION_REQUEST_SCHEDULE_EXACT_ALARM`,
   - podział ekranów-potworów (`SessionScreen`, `CalendarScreen`,
     `SettingsScreen`),
   - tag wydania przy `versionName 0.1.0`.
