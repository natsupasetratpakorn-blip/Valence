package dev.m4sh3r.valence.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class Format {

    private static final DecimalFormat MONEY = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Locale.US));
    private static String currency = "$";
    private static DateTimeFormatter date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);

    private Format() {
    }

    public static void setup(String currencySymbol, String datePattern) {
        currency = currencySymbol;
        try {
            date = DateTimeFormatter.ofPattern(datePattern, Locale.US);
        } catch (IllegalArgumentException ignored) {
            date = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US);
        }
    }

    public static String money(double amount) {
        synchronized (MONEY) {
            return currency + MONEY.format(amount);
        }
    }

    public static String date(long millis) {
        return date.format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()));
    }

    public static String ago(long millis) {
        long seconds = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
        if (seconds < 60) {
            return "just now";
        }
        if (seconds < 3600) {
            return (seconds / 60) + "m ago";
        }
        if (seconds < 86400) {
            return (seconds / 3600) + "h ago";
        }
        if (seconds < 7 * 86400) {
            return (seconds / 86400) + "d ago";
        }
        return date(millis);
    }

    public static String duration(long seconds) {
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    public static Double parseAmount(String input) {
        if (input == null) {
            return null;
        }
        String clean = input.trim().replace(",", "").replace(currency, "").toLowerCase(Locale.ROOT);
        double multiplier = 1;
        if (clean.endsWith("k")) {
            multiplier = 1_000;
        } else if (clean.endsWith("m")) {
            multiplier = 1_000_000;
        } else if (clean.endsWith("b")) {
            multiplier = 1_000_000_000;
        }
        if (multiplier != 1) {
            clean = clean.substring(0, clean.length() - 1);
        }
        try {
            double value = Double.parseDouble(clean) * multiplier;
            if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0) {
                return null;
            }
            return Math.floor(value * 100) / 100;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
