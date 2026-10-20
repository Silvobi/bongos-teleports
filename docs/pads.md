# Teleport pady i Harmonizator TP

[Powrót do README](../README.md)

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

Pady mogą łączyć różne wymiary. Teleportacja przez pady jest darmowa i niezależna od cooldownu TPA. Z GriefPrevention3D wymaga Access Trust w docelowej działce lub włączenia jej flagi publicznych padów; bez GP3D jest dostępna wszystkim graczom. Łączenie i podnoszenie jest dostępne właścicielowi. Aby podnieść własny pad, użyj **Shift + lewy przycisk**; pad wypada jako przedmiot, a połączenie w obu kierunkach znika. Połączenie nowych padów usuwa ich poprzednie połączenia. Kliknięcie wybranego pierwszego pada drugi raz czyści wybór w synchronizerze.

Pady są odporne na tłoki i eksplozje. Nie można dodawać do nich oczu Endu. Mod sprawdza istnienie obu padów i bezpieczne miejsce docelowe; zasłonięcie miejsca lądowania blokuje teleportację. Docelowy chunk jest wczytywany na czas przejścia. Aby podnieść pad przez Shift + LPM, trzymaj inny przedmiot lub pustą rękę — z synchronizerem ten gest czyści jego wybór.
