package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;

import java.util.HashMap;
import java.util.Map;

public final class TournamentPlayer {

    private final String uid;
    private final String username;
    private final String avatarUri;
    private final String leagueName;
    private final String leagueTierKey;

    public TournamentPlayer(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String avatarUri,
            @NonNull String leagueName,
            @NonNull String leagueTierKey
    ) {
        this.uid = uid;
        this.username = username;
        this.avatarUri = avatarUri;
        this.leagueName = leagueName;
        this.leagueTierKey = leagueTierKey;
    }

    @NonNull
    public static TournamentPlayer fromUserDocument(@NonNull DocumentSnapshot document) {
        return new TournamentPlayer(
                document.getId(),
                stringOrDefault(document.getString("username"), "Igrac"),
                stringOrEmpty(document.getString("avatarUri")),
                stringOrDefault(document.getString("leagueName"), "Pocetnicka liga"),
                stringOrDefault(document.getString("leagueTierKey"), "starter")
        );
    }

    @NonNull
    public static TournamentPlayer fromMap(@Nullable Object raw) {
        if (!(raw instanceof Map)) {
            return empty();
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;
        return new TournamentPlayer(
                stringOrEmpty(map.get("uid")),
                stringOrDefault(map.get("username"), "Igrac"),
                stringOrEmpty(map.get("avatarUri")),
                stringOrDefault(map.get("leagueName"), "Pocetnicka liga"),
                stringOrDefault(map.get("leagueTierKey"), "starter")
        );
    }

    @NonNull
    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();
        map.put("uid", uid);
        map.put("username", username);
        map.put("avatarUri", avatarUri);
        map.put("leagueName", leagueName);
        map.put("leagueTierKey", leagueTierKey);
        return map;
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
    public String getAvatarUri() {
        return avatarUri;
    }

    @NonNull
    public String getLeagueName() {
        return leagueName;
    }

    @NonNull
    public String getLeagueTierKey() {
        return leagueTierKey;
    }

    public boolean isEmpty() {
        return uid.isEmpty();
    }

    @NonNull
    public static TournamentPlayer empty() {
        return new TournamentPlayer("", "", "", "", "");
    }

    @NonNull
    private static String stringOrEmpty(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable Object value, @NonNull String fallback) {
        String text = stringOrEmpty(value);
        return text.isEmpty() ? fallback : text;
    }
}
