package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Privremeni podaci za testiranje prethodnog ciklusa.
 * Iskljuci {@link #USE_HARDCODED_PREVIOUS_TOP_REGIONS} kad pravi ciklus krene da radi.
 */
public final class RegionCycleTestConfig {

    public static final boolean USE_HARDCODED_PREVIOUS_TOP_REGIONS = true;

    /** 1. Beograd (zlatni), 2. Vojvodina (srebrni), 3. Šumadija (bronzani). */
    public static final List<String> HARDCODED_PREVIOUS_TOP_REGIONS = Collections.unmodifiableList(
            Arrays.asList("beograd", "vojvodina", "sumadija")
    );

    private RegionCycleTestConfig() {
    }

    @NonNull
    public static String previousCycleLabel() {
        return MonthlyCycleHelper.formatCycleLabel(MonthlyCycleHelper.previousCycleKey());
    }

    @NonNull
    public static List<String> previousTopRegions(@NonNull List<String> fromRemote) {
        if (USE_HARDCODED_PREVIOUS_TOP_REGIONS) {
            return HARDCODED_PREVIOUS_TOP_REGIONS;
        }
        return fromRemote != null ? fromRemote : Collections.emptyList();
    }

    /** 1, 2 ili 3 ako je region bio na tom mestu u prethodnom ciklusu; inace 0. */
    public static int previousCycleRank(@NonNull String regionKey) {
        if (!USE_HARDCODED_PREVIOUS_TOP_REGIONS || regionKey.isEmpty()) {
            return 0;
        }
        int index = HARDCODED_PREVIOUS_TOP_REGIONS.indexOf(regionKey.toLowerCase(Locale.ROOT));
        return index >= 0 ? index + 1 : 0;
    }

    public static int mergePodiumFirst(@NonNull String regionKey, int fromRemote) {
        return previousCycleRank(regionKey) == 1
                ? Math.max(fromRemote, 1)
                : fromRemote;
    }

    public static int mergePodiumSecond(@NonNull String regionKey, int fromRemote) {
        return previousCycleRank(regionKey) == 2
                ? Math.max(fromRemote, 1)
                : fromRemote;
    }

    public static int mergePodiumThird(@NonNull String regionKey, int fromRemote) {
        return previousCycleRank(regionKey) == 3
                ? Math.max(fromRemote, 1)
                : fromRemote;
    }
}
