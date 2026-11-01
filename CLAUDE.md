# clutter-seller – instrukcje dla Claude Code

## O projekcie
Prywatna aplikacja (jeden użytkownik, bez kont) do szybkiego wystawiania używanych rzeczy:
1. Robię zdjęcia telefonem w aplikacji webowej i wysyłam.
2. AI (Claude przez moją subskrypcję, z możliwością dodania innych LLM) generuje tytuł, opis, stan, sugestie kategorii i atrybutów, rozmiar paczki.
3. AI wyszukuje w sieci podobne oferty; aplikacja liczy statystyki cen i sugeruje cenę.
4. Na PC przeglądam szkic, poprawiam i zatwierdzam.
5. Publikacja na OLX i Vinted przez **własne rozszerzenie Chrome**:
   - sam otwieram formularz "Dodaj ogłoszenie" na OLX lub Vinted,
   - w rozszerzeniu wybieram przedmiot i klikam "Przenieś dane",
   - rozszerzenie wypełnia pola i dodaje zdjęcia,
   - sprawdzam i **sam** klikam "Opublikuj".

Projekt ma też dwa inne cele:
- **Nauka**: właściciel ma 16 lat doświadczenia w Javie, ale na wewnętrznych frameworkach. Uczy się Spring Boota, Angulara, TypeScriptu, Dockera i CI.
- **Portfolio**: kod ma być czysty, przetestowany i dobrze udokumentowany. To pierwszy publiczny projekt właściciela.

## Granice rozszerzenia (WAŻNE, nie łamać)
- Działa **wyłącznie** po kliknięciu użytkownika, na aktywnej karcie. Bez działań w tle, bez automatycznego otwierania stron.
- **Nigdy nie wysyła formularza** i nie klika "Opublikuj"/"Dodaj". Ostatni krok zawsze robi człowiek.
- Bez scrapingu, bez czytania cudzych ogłoszeń i wiadomości, bez wywołań wewnętrznych API OLX/Vinted. Tylko wypełnianie pól formularza na otwartej stronie.
- Minimalne uprawnienia (`activeTab`, `scripting`, `storage` i host permission tylko do adresu backendu). Właściciel świadomie akceptuje ryzyko po stronie regulaminów serwisów.

## Tryb pracy (WAŻNE)
- Przed każdą większą zmianą przedstaw plan i poczekaj na akceptację.
- Przy nowej technologii lub koncepcji (Spring, Angular, Chrome Extensions MV3, Testcontainers itp.) wyjaśnij krótko, *dlaczego* tak, a nie inaczej. Wskaż alternatywę i podaj link do oficjalnej dokumentacji.
- Gdy właściciel napisze "sam to napiszę", przygotuj tylko szkielet z TODO i testy, a implementację zostaw jemu. Potem zrób review jego kodu.
- Pracuj małymi krokami. Po każdym kroku: build, testy i krótkie podsumowanie, co powstało. Commit tylko na prośbę.
- Nie dodawaj zależności bez uzasadnienia.
- Pracuj etapami z `docs/PLAN.md`. Nie wybiegaj do kolejnego etapu.
- Przy integracjach (Claude Code headless, Chrome Extensions API) najpierw sprawdź **aktualną** dokumentację. Nie zgaduj flag ani API z pamięci.

## Stack
- Java 21+ (LTS), Spring Boot 4.x, Maven
- Spring Web, Spring Data JPA, Bean Validation, Actuator, Spring Security (Basic Auth)
- AI v1: Claude Code w trybie headless (`claude -p`, wynik w JSON) wywoływany z Javy jako proces, uwierzytelniony subskrypcją właściciela (`CLAUDE_CODE_OAUTH_TOKEN` z `claude setup-token`). Bez klucza API.
- PostgreSQL + Flyway; zdjęcia na dysku w wolumenie, w bazie tylko metadane
- Frontend: Angular (najnowszy stabilny, Angular CLI), standalone components, signals, nowy control flow (`@if`, `@for`), typowane formularze reaktywne, `HttpClient`; budowany przez Mavena do zasobów statycznych Spring Boota. Bez NgModules i bez zbędnych bibliotek UI.
- Rozszerzenie: Chrome Manifest V3, TypeScript, prosty bundler (np. Vite/esbuild), **bez frameworka** w popupie. Katalog `extension/`. Instalowane jako "rozpakowane" (tryb dewelopera), bez publikacji w Chrome Web Store.
- Testy: JUnit 5, AssertJ, Spring Boot Test, Testcontainers (Postgres), fake procesu CLI w testach adaptera AI; rozszerzenie: testy jednostkowe (np. Vitest + jsdom) na zanonimizowanych fixture'ach HTML formularzy
- Docker multi-stage (obraz linux/amd64), docker compose (app + postgres), cel: Synology DS220+ (Container Manager)
- CI: GitHub Actions (backend, frontend, rozszerzenie; obraz do prywatnego GHCR)

## Architektura
Monorepo: `backend` (Spring Boot), `frontend/` (Angular), `extension/` (Chrome).

Backend – modularny monolit, pakiety według funkcji (`com.clutterseller.<moduł>`):
- `item`: przedmiot/szkic ogłoszenia, statusy (NEW → PROCESSING → READY_FOR_REVIEW → APPROVED → LISTED) + osobno flaga/link per serwis (OLX, Vinted)
- `photo`: upload, miniatury, **usuwanie EXIF (w tym GPS) przed jakimkolwiek wysłaniem dalej**
- `ai`: porty `ListingGenerator` i `PriceResearcher`; adapter v1 `claudecode` (`ProcessBuilder`, katalog roboczy per przedmiot, **tylko** narzędzia do odczytu i web search/fetch, bez Bash i zapisu, timeout, limit tur, walidacja JSON). Zadania w kolejce, jedno naraz; błąd limitu subskrypcji → "spróbuj później".
- `pricing`: AI zwraca listę znalezionych ofert; statystyki (liczba, min, max, średnia, mediana, kwartyle) liczy **Java**. Sugerowana cena to osobne pole.
- `export`: REST dla rozszerzenia – lista zatwierdzonych przedmiotów i **payload per serwis** (`target=olx|vinted`): tytuł i opis przycięte do limitów danego serwisu, cena, stan, marka, rozmiar, rozmiar paczki, sugerowana ścieżka kategorii (tekstowo), atrybuty, URL-e zdjęć. Oznaczenie "wystawione" + link.
- `settings`: stałe dane (lokalizacja, kontakt, domyślne opcje wysyłki)

Rozszerzenie:
- `popup`: konfiguracja (adres backendu, hasło w `chrome.storage.local`), lista przedmiotów do wystawienia, przycisk "Przenieś dane"
- Pobieranie danych i zdjęć **w kontekście rozszerzenia** (popup/service worker), nie w content scripcie – strony OLX/Vinted są na HTTPS, a backend w LAN po HTTP (mixed content, Private Network Access)
- Wstrzykiwanie skryptu przez `chrome.scripting.executeScript` dopiero po kliknięciu; dane przekazywane jako argumenty (zdjęcia jako base64)
- Adaptery formularzy `olx` i `vinted`: rozpoznanie strony formularza, mapa pól → selektorów w **jednym** pliku per serwis (łatwa poprawka po zmianie UI). Preferowane stabilne atrybuty (`name`, `data-testid`, `aria-label`, `label`) zamiast klas CSS.
- Wypełnianie pól kontrolowanych przez frameworki: natywny setter `value` + zdarzenia `input`/`change`. Zdjęcia: `File` z blobów + `DataTransfer` na `input[type=file]` (lub zdarzenie `drop` na strefie zrzutu).
- Pola trudne do automatycznego wypełnienia (wielopoziomowy wybór kategorii, niestandardowe dropdowny) w v1 mogą zostać jako **podpowiedź** w panelu rozszerzenia; każde pole raportuje status: wypełnione / do ręcznego uzupełnienia.

## Sieć, bezpieczeństwo i sekrety
- Aplikacja domyślnie dostępna **tylko w sieci lokalnej**; ruch wychodzący (Claude, web search) dozwolony. Bez przekierowań portów i bez QuickConnect. Porty Dockera wiązane do adresu LAN NAS-a (Docker omija zaporę DSM).
- Sekrety (`CLAUDE_CODE_OAUTH_TOKEN`, hasło UI) wyłącznie w zmiennych środowiskowych / `.env` w `.gitignore`. W repo tylko `.env.example`.
- Basic Auth na całe UI i API. CORS dla API eksportu tylko dla origin rozszerzenia (`chrome-extension://<id>`, stałe ID przez klucz w manifeście).
- Fixture'y HTML formularzy w testach: zanonimizowane, bez danych osobowych i tokenów.
- Kontener działa jako użytkownik nie-root. Nigdy nie loguj tokenów ani haseł.

## Konwencje
- Kod, nazwy, komentarze, commity i README po angielsku. UI i treść ogłoszeń po polsku.
- Java: rekordy dla DTO, konstruktorowe wstrzykiwanie zależności, brak Lomboka.
- TypeScript w trybie `strict`.
- Komendy: `./mvnw verify` (backend + testy), `docker compose up -d`, frontend: `npm start` w `frontend/`, rozszerzenie: `npm run build` w `extension/` i "Załaduj rozpakowane" z `extension/dist`.
