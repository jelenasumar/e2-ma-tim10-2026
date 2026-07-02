package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

public final class Challenge {

    public static final String STATUS_OPEN = "OPEN";
    public static final String STATUS_FINISHED = "FINISHED";

    private final String challengeId;
    private final String regionKey;
    private final String creatorUid;
    private final String creatorUsername;
    private final int stakeStars;
    private final int stakeTokens;
    private final int participantCount;
    private final String status;
    private final String winnerUid;
    private final String runnerUpUid;

    public Challenge(
            @NonNull String challengeId,
            @NonNull String regionKey,
            @NonNull String creatorUid,
            @NonNull String creatorUsername,
            int stakeStars,
            int stakeTokens,
            int participantCount,
            @NonNull String status,
            @NonNull String winnerUid,
            @NonNull String runnerUpUid
    ) {
        this.challengeId = challengeId;
        this.regionKey = regionKey;
        this.creatorUid = creatorUid;
        this.creatorUsername = creatorUsername;
        this.stakeStars = stakeStars;
        this.stakeTokens = stakeTokens;
        this.participantCount = participantCount;
        this.status = status;
        this.winnerUid = winnerUid;
        this.runnerUpUid = runnerUpUid;
    }

    @NonNull
    public static Challenge fromDocument(@NonNull DocumentSnapshot document) {
        return new Challenge(
                document.getId(),
                stringOrEmpty(document.getString("regionKey")),
                stringOrEmpty(document.getString("creatorUid")),
                stringOrDefault(document.getString("creatorUsername"), "Igrac"),
                intOrZero(document.get("stakeStars")),
                intOrZero(document.get("stakeTokens")),
                intOrZero(document.get("participantCount")),
                stringOrDefault(document.getString("status"), STATUS_OPEN),
                stringOrEmpty(document.getString("winnerUid")),
                stringOrEmpty(document.getString("runnerUpUid"))
        );
    }

    @NonNull
    public String getChallengeId() {
        return challengeId;
    }

    @NonNull
    public String getRegionKey() {
        return regionKey;
    }

    @NonNull
    public String getCreatorUid() {
        return creatorUid;
    }

    @NonNull
    public String getCreatorUsername() {
        return creatorUsername;
    }

    public int getStakeStars() {
        return stakeStars;
    }

    public int getStakeTokens() {
        return stakeTokens;
    }

    public int getParticipantCount() {
        return participantCount;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @NonNull
    public String getWinnerUid() {
        return winnerUid;
    }

    @NonNull
    public String getRunnerUpUid() {
        return runnerUpUid;
    }

    public boolean isOpen() {
        return STATUS_OPEN.equals(status);
    }

    private static int intOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }
}