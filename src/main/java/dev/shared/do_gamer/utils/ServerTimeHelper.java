package dev.shared.do_gamer.utils;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import eu.darkbot.api.managers.BackpageAPI;

public final class ServerTimeHelper {

    private static volatile long serverOffsetMinutes = -1; // Offset in minutes between local time and server time
    private static volatile long lastOffsetCheckTime = 0; // Timestamp for the last offset check
    private static final long OFFSET_CHECK_INTERVAL_MS = 5 * 60_000L; // 5 minutes

    private ServerTimeHelper() {
        // Private constructor to hide the implicit public one
    }

    // Check if the server offset has been updated
    public static boolean offsetUpdated() {
        return serverOffsetMinutes != -1;
    }

    /**
     * Fetch the server time offset in minutes.
     *
     * @param backpageAPI the BackpageAPI instance used to retrieve server data
     */
    public static synchronized void fetchServerOffset(BackpageAPI backpageAPI) {
        if (offsetUpdated()) {
            return; // Already updated
        }

        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastOffsetCheckTime) < OFFSET_CHECK_INTERVAL_MS) {
            return;
        }
        lastOffsetCheckTime = currentTime;

        try {
            String response = backpageAPI.getHttp("indexInternal.es?action=internalStart").getContent();
            String serverDate = extractServerDate(response);
            String serverTime = extractServerTime(response);

            if (!serverDate.isEmpty() && !serverTime.isEmpty()) {
                serverOffsetMinutes = calcOffsetMinutes(serverDate, serverTime);
                System.out.printf("Server date: '%s', time: '%s'%n", serverDate, serverTime);
            }
        } catch (Exception e) {
            serverOffsetMinutes = -1;
            System.out.printf("Error retrieving server time: %s%n", e.getMessage());
        }
    }

    // Get the current server-adjusted LocalDateTime; use local time if no offset is available.
    public static LocalDateTime currentDateTime() {
        LocalDateTime currentTime = LocalDateTime.now();
        long offsetMinutes = serverOffsetMinutes;
        return offsetMinutes == -1 ? currentTime : currentTime.plusMinutes(offsetMinutes);
    }

    // Calculate the offset in minutes between local time and server time
    private static long calcOffsetMinutes(String serverDate, String serverTime) {
        LocalTime time = parseServerTime(serverTime);

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        LocalDate date = LocalDate.parse(serverDate, dateFormatter);

        LocalDateTime serverDateTime = LocalDateTime.of(date, time);
        LocalDateTime localDateTime = LocalDateTime.now();

        long offset = Duration.between(localDateTime, serverDateTime).toMinutes();
        return roundToNearest(offset);
    }

    // Round offset to the nearest 60 or 30 minutes for timezone alignment
    private static long roundToNearest(long offset) {
        long c60 = Math.round((double) offset / 60) * 60;
        long c30 = Math.round((double) offset / 30) * 30;
        long d60 = Math.abs(offset - c60);
        long d30 = Math.abs(offset - c30);
        return d60 <= d30 ? c60 : c30;
    }

    // Parse server time string to LocalTime
    private static LocalTime parseServerTime(String serverTime) {
        String timeStr = serverTime.toUpperCase(Locale.ENGLISH);
        String pattern = timeStr.endsWith("AM") || timeStr.endsWith("PM") ? "hh:mm a" : "HH:mm";
        DateTimeFormatter parser = DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH);
        return LocalTime.parse(timeStr, parser);
    }

    // Extracts the value of a JavaScript variable from the response string
    private static String extractJavaScriptVar(String response, String key) {
        if (!response.isEmpty()) {
            Pattern pattern = Pattern.compile("var " + key + " = '([^']+)';", Pattern.CASE_INSENSITIVE);
            Matcher matcher = pattern.matcher(response);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return "";
    }

    private static String extractServerDate(String response) {
        return extractJavaScriptVar(response, "serverDate");
    }

    private static String extractServerTime(String response) {
        return extractJavaScriptVar(response, "serverTime");
    }

    /**
     * Formats a LocalDateTime to a string in "HH:mm" format.
     *
     * @param dateTime the LocalDateTime to format
     * @return the formatted time string
     */
    public static String timeFormat(LocalDateTime dateTime) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
        return dateTime.format(format);
    }

    /**
     * Calculates the duration in seconds until the next occurrence
     * of the specified hour and minute.
     *
     * @param hour   the target hour (0-23)
     * @param minute the target minute (0-59)
     * @return the duration in seconds
     */
    public static long durationUntilTime(int hour, int minute) {
        LocalDateTime now = currentDateTime();
        LocalDateTime target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0);
        if (target.isBefore(now)) {
            target = target.plusDays(1);
        }
        return Duration.between(now, target).getSeconds();
    }

    /**
     * Formats a duration in seconds to a human-readable string format
     * (e.g., "1h 30m 45s").
     *
     * @param seconds the duration in seconds to format
     * @return the formatted duration string
     */
    public static String remainingTimeFormat(long seconds) {
        long hours = seconds / 3600;
        long min = (seconds % 3600) / 60;
        long sec = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (hours > 0) {
            sb.append(String.format("%dh ", hours));
        }
        if (min > 0 || hours > 0) {
            sb.append(String.format("%dm ", min));
        }
        sb.append(String.format("%ds", sec));
        return sb.toString();
    }
}
