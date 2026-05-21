# ArchiveMachine 1.1

Java 17 JavaFX (MVP) desktop app that transfers **level-0** items from a source directory to a destination directory, with optional 7‑Zip decompression.

Credits & rights: **code-Redot** — https://github.com/code-Redot

## Requirements
- Java 17+
- Windows recommended (supports Windows shortcuts skipping; works on other OSes too)
- Optional: 7‑Zip CLI available via PATH (`7z`) or configured in the UI.

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
