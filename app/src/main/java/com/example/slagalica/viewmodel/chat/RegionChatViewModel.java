package com.example.slagalica.viewmodel.chat;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.RegionChatRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.RegionChatMessage;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class RegionChatViewModel extends AndroidViewModel {

    private final RegionChatRepository repository;
    private final UserProfileRepository userProfileRepository;
    private final MutableLiveData<List<RegionChatMessage>> messages = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<String> regionKey = new MutableLiveData<>("");
    private ListenerRegistration messagesListener;

    public RegionChatViewModel(@NonNull Application application) {
        super(application);
        repository = new RegionChatRepository();
        userProfileRepository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<List<RegionChatMessage>> getMessages() {
        return messages;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<String> getRegionKey() {
        return regionKey;
    }

    @NonNull
    public String getCurrentUid() {
        String uid = repository.getCurrentUid();
        return uid != null ? uid : "";
    }

    public void start() {
        repository.loadMyRegion(
                loadedRegionKey -> {
                    regionKey.setValue(loadedRegionKey);
                    listenMessages(loadedRegionKey);
                },
                errorMessage::setValue
        );
    }

    public void sendMessage(@NonNull String text) {
        String currentRegionKey = regionKey.getValue();
        if (currentRegionKey == null || currentRegionKey.isEmpty()) {
            errorMessage.setValue("Region nije izabran.");
            return;
        }

        repository.sendMessage(
                currentRegionKey,
                text,
                () -> userProfileRepository.recordDailyChatMessage(() -> { }, error -> { }),
                errorMessage::setValue
        );
    }

    private void listenMessages(@NonNull String loadedRegionKey) {
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }

        messagesListener = repository.listenMessages(
                loadedRegionKey,
                messages::setValue,
                errorMessage::setValue
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (messagesListener != null) {
            messagesListener.remove();
            messagesListener = null;
        }
    }
}
