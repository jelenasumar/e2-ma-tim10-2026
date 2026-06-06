package com.example.slagalica.viewmodel.notifications;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.NotificationsRepository;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.NotificationStatus;
import com.example.slagalica.model.SystemNotification;

import java.util.ArrayList;
import java.util.List;

public class NotificationsViewModel extends AndroidViewModel {

    private final NotificationsRepository repository;
    private final MutableLiveData<List<SystemNotification>> visibleNotifications = new MutableLiveData<>();
    private final MutableLiveData<NotificationAction> selectedAction = new MutableLiveData<>();

    private List<SystemNotification> allNotifications = new ArrayList<>();
    private NotificationCategory selectedCategory = NotificationCategory.ALL;
    private NotificationStatus selectedStatus = NotificationStatus.ALL;

    public NotificationsViewModel(@NonNull Application application) {
        super(application);
        repository = new NotificationsRepository(application);
        refreshNotifications();
    }

    @NonNull
    public LiveData<List<SystemNotification>> getVisibleNotifications() {
        return visibleNotifications;
    }

    @NonNull
    public LiveData<NotificationAction> getSelectedAction() {
        return selectedAction;
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
        repository.markAsRead(notificationId);
        refreshNotifications();
    }

    public void reactToNotification(@NonNull SystemNotification notification) {
        if (!notification.isRead()) {
            repository.markAsRead(notification.getId());
            refreshNotifications();
        }
        selectedAction.setValue(notification.getAction());
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
