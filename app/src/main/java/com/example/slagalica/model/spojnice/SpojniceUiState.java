package com.example.slagalica.model.spojnice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public final class SpojniceUiState {

    public static final int NO_SELECTION = -1;
    public static final int NO_ROW = -1;
    public static final String PHASE_ACTIVE = "ACTIVE";
    public static final String PHASE_FOLLOWUP = "FOLLOWUP";

    private final int currentRound;
    private final int totalRounds;
    private final int secondsLeft;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final String playerOneLabel;
    private final String playerTwoLabel;
    private final String playerOneAvatarUri;
    private final String playerTwoAvatarUri;
    private final String criterion;
    private final List<String> leftTerms;
    private final List<String> rightTerms;
    private final List<Integer> connectedLeftIndices;
    private final List<Integer> attemptedLeftIndices;
    private final List<Integer> usedRightIndices;
    private final List<Integer> followupLockedLeftIndices;
    private final int currentLeftIndex;
    private final int activePlayerNumber;
    private final boolean myTurn;
    private final boolean inputsEnabled;
    private final boolean gameOver;
    private final boolean roundOver;
    private final String statusMessage;
    private final String phase;
    private final int selectedRow;
    private final int selectedRightIndex;
    private final boolean canSubmit;

    public SpojniceUiState(
            int currentRound,
            int totalRounds,
            int secondsLeft,
            int playerOneScore,
            int playerTwoScore,
            @NonNull String playerOneLabel,
            @NonNull String playerTwoLabel,
            @Nullable String playerOneAvatarUri,
            @Nullable String playerTwoAvatarUri,
            @NonNull String criterion,
            @NonNull List<String> leftTerms,
            @NonNull List<String> rightTerms,
            @NonNull List<Integer> connectedLeftIndices,
            @NonNull List<Integer> attemptedLeftIndices,
            @NonNull List<Integer> usedRightIndices,
            @NonNull List<Integer> followupLockedLeftIndices,
            int currentLeftIndex,
            int activePlayerNumber,
            boolean myTurn,
            boolean inputsEnabled,
            boolean gameOver,
            boolean roundOver,
            @NonNull String statusMessage,
            @NonNull String phase,
            int selectedRow,
            int selectedRightIndex,
            boolean canSubmit
    ) {
        this.currentRound = currentRound;
        this.totalRounds = totalRounds;
        this.secondsLeft = secondsLeft;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.playerOneLabel = playerOneLabel;
        this.playerTwoLabel = playerTwoLabel;
        this.playerOneAvatarUri = playerOneAvatarUri;
        this.playerTwoAvatarUri = playerTwoAvatarUri;
        this.criterion = criterion;
        this.leftTerms = new ArrayList<>(leftTerms);
        this.rightTerms = new ArrayList<>(rightTerms);
        this.connectedLeftIndices = new ArrayList<>(connectedLeftIndices);
        this.attemptedLeftIndices = new ArrayList<>(attemptedLeftIndices);
        this.usedRightIndices = new ArrayList<>(usedRightIndices);
        this.followupLockedLeftIndices = new ArrayList<>(followupLockedLeftIndices);
        this.currentLeftIndex = currentLeftIndex;
        this.activePlayerNumber = activePlayerNumber;
        this.myTurn = myTurn;
        this.inputsEnabled = inputsEnabled;
        this.gameOver = gameOver;
        this.roundOver = roundOver;
        this.statusMessage = statusMessage;
        this.phase = phase;
        this.selectedRow = selectedRow;
        this.selectedRightIndex = selectedRightIndex;
        this.canSubmit = canSubmit;
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

    @Nullable
    public String getPlayerOneAvatarUri() {
        return playerOneAvatarUri;
    }

    @Nullable
    public String getPlayerTwoAvatarUri() {
        return playerTwoAvatarUri;
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

    @NonNull
    public List<Integer> getUsedRightIndices() {
        return new ArrayList<>(usedRightIndices);
    }

    @NonNull
    public List<Integer> getFollowupLockedLeftIndices() {
        return new ArrayList<>(followupLockedLeftIndices);
    }

    @NonNull
    public List<Integer> getAvailableRightIndices() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < rightTerms.size(); i++) {
            if (!usedRightIndices.contains(i)) {
                available.add(i);
            }
        }
        return available;
    }

    @NonNull
    public List<String> getAvailableRightTerms() {
        List<String> terms = new ArrayList<>();
        for (int index : getAvailableRightIndices()) {
            if (index >= 0 && index < rightTerms.size()) {
                terms.add(rightTerms.get(index));
            }
        }
        return terms;
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

    public int getSelectedRow() {
        return selectedRow;
    }

    public int getSelectedRightIndex() {
        return selectedRightIndex;
    }

    public boolean isCanSubmit() {
        return canSubmit;
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
            return rowIndex == selectedRow && selectedRow >= 0;
        }
        return false;
    }

    public boolean isFollowupRowLocked(int rowIndex) {
        return PHASE_FOLLOWUP.equals(phase) && followupLockedLeftIndices.contains(rowIndex);
    }

    public boolean isFollowupLeftSelectable(int rowIndex) {
        return PHASE_FOLLOWUP.equals(phase)
                && inputsEnabled
                && myTurn
                && !connectedLeftIndices.contains(rowIndex)
                && !followupLockedLeftIndices.contains(rowIndex);
    }

    public boolean isFollowupRightSpinnerEnabled(int rowIndex) {
        return isFollowupLeftSelectable(rowIndex)
                && rowIndex == selectedRow
                && selectedRow >= 0;
    }

    public boolean hasFollowupLeftSelected() {
        return PHASE_FOLLOWUP.equals(phase) && selectedRow >= 0;
    }

    public boolean isFollowupPending(int rowIndex) {
        return PHASE_FOLLOWUP.equals(phase) && !connectedLeftIndices.contains(rowIndex);
    }

    public boolean isFollowupSelected(int rowIndex) {
        return PHASE_FOLLOWUP.equals(phase) && rowIndex == selectedRow && isFollowupPending(rowIndex);
    }

    public boolean isActiveRow(int rowIndex) {
        return PHASE_ACTIVE.equals(phase) && rowIndex == currentLeftIndex && !connectedLeftIndices.contains(rowIndex);
    }
}
