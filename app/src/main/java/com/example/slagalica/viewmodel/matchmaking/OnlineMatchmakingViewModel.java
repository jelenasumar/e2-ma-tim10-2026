package com.example.slagalica.viewmodel.matchmaking;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.data.repository.OnlineMatchmakingRepository;
import com.google.firebase.firestore.ListenerRegistration;

public class OnlineMatchmakingViewModel extends ViewModel {

    private final OnlineMatchmakingRepository repository = new OnlineMatchmakingRepository();
    private final MutableLiveData<String> status = new MutableLiveData<>("Trazenje protivnika...");
    private final MutableLiveData<String> roomId = new MutableLiveData<>();
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private ListenerRegistration queueListener;

    @NonNull
    public LiveData<String> getStatus() {
        return status;
    }

    @NonNull
    public LiveData<String> getRoomId() {
        return roomId;
    }

    @NonNull
    public LiveData<Boolean> getSearching() {
        return searching;
    }

    public void startLooking() {
        searching.setValue(true);
        status.setValue("Trazi se protivnik...");
        if (queueListener != null) {
            queueListener.remove();
        }
        queueListener = repository.listenMyQueue(this::onMatched, error -> status.setValue(error));
        repository.startLooking(
                this::onMatched,
                () -> status.setValue("Ceka se protivnik..."),
                error -> {
                    searching.setValue(false);
                    status.setValue(error);
                }
        );
    }

    public void cancelLooking() {
        repository.cancelLooking(() -> {
            searching.setValue(false);
            status.setValue("Trazenje je prekinuto.");
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (queueListener != null) {
            queueListener.remove();
            queueListener = null;
        }
    }

    private void onMatched(@NonNull String matchedRoomId) {
        searching.setValue(false);
        roomId.setValue(matchedRoomId);
        status.setValue("Protivnik je pronadjen. Soba: " + matchedRoomId);
    }
}
