package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class GameHeaderState {

    private final String roundText;
    private final String timeText;
    private final GameHeaderPlayerState playerOne;
    private final GameHeaderPlayerState playerTwo;
    private final int activePlayerNumber;

    public GameHeaderState(
            @NonNull String roundText,
            @NonNull String timeText,
            @NonNull GameHeaderPlayerState playerOne,
            @NonNull GameHeaderPlayerState playerTwo
    ) {
        this(roundText, timeText, playerOne, playerTwo, 1);
    }

    public GameHeaderState(
            @NonNull String roundText,
            @NonNull String timeText,
            @NonNull GameHeaderPlayerState playerOne,
            @NonNull GameHeaderPlayerState playerTwo,
            int activePlayerNumber
    ) {
        this.roundText = roundText;
        this.timeText = timeText;
        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
        this.activePlayerNumber = activePlayerNumber;
    }

    @NonNull
    public String getRoundText() {
        return roundText;
    }

    @NonNull
    public String getTimeText() {
        return timeText;
    }

    @NonNull
    public GameHeaderPlayerState getPlayerOne() {
        return playerOne;
    }

    @NonNull
    public GameHeaderPlayerState getPlayerTwo() {
        return playerTwo;
    }

    public int getActivePlayerNumber() {
        return activePlayerNumber;
    }

    @NonNull
    public GameHeaderState withRoundText(@NonNull String newRoundText) {
        return new GameHeaderState(newRoundText, timeText, playerOne, playerTwo, activePlayerNumber);
    }

    @NonNull
    public GameHeaderState withTimeText(@NonNull String newTimeText) {
        return new GameHeaderState(roundText, newTimeText, playerOne, playerTwo, activePlayerNumber);
    }

    @NonNull
    public GameHeaderState withScores(int playerOneScore, int playerTwoScore) {
        return new GameHeaderState(
                roundText,
                timeText,
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore),
                activePlayerNumber
        );
    }

    @NonNull
    public GameHeaderState withActivePlayerNumber(int newActivePlayerNumber) {
        return new GameHeaderState(roundText, timeText, playerOne, playerTwo, newActivePlayerNumber);
    }
}
