package com.archivemachine.core.destination;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class TransferDateStrategy implements BucketStrategy {
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH);

    @Override
    public String resolveBucket(Path item) {
        ZonedDateTime now = ZonedDateTime.now();
        return YearMonth.of(now.getYear(), now.getMonth()).format(FMT);
    }
}
