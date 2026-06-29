package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Privremeni podaci za testiranje okvira avatara iz prethodnog ciklusa.
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
    public static List<String> previousTopRegions(@NonNull List<String> fromRemote) {
        if (USE_HARDCODED_PREVIOUS_TOP_REGIONS) {
            return HARDCODED_PREVIOUS_TOP_REGIONS;
        }
        return fromRemote;
    }
}
