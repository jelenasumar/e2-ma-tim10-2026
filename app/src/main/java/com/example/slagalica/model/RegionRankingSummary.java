package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class RegionRankingSummary {

    private final String regionKey;
    private final String regionName;
    private final int iconRes;
    private final long totalMonthlyStars;
    private final int rank;
    private final boolean currentUserRegion;

    public RegionRankingSummary(
            @NonNull String regionKey,
            @NonNull String regionName,
            int iconRes,
            long totalMonthlyStars,
            int rank,
            boolean currentUserRegion
    ) {
        this.regionKey = regionKey;
        this.regionName = regionName;
        this.iconRes = iconRes;
        this.totalMonthlyStars = totalMonthlyStars;
        this.rank = rank;
        this.currentUserRegion = currentUserRegion;
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

    public long getTotalMonthlyStars() {
        return totalMonthlyStars;
    }

    public int getRank() {
        return rank;
    }

    public boolean isCurrentUserRegion() {
        return currentUserRegion;
    }
}
