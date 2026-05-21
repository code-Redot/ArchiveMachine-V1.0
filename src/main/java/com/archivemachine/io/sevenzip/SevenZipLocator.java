/*
 * Locates a usable 7-Zip CLI executable. Checks (in order):
 *   1. The explicitly configured path (if non-blank and executable).
 *   2. PATH (`where 7z` on Windows, `which 7z` on POSIX).
 *   3. Common install locations on Windows.
 */
package com.archivemachine.io.sevenzip;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class SevenZipLocator {

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public Optional<Path> locate(String configuredPath) {
        if (configuredPath != null && !configuredPath.isBlank()) {
            Path p = Path.of(configuredPath.trim());
            if (Files.isRegularFile(p) && Files.isExecutable(p)) {
                return Optional.of(p);
            }
        }

        Optional<Path> onPath = whichSevenZ();
        if (onPath.isPresent()) return onPath;

        if (IS_WINDOWS) {
            for (String candidate : List.of(
                    "C:\\Program Files\\7-Zip\\7z.exe",
                    "C:\\Program Files (x86)\\7-Zip\\7z.exe"
            )) {
                Path p = Path.of(candidate);
                if (Files.isRegularFile(p)) return Optional.of(p);
            }
        }
        return Optional.empty();
    }

    private Optional<Path> whichSevenZ() {
        String lookup = IS_WINDOWS ? "where" : "which";
        for (String name : List.of("7z", "7za", "7zz")) {
            try {
                ProcessBuilder pb = new ProcessBuilder(lookup, name);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                String first;
                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                    first = br.readLine();
                }
                proc.waitFor();
                if (first != null && !first.isBlank()) {
                    Path p = Path.of(first.trim());
                    if (Files.isRegularFile(p)) return Optional.of(p);
                }
            } catch (Exception ignored) { }
        }
        return Optional.empty();
    }
}
