package com.example.slagalica.viewmodel.invites;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.model.InviteUser;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InviteFriendsViewModel extends ViewModel {

    private final GameInviteRepository repository = new GameInviteRepository();
    private final MutableLiveData<List<InviteUser>> users = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final Set<String> pendingInviteUserIds = new HashSet<>();

    @NonNull
    public LiveData<List<InviteUser>> getUsers() {
        return users;
    }

    @NonNull
    public LiveData<String> getMessage() {
        return message;
    }

    @NonNull
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public boolean isInvitePending(@NonNull String userId) {
        return pendingInviteUserIds.contains(userId);
    }

    public void loadUsers() {
        loading.setValue(true);
        repository.loadUsers(
                loadedUsers -> {
                    loading.setValue(false);
                    users.setValue(loadedUsers);
                },
                error -> {
                    loading.setValue(false);
                    message.setValue(error);
                }
        );
    }

    public void sendInvite(@NonNull InviteUser user) {
        loading.setValue(true);
        repository.sendInvite(
                user,
                () -> {
                    pendingInviteUserIds.add(user.getUid());
                    users.setValue(users.getValue());
                    loading.setValue(false);
                    message.setValue("Poziv je poslat.");
                },
                error -> {
                    loading.setValue(false);
                    message.setValue(error);
                }
        );
    }
}
