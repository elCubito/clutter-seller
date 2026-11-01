# clutter-seller – plan etapów (docs/PLAN.md)

Każdy etap kończy się działającym buildem, testami i commitem. Kryteria "gotowe" są na końcu etapu.

## Etap 0 – Szkielet projektu
- Spring Boot 4.x (Maven), `groupId` `com.clutterseller`, moduły pakietowe zgodnie z CLAUDE.md, Actuator `/actuator/health`
- `docker-compose.yml` z PostgreSQL dla devu, Flyway z pierwszą migracją
- `.env.example`, `.gitignore`, README z opisem projektu i uruchomienia
- GitHub Actions: `./mvnw verify`
**Gotowe:** `./mvnw verify` przechodzi lokalnie i w CI, aplikacja startuje z Postgresem z compose.

## Etap 1 – Przedmioty i zdjęcia (backend)
- Encja `Item` (statusy jak w CLAUDE.md) + `Photo`, migracje
- REST: utworzenie przedmiotu z wieloma zdjęciami (multipart) + opcjonalna notatka tekstowa ("rysa na boku", "rozmiar M")
- Zapis plików na dysku (ścieżka z konfiguracji), miniatury, usunięcie EXIF/GPS, limit rozmiaru, kompresja do rozsądnej rozdzielczości
- Lista i szczegóły przedmiotów, usuwanie
**Gotowe:** testy integracyjne (Testcontainers) dla uploadu i listy; zdjęcia po zapisie nie mają GPS.

## Etap 2 – Frontend
- Angular w `frontend/` (Angular CLI, standalone components, signals), proxy `/api` na backend w trybie dev, build przez Mavena (np. frontend-maven-plugin) do static resources Spring Boota; routing Angulara działa po odświeżeniu strony (fallback na `index.html`)
- Ekran telefonu ("Nowy przedmiot"): przycisk aparatu (`<input type="file" accept="image/*" capture multiple>`), podgląd, notatka, "Wyślij". Cel: maks. 3 dotknięcia od otwarcia do wysłania.
- Manifest (`ng add @angular/pwa`) + skrót na ekranie głównym telefonu (pełna instalacja PWA wymaga HTTPS; w LAN po HTTP wystarczy skrót, aparat działa)
- Ekran PC: lista przedmiotów ze statusem, szczegóły
- Basic Auth na całość
**Gotowe:** z telefonu w sieci domowej da się dodać przedmiot ze zdjęciami, widać go na PC.

## Etap 3 – Generowanie ogłoszenia przez AI (subskrypcja Claude)
- Spike: sprawdzić w dokumentacji Claude Code (tryb headless) flagi do wyniku JSON, ograniczenia narzędzi, limitu tur i trybu uprawnień; ręcznie wywołać `claude -p` na katalogu z 2 zdjęciami
- Port `ListingGenerator` + adapter `claudecode` (`ProcessBuilder`, timeout, katalog roboczy per przedmiot, tylko narzędzia do odczytu)
- Wejście: zdjęcia + notatka + stałe ustawienia. Wyjście (JSON → rekord): tytuł, opis, marka, model, stan, kolor/rozmiar jeśli dotyczy, słowa kluczowe, rozmiar paczki (S/M/L), frazy do wyszukania ceny
- Szablony promptów w zasobach, po polsku, z limitami długości tytułu/opisu
- Kolejka zadań AI (jedno naraz), statusy, obsługa limitu subskrypcji ("spróbuj później"), przycisk "Wygeneruj ponownie"
**Gotowe:** testy adaptera z fake procesem (poprawny JSON, zły JSON, timeout, limit); ręczny test na 2–3 prawdziwych przedmiotach.

## Etap 4 – Wycena
- Port `PriceResearcher`, ten sam adapter CLI z web search: AI zwraca listę znalezionych ofert {tytuł, cena, stan, źródło, url} z polskich serwisów
- `PriceStatistics` w Javie: liczba ofert, min, max, średnia, mediana, kwartyle (widełki), odrzucenie outlierów
- Sugerowana cena (z uzasadnieniem od AI) + 3–5 linków do porównania w UI
**Gotowe:** testy jednostkowe statystyk (w tym przypadki brzegowe: 0 i 1 oferta).

## Etap 5 – Integracja OLX (Partner API)
- OAuth2 (authorization code) z OLX przez Spring Security OAuth2 Client, registration id `olx`; zapis i odświeżanie tokenów (szyfrowane)
- OLX nie przyjmuje `localhost`. Callback URI w developer.olx.pl oparty o publiczną nazwę DNS wskazującą na IP NAS-a w LAN, np. `http://192-168-1-50.nip.io:8088/login/oauth2/code/olx` (nip.io / sslip.io). Musi się zgadzać 1:1 z `redirect-uri` w konfiguracji, a przepływ OAuth trzeba zaczynać z aplikacji otwartej pod tą samą nazwą (ciasteczko sesji + `state`)
- Plan B, gdyby OLX wymagał HTTPS lub odrzucił nazwę: statyczna strona-przekaźnik na GitHub Pages (https), która przekazuje `code` i `state` do aplikacji w LAN; wtedy własny kontroler callbacku zamiast domyślnego filtra Spring Security
- Pobranie i cache drzewa kategorii; wybór kategorii (sugestia z API, jeśli jest taki endpoint, w przeciwnym razie AI wybiera z listy kandydatów)
- Atrybuty kategorii: pobranie wymaganych atrybutów i dozwolonych wartości, AI wypełnia, walidacja
- Ustawienia Przesyłki OLX (tylko gdzie kategoria/cena/rozmiar pozwala), stała lokalizacja i kontakt
- Publikacja ogłoszenia + upload zdjęć, zapis ID i URL ogłoszenia
**Gotowe:** testy z WireMockiem; jedno prawdziwe ogłoszenie opublikowane i ręcznie zweryfikowane.

## Etap 6 – Panel Vinted (kopiuj/wklej)
- Widok na PC: osobne pola z przyciskiem "Kopiuj" (tytuł, opis, cena, marka, rozmiar, stan, sugerowana ścieżka kategorii Vinted, rozmiar paczki)
- "Pobierz zdjęcia" (ZIP) do przeciągnięcia do formularza Vinted
- Checkbox "wystawione na Vinted" + pole na link
**Gotowe:** wystawienie na Vinted zajmuje ok. minuty.

## Etap 7 – Ekran przeglądu i ustawienia
- Jeden ekran "Przejrzyj i wystaw": edycja wszystkich pól, zmiana ceny z podglądem statystyk, wybór platform (OLX / Vinted), przycisk "Wystaw"
- Ustawienia: lokalizacja, kontakt, domyślne opcje wysyłki, wybór dostawcy/modelu AI
**Gotowe:** pełny przepływ telefon → PC → OLX + Vinted działa end-to-end.

## Etap 8 – Wdrożenie na Synology DS220+
- NAS: Intel Celeron J4025 (x86-64, 2 rdzenie), 10 GB RAM → obraz `linux/amd64`, budowany w GitHub Actions (nie na NAS-ie)
- Dockerfile multi-stage: frontend + backend + Claude Code CLI w obrazie runtime, użytkownik nie-root
- GitHub Actions: build obrazu i push do GHCR jako **prywatny** pakiet (obraz zawiera Claude Code CLI, który nie jest open source – nie redystrybuować go publicznie); alternatywa: instalacja CLI przy starcie kontenera do wolumenu
- `docker-compose.prod.yml`: app + postgres, wolumeny na zdjęcia i bazę, limity pamięci (np. JVM ~1 GB), restart policy, healthcheck, port wystawiony tylko w LAN
- Sieć: porty opublikowane przez Dockera omijają zaporę DSM, więc w compose port wiązać do adresu LAN NAS-a (np. `192.168.1.50:8088:8080`), nie do `0.0.0.0`; bez przekierowań na routerze
- `docs/DEPLOY-SYNOLOGY.md` dla początkującego: instalacja Container Manager, folder na projekt, utworzenie projektu z compose, plik `.env` (w tym token z `claude setup-token`), logowanie do GHCR, aktualizacja obrazu, backup wolumenów (Hyper Backup), opcjonalnie Tailscale
**Gotowe:** aplikacja działa na NAS-ie, telefon i PC łączą się w sieci domowej, z zewnątrz port jest niedostępny.

## Etap 9 – Szlif portfolio
- README: zrzuty ekranu, diagram architektury, decyzje projektowe (ADR w `docs/adr/`, m.in. "AI przez Claude Code headless zamiast API"), uruchomienie w 3 komendach
- Pokrycie testami kluczowych modułów, badge CI

## Wersja 2 (później)
- Zarządzanie ogłoszeniami: status, linki, odświeżanie
- "Sprzedane na Vinted" → automatyczne zakończenie ogłoszenia na OLX przez API
- Kolejni dostawcy AI (Anthropic API przez Spring AI, OpenAI, Gemini, Ollama)
- Opcjonalnie: HTTPS w LAN (pełna PWA)
- Opcjonalnie: rozszerzenie przeglądarki, które tylko wypełnia pola formularza Vinted (decyzja właściciela, ryzyko po stronie regulaminu)
