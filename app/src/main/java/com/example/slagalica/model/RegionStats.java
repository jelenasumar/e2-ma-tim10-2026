package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class RegionStats {

    private final String regionKey;
    private final String regionName;
    private final int iconRes;
    private final int podiumFirst;
    private final int podiumSecond;
    private final int podiumThird;
    private final int activePlayers;
    private final int totalRegistered;

    public RegionStats(
            @NonNull String regionKey,
            @NonNull String regionName,
            int iconRes,
            int podiumFirst,
            int podiumSecond,
            int podiumThird,
            int activePlayers,
            int totalRegistered
    ) {
        this.regionKey = regionKey;
        this.regionName = regionName;
        this.iconRes = iconRes;
        this.podiumFirst = podiumFirst;
        this.podiumSecond = podiumSecond;
        this.podiumThird = podiumThird;
        this.activePlayers = activePlayers;
        this.totalRegistered = totalRegistered;
    }

    @NonNull
    public String getRegionKey() {
        return regionKey;
    }

    @NonNull
    public String getRegionName() {
        return regionName;
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getPodiumFirst() {
        return podiumFirst;
    }

    public int getPodiumSecond() {
        return podiumSecond;
    }

    public int getPodiumThird() {
        return podiumThird;
    }

    public int getActivePlayers() {
        return activePlayers;
    }

    public int getTotalRegistered() {
        return totalRegistered;
    }
}
