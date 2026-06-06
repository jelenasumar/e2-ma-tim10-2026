package com.example.slagalica.viewmodel.games;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;

public class GameViewModel extends ViewModel {

    private final MutableLiveData<GameHeaderState> headerState = new MutableLiveData<>();

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
}
