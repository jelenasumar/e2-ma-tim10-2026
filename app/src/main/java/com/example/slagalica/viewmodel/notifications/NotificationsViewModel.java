package com.example.slagalica.viewmodel.notifications;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.data.repository.NotificationsRepository;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.NotificationStatus;
import com.example.slagalica.model.SystemNotification;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsViewModel extends AndroidViewModel {

    private final NotificationsRepository repository;
    private final GameInviteRepository inviteRepository;
    private final MutableLiveData<List<SystemNotification>> visibleNotifications = new MutableLiveData<>();
    private final MutableLiveData<NotificationAction> selectedAction = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<String> roomNavigation = new MutableLiveData<>();

    private List<SystemNotification> allNotifications = new ArrayList<>();
    private NotificationCategory selectedCategory = NotificationCategory.ALL;
    private NotificationStatus selectedStatus = NotificationStatus.ALL;
    private ListenerRegistration notificationsListener;

    public NotificationsViewModel(@NonNull Application application) {
        super(application);
        repository = new NotificationsRepository(application);
        inviteRepository = new GameInviteRepository();
        listenNotifications();
    }

    @NonNull
    public LiveData<List<SystemNotification>> getVisibleNotifications() {
        return visibleNotifications;
    }

    @NonNull
    public LiveData<NotificationAction> getSelectedAction() {
        return selectedAction;
    }

    @NonNull
    public LiveData<String> getMessage() {
        return message;
    }

    @NonNull
    public LiveData<String> getRoomNavigation() {
        return roomNavigation;
    }

    public void setCategoryFilter(@NonNull NotificationCategory category) {
        selectedCategory = category;
        applyFilters();
    }

    public void setStatusFilter(@NonNull NotificationStatus status) {
        selectedStatus = status;
        applyFilters();
    }

    public void markAsRead(@NonNull String notificationId) {
        inviteRepository.markNotificationAsRead(
                notificationId,
                () -> { },
                error -> {
                    repository.markAsRead(notificationId);
                    refreshNotifications();
                }
        );
    }

    public void reactToNotification(@NonNull SystemNotification notification) {
        if (notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            inviteRepository.acceptInvite(
                    notification,
                    roomId -> {
                        message.setValue("Poziv je prihvacen.");
                        roomNavigation.setValue(roomId);
                    },
                    error -> message.setValue(error)
            );
        } else if (notification.getAction() == NotificationAction.OPEN_ROOM
                && notification.getRoomId() != null
                && !notification.getRoomId().isEmpty()) {
            roomNavigation.setValue(notification.getRoomId());
        } else if (!notification.isRead()) {
            markAsRead(notification.getId());
        }
        selectedAction.setValue(notification.getAction());
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
    }

    private void listenNotifications() {
        notificationsListener = inviteRepository.listenNotifications(
                notifications -> {
                    allNotifications = notifications;
                    applyFilters();
                },
                error -> {
                    message.setValue(error);
                    refreshNotifications();
                }
        );

        if (notificationsListener == null) {
            refreshNotifications();
        }
    }

    private void refreshNotifications() {
        allNotifications = repository.loadNotifications();
        applyFilters();
    }

    private void applyFilters() {
        List<SystemNotification> filtered = new ArrayList<>();
        for (SystemNotification notification : allNotifications) {
            if (matchesCategory(notification) && matchesStatus(notification)) {
                filtered.add(notification);
            }
        }
        visibleNotifications.setValue(filtered);
    }

    private boolean matchesCategory(@NonNull SystemNotification notification) {
        return selectedCategory == NotificationCategory.ALL
                || notification.getCategory() == selectedCategory;
    }

    private boolean matchesStatus(@NonNull SystemNotification notification) {
        switch (selectedStatus) {
            case READ:
                return notification.isRead();
            case UNREAD:
                return !notification.isRead();
            case ALL:
            default:
                return true;
        }
    }
}
