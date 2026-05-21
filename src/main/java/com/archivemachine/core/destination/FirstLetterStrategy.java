package com.archivemachine.core.destination;

import java.nio.file.Path;

/**
 * Groups items by the first character of their (case-insensitive) name.
 *   A..Z      -> "A".."Z"
 *   0..9      -> "0-9"
 *   any other -> "#"
 */
public final class FirstLetterStrategy implements BucketStrategy {

    @Override
    public String resolveBucket(Path item) {
        if (item == null || item.getFileName() == null) return "#";
        String name = item.getFileName().toString();
        if (name.isEmpty()) return "#";

        int cp = name.codePointAt(0);
        if (Character.isLetter(cp)) {
            return String.valueOf(Character.toUpperCase((char) cp));
        }
        if (Character.isDigit(cp)) {
            return "0-9";
        }
        return "#";
    }
}
