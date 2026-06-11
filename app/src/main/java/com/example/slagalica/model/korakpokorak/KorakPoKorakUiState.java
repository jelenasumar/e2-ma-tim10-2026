package com.example.slagalica.model.korakpokorak;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class KorakPoKorakUiState {

    private final int currentRound;
    private final int totalRounds;
    private final int activePlayerNumber;
    private final int answeringPlayerNumber;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final int secondsLeft;
    private final List<String> visibleSteps;
    private final int currentStepIndex;
    private final boolean bonusPhase;
    private final boolean roundOver;
    private final boolean gameOver;
    private final boolean canSubmit;
    private final String statusMessage;

    public KorakPoKorakUiState(
            int currentRound,
            int totalRounds,
            int activePlayerNumber,
            int answeringPlayerNumber,
            int playerOneScore,
            int playerTwoScore,
            int secondsLeft,
            @NonNull List<String> visibleSteps,
            int currentStepIndex,
            boolean bonusPhase,
            boolean roundOver,
            boolean gameOver,
            boolean canSubmit,
            @NonNull String statusMessage
    ) {
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.activePlayerNumber = activePlayerNumber;
        this.answeringPlayerNumber = answeringPlayerNumber;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.secondsLeft = secondsLeft;
        this.visibleSteps = Collections.unmodifiableList(new ArrayList<>(visibleSteps));
        this.currentStepIndex = currentStepIndex;
        this.bonusPhase = bonusPhase;
        this.roundOver = roundOver;
        this.gameOver = gameOver;
        this.canSubmit = canSubmit;
        this.statusMessage = statusMessage;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getActivePlayerNumber() {
        return activePlayerNumber;
    }

    public int getAnsweringPlayerNumber() {
        return answeringPlayerNumber;
    }

    public int getPlayerOneScore() {
        return playerOneScore;
    }

    public int getPlayerTwoScore() {
        return playerTwoScore;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    @NonNull
    public List<String> getVisibleSteps() {
        return visibleSteps;
    }

    public int getCurrentStepIndex() {
        return currentStepIndex;
    }

    public boolean isBonusPhase() {
        return bonusPhase;
    }

    public boolean isRoundOver() {
        return roundOver;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isCanSubmit() {
        return canSubmit;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

}
