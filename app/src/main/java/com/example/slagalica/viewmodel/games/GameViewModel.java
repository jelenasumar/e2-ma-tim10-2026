package com.example.slagalica.viewmodel.games;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.google.firebase.firestore.ListenerRegistration;

public class GameViewModel extends AndroidViewModel {

    private final MutableLiveData<GameHeaderState> headerState = new MutableLiveData<>();
    protected final UserProfileRepository profileRepository;

    private String hostAvatarUri = "";
    private String guestAvatarUri = "";
    private boolean avatarsLoadRequested;
    private String observedHostUid = "";
    private String observedGuestUid = "";
    private ListenerRegistration hostAvatarListener;
    private ListenerRegistration guestAvatarListener;

    public GameViewModel(@NonNull Application application) {
        super(application);
        profileRepository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<GameHeaderState> getHeaderState() {
        return headerState;
    }

    protected void setHeaderState(@NonNull GameHeaderState state) {
        headerState.setValue(state);
    }

    protected void initializeHeader(
            @NonNull String roundText,
            @NonNull String timeText,
            @NonNull GameHeaderPlayerState playerOne,
            @NonNull GameHeaderPlayerState playerTwo
    ) {
        setHeaderState(new GameHeaderState(roundText, timeText, playerOne, playerTwo));
    }

    protected void updateRound(@NonNull String roundText) {
        GameHeaderState currentState = headerState.getValue();
        if (currentState != null) {
            setHeaderState(currentState.withRoundText(roundText));
        }
    }

    protected void updateTime(@NonNull String timeText) {
        GameHeaderState currentState = headerState.getValue();
        if (currentState != null) {
            setHeaderState(currentState.withTimeText(timeText));
        }
    }

    protected void updateScores(int playerOneScore, int playerTwoScore) {
        GameHeaderState currentState = headerState.getValue();
        if (currentState != null) {
            setHeaderState(currentState.withScores(playerOneScore, playerTwoScore));
        }
    }

    protected void updateActivePlayer(int activePlayerNumber) {
        GameHeaderState currentState = headerState.getValue();
        if (currentState != null) {
            setHeaderState(currentState.withActivePlayerNumber(activePlayerNumber));
        }
    }

    protected void ensurePlayerAvatars(
            @NonNull String myUid,
            @NonNull String hostUid,
            @NonNull String guestUid,
            @NonNull Runnable onUpdated
    ) {
        if (avatarsLoadRequested && hostUid.equals(observedHostUid) && guestUid.equals(observedGuestUid)) {
            applyLocalAvatarOverride(myUid, hostUid, guestUid);
            onUpdated.run();
            return;
        }
        avatarsLoadRequested = true;
        observedHostUid = hostUid;
        observedGuestUid = guestUid;
        profileRepository.ensurePublicAvatarUri(
                unused -> listenPlayerAvatars(myUid, hostUid, guestUid, onUpdated),
                error -> listenPlayerAvatars(myUid, hostUid, guestUid, onUpdated)
        );
    }

    private void listenPlayerAvatars(
            @NonNull String myUid,
            @NonNull String hostUid,
            @NonNull String guestUid,
            @NonNull Runnable onUpdated
    ) {
        removeAvatarListeners();
        hostAvatarListener = profileRepository.listenAvatarUriForUser(
                hostUid,
                uri -> {
                    hostAvatarUri = displayAvatarUri(uri, myUid.equals(hostUid));
                    applyLocalAvatarOverride(myUid, hostUid, guestUid);
                    onUpdated.run();
                },
                error -> { }
        );
        guestAvatarListener = profileRepository.listenAvatarUriForUser(
                guestUid,
                uri -> {
                    guestAvatarUri = displayAvatarUri(uri, myUid.equals(guestUid));
                    applyLocalAvatarOverride(myUid, hostUid, guestUid);
                    onUpdated.run();
                },
                error -> { }
        );
    }

    private void removeAvatarListeners() {
        if (hostAvatarListener != null) {
            hostAvatarListener.remove();
            hostAvatarListener = null;
        }
        if (guestAvatarListener != null) {
            guestAvatarListener.remove();
            guestAvatarListener = null;
        }
    }

    @NonNull
    protected GameHeaderPlayerState playerOneWithAvatars(
            @NonNull String username,
            int score
    ) {
        return new GameHeaderPlayerState(username, score, hostAvatarUri);
    }

    @NonNull
    protected GameHeaderPlayerState playerTwoWithAvatars(
            @NonNull String username,
            int score
    ) {
        return new GameHeaderPlayerState(username, score, guestAvatarUri);
    }

    private void applyLocalAvatarOverride(
            @NonNull String myUid,
            @NonNull String hostUid,
            @NonNull String guestUid
    ) {
        String localAvatar = profileRepository.loadProfile().getAvatarUri();
        if (localAvatar == null) {
            localAvatar = "";
        }
        if (myUid.equals(hostUid)) {
            hostAvatarUri = localAvatar;
        } else if (myUid.equals(guestUid)) {
            guestAvatarUri = localAvatar;
        }
    }

    @NonNull
    private static String displayAvatarUri(String avatarUri, boolean isCurrentUser) {
        if (avatarUri == null || avatarUri.isEmpty()) {
            return "";
        }
        if (isCurrentUser || avatarUri.startsWith("http://") || avatarUri.startsWith("https://")) {
            return avatarUri;
        }
        return "";
    }

    @Override
    protected void onCleared() {
        removeAvatarListeners();
        super.onCleared();
    }
}
