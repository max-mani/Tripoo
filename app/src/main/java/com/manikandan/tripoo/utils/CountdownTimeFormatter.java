package com.manikandan.tripoo.utils;

/**
 * Formats countdown time values for display in the trip countdown UI.
 * - Days: normal number (no leading zero)
 * - Hours, minutes, seconds: always 2 digits
 */
public final class CountdownTimeFormatter {

    private CountdownTimeFormatter() {
    }

    /**
     * @param days value 0 or positive
     * @return string representation (no leading zero)
     */
    public static String formatDays(long days) {
        return String.valueOf(Math.max(0L, days));
    }

    /**
     * @param hours value 0–23
     * @return 2-digit string, e.g. "00", "09", "23"
     */
    public static String formatTwoDigits(long hours) {
        long v = Math.max(0L, Math.min(99L, hours));
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
