package com.example.slagalica.model;

import androidx.annotation.NonNull;

public enum NotificationCategory {
    ALL(""),
    CHAT("chat_notifications"),
    RANKING("ranking_notifications"),
    REWARD("reward_notifications"),
    OTHER("other_notifications");

    private final String channelId;

    NotificationCategory(@NonNull String channelId) {
        this.channelId = channelId;
    }

    @NonNull
    public String getChannelId() {
        return channelId;
    }
}
