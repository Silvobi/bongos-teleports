# Budowanie i testy Teleports oraz wspólnego zestawu Bongo

[Powrót do README](../README.md)

## Build

Wymagane JDK 25. `bash gradlew build` (Linux) lub `gradlew.bat build` (Windows) kompiluje mod i uruchamia testy jednostkowe. Wynik: `build/libs/BongosTeleports-1.5.0.jar`, dodatkowo archiwum źródeł.

## Oddzielny serwer Teleports

Z JDK 25: `gradlew.bat build`. Wynik: `build/libs/BongosTeleports-1.5.0.jar`, dodatkowo JAR źródeł. Przed pierwszym testem integracyjnym uruchom `./prepare-verification.ps1 -ServerPath 'C:\ścieżka\do\serwera'`, a następnie `gradlew.bat integrationTest feedbackTest durabilityTest languageTest`. Testy używają osobnego katalogu `verification-server`, lokalnego portu 25582 i klientów protokołu vanilla. W tej kopii mod BongoUtils jest odsuwany do `external-auth-mods`, aby logowanie klientów testowych nie zależało od zewnętrznego API Mojang; serwer docelowy zachowuje ten mod. Środowisko testowe ma skrócone czasy; docelowy config zachowuje domyślne 5 i 60 sekund. Nie używaj świata produkcyjnego w katalogu weryfikacji: test resetuje dane teleportów i modyfikuje swój świat.

Plik `teleports-verification.jar` służy wyłącznie testom craftingu; nie instalować go na docelowym serwerze.

Źródło zaleceń dla środowiska 26.3: [Fabric for Minecraft 26.3](https://www.fabricmc.net/2026/09/15/263.html).

### Weryfikacja integracji GP3D

`gradlew build` uruchamia również testy polityki claimów, oddzielenia flag rodzica i poddziałki, obsługi błędów i migracji danych. `gradlew claimProtectionTest` uruchamia test na prawdziwym serwerze i klientach vanilla: umieść wcześniej uniwersalny `GriefPrevention3D-19.0.0.jar` w `verification-server/mods/`. Ten test tworzy własne fixture claimów 100/101 i resetuje dane teleportów w katalogu testowym. Uruchamiaj go wyłącznie na osobnym środowisku weryfikacji, nigdy na świecie produkcyjnym. Sprawdza odmowę bez trustu, Build/Access Trust, odebranie dostępu podczas odliczania, uprawnienia flagi, niezależność poddziałki 3D, publiczny pad bez publicznego TPA i rzeczywisty restart serwera. Harnessy obsługują Windows i Linux z JDK 25.

## Wspólne środowisko `verification-allmods`

Te zadania uruchamiają własny lokalny serwer i klientów protokołu vanilla. Nie wymagają otwierania klienta graficznego, ale wymagają przygotowanego środowiska Fabric 26.3. `prepare-verification.ps1` przygotowuje tylko starsze `verification-server`; nie przygotowuje automatycznie tego katalogu.

1. Utwórz **nowy, pusty, testowy** katalog `verification-allmods` w katalogu repozytorium. Przygotuj w nim launcher `fabric-server-launch.jar`, jego pliki konfiguracyjne i biblioteki dla Fabric 26.3. Nie kopiuj świata, kont, haseł ani prywatnej konfiguracji z serwera użytkowego.
2. W `mods/` umieść Fabric API 0.161.0+26.3, uniwersalny GP3D 19.0.0, BongoUtils 1.6.2 i BongosCommunityClans 1.1.1. Gradle dołoży bieżący Teleports i testowy JAR. Zachowaj po jednej wersji każdego moda.
3. Zaakceptuj EULA, wpisując `eula=true` do `eula.txt`, jeśli akceptujesz jej warunki.
4. Ustaw `server.properties` według poniższego przykładu. Nazwa świata musi być `world`; port 25582 musi być wolny.
5. W `claim-suite-fixture.txt` zapisz w osobnych wierszach: `Bcfowner`, `Bcfmate`, `Bcfother`. Marker umożliwia testowemu modowi zasymulowanie braku tych nicków w Mojang; szyfrowanie i logowanie hasłem pozostają rzeczywiste. Używaj go wyłącznie w tym odizolowanym środowisku.
6. Uruchamiaj zadania **kolejno**, przy zatrzymanym serwerze testowym. Harness sam uruchamia i zatrzymuje proces. Pierwszy `sharedClaimsTest` tworzy bazowy stan używany również przez część kolejnych prób.

```properties
server-ip=127.0.0.1
server-port=25582
online-mode=false
enforce-secure-profile=false
white-list=false
enforce-whitelist=false
spawn-protection=0
gamemode=creative
force-gamemode=false
level-name=world
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:air","height":64},{"block":"minecraft:stone","height":1}],"biome":"minecraft:plains","features":false,"lakes":false,"structure_overrides":[]}
max-tick-time=120000
view-distance=2
simulation-distance=2
network-compression-threshold=256
difficulty=peaceful
allow-flight=true
```

Ustawienia `online-mode=false` i pozostałe parametry powyżej dotyczą tylko lokalnego fixture. Nie zastępują instrukcji instalacji serwera użytkowego BongoUtils.

| Zadanie Gradle | Sprawdzane zachowanie |
| --- | --- |
| `sharedClaimsTest` | Dialogi lokalne/globalne, pierwszeństwo flag, migracja, trusty klanowe, odmowy GP3D, poddziałki 3D, ranking, TPA/pady i restart |
| `socialFeaturesTest` | Ruch/AFK, statusy, wzmianki per odbiorca, prywatność czatu, `/m`, `/r`, regulamin i reload |
| `utilsLocalizationTest` | Klienci PL, EN i `de_de`, logowanie/rejestracja, dialogi, błędy, AFK, regulamin i zmiana języka |
| `sharedClaimsWithoutGpTest` | Opcjonalność GP3D; wcześniej przenieś jego JAR poza testowe `mods/` |
| `sharedClaimsWithoutUtilsTest` | Samodzielne trusty klanowe i publiczne pady; po bazowym teście przenieś Utils poza testowe `mods/` |
| `clanTagCompatibilityTest` | Błąd starego `bad_tag` i uruchomienie tych samych danych z poprawką 1.1.1 |

Przykład (Linux, uruchomiony z katalogu Teleports):

```sh
bash gradlew sharedClaimsTest
bash gradlew socialFeaturesTest
bash gradlew utilsLocalizationTest
bash gradlew clanTagCompatibilityTest -PpreviousClansJar=/absolutna/sciezka/BongosCommunityClans-1.1.0.jar -PfixedClansJar=/absolutna/sciezka/BongosCommunityClans-1.1.1.jar
```

Dwa JAR-y Clans w ostatnim zadaniu muszą znajdować się poza testowym `mods/`. Harness podmienia je na czas próby i przywraca poprawiony JAR na końcu. Warianty bez GP3D/Utils uruchamiaj pojedynczo i przywracaj przeniesione mody przed kolejną pełną próbą. Wariant `socialFeaturesTest -PsocialStandalone` wymaga wcześniejszego przeniesienia GP3D i Clans poza testowe `mods/`.

Testy resetują dane fixture i zmieniają jego świat. Marker nie czyni świata bezpiecznym — nie wskazuj tutaj świata produkcyjnego. `teleports-verification.jar` zawiera narzędzia testowe i **nie należy instalować go na serwerze użytkowym ani dołączać do paczki modów dla graczy**.

Testy protokołu sprawdzają strukturę dialogów, pakiety, uprawnienia i zachowanie serwera. Nie zastępują oceny wyglądu czcionek i ekranów w graficznym kliencie ani uwierzytelnionego wejścia rzeczywistym kontem MSA.
