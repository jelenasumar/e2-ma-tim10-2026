package com.example.slagalica.model.spojnice;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

public final class SpojniceUiState {

    public static final int NO_SELECTION = -1;
    public static final String PHASE_ACTIVE = "ACTIVE";
    public static final String PHASE_FOLLOWUP = "FOLLOWUP";

    private final int currentRound;
    private final int totalRounds;
    private final int secondsLeft;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final String playerOneLabel;
    private final String playerTwoLabel;
    private final String criterion;
    private final List<String> leftTerms;
    private final List<String> rightTerms;
    private final List<Integer> connectedLeftIndices;
    private final List<Integer> attemptedLeftIndices;
    private final int currentLeftIndex;
    private final int activePlayerNumber;
    private final boolean myTurn;
    private final boolean inputsEnabled;
    private final boolean gameOver;
    private final boolean roundOver;
    private final String statusMessage;
    private final String phase;

    public SpojniceUiState(
            int currentRound,
            int totalRounds,
            int secondsLeft,
            int playerOneScore,
            int playerTwoScore,
            @NonNull String playerOneLabel,
            @NonNull String playerTwoLabel,
            @NonNull String criterion,
            @NonNull List<String> leftTerms,
            @NonNull List<String> rightTerms,
            @NonNull List<Integer> connectedLeftIndices,
            @NonNull List<Integer> attemptedLeftIndices,
            int currentLeftIndex,
            int activePlayerNumber,
            boolean myTurn,
            boolean inputsEnabled,
            boolean gameOver,
            boolean roundOver,
            @NonNull String statusMessage,
            @NonNull String phase
    ) {
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.secondsLeft = secondsLeft;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.playerOneLabel = playerOneLabel;
        this.playerTwoLabel = playerTwoLabel;
        this.criterion = criterion;
        this.leftTerms = new ArrayList<>(leftTerms);
        this.rightTerms = new ArrayList<>(rightTerms);
        this.connectedLeftIndices = new ArrayList<>(connectedLeftIndices);
        this.attemptedLeftIndices = new ArrayList<>(attemptedLeftIndices);
        this.currentLeftIndex = currentLeftIndex;
        this.activePlayerNumber = activePlayerNumber;
        this.myTurn = myTurn;
        this.inputsEnabled = inputsEnabled;
        this.gameOver = gameOver;
        this.roundOver = roundOver;
        this.statusMessage = statusMessage;
        this.phase = phase;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getTotalRounds() {
        return totalRounds;
    }

    public int getSecondsLeft() {
        return secondsLeft;
    }

    public int getPlayerOneScore() {
        return playerOneScore;
    }

    public int getPlayerTwoScore() {
        return playerTwoScore;
    }

    @NonNull
    public String getPlayerOneLabel() {
        return playerOneLabel;
    }

    @NonNull
    public String getPlayerTwoLabel() {
        return playerTwoLabel;
    }

    @NonNull
    public String getCriterion() {
        return criterion;
    }

    @NonNull
    public List<String> getLeftTerms() {
        return new ArrayList<>(leftTerms);
    }

    @NonNull
    public List<String> getRightTerms() {
        return new ArrayList<>(rightTerms);
    }

    @NonNull
    public List<Integer> getConnectedLeftIndices() {
        return new ArrayList<>(connectedLeftIndices);
    }

    @NonNull
    public List<Integer> getAttemptedLeftIndices() {
        return new ArrayList<>(attemptedLeftIndices);
    }

    public int getCurrentLeftIndex() {
        return currentLeftIndex;
    }

    public int getActivePlayerNumber() {
        return activePlayerNumber;
    }

    public boolean isMyTurn() {
        return myTurn;
    }

    public boolean isInputsEnabled() {
        return inputsEnabled;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public boolean isRoundOver() {
        return roundOver;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

    @NonNull
    public String getPhase() {
        return phase;
    }

    public boolean isRowConnected(int rowIndex) {
        return connectedLeftIndices.contains(rowIndex);
    }

    public boolean isRowLocked(int rowIndex) {
        return connectedLeftIndices.contains(rowIndex);
    }

    public boolean isRowSelectable(int rowIndex) {
        if (!inputsEnabled || !myTurn) {
            return false;
        }
        if (connectedLeftIndices.contains(rowIndex)) {
            return false;
        }
        if (PHASE_ACTIVE.equals(phase)) {
            return rowIndex == currentLeftIndex;
        }
        if (PHASE_FOLLOWUP.equals(phase)) {
            return !connectedLeftIndices.contains(rowIndex);
        }
        return false;
    }
}
