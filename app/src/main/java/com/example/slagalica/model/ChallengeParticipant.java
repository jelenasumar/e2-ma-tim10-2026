package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

public final class ChallengeParticipant {

    public static final String STATUS_JOINED = "JOINED";
    public static final String STATUS_FINISHED = "FINISHED";

    private final String uid;
    private final String username;
    private final int score;
    private final String status;
    private final long rewardStars;
    private final long rewardTokens;

    public ChallengeParticipant(
            @NonNull String uid,
            @NonNull String username,
            int score,
            @NonNull String status,
            long rewardStars,
            long rewardTokens
    ) {
        this.uid = uid;
        this.username = username;
        this.score = score;
        this.status = status;
        this.rewardStars = rewardStars;
        this.rewardTokens = rewardTokens;
    }

    @NonNull
    public static ChallengeParticipant fromDocument(@NonNull DocumentSnapshot document) {
        return new ChallengeParticipant(
                document.getId(),
                stringOrDefault(document.getString("username"), "Igrac"),
                intOrZero(document.get("score")),
                stringOrDefault(document.getString("status"), STATUS_JOINED),
                longOrZero(document.get("rewardStars")),
                longOrZero(document.get("rewardTokens"))
        );
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public int getScore() {
        return score;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public long getRewardStars() {
        return rewardStars;
    }

    public long getRewardTokens() {
        return rewardTokens;
    }

    public boolean isFinished() {
        return STATUS_FINISHED.equals(status);
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static int intOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}