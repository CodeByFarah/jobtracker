package com.jobtrack.mapper;

/** Normalises free-text input before it is stored. */
public final class TextUtils {

    private TextUtils() {
    }

    /** Trims the value and turns blank strings into null, so "" and "   " are stored as "not set". */
    public static String clean(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
