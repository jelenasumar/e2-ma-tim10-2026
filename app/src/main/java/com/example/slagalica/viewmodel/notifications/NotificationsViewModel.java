package com.example.slagalica.viewmodel.notifications;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.data.repository.NotificationsRepository;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.NotificationStatus;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.utils.SingleLiveEvent;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class NotificationsViewModel extends AndroidViewModel {

    private final NotificationsRepository repository;
    private final GameInviteRepository inviteRepository;
    private final MutableLiveData<List<SystemNotification>> visibleNotifications = new MutableLiveData<>();
    private final MutableLiveData<NotificationAction> selectedAction = new MutableLiveData<>();
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final SingleLiveEvent<String> roomNavigation = new SingleLiveEvent<>();
    private final SingleLiveEvent<String> notificationPageTitle = new SingleLiveEvent<>();

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

    @NonNull
    public LiveData<String> getNotificationPageTitle() {
        return notificationPageTitle;
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
                () -> updateLocalReadState(notificationId, true),
                error -> {
                    repository.markAsRead(notificationId);
                    updateLocalReadState(notificationId, true);
                }
        );
    }

    public void markAsUnread(@NonNull String notificationId) {
        inviteRepository.markNotificationAsUnread(
                notificationId,
                () -> updateLocalReadState(notificationId, false),
                error -> {
                    repository.markAsUnread(notificationId);
                    updateLocalReadState(notificationId, false);
                }
        );
    }

    public void reactToNotification(@NonNull SystemNotification notification) {
        if (notification.isActionHandled()) {
            message.setValue(notification.getActionResult());
            return;
        }
        if (notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            inviteRepository.acceptInvite(
                    notification,
                    roomId -> {
                        updateLocalActionHandled(notification.getId(), "Prihvatili ste poziv");
                        message.setValue("Poziv je prihvacen.");
                        roomNavigation.setValue(roomId);
                    },
                    error -> message.setValue(error)
            );
        } else if (notification.getAction() == NotificationAction.OPEN_ROOM
                && notification.getRoomId() != null
                && !notification.getRoomId().isEmpty()) {
            handleOpenRoomNotification(notification);
        } else if (notification.getAction() == NotificationAction.OPEN_CHAT) {
            notificationPageTitle.setValue("Čet");
        } else if (notification.getAction() == NotificationAction.OPEN_LEAGUE) {
            notificationPageTitle.setValue(getApplication().getString(R.string.notification_destination_ranking));
        } else if (notification.getAction() == NotificationAction.NONE) {
            notificationPageTitle.setValue(destinationTitle(notification));
        } else if (!notification.isRead()) {
            markAsRead(notification.getId());
        }
        selectedAction.setValue(notification.getAction());
    }

    public void declineInvite(@NonNull SystemNotification notification) {
        inviteRepository.declineInvite(
                notification,
                () -> {
                    updateLocalActionHandled(notification.getId(), "Odbili ste poziv");
                    message.setValue("Odbili ste poziv.");
                },
                error -> {
                    repository.markActionHandled(notification.getId(), "Odbili ste poziv");
                    updateLocalActionHandled(notification.getId(), "Odbili ste poziv");
                    message.setValue("Odbili ste poziv.");
                }
        );
    }

    public void openNotification(@NonNull SystemNotification notification) {
        if (notification.isActionHandled() && notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            message.setValue(notification.getActionResult());
            return;
        }
        if (!notification.isRead()) {
            markAsRead(notification.getId());
        }
        switch (notification.getAction()) {
            case OPEN_ROOM:
                if (notification.getRoomId() != null && !notification.getRoomId().isEmpty()) {
                    handleOpenRoomNotification(notification);
                } else {
                    message.setValue("Soba jos nije dostupna.");
                }
                break;
            case OPEN_CHAT:
                notificationPageTitle.setValue("Čet");
                break;
            case OPEN_LEAGUE:
                notificationPageTitle.setValue(getApplication().getString(R.string.notification_destination_ranking));
                break;
            case NONE:
            case ACCEPT_INVITE:
            default:
                notificationPageTitle.setValue(destinationTitle(notification));
                break;
        }
    }

    private void handleOpenRoomNotification(@NonNull SystemNotification notification) {
        if (notification.isRead() && !notification.isActionHandled()) {
            markRoomNotificationHandled(notification);
            return;
        }
        openRoomNotification(notification);
    }

    private void openRoomNotification(@NonNull SystemNotification notification) {
        String roomId = notification.getRoomId();
        if (roomId == null || roomId.isEmpty()) {
            message.setValue("Soba jos nije dostupna.");
            return;
        }
        inviteRepository.markNotificationActionHandled(
                notification.getId(),
                "Otvorili ste sobu",
                () -> {
                    updateLocalActionHandled(notification.getId(), "Otvorili ste sobu");
                    roomNavigation.setValue(roomId);
                },
                error -> {
                    repository.markActionHandled(notification.getId(), "Otvorili ste sobu");
                    updateLocalActionHandled(notification.getId(), "Otvorili ste sobu");
                    roomNavigation.setValue(roomId);
                }
        );
    }

    private void markRoomNotificationHandled(@NonNull SystemNotification notification) {
        inviteRepository.markNotificationActionHandled(
                notification.getId(),
                "Otvorili ste sobu",
                () -> {
                    updateLocalActionHandled(notification.getId(), "Otvorili ste sobu");
                    message.setValue("Otvorili ste sobu");
                },
                error -> {
                    repository.markActionHandled(notification.getId(), "Otvorili ste sobu");
                    updateLocalActionHandled(notification.getId(), "Otvorili ste sobu");
                    message.setValue("Otvorili ste sobu");
                }
        );
    }

    @NonNull
    private static String destinationTitle(@NonNull SystemNotification notification) {
        switch (notification.getCategory()) {
            case REWARD:
                return "Nagrada";
            case RANKING:
                return "Rang lista";
            case CHAT:
                return "Čet";
            case OTHER:
            case ALL:
            default:
                return "Detalji notifikacije";
        }
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
                error -> message.setValue(error)
        );

        if (notificationsListener == null) {
            refreshNotifications();
        }
    }

    private void refreshNotifications() {
        allNotifications = new ArrayList<>();
        applyFilters();
    }

    private void updateLocalReadState(@NonNull String notificationId, boolean read) {
        List<SystemNotification> updated = new ArrayList<>();
        for (SystemNotification notification : allNotifications) {
            if (notification.getId().equals(notificationId)) {
                updated.add(notification.withRead(read));
            } else {
                updated.add(notification);
            }
        }
        allNotifications = updated;
        applyFilters();
    }

    private void updateLocalActionHandled(
            @NonNull String notificationId,
            @NonNull String actionResult
    ) {
        List<SystemNotification> updated = new ArrayList<>();
        for (SystemNotification notification : allNotifications) {
            if (notification.getId().equals(notificationId)) {
                updated.add(notification.withActionHandled(actionResult));
            } else {
                updated.add(notification);
            }
        }
        allNotifications = updated;
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
