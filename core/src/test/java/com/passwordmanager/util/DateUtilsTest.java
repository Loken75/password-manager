package com.passwordmanager.util;

import org.junit.jupiter.api.Test;

import java.text.ParseException;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    void getCurrentTimestampIsIso8601() {
        String ts = DateUtils.getCurrentTimestamp();
        assertNotNull(ts);
        // Format: yyyy-MM-ddTHH:mm:ssZ
        assertTrue(ts.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z"),
                "Timestamp should be ISO 8601 format, got: " + ts);
    }

    @Test
    void parseTimestampRoundTrip() throws ParseException {
        String ts = DateUtils.getCurrentTimestamp();
        Date parsed = DateUtils.parseTimestamp(ts);
        assertNotNull(parsed);
        // The round-trip should be within a few seconds (generous for slow CI)
        long diff = Math.abs(System.currentTimeMillis() - parsed.getTime());
        assertTrue(diff < 5000, "Parsed timestamp should be close to current time, diff=" + diff + "ms");
    }

    @Test
    void parseTimestampValid() throws ParseException {
        Date parsed = DateUtils.parseTimestamp("2024-06-15T10:30:00Z");
        assertNotNull(parsed);
    }

    @Test
    void parseTimestampInvalidThrowsParseException() {
        assertThrows(ParseException.class, () -> DateUtils.parseTimestamp("not-a-date"));
    }

    @Test
    void parseTimestampNullThrows() {
        assertThrows(Exception.class, () -> DateUtils.parseTimestamp(null));
    }
}
