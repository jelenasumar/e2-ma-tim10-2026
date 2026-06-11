package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.KorakPoKorakPuzzlesRepository;
import com.example.slagalica.model.korakpokorak.KorakPoKorakPuzzle;
import com.example.slagalica.model.korakpokorak.KorakPoKorakUiState;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class StepByStepViewModel extends AndroidViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final int STEP_COUNT = 7;
    private static final int BONUS_POINTS = 5;
    private static final int[] STEP_POINTS = {20, 18, 16, 14, 12, 10, 8};

    private static final long STEP_DURATION_MS = 10_000L;
    private static final long BONUS_DURATION_MS = 10_000L;
    private static final long RESULT_VISIBLE_MS = 2_000L;
    private static final long TIMER_INTERVAL_MS = 1_000L;

    private final MutableLiveData<KorakPoKorakUiState> uiState = new MutableLiveData<>();
    private final KorakPoKorakPuzzlesRepository puzzlesRepository = new KorakPoKorakPuzzlesRepository();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();

    private CountDownTimer timer;
    private List<KorakPoKorakPuzzle> roundPuzzles = new ArrayList<>();

    private KorakPoKorakPuzzle currentPuzzle;
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int answeringPlayerNumber = 1;
    private int currentStepIndex = 0;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;

    private boolean bonusPhase = false;
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean started = false;

    private String lastStatusMessage = "";

    private int playerOneOwnRoundSolvedStepIndex = -1;

    public StepByStepViewModel(@NonNull Application application) {
        super(application);
    }

    @NonNull
    public LiveData<KorakPoKorakUiState> getUiState() {
        return uiState;
    }

    public void startGame() {
        if (started) {
            return;
        }
        started = true;

        playerOneOwnRoundSolvedStepIndex = -1;

        puzzlesRepository.loadPuzzles(
                puzzles -> {
                    List<KorakPoKorakPuzzle> source = puzzles.isEmpty()
                            ? KorakPoKorakPuzzle.defaultPuzzles()
                            : puzzles;
                    preparePuzzles(source);
                    startRound(1);
                },
                error -> {
                    preparePuzzles(KorakPoKorakPuzzle.defaultPuzzles());
                    startRound(1);
                }
        );
    }

    public void submitAnswer(@NonNull String answer) {
        if (currentPuzzle == null || gameOver || roundOver || answer.trim().isEmpty()) {
            return;
        }

        if (answersMatch(answer, currentPuzzle.getAnswer())) {
            if (bonusPhase) {
                addScore(answeringPlayerNumber, BONUS_POINTS);
                finishRound("Tacno. Igrac " + answeringPlayerNumber + " osvaja 5 bonus bodova.");
            } else {
                int points = STEP_POINTS[currentStepIndex];
                addScore(activePlayerNumber, points);

                if (activePlayerNumber == 1) {
                    playerOneOwnRoundSolvedStepIndex = currentStepIndex;
                }

                finishRound("Tacno. Igrac " + activePlayerNumber + " osvaja " + points + " bodova.");
            }
            return;
        }

        if (bonusPhase) {
            finishRound("Netacno. Bonus sansa nije iskoriscena.");
        } else {
            publishState(remainingSeconds(), "Netacno. Pokusaj ponovo.");
        }
    }

    private void preparePuzzles(@NonNull List<KorakPoKorakPuzzle> puzzles) {
        roundPuzzles = new ArrayList<>(puzzles);
        Collections.shuffle(roundPuzzles, random);

        while (roundPuzzles.size() < TOTAL_ROUNDS) {
            roundPuzzles.addAll(KorakPoKorakPuzzle.defaultPuzzles());
        }
    }

    private void startRound(int roundNumber) {
        stopTimer();

        currentRound = roundNumber;
        activePlayerNumber = roundNumber == 1 ? 1 : 2;
        answeringPlayerNumber = activePlayerNumber;
        currentStepIndex = 0;
        bonusPhase = false;
        roundOver = false;
        gameOver = false;
        currentPuzzle = roundPuzzles.get(roundNumber - 1);

        publishState(secondsFromMillis(STEP_DURATION_MS),
                "Runda " + currentRound + ". Igra igrac " + activePlayerNumber + ".");
        startStepTimer();
    }

    private void startStepTimer() {
        stopTimer();

        timer = new CountDownTimer(STEP_DURATION_MS, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                publishState(secondsFromMillis(millisUntilFinished), lastStatusMessage);
            }

            @Override
            public void onFinish() {
                openNextStepOrBonus();
            }
        };
        timer.start();
    }

    private void openNextStepOrBonus() {
        if (currentStepIndex < STEP_COUNT - 1) {
            currentStepIndex++;
            publishState(secondsFromMillis(STEP_DURATION_MS),
                    "Otvoren je korak " + (currentStepIndex + 1) + ".");
            startStepTimer();
            return;
        }

        startBonusPhase();
    }

    private void startBonusPhase() {
        stopTimer();

        bonusPhase = true;
        answeringPlayerNumber = activePlayerNumber == 1 ? 2 : 1;

        publishState(secondsFromMillis(BONUS_DURATION_MS),
                "Igrac " + activePlayerNumber + " nije pogodio. Bonus sansa za igraca "
                        + answeringPlayerNumber + ".");

        timer = new CountDownTimer(BONUS_DURATION_MS, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                publishState(secondsFromMillis(millisUntilFinished), lastStatusMessage);
            }

            @Override
            public void onFinish() {
                finishRound("Vreme za bonus je isteklo.");
            }
        };
        timer.start();
    }

    private void finishRound(@NonNull String message) {
        stopTimer();

        roundOver = true;
        bonusPhase = false;
        publishState(0, message + " Resenje: " + currentPuzzle.getAnswer());

        handler.postDelayed(() -> {
            if (currentRound < TOTAL_ROUNDS) {
                startRound(currentRound + 1);
            } else {
                finishGame();
            }
        }, RESULT_VISIBLE_MS);
    }

    private void finishGame() {
        stopTimer();

        gameOver = true;
        roundOver = true;
        bonusPhase = false;

        publishState(0, "Kraj igre. Igrac 1: " + playerOneScore
                + ", Igrac 2: " + playerTwoScore + ".");
    }

    private void addScore(int playerNumber, int points) {
        if (playerNumber == 1) {
            playerOneScore += points;
        } else {
            playerTwoScore += points;
        }
    }

    private void publishState(int secondsLeft, @NonNull String statusMessage) {
        if (currentPuzzle == null) {
            return;
        }

        if (!statusMessage.isEmpty()) {
            lastStatusMessage = statusMessage;
        }

        List<String> visibleSteps = new ArrayList<>();
        for (int i = 0; i <= currentStepIndex && i < STEP_COUNT; i++) {
            visibleSteps.add(currentPuzzle.getStep(i));
        }

        uiState.setValue(new KorakPoKorakUiState(
                currentRound,
                TOTAL_ROUNDS,
                activePlayerNumber,
                answeringPlayerNumber,
                playerOneScore,
                playerTwoScore,
                secondsLeft,
                visibleSteps,
                currentStepIndex,
                bonusPhase,
                roundOver,
                gameOver,
                !roundOver && !gameOver,
                lastStatusMessage
        ));
    }

    private int remainingSeconds() {
        KorakPoKorakUiState state = uiState.getValue();
        return state != null ? state.getSecondsLeft() : 0;
    }

    private void stopTimer() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }

    private static int secondsFromMillis(long millis) {
        return (int) Math.max(0, Math.ceil(millis / 1000.0));
    }

    private static boolean answersMatch(@NonNull String guess, @NonNull String answer) {
        return normalize(guess).equals(normalize(answer));
    }

    @NonNull
    private static String normalize(@NonNull String value) {
        String normalized = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return normalized.toLowerCase(Locale.ROOT);
    }

    @Override
    protected void onCleared() {
        stopTimer();
        handler.removeCallbacksAndMessages(null);
        super.onCleared();
    }

    public int getPlayerOneScore() {
        return playerOneScore;
    }

    public int getPlayerOneOwnRoundSolvedStepIndex() {
        return playerOneOwnRoundSolvedStepIndex;
    }

}