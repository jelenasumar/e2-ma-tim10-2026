package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class RegionLeaderboardEntry {

    private final String uid;
    private final String username;
    private final String regionKey;
    private final String regionName;
    private final long monthlyStars;
    private final String regionRankFrame;
    private final boolean currentUser;

    public RegionLeaderboardEntry(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String regionKey,
            @NonNull String regionName,
            long monthlyStars,
            @NonNull String regionRankFrame,
            boolean currentUser
    ) {
        this.uid = uid;
        this.username = username;
        this.regionKey = regionKey;
        this.regionName = regionName;
        this.monthlyStars = monthlyStars;
        this.regionRankFrame = regionRankFrame;
        this.currentUser = currentUser;
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getRegionKey() {
        return regionKey;
    }

    @NonNull
    public String getRegionName() {
        return regionName;
    }

    public long getMonthlyStars() {
        return monthlyStars;
    }

    @NonNull
    public String getRegionRankFrame() {
        return regionRankFrame;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }
}
