package com.example.slagalica.viewmodel.tournament;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.model.TournamentState;
import com.google.firebase.firestore.ListenerRegistration;

public class TournamentViewModel extends AndroidViewModel {

    private final TournamentRepository repository;
    private final MutableLiveData<String> status = new MutableLiveData<>("Spremno za turnir.");
    private final MutableLiveData<Boolean> searching = new MutableLiveData<>(false);
    private final MutableLiveData<TournamentState> tournament = new MutableLiveData<>();
    private ListenerRegistration queueListener;
    private ListenerRegistration tournamentListener;
    private String activeTournamentId = "";

    public TournamentViewModel(@NonNull Application application) {
        super(application);
        repository = new TournamentRepository(application);
    }

    @NonNull
    public LiveData<String> getStatus() {
        return status;
    }

    @NonNull
    public LiveData<Boolean> getSearching() {
        return searching;
    }

    @NonNull
    public LiveData<TournamentState> getTournament() {
        return tournament;
    }

    @NonNull
    public String getCurrentUid() {
        String uid = repository.getCurrentUid();
        return uid != null ? uid : "";
    }

    public void joinTournament() {
        searching.setValue(true);
        status.setValue("Skida se 3 tokena i traze se jos 3 igraca...");
        if (queueListener != null) {
            queueListener.remove();
        }
        queueListener = repository.listenMyQueue(this::listenTournament, this::showError);
        repository.joinTournament(
                () -> status.setValue("Cekamo da se skupe 4 aktivna igraca..."),
                this::showError
        );
    }

    public void cancelWaiting() {
        repository.cancelWaiting(() -> {
            searching.setValue(false);
            status.setValue("Napustio/la si cekanje. Ulaz se ne refundira.");
        });
    }

    @NonNull
    public String currentRoomId(@NonNull TournamentState state) {
        String uid = getCurrentUid();
        if (uid.isEmpty()) {
            return "";
        }
        if (TournamentState.STATUS_FINAL_READY.equals(state.getStatus())) {
            if (uid.equals(state.getSemiOneWinnerUid()) || uid.equals(state.getSemiTwoWinnerUid())) {
                return state.getFinalRoomId();
            }
            return "";
        }
        if (TournamentState.STATUS_SEMIS_READY.equals(state.getStatus())) {
            String roomId = state.roomForPlayer(uid);
            if (roomId.equals(state.getSemiOneRoomId()) && !state.getSemiOneWinnerUid().isEmpty()) {
                return "";
            }
            if (roomId.equals(state.getSemiTwoRoomId()) && !state.getSemiTwoWinnerUid().isEmpty()) {
                return "";
            }
            return roomId;
        }
        return "";
    }

    private void listenTournament(@NonNull String tournamentId) {
        if (tournamentId.equals(activeTournamentId)) {
            return;
        }
        activeTournamentId = tournamentId;
        searching.setValue(false);
        status.setValue("Turnir je popunjen. Parovi su izvuceni.");
        if (tournamentListener != null) {
            tournamentListener.remove();
        }
        tournamentListener = repository.listenTournament(
                tournamentId,
                state -> {
                    tournament.setValue(state);
                    updateStatusForState(state);
                },
                this::showError
        );
    }

    private void updateStatusForState(@NonNull TournamentState state) {
        String uid = getCurrentUid();
        if (TournamentState.STATUS_FINISHED.equals(state.getStatus())) {
            if (uid.equals(state.getChampionUid())) {
                status.setValue("Pobeda u turniru! Finale je osvojeno.");
            } else {
                status.setValue("Turnir je zavrsen.");
            }
            return;
        }
        if (TournamentState.STATUS_FINAL_READY.equals(state.getStatus())) {
            if (uid.equals(state.getSemiOneWinnerUid()) || uid.equals(state.getSemiTwoWinnerUid())) {
                status.setValue("Cestitamo, ulazis u finale.");
            } else {
                status.setValue("Ispao/la si u polufinalu. Nema dodatnih nagrada.");
            }
            return;
        }
        if (TournamentState.STATUS_SEMIS_READY.equals(state.getStatus())) {
            status.setValue("Polufinalni parovi su spremni.");
        }
    }

    private void showError(@NonNull String error) {
        searching.setValue(false);
        status.setValue(error);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (queueListener != null) {
            queueListener.remove();
            queueListener = null;
        }
        if (tournamentListener != null) {
            tournamentListener.remove();
            tournamentListener = null;
        }
    }
}
