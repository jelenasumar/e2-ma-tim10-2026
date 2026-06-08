package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class KoZnaZnaMatch {

    public static final String STATUS_PLAYING = "PLAYING";
    public static final String STATUS_FINISHED = "FINISHED";

    private final String matchId;
    private final String hostUid;
    private final String guestUid;
    private final String hostUsername;
    private final String guestUsername;
    private final int hostScore;
    private final int guestScore;
    private final int currentQuestionIndex;
    private final String status;
    private final long roundEndsAtMs;
    private final long questionStartedAtMs;
    private final long questionEndsAtMs;
    private final boolean questionResolved;
    private final String statusMessage;
    private final int hostAnswerIndex;
    private final int guestAnswerIndex;
    private final long hostAnsweredAtMs;
    private final long guestAnsweredAtMs;
    private final List<Integer> questionOrder;
    private final String hostAvatarUri;
    private final String guestAvatarUri;

    public KoZnaZnaMatch(
            @NonNull String matchId,
            @NonNull String hostUid,
            @NonNull String guestUid,
            @NonNull String hostUsername,
            @NonNull String guestUsername,
            int hostScore,
            int guestScore,
            int currentQuestionIndex,
            @NonNull String status,
            long roundEndsAtMs,
            long questionStartedAtMs,
            long questionEndsAtMs,
            boolean questionResolved,
            @NonNull String statusMessage,
            int hostAnswerIndex,
            int guestAnswerIndex,
            long hostAnsweredAtMs,
            long guestAnsweredAtMs,
            @NonNull List<Integer> questionOrder,
            @NonNull String hostAvatarUri,
            @NonNull String guestAvatarUri
    ) {
        this.matchId = matchId;
        this.hostUid = hostUid;
        this.guestUid = guestUid;
        this.hostUsername = hostUsername;
        this.guestUsername = guestUsername;
        this.hostScore = hostScore;
        this.guestScore = guestScore;
        this.currentQuestionIndex = currentQuestionIndex;
        this.status = status;
        this.roundEndsAtMs = roundEndsAtMs;
        this.questionStartedAtMs = questionStartedAtMs;
        this.questionEndsAtMs = questionEndsAtMs;
        this.questionResolved = questionResolved;
        this.statusMessage = statusMessage;
        this.hostAnswerIndex = hostAnswerIndex;
        this.guestAnswerIndex = guestAnswerIndex;
        this.hostAnsweredAtMs = hostAnsweredAtMs;
        this.guestAnsweredAtMs = guestAnsweredAtMs;
        this.questionOrder = questionOrder;
        this.hostAvatarUri = hostAvatarUri;
        this.guestAvatarUri = guestAvatarUri;
    }

    @NonNull
    public static KoZnaZnaMatch fromMap(@NonNull String matchId, @NonNull Map<String, Object> map) {
        List<Integer> order = new ArrayList<>();
        Object rawOrder = map.get("questionOrder");
        if (rawOrder instanceof List) {
            for (Object item : (List<?>) rawOrder) {
                if (item instanceof Number) {
                    order.add(((Number) item).intValue());
                }
            }
        }
        if (order.isEmpty()) {
            order.add(0);
            order.add(1);
            order.add(2);
            order.add(3);
            order.add(4);
        }

        return new KoZnaZnaMatch(
                matchId,
                stringValue(map.get("hostUid")),
                stringValue(map.get("guestUid")),
                stringValue(map.get("hostUsername")),
                stringValue(map.get("guestUsername")),
                intValue(map.get("hostScore")),
                intValue(map.get("guestScore")),
                intValue(map.get("currentQuestionIndex")),
                stringValue(map.get("status")),
                longValue(map.get("roundEndsAtMs")),
                longValue(map.get("questionStartedAtMs")),
                longValue(map.get("questionEndsAtMs")),
                boolValue(map.get("questionResolved")),
                stringValue(map.get("statusMessage")),
                intValue(map.get("hostAnswerIndex"), KoZnaZnaScoring.ANSWER_PENDING),
                intValue(map.get("guestAnswerIndex"), KoZnaZnaScoring.ANSWER_PENDING),
                longValue(map.get("hostAnsweredAtMs")),
                longValue(map.get("guestAnsweredAtMs")),
                order,
                stringValue(map.get("hostAvatarUri")),
                stringValue(map.get("guestAvatarUri"))
        );
    }

    @NonNull
    private static String stringValue(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static int intValue(@Nullable Object value) {
        return intValue(value, 0);
    }

    private static int intValue(@Nullable Object value, int fallback) {
        return value instanceof Number ? ((Number) value).intValue() : fallback;
    }

    private static long longValue(@Nullable Object value) {
        return value instanceof Number ? ((Number) value).longValue() : 0L;
    }

    private static boolean boolValue(@Nullable Object value) {
        return value instanceof Boolean && (Boolean) value;
    }

    @NonNull
    public String getMatchId() {
        return matchId;
    }

    @NonNull
    public String getHostUid() {
        return hostUid;
    }

    @NonNull
    public String getGuestUid() {
        return guestUid;
    }

    @NonNull
    public String getHostUsername() {
        return hostUsername;
    }

    @NonNull
    public String getGuestUsername() {
        return guestUsername;
    }

    public int getHostScore() {
        return hostScore;
    }

    public int getGuestScore() {
        return guestScore;
    }

    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    public long getRoundEndsAtMs() {
        return roundEndsAtMs;
    }

    public long getQuestionStartedAtMs() {
        return questionStartedAtMs;
    }

    public long getQuestionEndsAtMs() {
        return questionEndsAtMs;
    }

    public boolean isQuestionResolved() {
        return questionResolved;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

    public int getHostAnswerIndex() {
        return hostAnswerIndex;
    }

    public int getGuestAnswerIndex() {
        return guestAnswerIndex;
    }

    public long getHostAnsweredAtMs() {
        return hostAnsweredAtMs;
    }

    public long getGuestAnsweredAtMs() {
        return guestAnsweredAtMs;
    }

    @NonNull
    public List<Integer> getQuestionOrder() {
        return questionOrder;
    }

    @NonNull
    public String getHostAvatarUri() {
        return hostAvatarUri;
    }

    @NonNull
    public String getGuestAvatarUri() {
        return guestAvatarUri;
    }
}
