package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public final class TournamentState {

    public static final String STATUS_WAITING = "WAITING";
    public static final String STATUS_SEMIS_READY = "SEMIS_READY";
    public static final String STATUS_FINAL_READY = "FINAL_READY";
    public static final String STATUS_FINISHED = "FINISHED";

    private final String tournamentId;
    private final String status;
    private final List<TournamentPlayer> players;
    private final String semiOneRoomId;
    private final String semiTwoRoomId;
    private final String finalRoomId;
    private final String semiOneWinnerUid;
    private final String semiTwoWinnerUid;
    private final String championUid;

    public TournamentState(
            @NonNull String tournamentId,
            @NonNull String status,
            @NonNull List<TournamentPlayer> players,
            @NonNull String semiOneRoomId,
            @NonNull String semiTwoRoomId,
            @NonNull String finalRoomId,
            @NonNull String semiOneWinnerUid,
            @NonNull String semiTwoWinnerUid,
            @NonNull String championUid
    ) {
        this.tournamentId = tournamentId;
        this.status = status;
        this.players = new ArrayList<>(players);
        this.semiOneRoomId = semiOneRoomId;
        this.semiTwoRoomId = semiTwoRoomId;
        this.finalRoomId = finalRoomId;
        this.semiOneWinnerUid = semiOneWinnerUid;
        this.semiTwoWinnerUid = semiTwoWinnerUid;
        this.championUid = championUid;
    }

    @NonNull
    public static TournamentState fromDocument(@NonNull DocumentSnapshot document) {
        List<TournamentPlayer> parsedPlayers = new ArrayList<>();
        Object rawPlayers = document.get("players");
        if (rawPlayers instanceof List) {
            for (Object rawPlayer : (List<?>) rawPlayers) {
                TournamentPlayer player = TournamentPlayer.fromMap(rawPlayer);
                if (!player.isEmpty()) {
                    parsedPlayers.add(player);
                }
            }
        }
        return new TournamentState(
                document.getId(),
                stringOrDefault(document.getString("status"), STATUS_WAITING),
                parsedPlayers,
                stringOrEmpty(document.getString("semiOneRoomId")),
                stringOrEmpty(document.getString("semiTwoRoomId")),
                stringOrEmpty(document.getString("finalRoomId")),
                stringOrEmpty(document.getString("semiOneWinnerUid")),
                stringOrEmpty(document.getString("semiTwoWinnerUid")),
                stringOrEmpty(document.getString("championUid"))
        );
    }

    @NonNull
    public String getTournamentId() {
        return tournamentId;
    }

    @NonNull
    public String getStatus() {
        return status;
    }

    @NonNull
    public List<TournamentPlayer> getPlayers() {
        return new ArrayList<>(players);
    }

    @NonNull
    public String getSemiOneRoomId() {
        return semiOneRoomId;
    }

    @NonNull
    public String getSemiTwoRoomId() {
        return semiTwoRoomId;
    }

    @NonNull
    public String getFinalRoomId() {
        return finalRoomId;
    }

    @NonNull
    public String getSemiOneWinnerUid() {
        return semiOneWinnerUid;
    }

    @NonNull
    public String getSemiTwoWinnerUid() {
        return semiTwoWinnerUid;
    }

    @NonNull
    public String getChampionUid() {
        return championUid;
    }

    @NonNull
    public String roomForPlayer(@NonNull String uid) {
        if (players.size() >= 2
                && (players.get(0).getUid().equals(uid) || players.get(1).getUid().equals(uid))) {
            return semiOneRoomId;
        }
        if (players.size() >= 4
                && (players.get(2).getUid().equals(uid) || players.get(3).getUid().equals(uid))) {
            return semiTwoRoomId;
        }
        return "";
    }

    @Nullable
    public TournamentPlayer playerByUid(@NonNull String uid) {
        for (TournamentPlayer player : players) {
            if (player.getUid().equals(uid)) {
                return player;
            }
        }
        return null;
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
