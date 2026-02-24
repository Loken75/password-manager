package com.passwordmanager.util;

import java.text.ParseException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;

/**
 * Centralized date/time utilities using java.time (thread-safe, no allocation per call).
 */
public class DateUtils {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC);

    public static String getCurrentTimestamp() {
        return FORMATTER.format(Instant.now());
    }

    public static Date parseTimestamp(String timestamp) throws ParseException {
        try {
            Instant instant = Instant.from(FORMATTER.parse(timestamp));
            return Date.from(instant);
        } catch (DateTimeParseException e) {
            throw new ParseException(e.getMessage(), 0);
        }
    }
}
