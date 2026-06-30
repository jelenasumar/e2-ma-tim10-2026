package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class LeagueChangeEvent {

    public enum Direction {
        PROMOTED,
        DEMOTED
    }

    private final LeagueTier previousTier;
    private final LeagueTier newTier;
    private final Direction direction;
    private final long totalStars;
    private final String message;

    public LeagueChangeEvent(
            @NonNull LeagueTier previousTier,
            @NonNull LeagueTier newTier,
            @NonNull Direction direction,
            long totalStars,
            @NonNull String message
    ) {
        this.previousTier = previousTier;
        this.newTier = newTier;
        this.direction = direction;
        this.totalStars = totalStars;
        this.message = message;
    }

    @NonNull
    public LeagueTier getPreviousTier() {
        return previousTier;
    }

    @NonNull
    public LeagueTier getNewTier() {
        return newTier;
    }

    @NonNull
    public Direction getDirection() {
        return direction;
    }

    public long getTotalStars() {
        return totalStars;
    }

    @NonNull
    public String getMessage() {
        return message;
    }
}
