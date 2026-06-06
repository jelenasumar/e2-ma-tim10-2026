package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class SystemNotification {

    private final String id;
    private final NotificationCategory category;
    private final String categoryLabel;
    private final String title;
    private final String message;
    private final String dateLabel;
    private final boolean read;
    private final NotificationAction action;
    private final String actionLabel;
    private final String inviteId;
    private final String roomId;

    public SystemNotification(
            @NonNull String id,
            @NonNull NotificationCategory category,
            @NonNull String categoryLabel,
            @NonNull String title,
            @NonNull String message,
            @NonNull String dateLabel,
            boolean read,
            @NonNull NotificationAction action,
            @Nullable String actionLabel
    ) {
        this(id, category, categoryLabel, title, message, dateLabel, read, action, actionLabel, null, null);
    }

    public SystemNotification(
            @NonNull String id,
            @NonNull NotificationCategory category,
            @NonNull String categoryLabel,
            @NonNull String title,
            @NonNull String message,
            @NonNull String dateLabel,
            boolean read,
            @NonNull NotificationAction action,
            @Nullable String actionLabel,
            @Nullable String inviteId,
            @Nullable String roomId
    ) {
        this.id = id;
        this.category = category;
        this.categoryLabel = categoryLabel;
        this.title = title;
        this.message = message;
        this.dateLabel = dateLabel;
        this.read = read;
        this.action = action;
        this.actionLabel = actionLabel;
        this.inviteId = inviteId;
        this.roomId = roomId;
    }

    @NonNull
    public String getId() {
        return id;
    }

    @NonNull
    public NotificationCategory getCategory() {
        return category;
    }

    @NonNull
    public String getCategoryLabel() {
        return categoryLabel;
    }

    @NonNull
    public String getTitle() {
        return title;
    }

    @NonNull
    public String getMessage() {
        return message;
    }

    @NonNull
    public String getDateLabel() {
        return dateLabel;
    }

    public boolean isRead() {
        return read;
    }

    @NonNull
    public NotificationAction getAction() {
        return action;
    }

    @Nullable
    public String getActionLabel() {
        return actionLabel;
    }

    @Nullable
    public String getInviteId() {
        return inviteId;
    }

    @Nullable
    public String getRoomId() {
        return roomId;
    }

    @NonNull
    public SystemNotification markRead() {
        return new SystemNotification(
                id,
                category,
                categoryLabel,
                title,
                message,
                dateLabel,
                true,
                action,
                actionLabel,
                inviteId,
                roomId
        );
    }
}
