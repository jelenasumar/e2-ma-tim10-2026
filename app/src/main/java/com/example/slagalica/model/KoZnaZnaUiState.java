package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

public final class KoZnaZnaUiState {

    public static final int NO_SELECTION = -1;

    private final int questionNumber;
    private final int totalQuestions;
    private final int roundSecondsLeft;
    private final int questionSecondsLeft;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final String playerOneLabel;
    private final String playerTwoLabel;
    private final String playerOneAvatarUri;
    private final String playerTwoAvatarUri;
    private final String questionText;
    private final List<String> options;
    private final int selectedAnswerIndex;
    private final boolean answersEnabled;
    private final boolean gameFinished;
    private final String statusMessage;
    private final boolean waitingForOpponent;

    public KoZnaZnaUiState(
            int questionNumber,
            int totalQuestions,
            int roundSecondsLeft,
            int questionSecondsLeft,
            int playerOneScore,
            int playerTwoScore,
            @NonNull String playerOneLabel,
            @NonNull String playerTwoLabel,
            @Nullable String playerOneAvatarUri,
            @Nullable String playerTwoAvatarUri,
            @NonNull String questionText,
            @NonNull List<String> options,
            int selectedAnswerIndex,
            boolean answersEnabled,
            boolean gameFinished,
            @NonNull String statusMessage,
            boolean waitingForOpponent
    ) {
        this.questionNumber = questionNumber;
        this.totalQuestions = totalQuestions;
        this.roundSecondsLeft = roundSecondsLeft;
        this.questionSecondsLeft = questionSecondsLeft;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.playerOneLabel = playerOneLabel;
        this.playerTwoLabel = playerTwoLabel;
        this.playerOneAvatarUri = playerOneAvatarUri;
        this.playerTwoAvatarUri = playerTwoAvatarUri;
        this.questionText = questionText;
        this.options = options;
        this.selectedAnswerIndex = selectedAnswerIndex;
        this.answersEnabled = answersEnabled;
        this.gameFinished = gameFinished;
        this.statusMessage = statusMessage;
        this.waitingForOpponent = waitingForOpponent;
    }

    public int getQuestionNumber() {
        return questionNumber;
    }

    public int getTotalQuestions() {
        return totalQuestions;
    }

    public int getRoundSecondsLeft() {
        return roundSecondsLeft;
    }

    public int getQuestionSecondsLeft() {
        return questionSecondsLeft;
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
    public String getQuestionText() {
        return questionText;
    }

    @NonNull
    public List<String> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public int getSelectedAnswerIndex() {
        return selectedAnswerIndex;
    }

    public boolean isAnswersEnabled() {
        return answersEnabled;
    }

    public boolean isGameFinished() {
        return gameFinished;
    }

    @NonNull
    public String getStatusMessage() {
        return statusMessage;
    }

    public boolean isWaitingForOpponent() {
        return waitingForOpponent;
    }
}
