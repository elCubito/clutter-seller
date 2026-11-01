# clutter-seller – plan etapów (docs/PLAN.md)

Każdy etap kończy się działającym buildem i testami. Po etapie Claude pyta o commit i proponuje komunikat (commit tylko na prośbę, zgodnie z CLAUDE.md). Kryteria "gotowe" są na końcu etapu.

## Etap 0 – Szkielet projektu
- Spring Boot 4.x (Maven) **w korzeniu repo**, `groupId` `com.clutterseller`, pakiety modułów zgodnie z CLAUDE.md, Actuator `/actuator/health`
- `compose.yaml` z PostgreSQL dla devu (Spring Boot Docker Compose support tylko jako zależność developerska), Flyway z migracją bazową V1
- `.gitignore` uzupełniony (`.env`, `target/`, `node_modules/`, `dist/`, `.idea/`, `.mcp.json`) i `.idea/` usunięte z indeksu; `.gitattributes` (LF dla `mvnw` i `*.sh`); `mvnw` jako wykonywalny; `.env.example`; README
- GitHub Actions: `./mvnw verify`
**Gotowe:** `./mvnw verify` przechodzi lokalnie i w CI, aplikacja startuje z Postgresem z compose.

## Etap 1 – Przedmioty i zdjęcia (backend)
- Encje `Item`, `Photo` (z `position`), `Listing` (service, selected, listedAt, url), migracje
- Statusy i tabela przejść z CLAUDE.md w jednym miejscu kodu + testy jednostkowe wszystkich dozwolonych i niedozwolonych przejść; `LISTED` wyliczany z `Listing`
- REST: utworzenie przedmiotu z wieloma zdjęciami (multipart) + opcjonalna notatka; zmiana kolejności zdjęć
- Zapis plików na dysku, miniatury, usunięcie EXIF/GPS, limit rozmiaru, kompresja
- Lista i szczegóły przedmiotów, usuwanie
**Gotowe:** testy integracyjne (Testcontainers); zdjęcia po zapisie nie mają GPS; testy przejść statusów.

## Etap 2 – Frontend (Angular)
- Angular w `frontend/` (Angular CLI, standalone components, signals), proxy `/api` w dev, build przez Mavena do static resources; fallback na `index.html` dla routingu
- Ekran telefonu ("Nowy przedmiot"): aparat (`<input type="file" accept="image/*" capture multiple>`), podgląd, notatka, "Wyślij" – maks. 3 dotknięcia
- Manifest (`ng add @angular/pwa`) + skrót na ekranie głównym (pełna PWA wymaga HTTPS)
- Ekran PC: lista przedmiotów ze statusem, szczegóły; Basic Auth
**Gotowe:** z telefonu w sieci domowej da się dodać przedmiot, widać go na PC; `ng test` w CI.

## Etap 3 – Generowanie ogłoszenia przez AI (subskrypcja Claude)
- Spike: aktualna dokumentacja Claude Code headless (JSON, ograniczenie narzędzi, limit tur, uprawnienia); ręczny test `claude -p` na 2 zdjęciach
- Port `ListingGenerator` + adapter `claudecode`
- Wynik domenowy jak w CLAUDE.md: tytuł, opis, `condition` (enum), `packageSize` (enum), `attributes` (mapa etykieta → wartość), główna ścieżka kategorii per serwis + alternatywy, frazy do wyceny
- Szablony promptów w zasobach; kolejka zadań (jedno naraz); `GENERATION_FAILED` z powodem; "Wygeneruj ponownie"
**Gotowe:** testy adaptera z fake procesem (poprawny JSON, zły JSON, timeout, limit); ręczny test na 2–3 przedmiotach.

## Etap 4 – Wycena
- Port `PriceResearcher` (web search przez CLI): lista znalezionych ofert {tytuł, cena, stan, źródło, url}
- `PriceStatistics` w Javie: liczba, min, max, średnia, mediana, kwartyle, odrzucenie outlierów
- Sugerowana cena + 3–5 linków w UI
**Gotowe:** testy jednostkowe statystyk (w tym 0 i 1 oferta).

## Etap 5 – Ekran przeglądu i API eksportu
- Ekran "Przejrzyj i zatwierdź": edycja pól, cena z podglądem statystyk, wybór serwisów (`Listing.selected`), zatwierdzenie → APPROVED
- Moduł `export`: lista przedmiotów do wystawienia, payload per serwis (mapowanie enumów na etykiety serwisu, awaryjne przycięcie do limitów z konfiguracji, zdjęcia ~1600 px bez EXIF w kolejności `position`), "oznacz jako wystawione + link"
- Wartości limitów i etykiet na razie jako konfiguracja z placeholderami; uzupełniane w etapach 7–8 z fixture'ów
- Basic Auth; **bez CORS** (patrz CLAUDE.md)
- Moduł `settings` **pomijamy**, chyba że fixture'y pokażą, że jest potrzebny
**Gotowe:** testy kontrolerów eksportu (MockMvc); payload dla OLX i Vinted sprawdzony ręcznie.

## Etap 6 – Rozszerzenie Chrome: szkielet
- Spike w aktualnej dokumentacji Chrome: `optional_host_permissions` + `chrome.permissions.request` dla adresu z ustawień; potwierdzenie, że żądania z popupu/service workera z uprawnieniem do hosta omijają CORS; limit rozmiaru argumentów `executeScript`
- `extension/`: Manifest V3, TypeScript strict, bundler, stały klucz w manifeście (stałe ID)
- Popup: ustawienia (adres backendu, hasło), lista przedmiotów z API eksportu, wybór przedmiotu i serwisu
- Pobieranie danych i zdjęć w kontekście rozszerzenia; wstrzyknięcie skryptu na aktywną kartę po kliknięciu; rozpoznanie, czy karta to formularz OLX/Vinted (inaczej komunikat)
- Build i testy w CI, instrukcja instalacji w README
**Gotowe:** po kliknięciu na stronie formularza rozszerzenie wypisuje w konsoli odebrany payload; na innej stronie pokazuje komunikat.

## Etap 7 – Wypełnianie formularza OLX
- Właściciel zapisuje HTML formularza "Dodaj ogłoszenie", anonimizuje i dodaje jako fixture
- Z fixture'a: limity długości (`maxlength`), etykiety stanu, gabaryty, czy formularz wymaga lokalizacji/kontaktu → uzupełnienie konfiguracji eksportu
- Adapter `olx`: mapa pól → selektorów w jednym pliku; pola tekstowe i cena (natywny setter + zdarzenia), zdjęcia przez `DataTransfer`, atrybuty dopasowane po etykiecie
- Kategoria: najpierw podpowiedź w panelu, potem w miarę możliwości automatycznie
- Raport po wypełnieniu: pola ✓ / "uzupełnij ręcznie"
- Rozszerzenie **nie klika** publikacji; po publikacji popup proponuje URL aktywnej karty → "oznacz jako wystawione"
**Gotowe:** testy na fixture; ręczny test: prawdziwe ogłoszenie wypełnione i opublikowane własnym kliknięciem.

## Etap 8 – Wypełnianie formularza Vinted
- Analogicznie do etapu 7: fixture, odczyt limitów i etykiet, adapter `vinted` (marka, rozmiar, stan, kategoria, zdjęcia), raport pól
**Gotowe:** testy na fixture; ręczny test na prawdziwym ogłoszeniu.

## Etap 9 – Wdrożenie na Synology DS220+
- NAS: Intel Celeron J4025 (x86-64), 10 GB RAM → obraz `linux/amd64`, budowany w GitHub Actions
- Dockerfile multi-stage: Angular + backend + Claude Code CLI, użytkownik nie-root; obraz w **prywatnym** GHCR (CLI nie jest open source) lub instalacja CLI przy starcie kontenera
- `docker-compose.prod.yml`: app + postgres, wolumeny, limity pamięci, restart policy, healthcheck; **port wiązany do adresu LAN NAS-a** (np. `192.168.1.50:8088:8080`), bo Docker omija zaporę DSM
- Stały IP NAS-a (rezerwacja DHCP); adres backendu w ustawieniach rozszerzenia
- `docs/DEPLOY-SYNOLOGY.md` dla początkującego: Container Manager, projekt z compose, `.env` (token z `claude setup-token`), logowanie do GHCR, aktualizacja, backup (Hyper Backup), opcjonalnie Tailscale
**Gotowe:** aplikacja działa na NAS-ie; telefon, PC i rozszerzenie łączą się w LAN; z zewnątrz port niedostępny.

## Etap 10 – Szlif portfolio
- README: zrzuty ekranu, diagram architektury (backend + Angular + rozszerzenie), ADR w `docs/adr/` (m.in. "AI przez Claude Code headless", "rozszerzenie zamiast API serwisów", "Listing jako osobna encja"), uruchomienie w 3 komendach
- Pokrycie testami kluczowych modułów, badge CI
- Opcjonalnie: Spring Modulith – test weryfikujący granice modułów (`ApplicationModules.verify()`) i wygenerowana dokumentacja modułów

## Wersja 2 (później)
- Zarządzanie ogłoszeniami: status, linki, przypomnienia o odświeżeniu
- "Sprzedane na Vinted" → lista "do zakończenia na OLX" z linkiem (zakończenie ręczne)
- Automatyczne ponowienie dla `RATE_LIMITED`
- Kolejni dostawcy AI (Anthropic API przez Spring AI, OpenAI, Gemini, Ollama)
- HTTPS w LAN (pełna PWA)
