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
    private String lastEnteredTournamentId = "";
    private String lastEnteredRound = "";

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
        if (tournamentListener != null) {
            tournamentListener.remove();
            tournamentListener = null;
        }
        activeTournamentId = "";
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

    public boolean canStartAnotherTournament(@NonNull TournamentState state) {
        String uid = getCurrentUid();
        if (uid.isEmpty()) {
            return false;
        }
        if (TournamentState.STATUS_FINISHED.equals(state.getStatus())) {
            return true;
        }
        if (TournamentState.STATUS_FINAL_READY.equals(state.getStatus())) {
            return !uid.equals(state.getSemiOneWinnerUid()) && !uid.equals(state.getSemiTwoWinnerUid());
        }
        if (TournamentState.STATUS_SEMIS_READY.equals(state.getStatus())) {
            String roomId = state.roomForPlayer(uid);
            if (roomId.equals(state.getSemiOneRoomId()) && !state.getSemiOneWinnerUid().isEmpty()) {
                return !uid.equals(state.getSemiOneWinnerUid());
            }
            if (roomId.equals(state.getSemiTwoRoomId()) && !state.getSemiTwoWinnerUid().isEmpty()) {
                return !uid.equals(state.getSemiTwoWinnerUid());
            }
        }
        return false;
    }

    @NonNull
    public String currentRound(@NonNull TournamentState state) {
        String roomId = currentRoomId(state);
        if (roomId.isEmpty()) {
            return "";
        }
        if (roomId.equals(state.getSemiOneRoomId())) {
            return "semi_one";
        }
        if (roomId.equals(state.getSemiTwoRoomId())) {
            return "semi_two";
        }
        if (roomId.equals(state.getFinalRoomId())) {
            return "final";
        }
        return "";
    }

    public void markEnteredRoom(@NonNull TournamentState state) {
        lastEnteredTournamentId = state.getTournamentId();
        lastEnteredRound = currentRound(state);
    }

    public boolean consumeEnteredWinningRoom(@NonNull TournamentState state, @NonNull String phase) {
        if (!state.getTournamentId().equals(lastEnteredTournamentId) || !phase.equals(lastEnteredRound)) {
            return false;
        }
        String uid = getCurrentUid();
        if (uid.isEmpty() || !uid.equals(winnerForPhase(state, phase))) {
            return false;
        }
        lastEnteredTournamentId = "";
        lastEnteredRound = "";
        return true;
    }

    public boolean consumeEnteredLosingRoom(@NonNull TournamentState state, @NonNull String phase) {
        if (!state.getTournamentId().equals(lastEnteredTournamentId) || !phase.equals(lastEnteredRound)) {
            return false;
        }
        String uid = getCurrentUid();
        String winnerUid = winnerForPhase(state, phase);
        if (uid.isEmpty() || winnerUid.isEmpty() || uid.equals(winnerUid) || !playerInPhase(state, phase, uid)) {
            return false;
        }
        lastEnteredTournamentId = "";
        lastEnteredRound = "";
        return true;
    }

    @NonNull
    private static String winnerForPhase(@NonNull TournamentState state, @NonNull String phase) {
        switch (phase) {
            case "semi_one":
                return state.getSemiOneWinnerUid();
            case "semi_two":
                return state.getSemiTwoWinnerUid();
            case "final":
                return state.getChampionUid();
            default:
                return "";
        }
    }

    private static boolean playerInPhase(
            @NonNull TournamentState state,
            @NonNull String phase,
            @NonNull String uid
    ) {
        String roomId = state.roomForPlayer(uid);
        switch (phase) {
            case "semi_one":
                return roomId.equals(state.getSemiOneRoomId());
            case "semi_two":
                return roomId.equals(state.getSemiTwoRoomId());
            case "final":
                return uid.equals(state.getSemiOneWinnerUid()) || uid.equals(state.getSemiTwoWinnerUid());
            default:
                return false;
        }
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
