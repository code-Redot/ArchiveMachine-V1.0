# Build a portable app-image (folder with ArchiveMachine.exe + bundled JRE).
# Output: dist\ArchiveMachine\
#
# Prereqs:
#   - JDK 17+ (jpackage available on PATH)
#   - `mvn -DskipTests package` succeeded; the shaded jar is in target\.

$ErrorActionPreference = 'Stop'

$root = Resolve-Path "$PSScriptRoot\.."
$version = '1.1.0'
$jar = Join-Path $root "target\archivemachine-$version-all.jar"

if (-not (Test-Path $jar)) {
    Write-Error "Shaded jar not found at $jar`nRun: mvn -DskipTests package"
}

$outDir = Join-Path $root 'dist'
if (Test-Path (Join-Path $outDir 'ArchiveMachine')) {
    Remove-Item -Recurse -Force (Join-Path $outDir 'ArchiveMachine')
}

& jpackage `
    --type app-image `
    --name 'ArchiveMachine' `
    --app-version $version `
    --input (Split-Path $jar) `
    --main-jar (Split-Path $jar -Leaf) `
    --main-class 'com.archivemachine.app.Launcher' `
    --dest $outDir `
    --java-options '-Xms64m' `
    --java-options '-Xmx512m' `
    --vendor 'code-Redot' `
    --copyright 'code-Redot' `
    --description 'Level-0 file/folder transfer with date or first-letter bucketing.'

Write-Host "Portable image written to $outDir\ArchiveMachine"
