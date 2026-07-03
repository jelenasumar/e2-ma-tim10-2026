package com.example.slagalica.ui.notifications;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;

import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.data.repository.NotificationsRepository;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.ui.main.MainActivity;

public class NotificationActionReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }

        String notificationId = intent.getStringExtra(NotificationsRepository.EXTRA_NOTIFICATION_ID);
        String action = intent.getAction();
        if (notificationId == null || notificationId.isEmpty() || action == null) {
            return;
        }

        cancelSystemNotification(context, notificationId);

        PendingResult pendingResult = goAsync();
        GameInviteRepository repository = new GameInviteRepository();
        repository.loadNotification(
                notificationId,
                notification -> handleAction(context.getApplicationContext(), repository, notification, action, pendingResult),
                error -> pendingResult.finish()
        );
    }

    private void handleAction(
            @NonNull Context context,
            @NonNull GameInviteRepository repository,
            @NonNull SystemNotification notification,
            @NonNull String action,
            @NonNull PendingResult pendingResult
    ) {
        if (NotificationsRepository.ACTION_ACCEPT_INVITE.equals(action)) {
            repository.acceptInvite(
                    notification,
                    roomId -> {
                        openRoom(context, roomId);
                        pendingResult.finish();
                    },
                    error -> pendingResult.finish()
            );
        } else if (NotificationsRepository.ACTION_DECLINE_INVITE.equals(action)) {
            repository.declineInvite(
                    notification,
                    pendingResult::finish,
                    error -> pendingResult.finish()
            );
        } else if (NotificationsRepository.ACTION_OPEN_ROOM.equals(action)) {
            handleOpenRoom(context, repository, notification, pendingResult);
        } else {
            pendingResult.finish();
        }
    }

    private void handleOpenRoom(
            @NonNull Context context,
            @NonNull GameInviteRepository repository,
            @NonNull SystemNotification notification,
            @NonNull PendingResult pendingResult
    ) {
        String roomId = notification.getRoomId();
        if (roomId == null || roomId.isEmpty()) {
            pendingResult.finish();
            return;
        }

        repository.markNotificationActionHandled(
                notification.getId(),
                "Otvorili ste sobu",
                () -> {
                    openRoom(context, roomId);
                    pendingResult.finish();
                },
                error -> {
                    openRoom(context, roomId);
                    pendingResult.finish();
                }
        );
    }

    private void openRoom(@NonNull Context context, @NonNull String roomId) {
        if (roomId.isEmpty()) {
            return;
        }

        Intent activityIntent = new Intent(context, MainActivity.class);
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        activityIntent.putExtra(MainActivity.EXTRA_OPEN_ROOM_ID, roomId);
        context.startActivity(activityIntent);
    }

    private void cancelSystemNotification(@NonNull Context context, @NonNull String notificationId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.cancel(notificationId.hashCode());
        }
    }
}
