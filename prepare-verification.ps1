param([Parameter(Mandatory=$true)][string]$ServerPath)
$ErrorActionPreference = 'Stop'
$reference = (Resolve-Path -LiteralPath $ServerPath).Path
$testRoot = Join-Path $PSScriptRoot 'verification-server'
if ($reference.TrimEnd('\') -eq $testRoot.TrimEnd('\')) { throw 'Podaj serwer źródłowy, a nie katalog weryfikacji.' }
New-Item -ItemType Directory -Path $testRoot -Force | Out-Null
foreach ($folder in @('libraries','versions','.fabric')) {
    if (!(Test-Path -LiteralPath (Join-Path $testRoot $folder))) {
        Copy-Item -LiteralPath (Join-Path $reference $folder) -Destination $testRoot -Recurse
    }
}
Copy-Item -LiteralPath (Join-Path $reference 'fabric-server-launch.jar') -Destination $testRoot
New-Item -ItemType Directory -Path "$testRoot\mods","$testRoot\config" -Force | Out-Null
Get-ChildItem -LiteralPath "$reference\mods" -Filter '*.jar' | Copy-Item -Destination "$testRoot\mods"
Set-Content -LiteralPath "$testRoot\eula.txt" -Value 'eula=true'
@'
server-ip=127.0.0.1
server-port=25582
online-mode=false
white-list=false
enforce-whitelist=false
spawn-protection=0
gamemode=creative
force-gamemode=false
level-type=minecraft:flat
generator-settings={"layers":[{"block":"minecraft:air","height":64},{"block":"minecraft:stone","height":1}],"biome":"minecraft:plains","features":false,"lakes":false,"structure_overrides":[]}
max-tick-time=120000
view-distance=2
simulation-distance=2
network-compression-threshold=256
sync-chunk-writes=false
difficulty=peaceful
allow-flight=true
'@ | Set-Content -LiteralPath "$testRoot\server.properties"
$testConfig = Get-Content -Raw -LiteralPath (Join-Path $PSScriptRoot 'config\bongos-teleports.json') | ConvertFrom-Json
$testConfig.warmupSeconds=1
$testConfig.xpPerMeter=1
$testConfig.tooCloseDistanceMeters=0
$testConfig.cooldownSeconds=2
$testConfig.expensiveTeleportThresholdXp=50
$testConfig.requestTimeoutSeconds=2
$testConfig.confirmationTimeoutSeconds=2
$testConfig | ConvertTo-Json | Set-Content -LiteralPath "$testRoot\config\bongos-teleports.json"
Write-Output "Gotowe: $testRoot. Uruchom gradlew.bat integrationTest z JDK 25."
