package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class RoomSession {

    private final String roomId;
    private final String hostUid;
    private final String guestUid;
    private final String hostUsername;
    private final String guestUsername;
    private final int hostTotalScore;
    private final int guestTotalScore;
    private final String currentGame;
    private final int currentGameIndex;
    private final List<String> gameOrder;
    private final String status;
    private final String matchType;
    private final String koZnaZnaMatchId;
    private final String tournamentId;
    private final String tournamentRound;
    private final String finishReason;
    private final String abandonedByUid;
    private final long breakEndsAtMillis;

    public RoomSession(
            @NonNull String roomId,
            @NonNull String hostUid,
            @NonNull String guestUid,
            @NonNull String hostUsername,
            @NonNull String guestUsername,
            int hostTotalScore,
            int guestTotalScore,
            @NonNull String currentGame,
            int currentGameIndex,
            @NonNull List<String> gameOrder,
            @NonNull String status,
            @NonNull String matchType,
            @NonNull String koZnaZnaMatchId,
            @NonNull String tournamentId,
            @NonNull String tournamentRound,
            @NonNull String finishReason,
            @NonNull String abandonedByUid,
            long breakEndsAtMillis
    ) {
        this.roomId = roomId;
        this.hostUid = hostUid;
        this.guestUid = guestUid;
        this.hostUsername = hostUsername;
        this.guestUsername = guestUsername;
        this.hostTotalScore = hostTotalScore;
        this.guestTotalScore = guestTotalScore;
        this.currentGame = currentGame;
        this.currentGameIndex = currentGameIndex;
        this.gameOrder = new ArrayList<>(gameOrder);
        this.status = status;
        this.matchType = matchType;
        this.koZnaZnaMatchId = koZnaZnaMatchId;
        this.tournamentId = tournamentId;
        this.tournamentRound = tournamentRound;
        this.breakEndsAtMillis = breakEndsAtMillis;
        this.finishReason = finishReason;
        this.abandonedByUid = abandonedByUid;
    }

    @NonNull
    public static RoomSession fromDocument(@NonNull DocumentSnapshot document) {
        return new RoomSession(
                document.getId(),
                stringOrEmpty(document.getString("hostUid")),
                stringOrEmpty(document.getString("guestUid")),
                stringOrDefault(document.getString("hostUsername"), "Igrac 1"),
                stringOrDefault(document.getString("guestUsername"), "Igrac 2"),
                intOrZero(document.get("hostTotalScore")),
                intOrZero(document.get("guestTotalScore")),
                stringOrDefault(document.getString("currentGame"), "KO_ZNA_ZNA"),
                intOrZero(document.get("currentGameIndex")),
                stringList(document.get("gameOrder")),
                stringOrDefault(document.getString("status"), "READY"),
                stringOrDefault(document.getString("matchType"), "FRIENDLY"),
                stringOrEmpty(document.getString("koZnaZnaMatchId")),
                stringOrEmpty(document.getString("tournamentId")),
                stringOrEmpty(document.getString("tournamentRound")),
                stringOrEmpty(document.getString("finishReason")),
                stringOrEmpty(document.getString("abandonedByUid")),
                longOrZero(document.get("breakEndsAtMillis"))
        );
    }

    @NonNull
    public String getRoomId() {
        return roomId;
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

    public int getHostTotalScore() {
        return hostTotalScore;
    }

    public int getGuestTotalScore() {
        return guestTotalScore;
    }

    @NonNull
    public String getCurrentGame() {
        return currentGame;
    }

    public int getCurrentGameIndex() {
        return currentGameIndex;
    }

    @NonNull
    public List<String> getGameOrder() {
        return new ArrayList<>(gameOrder);
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @NonNull
    public String getMatchType() {
        return matchType;
    }

    @NonNull
    public String getKoZnaZnaMatchId() {
        return koZnaZnaMatchId;
    }

    @NonNull
    public String getTournamentId() {
        return tournamentId;
    }

    @NonNull
    public String getTournamentRound() {
        return tournamentRound;
    }

    public long getBreakEndsAtMillis() {
        return breakEndsAtMillis;
    }

    public boolean hasBothPlayers() {
        return !hostUid.isEmpty() && !guestUid.isEmpty();
    }

    @NonNull
    public String getFinishReason() {
        return finishReason;
    }

    @NonNull
    public String getAbandonedByUid() {
        return abandonedByUid;
    }

    public boolean wasAbandoned() {
        return !abandonedByUid.isEmpty();
    }

    @NonNull
    public String currentGameLabel() {
        return gameLabel(currentGame);
    }

    @NonNull
    public String gameOrderLabel() {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < gameOrder.size(); i++) {
            if (i > 0) {
                builder.append(" -> ");
            }
            builder.append(gameLabel(gameOrder.get(i)));
        }
        return builder.toString();
    }

    @NonNull
    private static String gameLabel(@NonNull String game) {
        switch (game) {
            case "KO_ZNA_ZNA":
                return "Ko zna zna";
            case "SPOJNICE":
                return "Spojnice";
            case "ASOCIJACIJE":
                return "Asocijacije";
            case "SKOCKO":
                return "Skocko";
            case "KORAK_PO_KORAK":
                return "Korak po korak";
            case "MOJ_BROJ":
                return "Moj broj";
            default:
                return game;
        }
    }

    @NonNull
    private static List<String> stringList(@Nullable Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            }
        }
        if (values.isEmpty()) {
            values.addAll(RoomGameKeys.DEFAULT_GAME_ORDER);
        }
        return values;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
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
}
