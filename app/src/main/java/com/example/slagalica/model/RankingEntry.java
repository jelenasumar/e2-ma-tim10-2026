package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class RankingEntry {

    private final String uid;
    private final String username;
    private final int rank;
    private final long stars;
    private final int matchesPlayed;

    public RankingEntry(
            @NonNull String uid,
            @NonNull String username,
            int rank,
            long stars,
            int matchesPlayed
    ) {
        this.uid = uid;
        this.username = username;
        this.rank = rank;
        this.stars = stars;
        this.matchesPlayed = matchesPlayed;
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public int getRank() {
        return rank;
    }

    public long getStars() {
        return stars;
    }

    public int getMatchesPlayed() {
        return matchesPlayed;
    }
}
