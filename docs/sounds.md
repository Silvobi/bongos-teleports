# Dźwięki i powiadomienia

[Powrót do README](../README.md)

## Zachowanie

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
