/*
 * Persists CoreSettings in java.util.prefs (per-user).
 * Backward compatible — older preference nodes without ignore-list load with an empty list.
 */
package com.archivemachine.core.settings;

import com.archivemachine.core.destination.DestinationMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.prefs.Preferences;

public final class SettingsStore {

    private static final String NODE = "com.archivemachine.ArchiveMachine.Core";

    private static final String K_LAST_SOURCE = "lastSourceDirectory";
    private static final String K_LAST_DEST = "lastDestinationDirectory";
    private static final String K_DECOMPRESS = "decompressionEnabled";
    private static final String K_SKIP_SHORTCUTS = "skipShortcutsEnabled";
    private static final String K_PARTITION_LIMIT = "partitionItemLimit";
    private static final String K_MODE = "destinationMode";
    private static final String K_7Z = "sevenZipPath";
    private static final String K_IGNORE_LIST = "ignoredPaths";

    // Newline-as-record separator (illegal in Windows paths) keeps prefs to one entry.
    private static final String IGNORE_SEP = "\n";

    private final Preferences p = Preferences.userRoot().node(NODE);

    public CoreSettings load() {
        String src = p.get(K_LAST_SOURCE, "");
        String dst = p.get(K_LAST_DEST, "");
        boolean dec = p.getBoolean(K_DECOMPRESS, false);
        boolean skipShortcuts = p.getBoolean(K_SKIP_SHORTCUTS, false);
        int partitionLimit = p.getInt(K_PARTITION_LIMIT, 0);

        DestinationMode mode;
        try { mode = DestinationMode.valueOf(p.get(K_MODE, DestinationMode.TRANSFER_DATE.name())); }
        catch (IllegalArgumentException ex) { mode = DestinationMode.TRANSFER_DATE; }

        String sevenZip = p.get(K_7Z, "7z");

        String raw = p.get(K_IGNORE_LIST, "");
        List<String> ignored = raw.isBlank()
                ? List.of()
                : Arrays.stream(raw.split(IGNORE_SEP))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toCollection(ArrayList::new));

        return new CoreSettings(src, dst, dec, skipShortcuts, Math.max(0, partitionLimit), mode, sevenZip, ignored);
    }

    public void save(CoreSettings s) {
        p.put(K_LAST_SOURCE, n(s.lastSourceDirectory()));
        p.put(K_LAST_DEST, n(s.lastDestinationDirectory()));
        p.putBoolean(K_DECOMPRESS, s.decompressionEnabled());
        p.putBoolean(K_SKIP_SHORTCUTS, s.skipShortcutsEnabled());
        p.putInt(K_PARTITION_LIMIT, Math.max(0, s.partitionItemLimit()));
        p.put(K_MODE, s.destinationMode().name());
        p.put(K_7Z, n(s.sevenZipPath()));

        String joined = String.join(IGNORE_SEP, s.ignoredPaths());
        p.put(K_IGNORE_LIST, joined);
    }

    private static String n(String v) { return v == null ? "" : v; }
}
