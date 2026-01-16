# ArchiveMachine

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

## Features

### Destination modes (date bucketing)
The app resolves a destination **date bucket folder** per level-0 item using one of:
- **Transfer date**
- **Creation date** (falls back to last modified if creation time is unavailable)
- **Last modified date**

Date folder format is deterministic: `MMM yyyy` with `Locale.ENGLISH`.

Base layout (no partitioning):
```
\Destination\MMM yyyy\<ItemName>
```

### Skip shortcuts (.lnk/.url + symlink/junction avoidance)
When enabled:
- Skips transferring `*.lnk` and `*.url` files.
- Does not follow directory links during traversal.
- Skips linked directories (symbolic links / reparse points) by skipping the subtree.

### Partitioning (Partition item limit)
Partitions are based on **level‑0 item count**.

Folder layout:
```
\Destination\MMM yyyy\P1\<ItemName>
\Destination\MMM yyyy\P2\<ItemName>
...
```

Behavior:
- If **limit <= 0**: partitioning is disabled (no `P#` folder).
- If **limit > 0**: the first `limit` level‑0 items for a given date bucket go to `P#`, then the next `limit` go to the next `P#`, etc.

#### Re-run rule (do not reuse existing partitions)
On a re-run, for each date bucket directory:
- If `P1`, `P2`, ... already exist, the run starts at **(max existing P + 1)**.
- New items are never placed into an already-existing `P#` for that same date bucket.

Example:
- limit = 100
- `\Destination\MMM yyyy\P1` and `P2` already exist
- the next run starts at `P3` (first 100 items -> `P3`, next 100 -> `P4`, ...)

### Decompression (7‑Zip)
When enabled, after transfer of a supported archive (`.rar/.7z/.zip`), the app runs 7‑Zip to decompress the archive into the same final destination path used by the transfer.

### Window behavior
The main window is fixed-size (non-resizable).
