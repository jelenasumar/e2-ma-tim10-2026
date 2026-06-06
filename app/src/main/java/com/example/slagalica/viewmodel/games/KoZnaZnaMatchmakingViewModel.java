package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.KoZnaZnaMatchRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.utils.SingleLiveEvent;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

public class KoZnaZnaMatchmakingViewModel extends AndroidViewModel {

    public static final class StartGameEvent {
        private final String matchId;
        private final String myUid;

        public StartGameEvent(@NonNull String matchId, @NonNull String myUid) {
            this.matchId = matchId;
            this.myUid = myUid;
        }

        @NonNull
        public String getMatchId() {
            return matchId;
        }

        @NonNull
        public String getMyUid() {
            return myUid;
        }
    }

    private final KoZnaZnaMatchRepository matchRepository;
    private final UserProfileRepository profileRepository;

    private final MutableLiveData<String> statusMessage = new MutableLiveData<>();
    private final MutableLiveData<String> lobbyCode = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final SingleLiveEvent<StartGameEvent> startGame = new SingleLiveEvent<>();

    @Nullable
    private ListenerRegistration lobbyListener;
    @Nullable
    private String activeLobbyCode;
    private boolean matchCreationStarted;

    public KoZnaZnaMatchmakingViewModel(@NonNull Application application) {
        super(application);
        matchRepository = new KoZnaZnaMatchRepository();
        profileRepository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<String> getStatusMessage() {
        return statusMessage;
    }

    @NonNull
    public LiveData<String> getLobbyCode() {
        return lobbyCode;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<StartGameEvent> getStartGame() {
        return startGame;
    }

    public void createLobby() {
        isLoading.setValue(true);
        profileRepository.ensureAuthenticated(
                () -> startCreateLobby(),
                error -> {
                    isLoading.setValue(false);
                    statusMessage.setValue(error);
                }
        );
    }

    private void startCreateLobby() {
        String uid = matchRepository.getCurrentUid();
        if (uid == null) {
            isLoading.setValue(false);
            statusMessage.setValue(getApplication().getString(R.string.error_guest_sign_in));
            return;
        }

        String username = profileRepository.getDisplayNameForGames();
        matchRepository.createLobby(uid, username, code -> {
            isLoading.setValue(false);
            activeLobbyCode = code;
            lobbyCode.setValue(code);
            statusMessage.setValue(getApplication().getString(R.string.kzz_lobby_waiting, code));
            listenLobby(code, uid, username, true);
        }, error -> {
            isLoading.setValue(false);
            statusMessage.setValue(mapJoinError(error));
        });
    }

    public void joinLobby(@NonNull String codeInput) {
        String code = codeInput.trim();
        if (TextUtils.isEmpty(code)) {
            statusMessage.setValue(getApplication().getString(R.string.kzz_enter_code));
            return;
        }

        isLoading.setValue(true);
        profileRepository.ensureAuthenticated(
                () -> startJoinLobby(code),
                error -> {
                    isLoading.setValue(false);
                    statusMessage.setValue(error);
                }
        );
    }

    private void startJoinLobby(@NonNull String code) {
        String uid = matchRepository.getCurrentUid();
        if (uid == null) {
            isLoading.setValue(false);
            statusMessage.setValue(getApplication().getString(R.string.error_guest_sign_in));
            return;
        }

        String username = profileRepository.getDisplayNameForGames();
        matchRepository.joinLobby(code, uid, username, () -> {
            isLoading.setValue(false);
            activeLobbyCode = code;
            statusMessage.setValue(getApplication().getString(R.string.kzz_lobby_joined));
            listenLobby(code, uid, username, false);
        }, error -> {
            isLoading.setValue(false);
            statusMessage.setValue(mapJoinError(error));
        });
    }

    public void cleanup() {
        if (lobbyListener != null) {
            lobbyListener.remove();
            lobbyListener = null;
        }
    }

    @Override
    protected void onCleared() {
        cleanup();
        super.onCleared();
    }

    private void listenLobby(
            @NonNull String code,
            @NonNull String myUid,
            @NonNull String myUsername,
            boolean isHost
    ) {
        cleanup();
        lobbyListener = matchRepository.listenLobby(code, snapshot -> {
            String matchId = snapshot.getString("matchId");
            if (matchId != null && !matchId.isEmpty()) {
                startGame.setValue(new StartGameEvent(matchId, myUid));
                return;
            }

            if (isHost) {
                String guestUid = snapshot.getString("guestUid");
                String guestUsername = snapshot.getString("guestUsername");
                if (guestUid != null && !guestUid.isEmpty() && !matchCreationStarted) {
                    matchCreationStarted = true;
                    statusMessage.setValue(getApplication().getString(
                            R.string.kzz_opponent_joined,
                            guestUsername != null ? guestUsername : getApplication().getString(R.string.kzz_opponent)
                    ));
                    matchRepository.createMatchFromLobby(
                            code,
                            myUid,
                            myUsername,
                            guestUid,
                            guestUsername != null ? guestUsername : "",
                            createdMatchId -> { },
                            error -> {
                                matchCreationStarted = false;
                                statusMessage.setValue(mapJoinError(error));
                            }
                    );
                }
            } else {
                statusMessage.setValue(getApplication().getString(R.string.kzz_lobby_waiting_start));
            }
        }, error -> statusMessage.setValue(mapJoinError(error)));
    }

    @NonNull
    private String mapJoinError(@NonNull String error) {
        switch (error) {
            case "LOBBY_NOT_FOUND":
                return getApplication().getString(R.string.kzz_lobby_not_found);
            case "LOBBY_FULL":
                return getApplication().getString(R.string.kzz_lobby_full);
            case "LOBBY_INVALID":
                return getApplication().getString(R.string.kzz_lobby_invalid);
            case "PERMISSION_DENIED":
                return getApplication().getString(R.string.kzz_firestore_permission_denied);
            default:
                return error;
        }
    }
}
