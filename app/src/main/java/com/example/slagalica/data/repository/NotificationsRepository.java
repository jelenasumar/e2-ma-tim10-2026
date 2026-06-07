package com.example.slagalica.data.repository;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.slagalica.R;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.SystemNotification;

import java.util.ArrayList;
import java.util.List;

public final class NotificationsRepository {

    private static final String PREFS = "slagalica_notifications";
    private static final String READ_PREFIX = "notification_read_";
    private static final String HANDLED_PREFIX = "notification_handled_";
    private static final String RESULT_PREFIX = "notification_result_";

    private final Context appContext;
    private final SharedPreferences prefs;

    public NotificationsRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        createNotificationChannels();
    }

    @NonNull
    public List<SystemNotification> loadNotifications() {
        return new ArrayList<>();
    }

    public void markAsRead(@NonNull String notificationId) {
        prefs.edit().putBoolean(READ_PREFIX + notificationId, true).apply();
    }

    public void markAsUnread(@NonNull String notificationId) {
        prefs.edit().putBoolean(READ_PREFIX + notificationId, false).apply();
    }

    public void markActionHandled(
            @NonNull String notificationId,
            @NonNull String actionResult
    ) {
        prefs.edit()
                .putBoolean(READ_PREFIX + notificationId, true)
                .putBoolean(HANDLED_PREFIX + notificationId, true)
                .putString(RESULT_PREFIX + notificationId, actionResult)
                .apply();
    }

    private SystemNotification createNotification(
            @NonNull String id,
            @NonNull NotificationCategory category,
            @NonNull String categoryLabel,
            @NonNull String title,
            @NonNull String message,
            @NonNull String dateLabel,
            boolean readByDefault,
            @NonNull NotificationAction action,
            String actionLabel
    ) {
        boolean read = prefs.getBoolean(READ_PREFIX + id, readByDefault);
        boolean handled = prefs.getBoolean(HANDLED_PREFIX + id, false);
        String result = prefs.getString(RESULT_PREFIX + id, "");
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
                null,
                null,
                handled,
                result
        );
    }

    private void createNotificationChannels() {
        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        manager.createNotificationChannel(new NotificationChannel(
                NotificationCategory.CHAT.getChannelId(),
                appContext.getString(R.string.notification_type_chat),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                NotificationCategory.RANKING.getChannelId(),
                appContext.getString(R.string.notification_type_ranking),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                NotificationCategory.REWARD.getChannelId(),
                appContext.getString(R.string.notification_type_reward),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
        manager.createNotificationChannel(new NotificationChannel(
                NotificationCategory.OTHER.getChannelId(),
                appContext.getString(R.string.notification_type_other),
                NotificationManager.IMPORTANCE_DEFAULT
        ));
    }
}
