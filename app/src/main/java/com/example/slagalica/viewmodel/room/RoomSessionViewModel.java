package com.example.slagalica.viewmodel.room;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.model.RoomSession;
import com.google.firebase.firestore.ListenerRegistration;

public class RoomSessionViewModel extends ViewModel {

    private final RoomSessionRepository repository = new RoomSessionRepository();
    private final MutableLiveData<RoomSession> room = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private ListenerRegistration listener;
    private String activeRoomId = "";

    @NonNull
    public LiveData<RoomSession> getRoom() {
        return room;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void start(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(activeRoomId)) {
            return;
        }

        activeRoomId = roomId;
        if (listener != null) {
            listener.remove();
        }
        listener = repository.listenRoom(roomId, room::setValue, errorMessage::setValue);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (listener != null) {
            listener.remove();
            listener = null;
        }
    }
}
