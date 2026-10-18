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

## 2. Timer przerwy (M5)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 2.1 | Zaloguj serię w ćwiczeniu z celem 90 s | Przerwa rusza sama, pasek pokazuje „cel 1:30”; w bazie wiersz `rest_intervals` z `planned_s = 90`, `is_auto = 1`, `ended_at = NULL` |
| 2.2 | Poczekaj do zera, patrząc na ekran | Dźwięk + wibracja + heads-up „Koniec przerwy” w momencie 0 (±0,5 s); pasek znika; `ended_reason = 'completed'` |
| 2.3 | Zablokuj ekran na czas przerwy (proces żyje) | To samo co 2.2 (±0,5 s) — przerwę domyka tyknięcie ekranu, a nie budzik, ale **i tak dzwoni** |
| 2.4 | Ubij aplikację w trakcie przerwy i nie budź ekranu | Przerwa domknięta jako `completed`; **budzik może się spóźnić do ~22 s** (okno `setAndAllowWhileIdle`, gdy system nie dał zgody na dokładne alarmy). Po włączeniu „Alarmy i przypomnienia” dla aplikacji — co do sekundy |
| 2.5 | „+15 s” w połowie przerwy | Odliczanie dłuższe o 15 s, `planned_s` rośnie, `ended_reason = 'extended'`, przerwa **wchodzi** do średniej |
| 2.6 | „-15 s” kilka razy | Cel nie schodzi poniżej 5 s (`ActiveRest.MIN_PLANNED_SECONDS`) |
| 2.7 | „Pomiń” | `ended_reason = 'skipped'`, `rest_after_s` puste, wiersz serii bez „przerwa …”, **poza** statystykami |
| 2.8 | Zaloguj kolejną serię przed końcem przerwy | Poprzednia przerwa `completed` z faktycznym czasem, wiersz serii pokazuje „przerwa 0:45”, bez dzwonienia (użytkownik właśnie wrócił do pracy) |
| 2.9 | Po 3 treningach spójrz na kartę ćwiczenia | „Przerwy: 3 • śr. 1:48” (ten trening) i „Średnia przerwa: 1:45 • ostatnio 2:05 (12 treningów)” — obie z pominięciem `skipped`/`cancelled` i czasów poza 10–900 s |
| 2.10 | Ustaw cel przerwy 10 min, przerwij po 20 s | Wartość **nie** wchodzi do średniej (poza zakresem 10–900 s) |
| 2.11 | Kliknij chip „przerwa 1:30” na karcie ćwiczenia | Dialog z efektywnym celem; zapis zmienia `session_exercises.rest_seconds` (kolejne przerwy w tym treningu), a nie kartę ćwiczenia ani ustawienie globalne |
| 2.12 | Zakończ trening przy trwającej przerwie | Przerwa `cancelled`, **poza** statystykami i bez dzwonienia |
| 2.13 | Pauza treningu przy trwającej przerwie | To samo co 2.12 |
| 2.14 | Nagłówek treningu w trakcie przerwy | „Pod sztangą 58:20 • przerwy 14:25” — suma przerw rośnie, gdy przerwa trwa |
| 2.15 | Po zakończeniu treningu: kalendarz → wpis → „Otwórz trening” | Sesja tylko do czytania, z tymi samymi średnimi przerw na kartach ćwiczeń |
| 2.16 | Notyfikacja usługi w trakcie przerwy | Tytuł = nazwa ćwiczenia, tekst „Przerwa”, odliczanie w dół, akcje „+15 s” i „Pomiń”; po przerwie tytuł wraca do nazwy dnia |
| 2.17 | Odmów zgody na powiadomienia (Android 13+) | Przerwa liczy się i zapisuje dalej; brak dzwonienia (system nie pozwala pokazać notyfikacji) — bez crasha |

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


## 4. Media: zdjęcia, filmy, link, kroki (M7)

Zasada: **wskazujemy plik, nie kopiujemy go.** Sprawdzając te scenariusze warto
zajrzeć do `filesDir` — po dodaniu zdjęcia i filmu ma tam być **tylko**
`thumbs/<uuid>.jpg`, bez katalogu `media/` i bez kopii oryginałów.

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 4.1 | Karta ćwiczenia → „Dodaj zdjęcie” → wybór z galerii | Kafelek z miniaturą od razu; w `filesDir/thumbs` przybywa jeden mały plik (~200 px), a nie kopia zdjęcia |
| 4.2 | Sprawdź `filesDir` po dodaniu zdjęcia i filmu | Tylko `thumbs/`; oryginały zostały w galerii |
| 4.3 | „Dodaj film” → wybór filmu | Kafelek z miniaturą z klatki i nakładką ▶; podgląd odtwarza film (Media3), widać czas trwania i rozmiar |
| 4.4 | Otwórz podgląd filmu i wyjdź strzałką w trakcie odtwarzania | Dźwięk milknie natychmiast (odtwarzacz zwolniony), nic nie gra w tle |
| 4.5 | Zablokuj i odblokuj ekran w trakcie odtwarzania | Film wraca w pauzie — sam się nie wznawia |
| 4.6 | Restart aplikacji | Kafelki z miniaturami na miejscu; zdjęcie otwiera się w podglądzie, film odtwarza |
| 4.7 | Restart urządzenia | Kafelki na miejscu; gdy system dał trwały dostęp (np. `ACTION_OPEN_DOCUMENT` na starszym Androidzie), pełny plik też działa — a gdy Photo Picker dostępu nie dał, podgląd pokazuje miniaturę i komunikat (nie pustkę) |
| 4.8 | Usuń zdjęcie z galerii (menedżer plików), wejdź w kafelek | Miniatura + „Miniatura — zdjęcia nie ma już w galerii albo dostęp do niego wygasł”; aplikacja nie crashuje, załącznik można odpiąć |
| 4.9 | W podglądzie „✎” → wpisz opis → zapisz; wróć i wejdź ponownie | Opis pod kafelkiem (jedna linia) i pod podglądem; wyczyszczenie pola usuwa opis |
| 4.10 | Podgląd → „🗑” → potwierdzenie | Kafelek znika, nasza miniatura usunięta z `filesDir/thumbs`, a **plik w galerii zostaje nietknięty** |
| 4.11 | Wklej link `youtu.be/ID`, `watch?v=ID`, `shorts/ID` w formularzu, otwórz kartę | Rozpoznany jako film YouTube; przycisk „Otwórz film” (bez miniatury — aplikacja nie ma sieci) |
| 4.12 | Stuknij „Otwórz film” | Otwiera się aplikacja YouTube (albo systemowy wybór); powrót zostawia stan ekranu |
| 4.13 | Tryb samolotowy + dodanie zdjęcia i filmu, potem „Otwórz film” | Dodawanie działa (zero sieci); link pokazuje systemowy komunikat o braku połączenia, aplikacja nie crashuje |
| 4.14 | Plik nieczytelny (usunięty w trakcie wyboru) albo nie zdjęcie/film | Komunikat „Nie udało się odczytać tego pliku z galerii” albo „Ten plik nie jest zdjęciem ani filmem”; **nic** nie trafia do listy |
| 4.15 | Formularz: kroki „1. Ustaw sztangę”, „2. Ściągnij łopatki”, „Trzymaj 3 s” | Karta ćwiczenia: numerowana lista 1., 2., 3. bez podwójnej numeracji, a krok z liczbą w treści („Trzymaj 3 s”) zostaje bez zmian |
| 4.16 | Zapisz ćwiczenie bez kroków, wejdź w kartę i wrócić do formularza | Sekcja „Kroki wykonania” nie pokazuje się; pole w formularzu puste |
| 4.17 | Usuń ćwiczenie z 2 załącznikami, zrestartuj aplikację | Wiersze załączników znikają kaskadą; osierocone miniatury sprząta start aplikacji (świeże pliki zostają, kasowane są starsze niż doba) |
| 4.18 | Aktualizacja z M6 (baza v6: katalog, plany, historia, przerwy) | Migracja 6 → 7 bez błędu; `user_version = 7`, katalog/plany/historia bez zmian, kolumna `instructions` pusta, lista załączników pusta |
| 4.19 | Interfejs po angielsku | „Add photo”, „Add video”, „Photos, videos and links”, „How to perform”, „Unlink this attachment?” |

## 5. Dane i backup

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 5.1 | Pierwsze uruchomienie na czystej instalacji | Katalog: 23 ćwiczenia; plan startowy z 3 dniami wczytuje się razem z nim (albo przyciskiem w Ustawieniach) |
| 5.2 | Zmień nazwę ćwiczenia z seeda, uruchom re-import | Nazwa użytkownika **nie** nadpisana |
| 5.3 | „Eksportuj kopię (ZIP)” → wskaż miejsce | Plik `gymtracker-<data>.zip` w „Pobranych”; komunikat „Kopia zapisana. Wiersze: N, miniatury: M.”; w archiwum `gymtracker.json` i `media/…` |
| 5.4 | „Wczytaj kopię (ZIP)” → wskaż plik | Pytanie „Wczytać kopię?” z datą, wersją schematu, liczbą wierszy i wersją aplikacji z kopii; „Anuluj” nic nie zmienia |
| 5.5 | Kopia → „Zastąp dane” | Komunikat „Dane wczytane. Wiersze: N, pominięte: 0.”; liczby wierszy w każdej tabeli jak w kopii; miniatury na miejscu |
| 5.6 | Kopia → „Dopisz” na bazie, która ma już te same dane | Ćwiczenia, plany, wskaźniki karty postaci, dni planu i pomiary **nie** dublują się (komunikat pokazuje „istniejące”); historia (treningi, serie, przerwy) dopisuje się obok |
| 5.7 | „Dopisz” kopię z innym ćwiczeniem własnym (bez `external_id`) o tej samej nazwie | Ćwiczenie rozpoznane po nazwie, historia dowiązuje się do niego zamiast tworzyć drugi wiersz |
| 5.8 | Wczytaj kopię z plikiem niebędącym kopią (`readme.txt` w ZIP) | „To nie jest kopia GymTracker.”; brak zmian w danych |
| 5.9 | Wczytaj kopię z pliku o wyższym `schema_version` | „Kopia z nowszej wersji aplikacji.”; brak częściowego importu |
| 5.10 | Wczytaj uszkodzony plik (obcięty JSON) | „Kopia jest uszkodzona.”; brak częściowego importu |
| 5.11 | Rozpocznij trening i spróbuj wczytać kopię | „Najpierw zakończ trwający trening.”; baza bez zmian |
| 5.12 | Eksport, wyczyszczenie danych apki, import tej samej kopii | Wszystkie dane i miniatury wracają; zdjęcia z galerii trzeba wskazać na nowo (aplikacja ich nie kopiuje) |
| 5.13 | „Wczytaj katalog z pliku (JSON)” z poprawnym plikiem (dwa nowe ćwiczenia) | „<plik>: dodano 2, pominięto 0.”; ćwiczenia widoczne w katalogu |
| 5.14 | Ten sam plik katalogu wczytany drugi raz | „dodano 0, pominięto 2.”; brak duplikatów |
| 5.15 | Plik katalogu bez `schema_version` / z nowszą wersją / bez ćwiczeń / nie-JSON | Komunikat z powodem w miejscu przycisku; brak zmian w danych |
| 5.16 | „Ostatnie importy” po imporcie pliku i po odrzuconym pliku | Wpis z nazwą pliku i datą; wpis odrzucony pokazuje powód; pozycja przeżywa restart aplikacji |
| 5.17 | Wyszukaj „obwod” (bez ogonka) | Znajduje „obwód” |
| 5.18 | Tryb samolotowy + pełne przejście apki | Wszystko działa, żadne żądanie sieciowe nie jest wymagane |
| 5.19 | Aktualizacja z M9a (baza v8) na v9 | Migracja 8 → 9 bez błędu; `user_version = 9`, dane i miniatura bez zmian, tabela `seed_imports` pusta |

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

## 9. Historia i statystyki (M6)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 9.1 | Aktualizacja z M5 (baza v6, treningi i przerwy) | Bez migracji (`user_version` zostaje 6); zakładka „Historia” od razu pokazuje dotychczasowe treningi |
| 9.2 | Otwórz zakładkę „Historia” po czystej instalacji (bez treningów) | „Nie masz jeszcze zakończonych treningów.” — bez chipów filtrów (nie ma czego zawężać) |
| 9.3 | Po kilku treningach otwórz „Historię” | Lista od najnowszych; wiersz: nazwa dnia, data, czas bez pauz, „N serii • X kg • śr. przerwa M:SS”; brakujące części (np. brak przerw) nie pokazują się jako „0” |
| 9.4 | Trening przerwany (`cancelled`) i trening w toku | **Nie** ma ich na liście historii |
| 9.5 | Chip z ćwiczeniem, które było na treningu | Lista zawężona do treningów z tym ćwiczeniem; „Wszystkie ćwiczenia” wraca do pełnej listy |
| 9.6 | Chip z nazwą dnia, np. „Workout A” | Lista zawężona do tego dnia planu |
| 9.7 | Zakres „1M” przy treningu starszym niż 30 dni | Stary trening znika z listy; „Wszystko” go przywraca |
| 9.8 | Filtr ćwiczenia + nazwa dnia bez wspólnego treningu | „Żaden trening nie pasuje do tych filtrów.” (a nie „nie masz treningów”) |
| 9.9 | Stuknij wiersz historii | Ekran treningu tylko do czytania (bez pól do edycji), z seriami, notatką i średnimi przerwami |
| 9.10 | Karta ćwiczenia z historią | Sekcja „Historia”: najcięższa seria, „Ostatnio”, „Przerwy” i mini wykres 3M |
| 9.11 | Ćwiczenie bez ani jednej zalogowanej serii | „Brak zakończonych treningów z tym ćwiczeniem.” (bez pustego wykresu) |
| 9.12 | „Pełna historia i statystyki” | Rekordy (najcięższa seria, najlepszy 1RM, największa objętość, przerwy) i podsumowanie „… w zakresie” |
| 9.13 | Ćwiczenie z seriami o rosnącym ciężarze | Wykres ciężaru: dwie linie (najcięższa seria i szacowany 1RM), punkty na kolejne **dni** treningowe; dwie serie tego samego dnia to jeden punkt |
| 9.14 | Dwa treningi jednego dnia | Wykresy pokazują **jeden** słupek na ten dzień, a jego objętość to suma obu treningów |
| 9.15 | Rekord sprzed roku i zakres „1M” | Wykres i tabela tylko z miesiąca, ale rekord nadal sprzed roku (rozdzielenie rekordów od zakresu) |
| 9.16 | Seria dłuższa niż 12 powtórzeń | 1RM pokazany z „~” i przypisem „Powyżej 12 powtórzeń ten wzór to już tylko szacunek” |
| 9.17 | Serie rozgrzewkowe w treningu | Nigdzie ich nie ma: ani w liczbach serii, ani w objętości, ani w rekordzie |
| 9.18 | Ćwiczenie na czas (plank, bez ciężaru) | Liczy się jako trening i ma wiersz w tabeli, ale zamiast wykresu ciężaru jest wyjaśnienie (bez płaskiej linii na zerze) |
| 9.19 | Tabela wykonań | Kolumny: data, serie, ciężar (najcięższa seria dnia), powtórzenia, przerwa; od najnowszych; brak przerw = „—” |
| 9.20 | Zmiana zakresu na wykresach | Wykresy i tabela przeliczają się na nowy zakres, chips zaznacza wybór |
| 9.21 | Przeciągnięcie/pinch na wykresie | Wykres da się przesunąć i przybliżyć (domyślny zoom Vico) |
| 9.22 | Ćwiczenie raz na czas (bez ciężaru), raz z ciężarem - oba w zakresie | Rekordy bez „0 kg”; wykres ciężaru ma punkt tylko z dnia z ciężarem, a tabela wykonań oba wiersze |

## 10. Karta postaci (M8)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 10.1 | Aktualizacja z M7 (baza v7, z treningami i mediami) | Migracja 7 → 8: `user_version` = 8, ćwiczenia/plany/treningi/załączniki bez zmian, a na zakładce „Postać” jest dziesięć wbudowanych wskaźników z pustą historią |
| 10.2 | Zakładka „Postać” na czystej instalacji | Dolny pasek ma pięć pozycji (Kalendarz, Ćwiczenia, Historia, Postać, Ustawienia); na ekranie przełącznik wskaźników i „Waga” jako pierwszą |
| 10.3 | Przełączanie wskaźników chipami | Wykres, podsumowanie i lista zmieniają się natychmiast; wybór nie dokłada ekranu do stosu |
| 10.4 | Wskaźnik bez pomiarów | „Wykres pojawi się przy dwóch pomiarach.”, w podsumowaniu „Brak pomiarów. Dodaj pierwszy, żeby zobaczyć zmiany.”, lista pusta |
| 10.5 | „Dodaj pomiar”: wartość 82,5 (przecinek) | Zapisane jako 82.5; data domyślnie dzisiejsza; w polu wartości jest sufiks jednostki (kg) |
| 10.6 | Data pomiaru z przyszłości | Nie da się wybrać w kalendarzu (dni po dzisiejszym są zablokowane) |
| 10.7 | Drugi pomiar tego samego dnia | Pomiar jest **poprawiany**, a nie dublowany: jedna pozycja na liście, jedna wartość w bazie |
| 10.8 | Poprawka pomiaru z 1.09 na 3.09 | Wiersz przenosi się na 3.09 (nie zostaje duplikat na 1.09); jeśli 3.09 miał już pomiar, zostaje przenoszony |
| 10.9 | Wpisanie daty pomiaru sprzed 5 dni | „Ostatni pomiar” pokazuje „(5 dni temu)”, a „Seria pomiarów” = 0 (seria się skończyła) |
| 10.10 | Pomiary w kolejne dni (wczoraj i dziś) | „Seria pomiarów: 2 dni z rzędu” |
| 10.11 | Trzy pomiary w zakresie 3M | Wykres: linia po dniach (oś X to daty), punkty tam, gdzie są pomiary, legenda „Wartość” |
| 10.12 | Wskaźnik z celem, np. 78 kg przy kierunku „w dół dobrze” | Na wykresie druga linia (cel) i legenda „Cel”; w podsumowaniu „78 kg • zostało 3.5 kg” |
| 10.13 | Cel osiągnięty (np. 78 kg przy wartości 77 kg, kierunek „w dół dobrze”) | „78 kg • osiągnięty” |
| 10.14 | Zmiana w dobrym i złym kierunku | Wartość zmiany na zielono (dobra strona) albo czerwono (zła); przy kierunku „Bez oceny” — zwykłym kolorem |
| 10.15 | Zmiana zakresu 1M/3M/6M/1R/Wszystko | Wykres i lista pomiarów przeliczają się, a podsumowanie (ostatni pomiar, zmiany, seria) zostaje z całej historii |
| 10.16 | „Usuń pomiar” | Dialog potwierdzenia z datą pomiaru; po potwierdzeniu wiersz znika także z wykresu |
| 10.17 | „Zarządzaj wskaźnikami” (ikona w pasku) | Lista wskaźników: nazwa, jednostka, kierunek; dodawanie w pasku |
| 10.18 | Dodanie własnego wskaźnika: „Obwód bicepsa”, cm, „w górę dobrze”, cel 40 | Chip na ekranie „Postać” i pusty wykres do pierwszego pomiaru |
| 10.19 | Wskaźnik bez nazwy | „Zapisz” nieaktywny |
| 10.20 | Poprawka wbudowanego wskaźnika bez zmiany nazwy (sam cel) | Nazwa zostaje przetłumaczona („Waga” / „Weight” zależnie od języka), bez jednostki w nazwie („Tłuszcz”, nie „Tłuszcz (%)” — jednostka stoi w podpisie wiersza) |
| 10.21 | Zmiana nazwy „Waga” na „Waga ciała” | Od tej chwili widać wpisaną nazwę — także po zmianie języka aplikacji na angielski |
| 10.22 | „Zarchiwizuj wskaźnik” z pomiarami | Znika z przełącznika, ale zostaje na liście „Wskaźniki” z podpisem „Zarchiwizowany”, a pomiary są nietknięte; „Przywróć wskaźnik” go wraca |
| 10.23 | Zarchiwizowanie wszystkich wskaźników | Ekran „Postać”: „Nie masz żadnych wskaźników. Dodaj własny albo przywróć wbudowany.” i przycisk do listy |
| 10.24 | Obrót ekranu na ekranie „Postać” | Wybrany wskaźnik i zakres zostają |
| 10.25 | TalkBack na liście pomiarów i chipach | Czyta nazwę wskaźnika, wartość z jednostką i datę; przyciski mają opisy |
| 10.26 | Aplikacja w trybie offline (bez sieci) | Karta postaci działa w całości — nic nie wymaga połączenia |

## 11. Ustawienia: alarm przerwy, język, o aplikacji (M9)

| # | Scenariusz | Oczekiwany wynik |
|---|---|---|
| 11.1 | Wyłącz „Dźwięk na koniec przerwy” i doczekaj końca przerwy | Alarm się nie odzywa dźwiękiem, komunikat nadal pojawia się na ekranie |
| 11.2 | Wyłącz też wibrację | Alarm jest cichy — zostaje sam komunikat (heads-up), bez dźwięku i wibracji |
| 11.3 | Ustawienia systemowe → Powiadomienia → kanały aplikacji | Widać kanał zgodny z ustawieniem: „Koniec przerwy”, „Koniec przerwy: dźwięk”, „…: wibracja” albo „…: cicho” |
| 11.4 | Włącz dźwięk z powrotem po wcześniejszym wyłączeniu | Alarm idzie na kanał „Koniec przerwy: dźwięk”; w systemie widać oba kanały — ten, którego używałeś, i ten, którego używasz teraz |
| 11.5 | Zmień ręcznie dźwięk w kanale (ustawienia systemu), potem przełącz nasze ustawienie | Nasze przełączenie nie kasuje tego, co ustawiłeś w systemie — kanału nie nadpisujemy |
| 11.6 | „Język aplikacji” na Androidzie 13+ | Otwiera się systemowy wybór języka z „Polski” i „English”; po zmianie interfejs i powiadomienia mówią wybranym językiem |
| 11.7 | „Język aplikacji” na Androidzie 12 i starszym | Wiersz pokazuje podpowiedź, że język idzie za systemem (żadnego martwego kliknięcia) |
| 11.8 | „O aplikacji” | Wersja zgodna z `versionName`; zdania o danych na urządzeniu i o mediach z galerii |
| 11.9 | TalkBack na wierszach z przełącznikami | Ustawienie czytane raz, dotknięcie całego wiersza przełącza wartość |
