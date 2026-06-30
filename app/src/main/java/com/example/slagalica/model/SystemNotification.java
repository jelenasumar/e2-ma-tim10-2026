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
    private final boolean actionHandled;
    private final String actionResult;
    private final int rank;

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
        this(id, category, categoryLabel, title, message, dateLabel, read, action, actionLabel, null, null, false, "", 0);
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
        this(id, category, categoryLabel, title, message, dateLabel, read, action, actionLabel, inviteId, roomId, false, "", 0);
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
            @Nullable String roomId,
            boolean actionHandled,
            @Nullable String actionResult
    ) {
        this(id, category, categoryLabel, title, message, dateLabel, read, action, actionLabel, inviteId, roomId, actionHandled, actionResult, 0);
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
            @Nullable String roomId,
            boolean actionHandled,
            @Nullable String actionResult,
            int rank
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
        this.actionHandled = actionHandled;
        this.actionResult = actionResult != null ? actionResult : "";
        this.rank = rank;
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

    public boolean isActionHandled() {
        return actionHandled;
    }

    public int getRank() {
        return rank;
    }

    @NonNull
    public String getActionResult() {
        return actionResult;
    }

    public boolean hasPendingAction() {
        return action != NotificationAction.NONE && !actionHandled;
    }

    @NonNull
    public SystemNotification markRead() {
        return withRead(true);
    }

    @NonNull
    public SystemNotification withRead(boolean read) {
        return new SystemNotification(
                id,
                category,
                categoryLabel,
                title,
                message,
                dateLabel,
                read,
                action,
                actionLabel,
                inviteId,
                roomId,
                actionHandled,
                actionResult,
                rank
        );
    }

    @NonNull
    public SystemNotification withActionHandled(@NonNull String actionResult) {
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
                roomId,
                true,
                actionResult,
                rank
        );
    }
}
