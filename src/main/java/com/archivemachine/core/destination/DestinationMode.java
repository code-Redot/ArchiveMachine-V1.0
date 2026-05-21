package com.archivemachine.core.destination;

public enum DestinationMode {
    TRANSFER_DATE,
    CREATION_DATE,
    LAST_MODIFIED_DATE,
    FIRST_LETTER;

    @Override
    public String toString() {
        return switch (this) {
            case TRANSFER_DATE -> "Transfer date";
            case CREATION_DATE -> "Creation date";
            case LAST_MODIFIED_DATE -> "Last modified date";
            case FIRST_LETTER -> "First letter";
        };
    }
}
