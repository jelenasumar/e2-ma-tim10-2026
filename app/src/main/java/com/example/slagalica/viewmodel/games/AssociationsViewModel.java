package com.example.slagalica.viewmodel.games;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.associations.AssociationColumn;
import com.example.slagalica.model.associations.AssociationPuzzle;
import com.example.slagalica.model.associations.AssociationsGameState;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AssociationsViewModel extends GameViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final long ROUND_DURATION_MILLIS = 120_000L;
    private static final long TIMER_INTERVAL_MILLIS = 1_000L;
    private static final long ROUND_RESULT_VISIBLE_MILLIS = 2_500L;
    private static final int COLUMN_BASE_SCORE = 2;
    private static final int FINAL_BASE_SCORE = 7;
    private static final int UNOPENED_COLUMN_SCORE = 6;

    private final MutableLiveData<AssociationsGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private CountDownTimer roundTimer;
    private GameHeaderPlayerState playerOne;
    private GameHeaderPlayerState playerTwo;
    private List<AssociationPuzzle> roundPuzzles = new ArrayList<>();
    private AssociationPuzzle currentPuzzle;
    private boolean[][] revealedFields = AssociationsGameState.emptyRevealedFields();
    private boolean[] solvedColumns = AssociationsGameState.emptySolvedColumns();
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private boolean fieldOpenedThisTurn = false;
    private boolean finalAnswerSolved = false;
    private boolean roundOver = false;
    private boolean gameOver = false;

    @NonNull
    public LiveData<AssociationsGameState> getGameState() {
        return gameState;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void startGame(
            @NonNull GameHeaderPlayerState playerOne,
            @NonNull GameHeaderPlayerState playerTwo
    ) {
        if (this.playerOne != null && this.playerTwo != null) {
            return;
        }

        this.playerOne = playerOne;
        this.playerTwo = playerTwo;
        roundPuzzles = shuffledPuzzles();
        startRound(1);
    }

    public void openField(int columnIndex, int clueIndex) {
        if (roundOver || gameOver) {
            return;
        }
        if (fieldOpenedThisTurn) {
            errorMessage.setValue("Vec je otvoreno jedno polje u ovom potezu.");
            return;
        }
        if (!isValidField(columnIndex, clueIndex)) {
            errorMessage.setValue("Polje ne postoji.");
            return;
        }
        if (solvedColumns[columnIndex]) {
            errorMessage.setValue("Kolona je vec resena.");
            return;
        }
        if (revealedFields[columnIndex][clueIndex]) {
            errorMessage.setValue("Polje je vec otvoreno.");
            return;
        }

        revealedFields[columnIndex][clueIndex] = true;
        fieldOpenedThisTurn = true;
        publishGameState();
    }

    public void submitColumnGuess(int columnIndex, @NonNull String guess) {
        if (roundOver || gameOver) {
            return;
        }
        if (!fieldOpenedThisTurn) {
            errorMessage.setValue("Prvo otvori jedno polje.");
            return;
        }
        if (!isValidColumn(columnIndex)) {
            errorMessage.setValue("Kolona ne postoji.");
            return;
        }
        if (solvedColumns[columnIndex]) {
            errorMessage.setValue("Kolona je vec resena.");
            return;
        }
        if (currentPuzzle == null) {
            return;
        }

        String answer = currentPuzzle.getColumn(columnIndex).getAnswer();
        if (answersMatch(guess, answer)) {
            solvedColumns[columnIndex] = true;
            addScoreForActivePlayer(scoreForColumn(columnIndex));
            updateScores(playerOneScore, playerTwoScore);
            publishGameState();
            return;
        }

        switchActivePlayer();
    }

    public void submitFinalGuess(@NonNull String guess) {
        if (roundOver || gameOver || currentPuzzle == null) {
            return;
        }
        if (!fieldOpenedThisTurn) {
            errorMessage.setValue("Prvo otvori jedno polje.");
            return;
        }

        if (answersMatch(guess, currentPuzzle.getFinalAnswer())) {
            finalAnswerSolved = true;
            addScoreForActivePlayer(scoreForFinalAnswer());
            completeRound();
            return;
        }

        switchActivePlayer();
    }

    public void finishTurn() {
        if (roundOver || gameOver) {
            return;
        }
        switchActivePlayer();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopRoundTimer();
        handler.removeCallbacksAndMessages(null);
    }

    private void startRound(int roundNumber) {
        currentRound = roundNumber;
        activePlayerNumber = roundNumber;
        currentPuzzle = puzzleForRound(roundNumber);
        revealedFields = AssociationsGameState.emptyRevealedFields();
        solvedColumns = AssociationsGameState.emptySolvedColumns();
        fieldOpenedThisTurn = false;
        finalAnswerSolved = false;
        roundOver = false;
        gameOver = false;

        initializeHeader(
                formatRoundText(currentRound, TOTAL_ROUNDS),
                formatTimeText(ROUND_DURATION_MILLIS),
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore)
        );
        updateActivePlayer(activePlayerNumber);
        publishGameState();
        startRoundTimer();
    }

    private void completeRound() {
        if (roundOver || gameOver) {
            return;
        }

        stopRoundTimer();
        roundOver = true;
        updateTime(formatTimeText(0));
        updateActivePlayer(0);
        updateScores(playerOneScore, playerTwoScore);
        publishGameState();

        if (currentRound < TOTAL_ROUNDS) {
            handler.postDelayed(
                    () -> startRound(currentRound + 1),
                    ROUND_RESULT_VISIBLE_MILLIS
            );
        } else {
            gameOver = true;
            publishGameState();
        }
    }

    private void startRoundTimer() {
        stopRoundTimer();
        roundTimer = new CountDownTimer(ROUND_DURATION_MILLIS, TIMER_INTERVAL_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTime(formatTimeText(millisUntilFinished));
            }

            @Override
            public void onFinish() {
                completeRound();
            }
        };
        roundTimer.start();
    }

    private void stopRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    private void switchActivePlayer() {
        activePlayerNumber = activePlayerNumber == 1 ? 2 : 1;
        fieldOpenedThisTurn = false;
        updateActivePlayer(activePlayerNumber);
        publishGameState();
    }

    private void addScoreForActivePlayer(int score) {
        if (activePlayerNumber == 1) {
            playerOneScore += score;
        } else {
            playerTwoScore += score;
        }
    }

    private int scoreForColumn(int columnIndex) {
        return COLUMN_BASE_SCORE + unopenedFieldsInColumn(columnIndex);
    }

    private int scoreForFinalAnswer() {
        int score = FINAL_BASE_SCORE;
        for (int columnIndex = 0; columnIndex < AssociationPuzzle.COLUMN_COUNT; columnIndex++) {
            if (isColumnCompletelyUnopened(columnIndex)) {
                score += UNOPENED_COLUMN_SCORE;
            } else {
                score += scoreForColumn(columnIndex);
            }
        }
        return score;
    }

    private boolean isColumnCompletelyUnopened(int columnIndex) {
        if (solvedColumns[columnIndex]) {
            return false;
        }
        for (int clueIndex = 0; clueIndex < AssociationColumn.CLUE_COUNT; clueIndex++) {
            if (revealedFields[columnIndex][clueIndex]) {
                return false;
            }
        }
        return true;
    }

    private int unopenedFieldsInColumn(int columnIndex) {
        int opened = 0;
        for (int clueIndex = 0; clueIndex < AssociationColumn.CLUE_COUNT; clueIndex++) {
            if (revealedFields[columnIndex][clueIndex]) {
                opened++;
            }
        }
        return AssociationColumn.CLUE_COUNT - opened;
    }

    private void publishGameState() {
        if (currentPuzzle == null) {
            return;
        }
        gameState.setValue(new AssociationsGameState(
                currentRound,
                activePlayerNumber,
                playerOneScore,
                playerTwoScore,
                currentPuzzle,
                revealedFields,
                solvedColumns,
                fieldOpenedThisTurn,
                finalAnswerSolved,
                roundOver,
                gameOver
        ));
    }

    @NonNull
    private List<AssociationPuzzle> shuffledPuzzles() {
        List<AssociationPuzzle> puzzles = new ArrayList<>(AssociationPuzzle.defaultPuzzles());
        Collections.shuffle(puzzles, random);
        return puzzles;
    }

    @NonNull
    private AssociationPuzzle puzzleForRound(int roundNumber) {
        if (roundPuzzles.isEmpty()) {
            roundPuzzles = shuffledPuzzles();
        }
        int index = Math.max(0, (roundNumber - 1) % roundPuzzles.size());
        return roundPuzzles.get(index);
    }

    private static boolean isValidField(int columnIndex, int clueIndex) {
        return isValidColumn(columnIndex)
                && clueIndex >= 0
                && clueIndex < AssociationColumn.CLUE_COUNT;
    }

    private static boolean isValidColumn(int columnIndex) {
        return columnIndex >= 0 && columnIndex < AssociationPuzzle.COLUMN_COUNT;
    }

    private static boolean answersMatch(
            @NonNull String guess,
            @NonNull String answer
    ) {
        return normalizeAnswer(guess).equals(normalizeAnswer(answer));
    }

    @NonNull
    private static String normalizeAnswer(@NonNull String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static String formatRoundText(int roundNumber, int totalRounds) {
        return String.format(Locale.getDefault(), "Runda: %d/%d", roundNumber, totalRounds);
    }

    @NonNull
    private static String formatTimeText(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.getDefault(), "Preostalo vreme: %02d:%02d", minutes, seconds);
    }
}
