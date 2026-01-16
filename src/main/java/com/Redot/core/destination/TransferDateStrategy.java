package com.Redot.core.destination;

import java.nio.file.Path;
import java.time.YearMonth;
import java.time.ZonedDateTime;

public final class TransferDateStrategy implements DestinationStrategy {
    @Override
    public YearMonth resolveBucket(Path item) {
        ZonedDateTime now = ZonedDateTime.now();
        return YearMonth.of(now.getYear(), now.getMonth());
    }
}
