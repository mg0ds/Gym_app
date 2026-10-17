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
| 1.4 | Start, wymuś zatrzymanie aplikacji (`Ustawienia → Aplikacje → Zatrzymaj`), otwórz | Sesja odzyskana, czas liczony od `started_at`; serie i notatka na miejscu |
| 1.5 | Start, zrestartuj telefon, otwórz | Sesja odzyskana |
| 1.6 | Start, zmień datę/godzinę w systemie do przodu o 1 h, wróć | Czas **przesuwa się** o tę godzinę — świadoma cena zegara ściennego (PLAN §4.2 pkt 20); scenariusz kontrolny, nie błąd |
| 1.7 | Start, obróć ekran 5× | Czas ciągły, brak resetu |
| 1.8 | Zakończ trening | `finished_at` zapisany; czas treningu = `finished_at - started_at - pauzy` |
| 1.9 | Tryb oszczędzania baterii włączony, 20 min treningu przy zgaszonym ekranie | Notyfikacja ongoing widoczna i odlicza dalej, czas poprawny |
| 1.10 | Start, zablokuj ekran, sprawdź notyfikację | Widoczna nazwa dnia i odliczający czas (chronometr systemowy) |
| 1.11 | W notyfikacji „Pauza” → czas stoi; „Wznów” → liczy dalej | Licznik na ekranie i w notyfikacji pokazują to samo |
| 1.12 | Pauza na 5 min, potem zakończ trening | Czas treningu **bez** tych 5 min; `paused_total_ms = 300000`, `paused_at_ms = NULL` |
| 1.13 | Pauza, potem ubij aplikację i wróć po 10 min | Zegar nadal w pauzie (nie doliczył 10 min do treningu) |
| 1.14 | Ubij aplikację, potem „Zakończ” z notyfikacji | Trening zamknięty w bazie (`status='finished'`), notyfikacja znika |
| 1.15 | Ukryj aplikację z listy ostatnich zadań (bez ubijania) | Notyfikacja zostaje (`stopWithTask="false"`), po powrocie czas poprawny |
| 1.16 | Odmów zgody na powiadomienia (Android 13+), zacznij trening | Zgoda pytana przy pierwszym treningu; bez zgody czas nadal się liczy i zapisuje |
| 1.17 | Kliknij notyfikację | Otwiera ekran treningu (nie nową kopię aplikacji) |

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

## 3. Kalendarz, reguły i harmonogram (M3)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 3.1 | Kryterium M3: plan A/B/C, reguła „od 1.10.2026, do 31.12.2026, co 3 dni”, rotacja dni | Podgląd pokazuje 31 treningów: pierwszy 1.10 (A), ostatni 30.12 (poprawny rytm „co 3 dni”) |
| 3.2 | Ten sam plan w kalendarzu po zapisie | Wpisy dokładnie na datach z podglądu, nazwy dni A/B/C zgodne z podglądem |
| 3.3 | Reguła „co 1 dzień” od 1.10 do 31.10 | 31 wpisów, każdy dzień miesiąca zajęty |
| 3.4 | Reguła przez zmianę czasu (29.03.2026) | Dni kalendarzowe bez przeskoku i bez powtórzonego dnia |
| 3.5 | „Co ile dni” = 0, puste albo „abc” | Pole podświetlone, zapis zablokowany, komunikat o minimum 1 |
| 3.6 | Data do wcześniejsza niż data od | Zapis zablokowany, komunikat; „Zapisz” wyszarzony |
| 3.7 | Data od albo do poza oknem 20 tygodni | Kalendarz nie pozwala wybrać dnia poza oknem; komunikat mówi, do kiedy planujemy |
| 3.8 | Tryb „Wybrane dni”: dotknij dni, w tym poza horyzontem | Dni poza oknem nie reagują na dotyk; podgląd pokazuje wybrane daty z rotacją dni |
| 3.9 | Reguła tylko z pon./śr./pt. (dni tygodnia) | Wpisy wyłącznie w te dni, pozostałe wolne |
| 3.10 | Menu wpisu: „przenieś” na inny dzień | Wpis na nowej dacie, status bez zmian (osobnego „przeniesiony” nie ma) |
| 3.11 | Menu wpisu: „oznacz jako odbyty” / „pominięty” / „przywróć do zaplanowanych” | Kropka zmienia kolor zgodnie ze statusem, historia zostaje |
| 3.12 | Zapisz identyczną regułę drugi raz | Jeden zestaw wpisów (bez duplikatów); istniejące (np. odbyty) nie znikają |
| 3.13 | Edytuj regułę po 2 odbytych treningach | Odbyte wpisy zostają w historii, przyszłe przeliczone |
| 3.14 | Usuń regułę | Wpisy „zaplanowane” znikają, odbyte zostają bez reguły |
| 3.15 | Usuń plan, który miał zaplanowane daty | Wpisy zostają bez planu („Wolny trening”); start z takiego wpisu tworzy sesję bez ćwiczeń, bez crasha |
| 3.16 | Otwórz apkę po kilku dniach, przewiń kalendarz w przód | Wpisy dosypane do nowego horyzontu (20 tyg.); pominięte i przeniesione nie wracają |
| 3.17 | Akcja „Dziś” w nagłówku | Kalendarz wraca do bieżącego miesiąca |
| 3.18 | Aktualizacja z M2 (baza v3: plan, sesja, własne ćwiczenie) | Migracja 3→4 bez błędu; plany i historia treningów zostają, kalendarz pusty |
| 3.19 | Interfejs po angielsku | Nazwy miesięcy i dni po angielsku („Mon, 1 Oct”), format dat spójny z resztą |
| 3.20 | W szczegółach planu sekcja „Harmonogram” bez reguł | Podpowiedź i przycisk „Zaplanuj treningi” |
| 3.21 | „Zaplanuj treningi” w planie bez dni | Sekcja się nie pokazuje (nie ma czego planować); dodaj dzień, żeby zaplanować |
| 3.22 | „Zmień” przy regule | Formularz wypełniony danymi reguły, podgląd widoczny od razu; zapis nadpisuje tę samą regułę |
| 3.23 | „Usuń” przy regule → potwierdzenie | Reguła znika z listy, wpisy „zaplanowane” znikają z kalendarza, odbyte zostają |
| 3.24 | Start treningu z wpisu w kalendarzu, potem „Zakończ trening” | Wpis odhaczony jako odbyty (zielona kropka) |
| 3.25 | Start z wpisu w kalendarzu, potem „Przerwij trening” | Wpis zostaje „zaplanowany” (przerwany trening nie odhacza dnia) |
| 3.26 | Start z karty dnia planu, gdy ten dzień jest też zaplanowany na dziś | Sesja wiąże się z wpisem; po „Zakończ trening” wpis odhaczony (M4 domyka odroczenie z M3) |
| 3.27 | Ten sam dzień planu zaplanowany na inny dzień niż dziś, start z planu | Sesja powstaje bez wiązania wpisu (wpis zostaje „zaplanowany”), bez odhaczania czegokolwiek innego |
| 3.28 | Aktualizacja z M3 (baza v4: plany, kalendarz, sesje, ćw. własne) | Migracja 4→5 bez błędu; treningi i wpisy bez zmian, tabela serii pusta |

## 3a. Logowanie serii i notatka (M4)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 3a.1 | Dodaj serię: ciężar 60, powtórzenia 5 | Wiersz „1  60 kg × 5”, postęp „1 z 5”, pola startują z poprzedniej serii |
| 3a.2 | Dodaj drugą serię bez wpisywania niczego | Formularz wstępnie wypełniony 60 kg × 5 (poprzednia seria) |
| 3a.3 | Ćwiczenie na czas (plank `3 × 60 s`) | Pole „Czas (s)”, podsumowanie „60 s”; pole powtórzeń się nie pokazuje |
| 3a.4 | Zapisz serię bez wartości (albo 0) | „Zapisz” wyszarzony, komunikat o brakującej wartości; seria nie trafia do bazy |
| 3a.5 | Ćwiczenie bez ciężaru (pompki) | Pusty ciężar jest w porządku — wystarczy liczba powtórzeń |
| 3a.6 | Kliknij zapisaną serię → zmień ciężar | Wartość poprawiona, numer serii i czas zapisu bez zmian |
| 3a.7 | Usuń serię ze środka (1, 2, 3 → usuń 2) | Zostają „1” i „2” (przenumerowane), ciężary bez dziur |
| 3a.8 | Przełącznik „rozgrzewka” | Wiersz oznaczony „rozgrzewka”; seria nadal w historii |
| 3a.9 | Wejdź na zakończony trening z historii | Serie widoczne, ale bez „+ Dodaj serię” i bez pauzy (historia zamknięta) |
| 3a.10 | Zapisz notatkę „plecy bolą”, ubij aplikację, wróć | Notatka na ekranie treningu (zapisana od razu) |
| 3a.11 | „Wyczyść” w notatce | Notatka znika (`note = NULL`), karta pokazuje „Dodaj notatkę” |
| 3a.12 | Wyjdź z ekranu treningu strzałką, potem wróć z „Kontynuuj trening” | Trening trwa dalej (zegar i notyfikacja nie zostały zatrzymane) |
| 3a.13 | Interfejs po angielsku | „Set 1: 60 kg × 5”, „Add a set”, „Pause/Resume/Finish” w notyfikacji, „%1$d of %2$d” w postępie |


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
| 5.1 | Pierwsze uruchomienie na czystej instalacji | Katalog: 23 ćwiczenia; plan startowy z 3 dniami wczytuje się razem z nim (albo przyciskiem w Ustawieniach) |
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

## 8. Plany i trening z planu (M2)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 8.1 | Aktualizacja z M1 (baza v2, własne ćwiczenie + ulubione) | Migracja 2→3 bez błędu; katalog, ulubione i własne ćwiczenia zostają; plan startowy się importuje |
| 8.2 | Wejdź w plany po czystej instalacji | 1 plan („Muscle & Strength Full Body Workout Routine”), 3 dni: A/pon, B/śr, C/pt, liczby ćwiczeń 8/8/9 |
| 8.3 | Otwórz dzień B | Pozycje z celami (`3 × 5`, `2 × 8-12`), deska jako `3 × 60 s`, przy ćwiczeniu 4 para „4 / 4a **albo**” z etykietą „wybór domyślny” |
| 8.4 | Dodaj własny plan („+” → nazwa) | Plan na liście, w środku pusto z podpowiedzią o dodaniu dnia |
| 8.5 | Dodaj dzień z dniem tygodnia | Dzień na liście z etykietą dnia tygodnia i „0 ćwiczeń”; zmiana nazwy i usunięcie działają |
| 8.6 | Dodaj ćwiczenie z katalogu (szukanie w wyborze) | Pozycja na końcu dnia z domyślnym celem `3 serie` |
| 8.7 | „Zmień serie i powtórzenia”: 5 / 5-5 / 60 kg / 120 s | Wiersz pokazuje `5 × 5 • przerwa 120 s` |
| 8.8 | Ustaw jednostkę „sekundy” i 60 | Wiersz pokazuje `3 × 60 s` |
| 8.9 | „Dodaj alternatywę” do pozycji | Nowa pozycja jako `Na` z „albo …”, podstawowa ma etykietę „wybór domyślny” |
| 8.10 | Na alternatywie „Ustaw jako podstawowe” | Etykieta „wybór domyślny” przechodzi na nią, kolejność bez zmian |
| 8.11 | „Połącz w superserię z następnym” | Obie pozycje z etykietą „superseria 1”; „Rozłącz superserię” usuwa etykiety |
| 8.12 | Przeciągnij pozycję (przytrzymaj i przesuń) | Po upuszczeniu pozycja zostaje na nowym miejscu, alternatywa idzie razem z podstawową, numeracja superserii bez zmian |
| 8.13 | Przeciągnij i upuść w to samo miejsce | Kolejność **bez zmian** (nic się nie zapisuje) |
| 8.14 | „Przenieś wyżej”/„Przenieś niżej” z menu wiersza | To samo co przeciąganie (droga bez gestu) |
| 8.15 | „Rozpocznij” na dniu | Ekran treningu: „Trening w toku”, licznik rośnie, lista celów; alternatywy **nie** ma na liście |
| 8.16 | Wyjdź z treningu na Dziś | Karta „Masz rozpoczęty trening” → „Kontynuuj trening” wznawia z licznikiem od `started_at` |
| 8.17 | W trakcie treningu ustal nowy (z planu) | Poprzedni zamyka się jako `cancelled`, nowy jest aktywny; tylko jeden trwa |
| 8.18 | „Zakończ trening” | Karta znika; sesja `finished` z czasem i kopią ćwiczeń (tylko wybory domyślne) |
| 8.19 | „Przerwij trening” → potwierdzenie | Karta znika; sesja `cancelled`, nie liczona jako odbyta |
| 8.20 | Usuń plan, który ma zakończony trening | Plan i dni znikają, historia treningu zostaje (z nazwą dnia z planu) |
| 8.21 | Usuń ćwiczenie używane w planie i treningu | Potwierdzenie mówi, ile pozycji **zostaje** („Zostanie w planach: N pozycji.”); ćwiczenie znika z katalogu i z wyszukiwania |
| 8.22 | Po usunięciu ćwiczenia otwórz plan i kartę treningu z historii | Wiersze dalej są, z nazwą ćwiczenia — historia treningów nie ginie |
| 8.23 | „Wczytaj plany startowe” przy już zaimportowanym planie | „Gotowe: plany +0, pominięto 1, dni 0.”; brak duplikatów dni i pozycji, zmiany użytkownika **nie** nadpisane |
