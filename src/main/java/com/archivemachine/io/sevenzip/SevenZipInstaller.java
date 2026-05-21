/*
 * Extracts the bundled 7-Zip installer from the classpath and runs it silently.
 *
 * Bundled resource path: /installers/7z-installer.exe (Windows-only).
 * To make this work in a build, drop the official Igor-Pavlov-signed installer
 * at: src/main/resources/installers/7z-installer.exe (see README §7-Zip install).
 *
 * If the resource is absent, install() returns Result.NOT_BUNDLED so callers can
 * point the user at a manual install path (e.g. open https://www.7-zip.org/download.html).
 */
package com.archivemachine.io.sevenzip;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SevenZipInstaller {

    public enum Result { INSTALLED, NOT_BUNDLED, UNSUPPORTED_OS, FAILED }

    private static final String BUNDLED_RESOURCE = "/installers/7z-installer.exe";

    private static final boolean IS_WINDOWS =
            System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

    public Result install() {
        if (!IS_WINDOWS) return Result.UNSUPPORTED_OS;

        try (InputStream in = SevenZipInstaller.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in == null) return Result.NOT_BUNDLED;

            Path tmp = Files.createTempFile("7z-install-", ".exe");
            try {
                Files.copy(in, tmp, StandardCopyOption.REPLACE_EXISTING);

                ProcessBuilder pb = new ProcessBuilder(tmp.toString(), "/S");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                boolean done = p.waitFor(120, TimeUnit.SECONDS);
                if (!done) {
                    p.destroyForcibly();
                    return Result.FAILED;
                }
                return (p.exitValue() == 0) ? Result.INSTALLED : Result.FAILED;
            } finally {
                try { Files.deleteIfExists(tmp); } catch (IOException ignored) { }
            }
        } catch (Exception e) {
            return Result.FAILED;
        }
    }

    public boolean isBundleAvailable() {
        try (InputStream in = SevenZipInstaller.class.getResourceAsStream(BUNDLED_RESOURCE)) {
            return in != null;
        } catch (IOException e) {
            return false;
        }
    }
}
