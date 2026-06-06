package com.example.slagalica.viewmodel.games;

import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.skocko.SkockoAttempt;
import com.example.slagalica.model.skocko.SkockoAttemptResult;
import com.example.slagalica.model.skocko.SkockoGameState;
import com.example.slagalica.model.skocko.SkockoSymbol;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SkockoViewModel extends GameViewModel {

    private static final int COMBINATION_SIZE = 4;
    private static final int MAX_ATTEMPTS = 6;
    private static final int TOTAL_ROUNDS = 2;
    private static final int BONUS_SCORE = 10;
    private static final long ROUND_DURATION_MILLIS = 30_000L;
    private static final long BONUS_DURATION_MILLIS = 10_000L;
    private static final long TIMER_INTERVAL_MILLIS = 1_000L;
    private static final long ROUND_RESULT_VISIBLE_MILLIS = 2_500L;

    private final MutableLiveData<SkockoGameState> gameState = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private CountDownTimer roundTimer;
    private GameHeaderPlayerState playerOne;
    private GameHeaderPlayerState playerTwo;
    private List<SkockoAttempt> attempts = new ArrayList<>();
    private List<SkockoSymbol> currentInput = new ArrayList<>();
    private List<SkockoSymbol> bonusInput = new ArrayList<>();
    private List<SkockoSymbol> secretCombination = new ArrayList<>();
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private boolean bonusPhase = false;
    private boolean roundOver = false;
    private boolean gameOver = false;

    @NonNull
    public LiveData<SkockoGameState> getGameState() {
        return gameState;
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
        startRound(1);
    }

    public void selectSymbol(@NonNull SkockoSymbol symbol) {
        if (roundOver || gameOver) {
            return;
        }

        if (bonusPhase) {
            if (bonusInput.size() >= COMBINATION_SIZE) {
                return;
            }
            bonusInput.add(symbol);
            publishGameState();
            return;
        }

        if (currentInput.size() >= COMBINATION_SIZE) {
            return;
        }
        currentInput.add(symbol);
        publishGameState();
    }

    public void removeInputAt(int row, int index) {
        if (roundOver || gameOver || bonusPhase || row != attempts.size()) {
            return;
        }

        if (index >= 0 && index < currentInput.size()) {
            currentInput.remove(index);
            publishGameState();
        }
    }

    public void removeBonusInputAt(int index) {
        if (roundOver || gameOver || !bonusPhase) {
            return;
        }

        if (index >= 0 && index < bonusInput.size()) {
            bonusInput.remove(index);
            publishGameState();
        }
    }

    public void submitAttempt() {
        if (roundOver || gameOver) {
            return;
        }

        if (bonusPhase) {
            submitBonusAttempt();
            return;
        }

        if (currentInput.size() != COMBINATION_SIZE) {
            return;
        }
        SkockoAttemptResult result = evaluateAttempt(currentInput, secretCombination);
        attempts.add(new SkockoAttempt(currentInput, result));
        currentInput = new ArrayList<>();

        if (result.isSolved()) {
            addScoreForActivePlayer(scoreForAttempt(attempts.size()));
            completeRound();
            return;
        }

        if (attempts.size() >= MAX_ATTEMPTS) {
            startBonusAttempt();
            return;
        }

        publishGameState();
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
        attempts = new ArrayList<>();
        currentInput = new ArrayList<>();
        bonusInput = new ArrayList<>();
        secretCombination = generateSecretCombination();
        bonusPhase = false;
        roundOver = false;
        gameOver = false;

        initializeHeader(
                formatRoundText(currentRound, TOTAL_ROUNDS),
                formatTimeText(ROUND_DURATION_MILLIS),
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore)
        );
        publishGameState();
        startRoundTimer();
    }

    private void submitBonusAttempt() {
        if (bonusInput.size() != COMBINATION_SIZE) {
            return;
        }

        SkockoAttemptResult result = evaluateAttempt(bonusInput, secretCombination);
        if (result.isSolved()) {
            addScoreForBonusPlayer();
        }
        completeRound();
    }

    private void startBonusAttempt() {
        stopRoundTimer();
        bonusPhase = true;
        bonusInput = new ArrayList<>();
        updateTime(formatTimeText(BONUS_DURATION_MILLIS));
        publishGameState();
        startBonusTimer();
    }

    private void completeRound() {
        if (roundOver || gameOver) {
            return;
        }

        stopRoundTimer();
        bonusPhase = false;
        roundOver = true;
        updateTime(formatTimeText(0));
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
                startBonusAttempt();
            }
        };
        roundTimer.start();
    }

    private void startBonusTimer() {
        stopRoundTimer();
        roundTimer = new CountDownTimer(BONUS_DURATION_MILLIS, TIMER_INTERVAL_MILLIS) {
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

    private void addScoreForActivePlayer(int score) {
        if (activePlayerNumber == 1) {
            playerOneScore += score;
        } else {
            playerTwoScore += score;
        }
    }

    private void addScoreForBonusPlayer() {
        if (activePlayerNumber == 1) {
            playerTwoScore += BONUS_SCORE;
        } else {
            playerOneScore += BONUS_SCORE;
        }
    }

    private void publishGameState() {
        gameState.setValue(new SkockoGameState(
                currentRound,
                activePlayerNumber,
                attempts,
                currentInput,
                bonusInput,
                secretCombination,
                bonusPhase,
                roundOver,
                gameOver
        ));
    }

    @NonNull
    private List<SkockoSymbol> generateSecretCombination() {
        List<SkockoSymbol> combination = new ArrayList<>();
        SkockoSymbol[] symbols = SkockoSymbol.values();
        for (int i = 0; i < COMBINATION_SIZE; i++) {
            combination.add(symbols[random.nextInt(symbols.length)]);
        }
        return combination;
    }

    @NonNull
    private static SkockoAttemptResult evaluateAttempt(
            @NonNull List<SkockoSymbol> attempt,
            @NonNull List<SkockoSymbol> secret
    ) {
        int exact = 0;
        int partial = 0;
        boolean[] usedAttempt = new boolean[COMBINATION_SIZE];
        boolean[] usedSecret = new boolean[COMBINATION_SIZE];

        for (int i = 0; i < COMBINATION_SIZE; i++) {
            if (attempt.get(i) == secret.get(i)) {
                exact++;
                usedAttempt[i] = true;
                usedSecret[i] = true;
            }
        }

        for (int i = 0; i < COMBINATION_SIZE; i++) {
            if (usedAttempt[i]) {
                continue;
            }
            for (int j = 0; j < COMBINATION_SIZE; j++) {
                if (!usedSecret[j] && attempt.get(i) == secret.get(j)) {
                    partial++;
                    usedSecret[j] = true;
                    break;
                }
            }
        }

        return new SkockoAttemptResult(exact, partial);
    }

    private static int scoreForAttempt(int attemptNumber) {
        if (attemptNumber <= 2) {
            return 20;
        }
        if (attemptNumber <= 4) {
            return 15;
        }
        return 10;
    }

    @NonNull
    private static String formatRoundText(int roundNumber, int totalRounds) {
        return String.format(Locale.getDefault(), "Runda: %d/%d", roundNumber, totalRounds);
    }

    @NonNull
    private static String formatTimeText(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "Preostalo vreme: 00:%02d", totalSeconds);
    }
}
