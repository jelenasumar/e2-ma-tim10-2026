package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class MonthlyCycleHelper {

    private static final DateTimeFormatter CYCLE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM", Locale.ROOT);

    private MonthlyCycleHelper() {
    }

    @NonNull
    public static String currentCycleKey() {
        return LocalDate.now(ZoneId.systemDefault()).format(CYCLE_FORMAT);
    }

    @NonNull
    public static String previousCycleKey() {
        return LocalDate.now(ZoneId.systemDefault()).minusMonths(1).format(CYCLE_FORMAT);
    }

    @NonNull
    public static String formatCycleLabel(@NonNull String cycleKey) {
        String[] parts = cycleKey.split("-");
        if (parts.length != 2) {
            return cycleKey;
        }
        return parts[1] + "/" + parts[0];
    }
}
