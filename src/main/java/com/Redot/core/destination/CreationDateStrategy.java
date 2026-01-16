package com.Redot.core.destination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;

public final class CreationDateStrategy implements DestinationStrategy {
    @Override
    public YearMonth resolveBucket(Path item) throws Exception {
        BasicFileAttributes a = Files.readAttributes(item, BasicFileAttributes.class);
        Instant t;
        try { t = a.creationTime().toInstant(); } catch (Exception ignored) { t = null; }
        if (t == null) {
            try { t = a.lastModifiedTime().toInstant(); } catch (Exception ignored) { t = Instant.now(); }
        }
        return YearMonth.from(t.atZone(ZoneId.systemDefault()));
    }
}
