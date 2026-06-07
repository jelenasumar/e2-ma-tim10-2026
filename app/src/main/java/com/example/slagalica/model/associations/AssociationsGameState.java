package com.example.slagalica.model.associations;

import androidx.annotation.NonNull;

public final class AssociationsGameState {

    private final int currentRound;
    private final int activePlayerNumber;
    private final int playerOneScore;
    private final int playerTwoScore;
    private final AssociationPuzzle puzzle;
    private final boolean[][] revealedFields;
    private final boolean[] solvedColumns;
    private final boolean fieldOpenedThisTurn;
    private final boolean myTurn;
    private final boolean inputsEnabled;
    private final boolean finalAnswerSolved;
    private final boolean roundOver;
    private final boolean gameOver;

    public AssociationsGameState(
            int currentRound,
            int activePlayerNumber,
            int playerOneScore,
            int playerTwoScore,
            @NonNull AssociationPuzzle puzzle,
            @NonNull boolean[][] revealedFields,
            @NonNull boolean[] solvedColumns,
            boolean fieldOpenedThisTurn,
            boolean myTurn,
            boolean inputsEnabled,
            boolean finalAnswerSolved,
            boolean roundOver,
            boolean gameOver
    ) {
        this.currentRound = currentRound;
        this.activePlayerNumber = activePlayerNumber;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;
        this.puzzle = puzzle;
        this.revealedFields = copyRevealedFields(revealedFields);
        this.solvedColumns = copySolvedColumns(solvedColumns);
        this.fieldOpenedThisTurn = fieldOpenedThisTurn;
        this.myTurn = myTurn;
        this.inputsEnabled = inputsEnabled;
        this.finalAnswerSolved = finalAnswerSolved;
        this.roundOver = roundOver;
        this.gameOver = gameOver;
    }

    public int getCurrentRound() {
        return currentRound;
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

    @NonNull
    public AssociationPuzzle getPuzzle() {
        return puzzle;
    }

    @NonNull
    public boolean[][] getRevealedFields() {
        return copyRevealedFields(revealedFields);
    }

    @NonNull
    public boolean[] getSolvedColumns() {
        return copySolvedColumns(solvedColumns);
    }

    public boolean isFieldRevealed(int columnIndex, int clueIndex) {
        if (columnIndex < 0 || columnIndex >= revealedFields.length) {
            return false;
        }
        if (clueIndex < 0 || clueIndex >= revealedFields[columnIndex].length) {
            return false;
        }
        return revealedFields[columnIndex][clueIndex];
    }

    public boolean isColumnSolved(int columnIndex) {
        return columnIndex >= 0
                && columnIndex < solvedColumns.length
                && solvedColumns[columnIndex];
    }

    public boolean isFieldOpenedThisTurn() {
        return fieldOpenedThisTurn;
    }

    public boolean isMyTurn() {
        return myTurn;
    }

    public boolean isInputsEnabled() {
        return inputsEnabled;
    }

    public boolean isFinalAnswerSolved() {
        return finalAnswerSolved;
    }

    public boolean isRoundOver() {
        return roundOver;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    @NonNull
    public static boolean[][] emptyRevealedFields() {
        return new boolean[AssociationPuzzle.COLUMN_COUNT][AssociationColumn.CLUE_COUNT];
    }

    @NonNull
    public static boolean[] emptySolvedColumns() {
        return new boolean[AssociationPuzzle.COLUMN_COUNT];
    }

    @NonNull
    private static boolean[][] copyRevealedFields(@NonNull boolean[][] source) {
        boolean[][] copy = new boolean[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    @NonNull
    private static boolean[] copySolvedColumns(@NonNull boolean[] source) {
        return source.clone();
    }
}
