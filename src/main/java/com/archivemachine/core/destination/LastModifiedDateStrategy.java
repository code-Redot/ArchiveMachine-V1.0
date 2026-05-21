package com.archivemachine.core.destination;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class LastModifiedDateStrategy implements BucketStrategy {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @Override
    public String resolveBucket(Path item) throws Exception {
        BasicFileAttributes a = Files.readAttributes(item, BasicFileAttributes.class);
        Instant t;
        try { t = a.lastModifiedTime().toInstant(); } catch (Exception ignored) { t = Instant.now(); }
        return YearMonth.from(t.atZone(ZoneId.systemDefault())).format(FMT);
    }
}
