param(
    [string]$GameDir = "$env:APPDATA\silentclient-launcher\game",
    [string]$Jdk = "",
    [switch]$Install,
    [switch]$SkipMixinCheck,
    [switch]$Wait
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
$mcVersion = "1.21.11"
$modsDir = Join-Path $GameDir "silentclient\fabric-mods\$mcVersion"
$lib = Join-Path $GameDir "fabric\libraries"

$silentclient = Find-Jar -Directory $modsDir -Pattern "client-runtime-*.jar"
$fabricApi = Find-Jar -Directory $modsDir -Pattern "fabric-api-*.jar"
$mixinJar = Find-Jar -Directory $lib -Pattern "sponge-mixin-*.jar"
$loaderJar = Find-Jar -Directory $lib -Pattern "fabric-loader-*.jar"
$gsonJar = Find-Jar -Directory $lib -Pattern "gson-*.jar"
$brigadierJar = Find-Jar -Directory $lib -Pattern "brigadier-*.jar"
$gameJar = Join-Path $GameDir "fabric\$mcVersion-client.jar"
if (-not (Test-Path -LiteralPath $gameJar)) { throw "minecraft client jar not found at $gameJar" }
"compiling against $silentclient"

$manifestPath = Join-Path $root "fabric.mod.json"
$mixinConfigPath = Join-Path $root "mixins.fent.json"
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json

$id = $manifest.id
if ($id -cnotmatch '^[a-z0-9_-]{1,64}$') {
    throw "invalid fabric mod id '$id' (allowed: a-z 0-9 - _ ; max 64 chars)"
}
$version = $manifest.version
if (-not $version -or $version -notmatch '^[0-9A-Za-z][0-9A-Za-z._+-]{0,63}$') {
    throw "invalid version '$version' in fabric.mod.json"
}

$jar = Join-Path $root "fent-$version.jar"
$classesDir = Join-Path $root "build\classes"
$stageDir = Join-Path $root "build\jar"

Remove-Item -LiteralPath (Join-Path $root "build") -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Path $classesDir -Force | Out-Null
New-Item -ItemType Directory -Path $stageDir -Force | Out-Null
$apiDir = Join-Path $root "build\fabric-api"
New-Item -ItemType Directory -Path $apiDir -Force | Out-Null
$apiZip = Join-Path $root "build\fabric-api.zip"
Copy-Item -LiteralPath $fabricApi -Destination $apiZip -Force
Expand-Archive -LiteralPath $apiZip -DestinationPath $apiDir -Force | Out-Null

$apiJars = Join-Path $apiDir "META-INF\jars"
$commandApi = Find-Jar -Directory $apiJars -Pattern "fabric-command-api-v2-*.jar"
$apiBase = Find-Jar -Directory $apiJars -Pattern "fabric-api-base-*.jar"
$cp = @($silentclient, $mixinJar, $loaderJar, $gsonJar, $brigadierJar, $gameJar, $commandApi, $apiBase) -join ";"

$defaultKey = ""
if ($env:FENT_API_KEY) { $defaultKey = "$env:FENT_API_KEY".Trim() }
else {
    $keyFile = Join-Path (Split-Path -Parent $root) "work\api_key.txt"
    if (Test-Path -LiteralPath $keyFile) { $defaultKey = (Get-Content -LiteralPath $keyFile -Raw).Trim() }
}
if ($defaultKey -cnotmatch '^[A-Za-z0-9._-]{1,128}$') {
    if ($defaultKey) { Write-Warning "ignoring malformed default sync key" }
    $defaultKey = ""
}
$keyGenPath = Join-Path $root "build\keygen\net\silentclient\fent\FentKeyDefault.java"
New-Item -ItemType Directory -Path (Split-Path -Parent $keyGenPath) -Force | Out-Null
[System.IO.File]::WriteAllText($keyGenPath, "package net.silentclient.fent;`r`n`r`nfinal class FentKeyDefault {`r`n    static final String VALUE = `"$defaultKey`";`r`n}`r`n", [System.Text.Encoding]::ASCII)
if ($defaultKey) { "baked default sync key: yes (a key set via /fentshare key still wins)" } else { Write-Warning "no default sync key baked - uploads need /fentshare key in game" }

$sources = Get-ChildItem -Path (Join-Path $root "net") -Recurse -Filter *.java | ForEach-Object { $_.FullName }
if (-not $sources) { throw "no sources found under $root\net" }
$stubPath = Join-Path $root "build\compile-stubs\net\minecraft\class_7157.java"
New-Item -ItemType Directory -Path (Split-Path -Parent $stubPath) -Force | Out-Null
[System.IO.File]::WriteAllText(
    $stubPath,
    "package net.minecraft;`r`n`r`npublic class class_7157 {`r`n}`r`n",
    [System.Text.Encoding]::ASCII
)
$textStubPath = Join-Path $root "build\compile-stubs\net\minecraft\class_2561.java"
[System.IO.File]::WriteAllText(
    $textStubPath,
    "package net.minecraft;`r`n`r`npublic interface class_2561 {`r`n    static class_2561 method_43470(String text) { return null; }`r`n}`r`n",
    [System.Text.Encoding]::ASCII
)
$srcStubPath = Join-Path $root "build\compile-stubs\net\minecraft\class_2172.java"
[System.IO.File]::WriteAllText(
    $srcStubPath,
    "package net.minecraft;`r`n`r`npublic interface class_2172 {`r`n    void method_47399(net.minecraft.class_2561 component);`r`n}`r`n",
    [System.Text.Encoding]::ASCII
)
$sources += @($stubPath, $textStubPath, $srcStubPath)
$sources += @($keyGenPath)

& $javac --release 21 -proc:none -nowarn -cp $cp -d $classesDir @sources
if ($LASTEXITCODE -ne 0) { throw "javac failed" }
Remove-Item -LiteralPath (Join-Path $classesDir "net\minecraft") -Recurse -Force -ErrorAction SilentlyContinue

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
        $mixinClasses = @()
        $packagePath = $mixinConfig.package.Replace('.', [string][IO.Path]::DirectorySeparatorChar)
        foreach ($mixin in @($mixinConfig.client) + @($mixinConfig.mixins) + @($mixinConfig.server)) {
            if ($mixin) {
                $mixinClasses += Join-Path (Join-Path $classesDir $packagePath) ($mixin + ".class")
            }
        }
        & (Join-Path $jdk "bin\java.exe") -cp "$toolDir;$asmCp" MixinCheck ($checkCp -join ";") @mixinClasses
        if ($LASTEXITCODE -ne 0) { throw "mixin target check failed - a SilentClient update moved an injection target" }
    }
}

if (-not $SkipMixinCheck) {
    $toolDir = Join-Path $root "build\tools"
    New-Item -ItemType Directory -Path $toolDir -Force | Out-Null
    $materialCp = (@($classesDir, $silentclient, $gameJar, $loaderJar) + @($toolDir)) -join ";"
    & $javac -nowarn -cp $materialCp -d $toolDir (Join-Path $root "tools\MaterialCheck.java")
    if ($LASTEXITCODE -ne 0) { throw "tools/MaterialCheck.java did not compile" }
    & (Join-Path $jdk "bin\java.exe") -cp "$toolDir;$materialCp" MaterialCheck
    if ($LASTEXITCODE -ne 0) { throw "material check failed - the Aero material no longer reaches what it should" }
}

if ($Install) {
    $targets = New-Object System.Collections.Generic.List[string]
    $targets.Add($modsDir)
    $mirror = Join-Path $env:APPDATA ".minecraft\mods\$mcVersion"
    if (Test-Path -LiteralPath $mirror) {
        $targets.Add($mirror)
    }

    $installed = @{}
    foreach ($target in ($targets | Select-Object -Unique)) {
        $file = Join-Path $target (Split-Path -Leaf $jar)
        $installed[$target] = $file
        foreach ($stale in @(Get-ChildItem -LiteralPath $target -Filter "fent-*.jar" -File | Where-Object { $_.FullName -ne $file })) {
            "removing stale $($stale.Name) from $target"
            while ($true) {
                try {
                    Remove-Item -LiteralPath $stale.FullName -Force -ErrorAction Stop
                    break
                } catch {
                    if (-not $Wait) { throw }
                    "game is holding $($stale.Name), waiting for it to close ..."
                    Start-Sleep -Seconds 5
                }
            }
        }
    }
    foreach ($target in ($targets | Select-Object -Unique)) {
        while ($true) {
            try {
                Copy-Item -LiteralPath $jar -Destination $installed[$target] -Force -ErrorAction Stop
                break
            } catch {
                if (-not $Wait) { throw }
                "cannot write $($installed[$target]) yet, waiting ..."
                Start-Sleep -Seconds 5
            }
        }
        "installed $($installed[$target])"
    }

    $leaf = Split-Path -Leaf $jar
    foreach ($target in ($targets | Select-Object -Unique)) {
        $profilesPath = Join-Path $target ".silent-profiles.json"
        if (-not (Test-Path -LiteralPath $profilesPath)) { continue }
        try {
            $profiles = Get-Content -LiteralPath $profilesPath -Raw | ConvertFrom-Json
            $changed = $false
            foreach ($profile in @($profiles.profiles)) {
                $carried = @($profile.mods | Where-Object { $_ -like "fent-*.jar" }).Count -gt 0
                if (-not $carried -and ($profile.name -notlike "*fent*")) { continue }
                $mods = New-Object System.Collections.Generic.List[string]
                foreach ($mod in @($profile.mods)) {
                    if ($mod -notlike "fent-*.jar") { $mods.Add($mod) }
                }
                if (-not $mods.Contains($leaf)) { $mods.Add($leaf) }
                $profile.mods = @($mods)
                $changed = $true
            }
            if ($changed) {
                $json = $profiles | ConvertTo-Json -Depth 16
                [System.IO.File]::WriteAllText($profilesPath, $json, (New-Object System.Text.UTF8Encoding($false)))
                "updated the profile mod list in $profilesPath"
            }
        }
        catch {
            Write-Warning "could not update .silent-profiles.json in ${target}: $($_.Exception.Message)"
        }
    }
}

"built $jar (mod id = $id, version = $version)"
