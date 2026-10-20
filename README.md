# Bongo's Teleports 1.5.0

Serwerowy mod Fabric dla **Minecraft Java 26.3**: prośby TPA opłacane punktami XP, parowane teleport pady oraz opcjonalna ochrona działek GP3D. Wystarczy klient vanilla — bez moda klientowego i paczki zasobów.

## Wymagania i instalacja

- **Java 25**, Fabric Loader **0.19.5+**, Fabric API **0.161.0+26.3**.
- Opcjonalnie: **GriefPrevention3D 19.0.0** (uniwersalny JAR), [BongoUtils 1.6.2](https://github.com/Silvobi/bongo-utils) do wspólnego panelu flag i [BongosCommunityClans 1.1.1](https://github.com/Silvobi/bongos-community-clans) do trustów klanowych.

Zatrzymaj serwer, wykonaj kopię świata i konfiguracji, usuń poprzedni JAR Teleports i włóż `BongosTeleports-1.5.0.jar` do `mods/`. Zachowaj `config/bongos-teleports.json` oraz `<świat>/bongos-teleports.json`. Uruchom ponownie serwer. GP3D i pozostałe mody Bongo instalowane są osobno.

## Komendy

| Komenda | Działanie |
| --- | --- |
| `/tpa <nickname>` | Wysyła prośbę do gracza online |
| `/tpaccept [nickname]` | Akceptuje prośbę; przy kilku podaj nick |
| `/tpdeny [nickname]` | Odrzuca prośbę |
| `/confirm-tpa` | Potwierdza wysoki koszt przed wysłaniem prośby |
| `/ignore-tpa` | Przełącza ignorowanie próśb; ustawienie jest trwałe |
| `/tpacancel` | Anuluje własną prośbę, potwierdzenie lub odliczanie |
| `/bongoteleports` | Krótka pomoc |
| `/bongoteleports publicpads [on\|off\|status]` | Flaga docelowej działki/poddziałki; właściciel lub Manage Trust |
| `/bongoteleports reload` | Przeładowuje konfigurację; OP 2+ lub konsola |

## TPA i koszt XP

Koszt to `ceil(odległość w X/Z × xpPerMeter)` — **punkty XP, nie poziomy**. Domyślnie stawka wynosi 0,1 XP/m, minimalna odległość 50 m, odliczanie 5 sekund, cooldown po sukcesie 60 sekund. Dokładnie 50 m kosztuje 5 XP. TPA działa w obrębie jednego wymiaru.

Po akceptacji nadawca musi pozostać w miejscu; kamera może się obracać. Ruch ponad 0,01 bloku, śmierć, rozłączenie, zmiana wymiaru, pojazd lub łóżko przerywają odliczanie. Mod ponownie sprawdza bezpieczne miejsce, odległość, XP, dostęp GP3D i cenę. Wzrost ceny ponad zaakceptowaną kwotę anuluje prośbę; spadek obniża opłatę.

XP jest pobierane dopiero po udanej teleportacji. Nieudana próba nie pobiera XP i nie uruchamia cooldownu. Cooldown sukcesu przeżywa restart; oczekujące prośby i odliczania są tymczasowe.

## Teleport pady

- **Pad Teleportacyjny / Teleporting Pad**: rama portalu Endu z oznaczeniem moda i glintem. Przepis: oko Endu w środku stołu oraz cztery stone bricks nad, pod i po bokach.
- **Harmonizator TP / TP Harmonizer**: oko Endu z glintem. Przepis bez kształtu: ender pearl + gold ingot + redstone dust. Domyślnie 10 udanych synchronizacji, także w Creative.

Zwykłe ramy portalu i oczy Endu nie uruchamiają funkcji moda.

1. Postaw dwa pady na stabilnym podłożu, zostawiając dwa wolne bloki nad nimi.
2. Kliknij oba własne pady harmonizatorem, używając przycisku używania przedmiotu (domyślnie PPM).
3. Stań na padzie i poczekaj domyślnie 3 sekundy. Zejście anuluje odliczanie.
4. Po przejściu oddal się **ponad 1 m od środka pada w X/Z**, aby zwolnić całą parę. Wysokość nie ma znaczenia.

Pady są darmowe, niezależne od cooldownu TPA i mogą łączyć wymiary. Para obsługuje jednego gracza naraz. Synchronizacja zużywa 1 punkt harmonizatora; ostatnie użycie go niszczy, zachowując połączenie. Nieudana synchronizacja nie zużywa trwałości.

**Shift + LPM harmonizatorem** czyści wybór bez rozłączania padów. **Shift + LPM innym przedmiotem lub pustą ręką** podnosi własny pad i usuwa jego połączenie. Pady są chronione przed tłokami i eksplozjami; zablokowane lub niebezpieczne miejsce docelowe uniemożliwia przejście.

Particles: czerwone — brak połączenia, zielone — gotowy, żółte — odliczanie, szare — blokada pary po przejściu. [Przepisy, lokalizacja przedmiotów, trwałość i szczegółowe zachowanie](docs/pads.md).

## GP3D i publiczne pady

Integracja wykrywa GP3D automatycznie i uwzględnia jego natywne trusty, odmowy, kształty oraz poddziałki 3D w pełnym X/Y/Z.

| Operacja | Wymagany dostęp |
| --- | --- |
| Postawienie pada | Build Trust przy bloku |
| Podniesienie/synchronizacja | Własność pada i Build Trust przy odpowiednich padach |
| TPA | Access Trust w aktualnym miejscu docelowym |
| Przejście padem | Access Trust przy lądowaniu **lub** włączone publiczne pady w tej działce |
| Zmiana flagi | Właściciel lub Manage Trust według GP3D |

Dostęp jest sprawdzany ponownie podczas odliczania. Cofnięcie trustu lub zmiana flagi może anulować teleportację bez opłaty. Niezgodne API obecnego GP3D blokuje uruchomienie integracji, a błąd odczytu blokuje daną operację. Bez GP3D pozostałe funkcje działają normalnie.

`/bongoteleports publicpads on` otwiera korzystanie z padów **do konkretnej działki/poddziałki**. `off` przywraca wymaganie Access Trust, a `status` pokazuje stan. Liczy się miejsce przy stopach gracza po lądowaniu, czyli nad blokiem pada. Flaga nie daje dostępu do TPA, budowania ani cudzych padów.

Z BongoUtils 1.6.2 komendy używają tej samej flagi `bongos_teleports:public_pads` co `/manageclaim`. `/manageclaim global` ustawia domyślną wartość działek **danego właściciela**. Kolejność: lokalny wyjątek tej działki (także „Wyłączone”) → domyślna właściciela → domyślne `off`. Lokalna flaga rodzica nie otwiera automatycznie poddziałek.

Stare flagi publicznych padów są importowane do wspólnego zapisu jednorazowo. Bez BongoUtils działa samodzielna flaga w danych Teleports. Z Clans 1.1.1 natywny GP3D uwzględnia również bieżące trusty klanowe; własność pada nadal jest wymagana do zarządzania nim. [Pełne zasady, migracja i zapis](docs/claims.md).

## Konfiguracja

| Ustawienie | Domyślnie | Znaczenie |
| --- | --- | --- |
| `xpCostEnabled` | `true` | Włącza opłatę XP dla TPA |
| `xpPerMeter` | `0.1` | Punkty XP za metr, zaokrąglane w górę; `0` oznacza darmowe TPA |
| `tooCloseDistanceMeters` | `50.0` | Minimalna odległość TPA w X/Z; `0` wyłącza ograniczenie |
| `warmupSeconds` | `5` | Czas stania nieruchomo po akceptacji, `0` bez oczekiwania |
| `cooldownSeconds` | `60` | Cooldown TPA po sukcesie, `0` wyłącza |
| `expensiveTeleportThresholdXp` | `1000` | Od tej kwoty potrzebne `/confirm-tpa`; `0` oznacza każdy dodatni koszt |
| `requestTimeoutSeconds` | `60` | Czas ważności prośby |
| `confirmationTimeoutSeconds` | `30` | Czas na potwierdzenie ceny |
| `padWarmupSeconds` | `3` | Oddzielne odliczanie pada nad hotbarem; `0` wyłącza oczekiwanie |
| `synchronizerDurability` | `10` | Trwałość nowych synchronizerów; dotychczasowe zachowują pozostałą trwałość |
| `padsAllowCrossDimension` | `true` | Pozwala łączyć pady między wymiarami |
| `soundsEnabled` | `true` | Włącza dźwięki emitowane przez mod |
| `sounds` | mapa eventów | Dźwięk, głośność, ton i przełącznik każdego eventu |

Dane padów, ignorowania i cooldownów znajdują się w `<folder świata>/bongos-teleports.json` i są zapisywane atomowo. Nieprawidłowy JSON nie jest nadpisywany. Oczekujące prośby i warm-up nie są odtwarzane po restarcie.

Pady utworzone w wersji 1.0.0 zachowują swoje połączenia. Stare synchronizery otrzymują trwałość przy wejściu do ekwipunku gracza; ich nieaktualne lore jest zastępowane nowym opisem zastosowania. Zmiana `synchronizerDurability` dotyczy nowych przedmiotów oraz starych przedmiotów, które jeszcze nie miały komponentu trwałości. Dawne `padCooldownSeconds` jest ignorowane — zastępuje je martwa strefa.

## Język, hotbar i dźwięki

Nazwy i lore przedmiotów są dostosowywane osobno do języka klienta: `pl_*` → polski, pozostałe → angielski. Dotyczy to także przedmiotu oglądanego przez dwie osoby w jednej skrzyni, przepisu i zmiany języka podczas gry. W tej gałęzi 1.5.0 lokalizacja Teleports obejmuje **prezentację przedmiotów**; komunikaty TPA i GP3D pozostają polskie. Osobne zmiany pełniejszych tłumaczeń i integracji ignore/vanish znajdują się w [PR #2](https://github.com/Silvobi/bongos-teleports/pull/2).

Instrukcje z komendami pozostają na czacie. Rutynowe potwierdzenia, błędy i odliczanie trafiają nad hotbar. Odliczanie ma pierwszeństwo przed zwykłymi potwierdzeniami. Dźwięki są vanilla, konfigurowalne globalnie i per event. [Zachowanie powiadomień i kompletna mapa dźwięków](docs/sounds.md).

## Budowanie i testy

Z JDK 25: `bash gradlew build` na Linuxie lub `gradlew.bat build` na Windowsie. Gotowy mod: `build/libs/BongosTeleports-1.5.0.jar`. `-sources.jar` zawiera kod źródłowy.

Repozytorium zawiera testy jednostkowe i testy z klientami protokołu vanilla: TPA, przepisy, trwałość, język przedmiotów, GP3D oraz wspólne testy wszystkich trzech modów (`sharedClaimsTest`, `socialFeaturesTest`, `utilsLocalizationTest`, `clanTagCompatibilityTest`). Wymagają przygotowanego, oddzielnego serwera testowego. [Instrukcje przygotowania, zadania i ograniczenia](docs/testing.md).

`teleports-verification.jar` służy wyłącznie testom. Nie instaluj go na serwerze użytkowym.

## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.
