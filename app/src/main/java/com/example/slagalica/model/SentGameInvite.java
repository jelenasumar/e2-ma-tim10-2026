package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class SentGameInvite {

    private final String inviteId;
    private final String toUid;
    private final String toUsername;
    private final String notificationId;

    public SentGameInvite(
            @NonNull String inviteId,
            @NonNull String toUid,
            @NonNull String toUsername,
            @NonNull String notificationId
    ) {
        this.inviteId = inviteId;
        this.toUid = toUid;
        this.toUsername = toUsername;
        this.notificationId = notificationId;
    }

    @NonNull
    public String getInviteId() {
        return inviteId;
    }

    @NonNull
    public String getToUid() {
        return toUid;
    }

    @NonNull
    public String getToUsername() {
        return toUsername;
    }

    @NonNull
    public String getNotificationId() {
        return notificationId;
    }
}
