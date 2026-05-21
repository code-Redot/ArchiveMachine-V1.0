# ArchiveMachine 1.1

Java 17 / JavaFX desktop app that transfers **level-0** items from a source directory to a destination directory, with optional 7‑Zip decompression. Strict MVP architecture, single-threaded pipeline, persistent settings.

Credits & rights: **code-Redot** — https://github.com/code-Redot

## What's new in 1.1
- **Ignore list** — pick files/folders to exclude from a run.
- **First-letter bucketing** — organize destination by item's leading character (A..Z, 0-9, #).
- **7-Zip auto-detect + bundled-installer prompt** — on first run, the app checks PATH, the configured path, and common Program Files locations. If nothing is found and a bundled installer is present, the app offers a silent install.
- **Tabbed UI** — *Pipeline*, *Ignored items*, *Help/About*.
- **Audit fixes** — refuses to silently overwrite existing destinations on cross-volume moves, case-insensitive source/destination containment check on Windows, system housekeeping folders/files (`$RECYCLE.BIN`, `System Volume Information`, `desktop.ini`, …) are auto-filtered, archive-into-extract-dir name collision is suffixed instead of clobbering.
- **Full restructure** — packages renamed to `com.archivemachine.*` and split into `app` / `mvp` / `core` / `io`. Pipeline math extracted from the presenter into `core.pipeline.PipelinePlanner`.

## Requirements
- Java 17+
- Windows recommended (system-folder filter + bundled-installer prompt are Windows-aware; the core pipeline works on any OS)
- Optional: 7‑Zip CLI on PATH (`7z`) or configured in the Pipeline tab.

## Run

### Maven (recommended)
```bash
mvn javafx:run
```

### Gradle
```bash
./gradlew run
```

## Build the exe (1.1 release)

You'll produce two artifacts: a portable app-image and a Windows MSI.

```powershell
# 1. Build the shaded fat-jar
mvn -DskipTests package

# 2. Portable image  -> dist\ArchiveMachine\ArchiveMachine.exe
powershell -ExecutionPolicy Bypass -File .\packaging\jpackage-portable.ps1

# 3. MSI installer   -> dist\ArchiveMachine-1.1.0.msi
#    (requires WiX Toolset on PATH)
powershell -ExecutionPolicy Bypass -File .\packaging\jpackage-msi.ps1
```

## 7-Zip install (bundled)
Drop the official 7-Zip installer at `src/main/resources/installers/7z-installer.exe` before building. See `src/main/resources/installers/README.txt`. The installer is **not committed to git** — fetch it from https://www.7-zip.org/download.html.

If no installer is bundled, the app still works; the missing-7z prompt becomes a "download manually" message instead.

## Architecture (MVP + core/io)

```
com.archivemachine
├── app/             — JavaFX bootstrap (Main)
├── mvp/             — MainView, MainViewContract, MainPresenter
├── core/
│   ├── pipeline/    — PipelinePlanner, PipelineEventListener
│   ├── task/        — TaskManager, CoreTask, TaskState, runtime/TaskRuntime
│   ├── destination/ — BucketStrategy + 4 strategies + DestinationResolver + DestinationMode
│   ├── tasks/       — TransferMoveTask, Decompress7ZipTask
│   ├── settings/    — CoreSettings (record), SettingsStore (java.util.prefs)
│   └── util/        — ArchiveType, Level0Enumerator, PathSafety
└── io/
    └── sevenzip/    — SevenZipLocator, SevenZipInstaller
```

## Features

### Organize by
- **Transfer date** — current `MMM yyyy`
- **Creation date** — the item's creation time (falls back to last-modified)
- **Last modified** — the item's last write time
- **First letter** — `A..Z` / `0-9` / `#`

### Skip shortcuts
Skips `*.lnk`, `*.url` and avoids following symbolic links / Windows junctions.

### Ignore list
Each entry is an absolute path (file or folder). Entries are matched against level-0 items at the start of every run. Persisted across launches.

### Partitioning
A *Partition item limit* greater than zero caps how many level-0 items go into each bucket folder. Layout becomes:

```
Destination/MMM yyyy/P1/<item>
Destination/MMM yyyy/P2/<item>
...
```

On re-runs the next partition index is `max(existing P#) + 1` per bucket.

### Decompression
Archives (`.rar`, `.7z`, `.zip`) get decompressed in-place after transfer; the original archive is then moved into the extracted folder. If a file with the same name already exists in the extract dir, the archive copy is suffixed `(1)`, `(2)`, …
