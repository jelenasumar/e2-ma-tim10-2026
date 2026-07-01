package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

public final class RegionChatMessage {

    private final String id;
    private final String regionKey;
    private final String senderUid;
    private final String senderUsername;
    private final String text;
    private final long createdAtMillis;

    public RegionChatMessage(
            @NonNull String id,
            @NonNull String regionKey,
            @NonNull String senderUid,
            @NonNull String senderUsername,
            @NonNull String text,
            long createdAtMillis
    ) {
        this.id = id;
        this.regionKey = regionKey;
        this.senderUid = senderUid;
        this.senderUsername = senderUsername;
        this.text = text;
        this.createdAtMillis = createdAtMillis;
    }

    @NonNull
    public static RegionChatMessage fromDocument(@NonNull DocumentSnapshot document) {
        return new RegionChatMessage(
                document.getId(),
                stringOrEmpty(document.getString("regionKey")),
                stringOrEmpty(document.getString("senderUid")),
                stringOrDefault(document.getString("senderUsername"), "Igrac"),
                stringOrEmpty(document.getString("text")),
                timestampMillis(document.get("createdAt"))
        );
    }

    @NonNull public String getId() { return id; }
    @NonNull public String getRegionKey() { return regionKey; }
    @NonNull public String getSenderUid() { return senderUid; }
    @NonNull public String getSenderUsername() { return senderUsername; }
    @NonNull public String getText() { return text; }
    public long getCreatedAtMillis() { return createdAtMillis; }

    public boolean isMine(@NonNull String uid) {
        return senderUid.equals(uid);
    }

    private static long timestampMillis(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
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
}