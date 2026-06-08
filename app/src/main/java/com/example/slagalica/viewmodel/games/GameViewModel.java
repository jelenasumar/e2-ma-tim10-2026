package com.example.slagalica.viewmodel.games;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;

public class GameViewModel extends AndroidViewModel {

    private final MutableLiveData<GameHeaderState> headerState = new MutableLiveData<>();
    protected final UserProfileRepository profileRepository;

    private String hostAvatarUri = "";
    private String guestAvatarUri = "";
    private boolean avatarsLoadRequested;

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
        if (avatarsLoadRequested) {
            applyLocalAvatarOverride(myUid, hostUid, guestUid);
            onUpdated.run();
            return;
        }
        avatarsLoadRequested = true;
        profileRepository.fetchAvatarUriForUser(
                hostUid,
                uri -> {
                    hostAvatarUri = uri != null ? uri : "";
                    applyLocalAvatarOverride(myUid, hostUid, guestUid);
                    onUpdated.run();
                },
                error -> { }
        );
        profileRepository.fetchAvatarUriForUser(
                guestUid,
                uri -> {
                    guestAvatarUri = uri != null ? uri : "";
                    applyLocalAvatarOverride(myUid, hostUid, guestUid);
                    onUpdated.run();
                },
                error -> { }
        );
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
}
