# Scenariusze testów ręcznych (QA)

Testy automatyczne nie złapią zachowania timera na prawdziwym telefonie z
agresywnym zarządzaniem energią. Ten plik to lista do przeklikania przed
każdym wydaniem. Każdy scenariusz: **kroki → oczekiwany wynik**.

## 1. Timer treningu

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 1.1 | Start treningu, zablokuj ekran, poczekaj 10 min | Po odblokowaniu czas = 10 min (+/- 1 s) |
| 1.2 | Start, włącz tryb samolotowy, 5 min | Czas leci dalej (brak zależności od sieci) |
| 1.3 | Start, zamknij aplikację z ostatnich, poczekaj 5 min, otwórz | Propozycja „Kontynuuj trening”, czas poprawny |
| 1.4 | Start, wymuś zatrzymanie aplikacji (`Ustawienia → Aplikacje → Zatrzymaj`), otwórz | Sesja odzyskana, czas z `startedAt` |
| 1.5 | Start, zrestartuj telefon, otwórz | Sesja odzyskana |
| 1.6 | Start, zmień datę/godzinę w systemie do przodu o 1 h, wróć | Czas treningu nie „skacze” (licznik monotoniczny) |
| 1.7 | Start, obróć ekran 5× | Czas ciągły, brak resetu |
| 1.8 | Zakończ trening | `finished_at` zapisany; czas całkowity i „pod sztangą” różnią się o sumę przerw |
| 1.9 | Tryb oszczędzania baterii włączony, 20 min treningu | Notyfikacja ongoing widoczna, czas poprawny |

## 2. Timer przerwy

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 2.1 | Zaloguj serię, poczekaj do końca przerwy | Dźwięk + wibracja + notyfikacja w momencie 0 |
| 2.2 | „+15 s” w połowie przerwy | Odliczanie wydłużone, `ended_reason='extended'` |
| 2.3 | „Pomiń” | Przerwa zapisana jako `cancelled`, **nie wliczona** do średniej |
| 2.4 | Zablokuj ekran na czas przerwy | Alarm odpala punktualnie (±2 s) |
| 2.5 | Otwórz szczegóły ćwiczenia po 3 treningach | „Średnia przerwa” i „Ostatnio” zgodne z tym, co było ustawiane |
| 2.6 | Ustaw przerwę 10 min, przerwij po 20 s | Wartość odfiltrowana ze statystyk (poza zakresem 10–900 s) |
| 2.7 | Odrzuć uprawnienie „Alarmy i przypomnienia” | Przerwa nadal działa (z tolerancją), w Ustawieniach widoczna informacja |

## 3. Kalendarz i reguły

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 3.1 | Reguła: od 01.10.2026, do 31.12.2026, co 3 dni | Poprawne daty (sprawdź na kartce: 1, 4, 7, 10… paź) |
| 3.2 | Ta sama reguła + rotacja A/B/C | Daty naprzemiennie A, B, C, A… |
| 3.3 | Reguła przez zmianę czasu (29.03.2026) | Dni kalendarzowe bez przeskoku/powtórzenia |
| 3.4 | „Co ile dni” = 0 lub puste | Walidacja blokuje zapis z komunikatem |
| 3.5 | Data do < data od | Walidacja blokuje zapis |
| 3.6 | Dodaj wyjątek (skip) na 25.12 | Wpis znika, pozostałe bez zmian |
| 3.7 | Edytuj regułę po 2 zrobionych treningach | Zrobione zostają w historii, przyszłe przeliczone |
| 3.8 | Usuń plan, który miał zaplanowane daty | Daty zostają (bez planu → „Wolny trening”), brak crasha |
| 3.9 | Reguła bezterminowa, przewiń kalendarz 12 mies. w przód | Daty dosypywane płynnie, bez zacięcia |

## 4. Media i YouTube

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 4.1 | Dodaj zdjęcie z galerii | Skopiowane do `filesDir/media/...`, widoczne w szczegółach |
| 4.2 | Dodaj film 40 MB | Odtwarza się w apce, czas trwania widoczny |
| 4.3 | Dodaj film 200 MB | Czytelny komunikat o limicie rozmiaru |
| 4.4 | Wklej link `youtu.be/ID`, `watch?v=ID`, `shorts/ID` | Każdy rozpoznany, ta sama miniatura i odtwarzanie |
| 4.5 | Wklej zwykły link (nie YouTube) | Zapisany jako link, bez próby odtwarzania |
| 4.6 | Wyłącz internet, otwórz ćwiczenie z filmem | Miniatura z cache (jeśli była), przycisk odtwarzania nie crashuje |
| 4.7 | Usuń ćwiczenie z 3 załącznikami | Pliki usunięte z pamięci (sprawdź w menedżerze plików) |

## 5. Dane i backup

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 5.1 | Pierwsze uruchomienie na czystej instalacji | Import katalogu: 23 ćwiczenia (plan i dni dochodzą w M2) |
| 5.2 | Zmień nazwę ćwiczenia z seeda, uruchom re-import | Nazwa użytkownika **nie** nadpisana |
| 5.3 | Eksport ZIP → odinstaluj → zainstaluj → import | Wszystkie dane i media wracają |
| 5.4 | Import z pliku o wyższym `schemaVersion` | Czytelny błąd, brak częściowego importu |
| 5.5 | Wyszukaj „obwod” (bez ogonka) | Znajduje „obwód” |
| 5.6 | Tryb samolotowy + pełne przejście apki | Wszystko działa, żadne żądanie sieciowe nie jest wymagane |

## 6. Dostępność

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 6.1 | TalkBack: przejdź przez start treningu | Wszystkie przyciski mają etykiety, timer ogłaszany bez spamu |
| 6.2 | Czcionka 200% | Brak uciętych tekstów, przyciski nadal klikalne |
| 6.3 | Motyw ciemny + dynamiczne kolory | Wykresy czytelne, brak białych „dziur” |

## 7. Katalog ćwiczeń (M1)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 7.1 | Pierwsze uruchomienie na czystej instalacji | Katalog sam się wypełnia 23 ćwiczeniami z danych startowych |
| 7.2 | Wpisz „bench” w szukanie | Zostają tylko ćwiczenia pasujące do wpisu |
| 7.3 | Ćwiczenie „Wyciskanie sztangą leżąc”, wpisz „sztanga” (bez ogonka) | Ćwiczenie znalezione (odporność na polskie znaki) |
| 7.4 | Wpisz „sztan” (urwany wyraz) | Ćwiczenie znalezione (szukanie prefiksowe) |
| 7.5 | Wpisz tekst bez wyników | „Brak wyników” i podpowiedź z powtórzonym wpisem |
| 7.6 | Filtr partii „Brzuch” | Tylko ćwiczenia z tej partii; „Wszystkie” wraca do pełnej listy |
| 7.7 | Serduszko w wierszu listy | Ćwiczenie pojawia się w „Ulubione”; stan przeżywa restart apki |
| 7.8 | Dodaj własne ćwiczenie (nazwa + serie/powtórzenia/przerwa) | Widać je w katalogu i znajduje je wyszukiwanie |
| 7.9 | Zapisz ćwiczenie bez nazwy | Zapis zablokowany, komunikat przy polu nazwy |
| 7.10 | Zmień nazwę i ulubione ćwiczenia z seeda, potem „Wczytaj katalog startowy” | „Gotowe: dodano 0, pominięto 23”; zmiany użytkownika **nie** nadpisane |
| 7.11 | Usuń własne ćwiczenie (kosz → potwierdzenie) | Znika z katalogu, ekran wraca do listy |
| 7.12 | Wklej `youtu.be/ID`, `watch?v=ID`, `shorts/ID` | Każdy rozpoznany jako film YouTube (podpowiedź pod polem) |
| 7.13 | Wklej zwykły link | Podpowiedź „Zapiszemy to jako zwykły link”; w szczegółach „Zobacz źródło” |
| 7.14 | Aktualizacja apki z wersji M0 (baza v1, dane użytkownika) | Migracja 1→2 bez błędu, dane i motyw zostają |
