# GP3D i publiczne pady

[Powrót do README](../README.md)

## Sprawdzanie uprawnień

Opcjonalna integracja z [GriefPrevention3D](https://github.com/castledking/GriefPrevention3D), sprawdzona z uniwersalnym JAR-em **19.0.0** na Fabric **26.3**. Umieść oba mody w `mods/`. Integracja włącza się automatycznie; bez GP3D dotychczasowe działanie moda pozostaje dostępne. GP3D nie jest dołączone do JAR-a Bongo Teleports. Wykrycie GP3D z niedostępnym lub niezgodnym API zatrzymuje start integracji/serwera zamiast pomijać ochronę. Błąd odczytu uprawnień podczas gry blokuje daną operację.

| Operacja | Wymagane prawo GP3D |
| --- | --- |
| Stawianie pada | Build Trust w miejscu bloku |
| Podnoszenie własnego pada | Build Trust w miejscu bloku oraz własność pada |
| Wybór i synchronizacja własnych padów | Build Trust przy obu padach, ponownie sprawdzany przed połączeniem |
| TPA | Access Trust teleportowanego gracza w miejscu docelowym |
| Teleportacja przez pad | Access Trust w miejscu lądowania **lub** publiczne pady w tej działce/poddziałce |
| Zmiana flagi publicznych padów | Właściciel lub Manage Trust, zgodnie z regułami GP3D |

Sprawdzenia korzystają z silnika trustów GP3D: uwzględniają publiczne i indywidualne trusty, uprawnienia, odmowy, dziedziczenie, ograniczone i zagnieżdżone poddziałki, kształt działek oraz pełne X/Y/Z. Administracyjne działki i `/ignoreclaims` działają zgodnie z GP3D. Sam status operatora nie daje dodatkowego obejścia w Bongo Teleports.

TPA sprawdza aktualne miejsce odbiorcy przy wysłaniu, potwierdzeniu kosztu, akceptacji i zakończeniu odliczania. Pad sprawdza dostęp przy rozpoczęciu i podczas odliczania, również przed samym przejściem. Odebranie trustu, zmiana działki albo wyłączenie publicznego dostępu może anulować teleportację; anulowanie nie pobiera XP ani nie uruchamia cooldownu.

### Publiczne pady — flaga per działka

Stań w działce lub poddziałce, którą chcesz ustawić:

- `/bongoteleports publicpads on` — pozwala wszystkim graczom teleportować się przez pady do tej działki.
- `/bongoteleports publicpads off` — przywraca wymaganie Access Trust (domyślne ustawienie).
- `/bongoteleports publicpads status` — pokazuje aktualny stan; samo `publicpads` także pokazuje stan.

Flaga działa **osobno dla każdej działki i poddziałki**, w tym poddziałek 3D. Ustawienie rodzica nie otwiera jego poddziałek; ustaw flagę bezpośrednio w wybranej poddziałce. Liczy się działka przy **stopach gracza w miejscu lądowania**, czyli blok nad padem, a nie działka obejmująca sam blok pada. Pozwala to prawidłowo obsłużyć granice wysokości poddziałek 3D. Flaga docelowa rozstrzyga kierunek podróży; drugi kierunek podlega fladze i trustom drugiego miejsca docelowego.

Publiczne pady nie otwierają TPA, nie przyznają Build Trust i nie pozwalają przejmować, podnosić ani synchronizować cudzych padów. Ustawienie jest flagą **Bongo Teleports**, ponieważ GP3D 19.0.0 nie udostępnia własnej flagi teleportów ani rejestracji dodatkowych flag przez API. Własne komendy GP3D do PvP i eksplozji nadal służą swoim dotychczasowym ustawieniom.

Bez wspólnego panelu BongoUtils flagi zapisują się w `publicPadClaims` w `<folder świata>/bongos-teleports.json`, razem z padami; zachowują się po restarcie. Istniejące dane 1.3.0 są odczytywane z pustą listą publicznych działek, bez zmiany połączeń padów. Nieistniejące flagi claimów są czyszczone przy starcie i zapisie. Przy wyłączonym GP3D komendy flag zgłaszają brak integracji.

## Wspólny panel flag BongoUtils — 1.5.0

Z **BongoUtils 1.6.2** publiczne pady korzystają ze wspólnej flagi `bongos_teleports:public_pads`, dostępnej w `/manageclaim` i `/manageclaim global`. Domyślnie jest wyłączona. Lokalny wyjątek ma pierwszeństwo przed domyślną właściciela, również gdy lokalnie ustawiono „Wyłączone”. Przywrócenie dziedziczenia odbywa się w panelu. Flaga jest rozstrzygana w działce obejmującej stopy w docelowym miejscu lądowania. Nadal nie otwiera TPA ani zarządzania cudzymi padami.

Komendy `/bongoteleports publicpads on|off|status` pozostają dostępne. Z nowym BongoUtils odczytują tę samą flagę, a `on`/`off` zapisują lokalny wyjątek. Stare publiczne flagi wersji 1.4 są migrowane jednorazowo; przywrócenie dziedziczenia nie jest cofane po restarcie. Wspólny zapis znajduje się w `<świat>/bongo-claim-flags.json`. Dotychczasowy plik Teleports nadal przechowuje pady, cooldowny i pozostałe dane.

Z **BongosCommunityClans 1.1.1** natywne sprawdzenie GP3D uwzględnia aktualny dostęp klanowy. Domyślny Build Trust członków klanu obejmuje także podstawowy dostęp wymagany przez TPA i pady; zarządzanie padem nadal wymaga jego własności. Bez nowego BongoUtils mod zachowuje samodzielną obsługę GP3D i dotychczasowe komendy publicznych padów. Bez GP3D teleportacje działają jak wcześniej, bez kontroli claimów.

`sharedClaimsTest` uruchamia wszystkie trzy produkcyjne mody i GP3D 19.0.0 na osobnym `verification-allmods`, z testowym JAR-em weryfikacji i markerem fixture. `sharedClaimsWithoutGpTest` sprawdza ten sam zestaw po wyjęciu GP3D. Testy używają natywnych dialogów, zaszyfrowanego logowania offline, haseł i klientów protokołu vanilla. Nie uruchamiać harnessu na świecie użytkowym; kod testowy wymaga jawnego markera katalogu. Binarny JAR weryfikacji służy wyłącznie testom.

`sharedClaimsWithoutUtilsTest` sprawdza GP3D, klany i samodzielne flagi Teleports po wyjęciu BongoUtils z katalogu testowego. Testy wymagają osobnego markera środowiska; zasoby i pliki serwera docelowego nie są modyfikowane.
