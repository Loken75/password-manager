package com.passwordmanager.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

/**
 * Centralized date/time utilities.
 * Creates a new SimpleDateFormat per call to ensure thread safety.
 */
public class DateUtils {

    private static SimpleDateFormat createFormatter() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    }

    public static String getCurrentTimestamp() {
        return createFormatter().format(new Date());
    }

    public static Date parseTimestamp(String timestamp) throws ParseException {
        return createFormatter().parse(timestamp);
    }
}
