# Stan prac

## 07.11.2026 — M10i na telefonie

Wydanie M10i (commit `4ad2a60`) wdrożone na telefonie **Samsung SM-S928B**
(Android 17, API 37) — aktualizacja w miejscu, bez utraty danych.

- zainstalowany APK release `0.1.0` z podpisem `CN=GymTracker` — ten sam
  certyfikat co poprzednia wersja, więc `adb install -r` przyjął aktualizację
  bez odinstalowywania,
- aplikacja startuje bez `FATAL EXCEPTION`,
- historia i plany zostały na miejscu (m.in. treningi z 1.11 i 4.11.2026),
- CI po commicie `4ad2a60`: zielone (run `41945274912`).

## 05.11.2026 — po M10i

Runda siedmiu poprawek UX i nowa szata graficzna. Baza w wersji **11**, bez
migracji.

- usuwanie ćwiczenia z trwającego treningu (long-press + potwierdzenie; kasuje
  serie i przerwy, przenumerowuje pozycje),
- potwierdzenie zakończenia treningu i „Wznów trening” do końca dnia
  (czas przerwy doliczony do pauz, wpis w kalendarzu wraca na „zaplanowany”),
- widoczny kosz przy wpisie w kalendarzu z potwierdzeniem,
- wyjaśnienie szacowanego 1RM w statystykach i szczegółach ćwiczenia,
- „ładna” oś Y wykresów (pełne wartości i krok, bez 32/42/52),
- etykiety osi z motywu aplikacji (koniec białych napisów w jasnym motywie
  przy ciemnym telefonie),
- redesign „Obsidian & Citrus”: atramentowa zieleń, mech, porcelana, limonka,
  szeryfowe nagłówki, pływająca dolna nawigacja, ciemny herb kalendarza,
  ciemny panel zegara i limonkowy pasek przerwy.

Weryfikacja: **331 testów JVM** (0 błędów), `lintDebug`, `assembleDebug`,
`bundleRelease`; ręcznie na emulatorze API 36: long-press i potwierdzenie,
zakończenie → wznowienie, kosz w kalendarzu, wykres z etykietami
`40/60/80/100/120` i ciemnymi opisami osi.

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
