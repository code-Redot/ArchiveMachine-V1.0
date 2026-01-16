package com.Redot.core.util;

public enum ArchiveType {
    RAR(".rar"),
    SEVEN_Z(".7z"),
    ZIP(".zip");

    private final String ext;
    ArchiveType(String ext) { this.ext = ext; }
    public String ext() { return ext; }

    public static boolean isSupportedArchiveName(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".rar") || n.endsWith(".7z") || n.endsWith(".zip");
    }
}
