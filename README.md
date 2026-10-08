# Bongo's Teleports 1.3.0


## Autorstwo AI / AI disclosure

Kod tego moda został napisany przez AI — OpenAI Codex — na podstawie wymagań i wskazówek Bongo.

This mod's code was written by AI — OpenAI Codex — based on Bongo's requirements and guidance.

Mod wyłącznie na serwer Fabric Minecraft **26.3**, Java 25, Fabric Loader 0.19.5+, Fabric API 0.161.0+26.3. Klientowi wystarczy vanilla 26.3. Nie wymaga paczki zasobów ani moda klientowego.

## Instalacja

Umieść `BongosTeleports-1.3.0.jar` w `mods/`, usuń starszy JAR tego moda z `mods/` i uruchom ponownie serwer. Fabric API jest wymagane i już znajduje się na serwerze testowym Bongo. Plik konfiguracyjny to `config/bongos-teleports.json`. Operator poziomu 2+ może wczytać zmiany przez `/bongoteleports reload`. Przeładowanie anuluje oczekujące teleportacje, w tym odliczania padów, aby nowe parametry nie zmieniły zaakceptowanej teleportacji.

## Prośby o teleportację

- `/tpa <nickname>` — wysyła prośbę do gracza online.
- `/tpaccept [nickname]` — odbiorca akceptuje prośbę. Przy kilku prośbach należy podać nick.
- `/tpdeny [nickname]` — odbiorca odrzuca prośbę.
- `/confirm-tpa` — nadawca potwierdza wysoki koszt przed wysłaniem prośby.
- `/ignore-tpa` — przełącza ignorowanie próśb; ustawienie zachowuje się po restarcie.
- `/tpacancel` — anuluje własną prośbę, potwierdzenie albo warm-up.
- `/bongoteleports` — krótka pomoc.

Koszt to `ceil(sqrt((X1-X2)² + (Z1-Z2)²) * xpPerMeter)`. Wysokość Y jest pomijana. Są to **punkty XP**, nie poziomy. Domyślnie 10 m kosztuje 1 XP, z zaokrągleniem w górę: odległości 1, 2, 3, 5 i 8 m kosztowałyby po 1 XP. Domyślny próg `tooCloseDistanceMeters=50` blokuje jednak TPA na odległości poniżej 50 m. Dokładnie 50 m jest dozwolone i kosztuje 5 XP. Próg można ustawić na `0`, aby wyłączyć tę blokadę. Jest sprawdzany przy wysyłaniu prośby, potwierdzeniu, akceptacji i zakończeniu warm-up.

TPA działa w tym samym wymiarze. Koszt między wymiarami nie ma jednoznacznego znaczenia, więc taka prośba jest odrzucana. Po zgodzie odbiorcy nadawca musi stać nieruchomo przez 5 sekund. Obracanie kamery jest dozwolone; przemieszczenie o ponad 0.01 bloku przerywa warm-up. Śmierć, rozłączenie, zmiana wymiaru, wejście do pojazdu lub łóżka także przerywa teleportację.

XP pobierane jest dopiero po udanej teleportacji. Cena zostaje ponownie sprawdzona po akceptacji i na końcu warm-up. Jeśli wzrosła ponad pierwotnie zaakceptowaną kwotę, teleportacja zostaje anulowana: nadawca musi ponownie użyć `/tpa`. Niższa cena oznacza niższą opłatę. Mod sprawdza XP z poziomu i paska postępu, także po komendach `/experience` lub zaklinaniu.

Nieudane próby nie pobierają XP i nie uruchamiają cooldownu. Po sukcesie cooldown wynosi 60 sekund i zachowuje się po restarcie. Cel musi mieć wolne miejsce, podłoże i znajdować się w granicy świata, bez płynu lub ognia w miejscu lądowania.

## Teleport pady

Nazwy i lore obu przedmiotów automatycznie dopasowują się do języka klienta:

| Język | Pad | Harmonizator |
| --- | --- | --- |
| Polski | Pad Teleportacyjny | Harmonizator TP |
| Angielski | Teleporting Pad | TP Harmonizer |

Pozostałe języki otrzymują angielskie nazwy i opisy. Obsługa PL/EN w tej wersji obejmuje nazwy i lore przedmiotów, w tym informacje o wybranym padzie. Dwie osoby oglądające ten sam przedmiot we wspólnej skrzyni widzą swój język. Zmiana języka odświeża otwarte okno bez ponownego logowania. Crafting, ekwipunek oraz wcześniej utworzone przedmioty używają tych samych nazw.

Lore Pada Teleportacyjnego:

- PL: „Postaw dwa pady i zsynchronizuj je za pomocą Harmonizatora TP.”
- EN: „Place both pads and sync them with TP Harmonizer.”

Fraza „Harmonizatora TP” / „TP Harmonizer” jest żółta. Pozostały tekst jest szary, bez kursywy.

Lore pustego Harmonizatora TP:

- PL: „Służy do synchronizowania dwóch padów teleportacyjnych. Kliknij [BIND] na jeden, a potem na drugi pad, aby zsynchronizować.”
- EN: „Syncs two teleport pads. Press [BIND] on first and then second pad to sync them.”

Fraza „padów teleportacyjnych” / „teleport pads” jest żółta. `[BIND]` to rzeczywiste przypisanie używania przedmiotu w ustawieniach klienta — domyślnie prawy przycisk myszy. Opisy są rozłożone na kilka krótszych linii. Po wyczyszczeniu wyboru lub ukończeniu synchronizacji wraca ten opis; współrzędne i informacje o poprzednim wyborze znikają. Podczas wyboru nazwa pozostaje żółta, a informacje o synchronizacji szare i także są tłumaczone.

Dummy asset pada to **rama portalu Endu**. Przedmiot ma nazwę Pad Teleportacyjny / Teleporting Pad oraz glint. Harmonizator TP / TP Harmonizer to **oko Endu z glintem**, nazwą i aktualizowanym lore. Zwykłe ramy portalu i oczy Endu nie mają funkcji moda.

Crafting Pada Teleportacyjnego (1 sztuka, stół rzemieślniczy):

```text
[pusto]        [stone bricks] [pusto]
[stone bricks] [eye of ender] [stone bricks]
[pusto]        [stone bricks] [pusto]
```

Crafting Harmonizatora TP (1 sztuka): ender pearl + gold ingot + redstone dust, w dowolnym układzie. Synchronizer ma domyślnie **10 punktów trwałości** i nie stackuje się. Każda udana synchronizacja zużywa dokładnie 1 punkt, także w Creative. Ostatnie użycie niszczy przedmiot, ale gotowe połączenie pozostaje aktywne. Wybór pierwszego pada, odrzucenie synchronizacji i czyszczenie wyboru nie zużywają trwałości. Pasek trwałości jest widoczny w kliencie vanilla.

1. Postaw dwa pady prawym przyciskiem na stabilnym podłożu, pozostawiając nad nimi dwa wolne bloki.
2. Kliknij pierwszy pad synchronizerem. Nazwa przedmiotu stanie się żółta, a jego współrzędne i instrukcja pojawią się w szarym lore.
3. Kliknij drugi pad tym samym synchronizerem. Po udanym połączeniu informacje o wybranych padach znikają, wraca opis zastosowania, a nazwa wraca do białego koloru. **Shift + LPM**, również w powietrze, czyści wybór bez zużywania trwałości i bez rozłączania już połączonych padów.
4. Stań na padzie i pozostań na nim przez domyślnie **3 sekundy**. Odliczanie wyświetla się nad hotbarem. Zejście z pada przerywa i resetuje odliczanie; ponowne wejście zaczyna cały czas od początku.

Kolory particlesów nad padem określają stan pracy:

| Status | Kolor |
| --- | --- |
| `not synchronized` — brak połączenia | czerwony |
| `idle` — gotowy | zielony |
| `teleporting` — trwa odliczanie | żółty na obu padach |
| blokada martwej strefy | szary na obu padach |

Po teleportacji **cała para** jest niedostępna dla wszystkich, dopóki teleportowany gracz nie opuści koła o promieniu **1 m od środka pada w X/Z**. Wysokość Y nie jest liczona. Samo zejście z bloku może być niewystarczające; trzeba oddalić się o więcej niż 1 m. Nie ma dodatkowego czasowego cooldownu padów. W czasie odliczania para jest zajęta przez jednego gracza. Śmierć lub rozłączenie gracza zwalnia blokadę; odliczania i blokady nie są odtwarzane po restarcie.

Pady mogą łączyć różne wymiary. Teleportacja przez pady jest darmowa, niezależna od cooldownu TPA i dostępna wszystkim graczom. Łączenie i podnoszenie jest dostępne właścicielowi. Aby podnieść własny pad, użyj **Shift + lewy przycisk**; pad wypada jako przedmiot, a połączenie w obu kierunkach znika. Połączenie nowych padów usuwa ich poprzednie połączenia. Kliknięcie wybranego pierwszego pada drugi raz czyści wybór w synchronizerze.

Pady są odporne na tłoki i eksplozje. Nie można dodawać do nich oczu Endu. Mod sprawdza istnienie obu padów i bezpieczne miejsce docelowe; zasłonięcie miejsca lądowania blokuje teleportację. Docelowy chunk jest wczytywany na czas przejścia. Aby podnieść pad przez Shift + LPM, trzymaj inny przedmiot lub pustą rękę — z synchronizerem ten gest czyści jego wybór.

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

## Budowanie i testy

Z JDK 25: `gradlew.bat build`. Wynik: `build/libs/BongosTeleports-1.3.0.jar`, dodatkowo JAR źródeł. Przed pierwszym testem integracyjnym uruchom `./prepare-verification.ps1 -ServerPath 'C:\ścieżka\do\serwera'`, a następnie `gradlew.bat integrationTest feedbackTest durabilityTest languageTest`. Testy używają osobnego katalogu `verification-server`, lokalnego portu 25582 i klientów protokołu vanilla. W tej kopii mod BongoUtils jest odsuwany do `external-auth-mods`, aby logowanie klientów testowych nie zależało od zewnętrznego API Mojang; serwer docelowy zachowuje ten mod. Środowisko testowe ma skrócone czasy; docelowy config zachowuje domyślne 5 i 60 sekund. Nie używaj świata produkcyjnego w katalogu weryfikacji: test resetuje dane teleportów i modyfikuje swój świat.

Plik `teleports-verification.jar` służy wyłącznie testom craftingu; nie instalować go na docelowym serwerze.

Źródło zaleceń dla środowiska 26.3: [Fabric for Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html).


## Dźwięki i powiadomienia — od wersji 1.2.0

Na czacie zostają instrukcje zawierające komendy: akceptacja/odrzucenie prośby TPA, potwierdzenie wysokiej lub nowej ceny, wskazanie nadawcy przy kilku prośbach, wskazówka `/tpacancel` i pomoc z `/bongoteleports`.

Pozostałe komunikaty moda trafiają nad hotbar: statusy próśb, pobrane XP, cooldown, błędy, ignorowanie, stawianie i podnoszenie padów, wybór/czyszczenie synchronizera, synchronizacja, jego zużycie, blokada martwej strefy i wynik przeładowania konfiguracji dla operatora będącego graczem. Konsola otrzymuje zwykły tekst.

Odliczanie ma pierwszeństwo przed rutynowymi potwierdzeniami. Takie potwierdzenie jest odkładane i wyświetlane po komunikacie z wynikiem teleportacji. Błąd może na krótko zastąpić odliczanie, po czym odliczanie wraca. Udana synchronizacja i zużycie ostatniego punktu trwałości są jednym alertem: „Pady zsynchronizowane — Harmonizator TP zużyty”.

Każda sekunda warm-up TPA i padów ma dokładnie jedno tyknięcie, z rosnącym tonem. Wysoki koszt sygnalizują dwa oddzielone uderzenia dzwonka. Prywatne komunikaty TPA i błędy słyszy właściwy gracz; działanie padów, w tym przerwanie odliczania, ma dźwięk przestrzenny. Crafting odzywa się przy odebraniu wyniku, a nie podczas podglądu przepisu.

Dźwięki są vanilla i nie wymagają dodatkowych plików u graczy. W `sounds` każdy event ma pola `enabled`, `sound` (pełne ID), `volume` (0–4) i `pitch` (0.5–2). `enabled=false` wycisza pojedynczy event, a `soundsEnabled=false` wycisza wszystkie dźwięki emitowane przez mod. Nieznane ID dźwięku lub nieprawidłowe wartości odrzucają przeładowanie i zachowują dotychczasową działającą konfigurację. Brakujące eventy otrzymują domyślne ustawienia.

Domyślne przypisania:

| Event w configu | Dźwięk vanilla |
| --- | --- |
| `tpa_request_sent` | `minecraft:ui.button.click` |
| `tpa_request_received` | `minecraft:block.note_block.pling` |
| `tpa_cost_warning` | `minecraft:block.note_block.bell` |
| `tpa_cost_confirmed` | `minecraft:ui.button.click` |
| `tpa_accepted` | `minecraft:entity.experience_orb.pickup` |
| `tpa_denied` | `minecraft:block.note_block.bass` |
| `tpa_warmup_start` | `minecraft:block.beacon.power_select` |
| `tpa_warmup_tick` | `minecraft:block.note_block.hat` |
| `tpa_teleport` | `minecraft:entity.enderman.teleport` |
| `tpa_cancelled` | `minecraft:block.beacon.deactivate` |
| `tpa_expired` | `minecraft:block.note_block.bass` |
| `tpa_error` | `minecraft:block.note_block.bass` |
| `ignore_enabled` | `minecraft:ui.button.click` |
| `ignore_disabled` | `minecraft:ui.button.click` |
| `pad_crafted` | `minecraft:entity.experience_orb.pickup` |
| `synchronizer_crafted` | `minecraft:entity.experience_orb.pickup` |
| `pad_placed` | `minecraft:block.stone.place` |
| `pad_picked_up` | `minecraft:block.stone.break` |
| `synchronizer_selected` | `minecraft:block.amethyst_block.chime` |
| `synchronizer_cleared` | `minecraft:ui.button.click` |
| `pads_synchronized` | `minecraft:block.enchantment_table.use` |
| `synchronizer_broken` | `minecraft:entity.item.break` |
| `pad_unlinked` | `minecraft:block.beacon.deactivate` |
| `pad_warmup_start` | `minecraft:block.beacon.activate` |
| `pad_warmup_tick` | `minecraft:block.note_block.hat` |
| `pad_teleport` | `minecraft:entity.enderman.teleport` |
| `pad_cancelled` | `minecraft:block.beacon.deactivate` |
| `pad_error` | `minecraft:block.note_block.bass` |
| `pad_unlocked` | `minecraft:block.amethyst_block.chime` |
| `config_reloaded` | `minecraft:ui.button.click` |
| `config_error` | `minecraft:block.note_block.bass` |
