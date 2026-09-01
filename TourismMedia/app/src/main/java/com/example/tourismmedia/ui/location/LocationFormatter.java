package com.example.tourismmedia.ui.location;

import android.content.Context;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.model.AppModels.Location;

import java.util.Locale;

/** Formatting shared by Home, Explore and Location detail so every screen reads the same. */
public final class LocationFormatter {

    /** Prices come from the API in Vietnamese Dong; the app formats them English-style. */
    private static final String CURRENCY = "\u20AB";

    private LocationFormatter() {
    }

    public static String price(Context context, double value) {
        return value <= 0
                ? context.getString(R.string.location_free)
                : String.format(Locale.US, "%,.0f %s", value, CURRENCY);
    }

    public static String rating(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    /** "Attraction \u00B7 Ho Chi Minh City", collapsing the separator when one half is missing. */
    public static String meta(Location location) {
        String category = blankToNull(location.category) != null ? location.category : location.type;
        String city = location.city;
        if (blankToNull(category) == null) {
            return blankToNull(city) == null ? "" : city;
        }
        return blankToNull(city) == null ? category : category + " · " + city;
    }

    public static String reviewCount(Context context, int count) {
        return context.getString(R.string.location_review_count, count);
    }

    public static String stars(int rating) {
        int clamped = Math.max(0, Math.min(5, rating));
        return "★★★★★".substring(0, clamped) + "☆☆☆☆☆".substring(0, 5 - clamped);
    }

    /** Server timestamps are ISO-8601 UTC; the list only needs the calendar day. */
    public static String date(String isoTimestamp) {
        if (blankToNull(isoTimestamp) == null) {
            return "";
        }
        String datePart = isoTimestamp.length() >= 10 ? isoTimestamp.substring(0, 10) : isoTimestamp;
        String[] parts = datePart.split("-");
        return parts.length == 3 ? parts[2] + "/" + parts[1] + "/" + parts[0] : datePart;
    }

    public static String orUnknown(Context context, String value) {
        return blankToNull(value) == null ? context.getString(R.string.location_unknown) : value;
    }

    public static String initials(String name) {
        String trimmed = blankToNull(name) == null ? "TM" : name.trim();
        return trimmed.substring(0, 1).toUpperCase(Locale.US);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
