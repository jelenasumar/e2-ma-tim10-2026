package com.example.slagalica.data.repository;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.example.slagalica.R;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.ui.notifications.NotificationActionReceiver;

import java.util.ArrayList;
import java.util.List;

public final class NotificationsRepository {

    public static final String ACTION_ACCEPT_INVITE = "com.example.slagalica.ACTION_ACCEPT_INVITE";
    public static final String ACTION_DECLINE_INVITE = "com.example.slagalica.ACTION_DECLINE_INVITE";
    public static final String ACTION_OPEN_ROOM = "com.example.slagalica.ACTION_OPEN_ROOM";
    public static final String EXTRA_NOTIFICATION_ID = "com.example.slagalica.EXTRA_NOTIFICATION_ID";

    private static final String PREFS = "slagalica_notifications";
    private static final String READ_PREFIX = "notification_read_";
    private static final String HANDLED_PREFIX = "notification_handled_";
    private static final String RESULT_PREFIX = "notification_result_";
    private static final String DELIVERED_PREFIX = "notification_delivered_";

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

    public void showSystemNotification(@NonNull SystemNotification notification) {
        if (notification.isRead() || wasDelivered(notification.getId()) || !canPostNotifications()) {
            return;
        }

        NotificationManager manager = appContext.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }

        Intent intent = new Intent(appContext, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        intent.putExtra(MainActivity.EXTRA_OPEN_NOTIFICATIONS, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                appContext,
                notification.getId().hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                appContext,
                notification.getCategory().getChannelId()
        )
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(notification.getTitle())
                .setContentText(notification.getMessage())
                .setStyle(new NotificationCompat.BigTextStyle().bigText(notification.getMessage()))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        if (notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            builder.addAction(
                    0,
                    appContext.getString(R.string.accept_invite),
                    actionPendingIntent(ACTION_ACCEPT_INVITE, notification)
            );
            builder.addAction(
                    0,
                    appContext.getString(R.string.decline_invite),
                    actionPendingIntent(ACTION_DECLINE_INVITE, notification)
            );
        } else if (notification.getAction() == NotificationAction.OPEN_ROOM
                && notification.getRoomId() != null
                && !notification.getRoomId().isEmpty()) {
            builder.addAction(
                    0,
                    notification.getActionLabel() != null
                            ? notification.getActionLabel()
                            : appContext.getString(R.string.room_session_title),
                    actionPendingIntent(ACTION_OPEN_ROOM, notification)
            );
        }

        markDelivered(notification.getId());
        manager.notify(notification.getId().hashCode(), builder.build());
    }

    private PendingIntent actionPendingIntent(
            @NonNull String action,
            @NonNull SystemNotification notification
    ) {
        Intent intent = new Intent(appContext, NotificationActionReceiver.class);
        intent.setAction(action);
        intent.putExtra(EXTRA_NOTIFICATION_ID, notification.getId());
        return PendingIntent.getBroadcast(
                appContext,
                31 * notification.getId().hashCode() + action.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }

    private boolean canPostNotifications() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || ContextCompat.checkSelfPermission(
                appContext,
                android.Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean wasDelivered(@NonNull String notificationId) {
        return prefs.getBoolean(DELIVERED_PREFIX + notificationId, false);
    }

    private void markDelivered(@NonNull String notificationId) {
        prefs.edit().putBoolean(DELIVERED_PREFIX + notificationId, true).apply();
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
