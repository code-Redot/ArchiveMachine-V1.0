/*
 * Purpose: Persists CoreSettings in java.util.prefs (per-user).
 * Keys are stable and defaults are used to keep backward compatibility across versions.
 */
package com.Redot.core.settings;

import com.Redot.core.destination.DestinationMode;
import java.util.prefs.Preferences;

public final class SettingsStore {
    // region Preferences keys + defaults
    private static final String NODE = "com.Redot.ArchiveMachine.Core";

    private static final String K_LAST_SOURCE = "lastSourceDirectory";
    private static final String K_LAST_DEST = "lastDestinationDirectory";
    private static final String K_DECOMPRESS = "decompressionEnabled";
    private static final String K_SKIP_SHORTCUTS = "skipShortcutsEnabled";
    private static final String K_PARTITION_LIMIT = "partitionItemLimit";
    private static final String K_MODE = "destinationMode";
    private static final String K_7Z = "sevenZipPath";

    private final Preferences p = Preferences.userRoot().node(NODE);

    // region Load/save rules (backward compatible)
    public CoreSettings load() {
        String src = p.get(K_LAST_SOURCE, "");
        String dst = p.get(K_LAST_DEST, "");
        boolean dec = p.getBoolean(K_DECOMPRESS, false);
        boolean skipShortcuts = p.getBoolean(K_SKIP_SHORTCUTS, false);
        int partitionLimit = p.getInt(K_PARTITION_LIMIT, 0);
        DestinationMode mode = DestinationMode.valueOf(p.get(K_MODE, DestinationMode.TRANSFER_DATE.name()));
        String sevenZip = p.get(K_7Z, "7z");
        return new CoreSettings(src, dst, dec, skipShortcuts, Math.max(0, partitionLimit), mode, sevenZip);
    }

    public void save(CoreSettings s) {
        p.put(K_LAST_SOURCE, n(s.lastSourceDirectory()));
        p.put(K_LAST_DEST, n(s.lastDestinationDirectory()));
        p.putBoolean(K_DECOMPRESS, s.decompressionEnabled());
        p.putBoolean(K_SKIP_SHORTCUTS, s.skipShortcutsEnabled());
        p.putInt(K_PARTITION_LIMIT, Math.max(0, s.partitionItemLimit()));
        p.put(K_MODE, s.destinationMode().name());
        p.put(K_7Z, n(s.sevenZipPath()));
    }

    private static String n(String v) { return v == null ? "" : v; }
}
