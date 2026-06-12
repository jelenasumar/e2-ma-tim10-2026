package com.example.slagalica.model.mynumber;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MyNumberUiState {

    private final int currentRound;
    private final int totalRounds;
    private final int activePlayerNumber;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final int secondsLeft;

    private final int targetNumber;
    private final List<Integer> numbers;

    private final boolean targetRevealed;
    private final boolean numbersRevealed;
    private final boolean roundOver;
    private final boolean gameOver;

    private final boolean canStopTarget;
    private final boolean canStopNumbers;
    private final boolean canSubmitExpression;

    private final String statusMessage;

    public MyNumberUiState(
            int currentRound,
            int totalRounds,
            int activePlayerNumber,
            int playerOneScore,
            int playerTwoScore,
            int secondsLeft,
            int targetNumber,
            @NonNull List<Integer> numbers,
            boolean targetRevealed,
            boolean numbersRevealed,
            boolean roundOver,
            boolean gameOver,
            boolean canStopTarget,
            boolean canStopNumbers,
            boolean canSubmitExpression,
            @NonNull String statusMessage
    ) {
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.activePlayerNumber = activePlayerNumber;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.secondsLeft = secondsLeft;
        this.targetNumber = targetNumber;
        this.numbers = Collections.unmodifiableList(new ArrayList<>(numbers));
        this.targetRevealed = targetRevealed;
        this.numbersRevealed = numbersRevealed;
        this.roundOver = roundOver;
        this.gameOver = gameOver;
        this.canStopTarget = canStopTarget;
        this.canStopNumbers = canStopNumbers;
        this.canSubmitExpression = canSubmitExpression;
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

    public int getPlayerOneScore() {
        return playerOneScore;
    }

    public int getPlayerTwoScore() {
        return playerTwoScore;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public int getTargetNumber() {
        return targetNumber;
    }

    @NonNull
    public List<Integer> getNumbers() {
        return numbers;
    }

    public boolean isTargetRevealed() {
        return targetRevealed;
    }

    public boolean isNumbersRevealed() {
        return numbersRevealed;
    }

    public boolean isRoundOver() {
        return roundOver;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isCanStopTarget() {
        return canStopTarget;
    }

    public boolean isCanStopNumbers() {
        return canStopNumbers;
    }

    public boolean isCanSubmitExpression() {
        return canSubmitExpression;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }
}