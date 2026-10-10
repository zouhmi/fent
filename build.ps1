# Builds fent-<version>.jar against the installed SilentClient runtime.
# Compiles net/**/*.java, stages the resources, then checks the packed jar.
# Checks: mod id rules, every class named in fabric.mod.json and mixins.fent.json is present,
# and every mixin target still resolves (tools/MixinCheck.java, skipped without the asm jars).
# -GameDir <path> silentclient game dir, default %APPDATA%\silentclient-launcher\game
# -Jdk <path>     jdk to use, default the first jdk 21 found
# -Install        copy the jar into the launcher's fabric-mods folder and drop older builds
#                 (two fent jars in there would abort the launch with a duplicate mod id)
# -SkipMixinCheck skip the mixin target check
param(
    [string]$GameDir = "$env:APPDATA\silentclient-launcher\game",
    [string]$Jdk = "",
    [switch]$Install,
    [switch]$SkipMixinCheck
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

function Get-JdkFeature {
    param([string]$JdkHome)
    $release = Join-Path $JdkHome "release"
    if (Test-Path -LiteralPath $release) {
        $match = [regex]::Match((Get-Content -LiteralPath $release -Raw), 'JAVA_VERSION="(\d+)')
        if ($match.Success) { return [int]$match.Groups[1].Value }
    }
    return 0
}

function Resolve-Jdk {
    param([string]$Preferred)
    $candidates = New-Object System.Collections.Generic.List[string]
    if ($Preferred) { $candidates.Add($Preferred) }
    if ($env:JAVA_HOME) { $candidates.Add($env:JAVA_HOME) }
    foreach ($parent in @("$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Java", "$env:LOCALAPPDATA\Programs\Eclipse Adoptium")) {
        if ($parent -and (Test-Path -LiteralPath $parent)) {
            foreach ($dir in (Get-ChildItem -LiteralPath $parent -Directory | Sort-Object Name -Descending)) {
                $candidates.Add($dir.FullName)
            }
        }
    }

    $usable = @()
    foreach ($candidate in $candidates) {
        if ((Test-Path -LiteralPath (Join-Path $candidate "bin\javac.exe")) -and (Test-Path -LiteralPath (Join-Path $candidate "bin\jar.exe"))) {
            $usable += [pscustomobject]@{ JdkHome = $candidate; Feature = (Get-JdkFeature $candidate) }
        }
    }
    if ($usable.Count -eq 0) { throw "no JDK with javac/jar found - pass -Jdk <path>" }

    # Prefer jdk 21, same major as the runtime.
    $jdk21 = $usable | Where-Object { $_.Feature -eq 21 } | Select-Object -First 1
    if ($jdk21) { return $jdk21.JdkHome }

    $newest = $usable | Sort-Object Feature -Descending | Select-Object -First 1
    Write-Host "no JDK 21 found - compiling with $($newest.JdkHome)"
    return $newest.JdkHome
}

function Find-Jar {
    param([string]$Directory, [string]$Pattern)
    if (-not (Test-Path -LiteralPath $Directory)) { throw "missing directory $Directory" }
    $found = Get-ChildItem -LiteralPath $Directory -Filter $Pattern -File -ErrorAction SilentlyContinue |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $found) { throw "no $Pattern under $Directory" }
    return $found.FullName
}

$jdk = Resolve-Jdk -Preferred $Jdk
$javac = Join-Path $jdk "bin\javac.exe"
$jartool = Join-Path $jdk "bin\jar.exe"
$modsDir = Join-Path $GameDir "silentclient\fabric-mods\1.21.11"
$lib = Join-Path $GameDir "fabric\libraries"

$silentclient = Find-Jar -Directory $modsDir -Pattern "client-runtime-*.jar"
$mixinJar = Find-Jar -Directory $lib -Pattern "sponge-mixin-*.jar"
$loaderJar = Find-Jar -Directory $lib -Pattern "fabric-loader-*.jar"
$gameJar = Join-Path $GameDir "fabric\1.21.11-client.jar"
if (-not (Test-Path -LiteralPath $gameJar)) { throw "minecraft client jar not found at $gameJar" }
"compiling against $silentclient"

$manifestPath = Join-Path $root "fabric.mod.json"
$mixinConfigPath = Join-Path $root "mixins.fent.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

# Fabric ids are [a-z0-9-_]{1,64}, no dots, no uppercase. Fail here, not at launch.
# -cnotmatch: -notmatch is case insensitive, Fabric's check is not.
$id = $manifest.id
if ($id -cnotmatch '^[a-z0-9_-]{1,64}$') {
    throw "invalid fabric mod id '$id' (allowed: a-z 0-9 - _ ; max 64 chars)"
}
$version = $manifest.version
if (-not $version -or $version -notmatch '^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$') {
    throw "invalid version '$version' in fabric.mod.json"
}

# Jar name comes from the manifest version.
$jar = Join-Path $root "fent-$version.jar"
$classesDir = Join-Path $root "build\classes"
$stageDir = Join-Path $root "build\jar"

$cp = @($silentclient, $mixinJar, $loaderJar, $gameJar) -join ";"

Remove-Item -LiteralPath (Join-Path $root "build") -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $stageDir -Force | Out-Null

# Only the mod tree is compiled, tools/ stays out of the jar.
$sources = Get-ChildItem -Path (Join-Path $root "net") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) { throw "no sources found under $root\net" }

# -proc:none disables Mixin's annotation processor, which needs ASM on the processor path.
& $javac --release 21 -proc:none -nowarn -cp $cp -d $classesDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }

Copy-Item -Path "$classesDir\*" -Destination $stageDir -Recurse -Force
Copy-Item -LiteralPath $manifestPath -Destination $stageDir -Force
Copy-Item -LiteralPath $mixinConfigPath -Destination $stageDir -Force
if (Test-Path -LiteralPath (Join-Path $root "assets")) {
    Copy-Item -Path (Join-Path $root "assets") -Destination $stageDir -Recurse -Force
}

Remove-Item -LiteralPath $jar -Force -ErrorAction SilentlyContinue
& $jartool --create --file $jar -C $stageDir .
if ($LASTEXITCODE -ne 0) { throw "jar failed" }

$entries = @{}
foreach ($line in (& $jartool --list --file $jar)) {
    $entries[$line.Trim().Replace('\', '/').TrimEnd('/')] = $true
}

function Assert-JarEntry {
    param([string]$Entry)
    if (-not $entries.ContainsKey($Entry)) { throw "$(Split-Path -Leaf $jar) is missing $Entry" }
}

Assert-JarEntry "fabric.mod.json"
Assert-JarEntry "mixins.fent.json"
if ($manifest.icon) { Assert-JarEntry $manifest.icon }
foreach ($entrypoint in @($manifest.entrypoints.client)) {
    if ($entrypoint) { Assert-JarEntry (($entrypoint -replace '\.', '/') + ".class") }
}

$mixinConfig = Get-Content -LiteralPath $mixinConfigPath -Raw | ConvertFrom-Json
foreach ($mixin in @($mixinConfig.client) + @($mixinConfig.mixins) + @($mixinConfig.server)) {
    if ($mixin) {
        Assert-JarEntry ((($mixinConfig.package + "." + $mixin) -replace '\.', '/') + ".class")
    }
}

if (-not $SkipMixinCheck) {
    $asmJars = @(Get-ChildItem -LiteralPath $lib -Filter "asm-*.jar" -File -ErrorAction SilentlyContinue)
    if ($asmJars.Count -eq 0) {
        Write-Warning "asm jars not found under $lib - skipping the mixin target check"
    } else {
        $toolDir = Join-Path $root "build\tools"
        New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
        $asmCp = ($asmJars | ForEach-Object { $_.FullName }) -join ";"
        & $javac -nowarn -cp $asmCp -d $toolDir (Join-Path $root "tools\MixinCheck.java")
        if ($LASTEXITCODE -ne 0) { throw "tools/MixinCheck.java did not compile" }

        $checkCp = @($silentclient, $gameJar, $loaderJar) + ($asmJars | ForEach-Object { $_.FullName })
        & (Join-Path $jdk "bin\java.exe") -cp "$toolDir;$asmCp" MixinCheck ($checkCp -join ";") (Join-Path $classesDir "net\silentclient\fent\mixin")
        if ($LASTEXITCODE -ne 0) { throw "mixin target check failed - a SilentClient update moved an injection target" }
    }
}

if ($Install) {
    $installed = Join-Path $modsDir (Split-Path -Leaf $jar)
    foreach ($stale in @(Get-ChildItem -LiteralPath $modsDir -Filter "fent-*.jar" -File | Where-Object { $_.FullName -ne $installed })) {
        "removing stale $($stale.Name)"
        Remove-Item -LiteralPath $stale.FullName -Force
    }
    Copy-Item -LiteralPath $jar -Destination $installed -Force
    "installed $installed"
}

"built $jar (mod id = $id, version = $version)"
