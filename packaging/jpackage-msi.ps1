# Build a Windows MSI installer.
# Output: dist\ArchiveMachine-1.1.0.msi
#
# Prereqs:
#   - JDK 17+ (jpackage available on PATH)
#   - WiX Toolset 3.x  (candle.exe / light.exe). If not already on PATH, this
#     script will fetch the portable WiX 3.11 binaries into target\wix\ and
#     prepend that folder to PATH for the run.
#   - `mvn -DskipTests package` succeeded; the shaded jar is in target\.

$ErrorActionPreference = 'Stop'

# Ensure WiX is reachable
if (-not (Get-Command candle -ErrorAction SilentlyContinue)) {
    $wixDir = Join-Path (Resolve-Path "$PSScriptRoot\..") 'target\wix'
    if (-not (Test-Path (Join-Path $wixDir 'candle.exe'))) {
        Write-Host 'Fetching portable WiX 3.11 binaries...'
        $zip = Join-Path (Resolve-Path "$PSScriptRoot\..") 'target\wix311-binaries.zip'
        New-Item -ItemType Directory -Force -Path (Split-Path $zip) | Out-Null
        Invoke-WebRequest -Uri 'https://github.com/wixtoolset/wix3/releases/download/wix3112rtm/wix311-binaries.zip' -OutFile $zip -UseBasicParsing
        Expand-Archive -Path $zip -DestinationPath $wixDir -Force
    }
    $env:Path = "$wixDir;$env:Path"
}

$root = Resolve-Path "$PSScriptRoot\.."
$version = '1.1.0'
$jar = Join-Path $root "target\archivemachine-$version-all.jar"

if (-not (Test-Path $jar)) {
    Write-Error "Shaded jar not found at $jar`nRun: mvn -DskipTests package"
}

$outDir = Join-Path $root 'dist'
New-Item -ItemType Directory -Force -Path $outDir | Out-Null

& jpackage `
    --type msi `
    --name 'ArchiveMachine' `
    --app-version $version `
    --input (Split-Path $jar) `
    --main-jar (Split-Path $jar -Leaf) `
    --main-class 'com.archivemachine.app.Launcher' `
    --dest $outDir `
    --vendor 'code-Redot' `
    --copyright 'code-Redot' `
    --win-dir-chooser `
    --win-shortcut `
    --win-menu `
    --win-menu-group 'ArchiveMachine' `
    --description 'Level-0 file/folder transfer with date or first-letter bucketing.'

Write-Host "MSI written to $outDir"
