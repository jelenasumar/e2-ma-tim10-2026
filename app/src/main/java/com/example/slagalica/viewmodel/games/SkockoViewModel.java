package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.data.repository.SkockoRoomRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.skocko.SkockoAttempt;
import com.example.slagalica.model.skocko.SkockoAttemptResult;
import com.example.slagalica.model.skocko.SkockoGameState;
import com.example.slagalica.model.skocko.SkockoSymbol;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class SkockoViewModel extends GameViewModel {

    private static final int COMBINATION_SIZE = 4;
    private static final int MAX_ATTEMPTS = 6;
    private static final int TOTAL_ROUNDS = 2;
    private static final int BONUS_SCORE = 10;
    private static final long ROUND_DURATION_MILLIS = 30_000L;
    private static final long BONUS_DURATION_MILLIS = 10_000L;
    private static final long TIMER_INTERVAL_MILLIS = 1_000L;
    private static final long ROUND_RESULT_VISIBLE_MILLIS = 5_000L;

    private final MutableLiveData<SkockoGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final SkockoRoomRepository skockoRoomRepository = new SkockoRoomRepository();

    private CountDownTimer roundTimer;
    private ListenerRegistration roomListener;
    private ListenerRegistration skockoListener;
    private GameHeaderPlayerState playerOne;
    private GameHeaderPlayerState playerTwo;
    private RoomSession roomSession;
    private List<SkockoAttempt> attempts = new ArrayList<>();
    private List<SkockoSymbol> currentInput = new ArrayList<>();
    private List<SkockoSymbol> bonusInput = new ArrayList<>();
    private List<SkockoSymbol> secretCombination = new ArrayList<>();
    private String roomId = "";
    private String myUid = "";
    private String activePlayerUid = "";
    private String bonusPlayerUid = "";
    private String phase = SkockoRoomRepository.PHASE_ROUND;
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
    private boolean bonusPhase = false;
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean roomMode = false;
    private int statsExactMatches = 0;
    private int statsTotalSlots = 0;
    private int statsLastRecordedRound = 0;

    public SkockoViewModel(@NonNull Application application) {
        super(application);
    }

    @NonNull
    public LiveData<SkockoGameState> getGameState() {
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
        startRound(1);
    }

    public void startRoomGame(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(this.roomId)) {
            return;
        }

        roomMode = true;
        this.roomId = roomId;
        String uid = skockoRoomRepository.getCurrentUid();
        myUid = uid != null ? uid : "";
        roomListener = roomRepository.listenRoom(
                roomId,
                this::onRoomChanged,
                errorMessage::setValue
        );
    }

    public void selectSymbol(@NonNull SkockoSymbol symbol) {
        if (roundOver || gameOver || !canCurrentUserPlay()) {
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
        if (roundOver || gameOver || bonusPhase || row != attempts.size() || !canCurrentUserPlay()) {
            return;
        }

        if (index >= 0 && index < currentInput.size()) {
            currentInput.remove(index);
            publishGameState();
        }
    }

    public void removeBonusInputAt(int index) {
        if (roundOver || gameOver || !bonusPhase || !canCurrentUserPlay()) {
            return;
        }

        if (index >= 0 && index < bonusInput.size()) {
            bonusInput.remove(index);
            publishGameState();
        }
    }

    public void submitAttempt() {
        if (roundOver || gameOver || !canCurrentUserPlay()) {
            return;
        }

        if (bonusPhase) {
            if (roomMode) {
                submitRoomBonusAttempt();
                return;
            }
            submitBonusAttempt();
            return;
        }

        if (currentInput.size() != COMBINATION_SIZE) {
            return;
        }
        if (roomMode) {
            submitRoomRoundAttempt();
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

    public int getCurrentUserScore() {
        if (!roomMode) {
            return playerOneScore;
        }
        if (roomSession == null || myUid.isEmpty()) {
            return 0;
        }
        if (myUid.equals(roomSession.getHostUid())) {
            return playerOneScore;
        }
        if (myUid.equals(roomSession.getGuestUid())) {
            return playerTwoScore;
        }
        return 0;
    }

    public int getPlayerOneScore() {
        return playerOneScore;
    }

    public int getPlayerTwoScore() {
        return playerTwoScore;
    }

    public int getCurrentUserGameScore() {
        if (!roomMode) {
            return playerOneScore;
        }
        if (roomSession == null || myUid.isEmpty()) {
            return 0;
        }
        if (myUid.equals(roomSession.getHostUid())) {
            return playerOneScore - basePlayerOneScore;
        }
        if (myUid.equals(roomSession.getGuestUid())) {
            return playerTwoScore - basePlayerTwoScore;
        }
        return 0;
    }

    public float getStatsComboPercent() {
        if (statsTotalSlots == 0) {
            return 0f;
        }
        return (statsExactMatches * 100f) / statsTotalSlots;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopRoundTimer();
        handler.removeCallbacksAndMessages(null);
        if (roomListener != null) {
            roomListener.remove();
        }
        if (skockoListener != null) {
            skockoListener.remove();
        }
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;
        basePlayerOneScore = room.getHostTotalScore();
        basePlayerTwoScore = room.getGuestTotalScore();
        applyRoomPlayers(room, playerOneScore, playerTwoScore);
        if (skockoListener == null) {
            skockoListener = skockoRoomRepository.listenState(
                    room.getRoomId(),
                    this::onRemoteStateChanged,
                    errorMessage::setValue
            );
        }
        skockoRoomRepository.initializeIfNeeded(
                room,
                generateSecretCombination(),
                errorMessage::setValue
        );
    }

    private void onRemoteStateChanged(@NonNull DocumentSnapshot snapshot) {
        String previousPhase = phase;
        int previousAttemptsCount = attempts.size();

        currentRound = intOrDefault(snapshot.get("currentRound"), 1);
        activePlayerNumber = intOrDefault(snapshot.get("activePlayerNumber"), currentRound);
        playerOneScore = intOrDefault(snapshot.get("playerOneScore"), 0);
        playerTwoScore = intOrDefault(snapshot.get("playerTwoScore"), 0);
        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        bonusPlayerUid = stringOrEmpty(snapshot.getString("bonusPlayerUid"));
        phase = stringOrDefault(snapshot.getString("phase"), SkockoRoomRepository.PHASE_ROUND);
        bonusPhase = SkockoRoomRepository.PHASE_BONUS.equals(phase);
        roundOver = SkockoRoomRepository.PHASE_ROUND_OVER.equals(phase)
                || SkockoRoomRepository.PHASE_GAME_OVER.equals(phase);
        gameOver = SkockoRoomRepository.PHASE_GAME_OVER.equals(phase);
        attempts = parseAttempts(snapshot.get("attempts"));
        secretCombination = parseSymbols(snapshot.get("secretCombination"));

        if (!phase.equals(previousPhase) || attempts.size() != previousAttemptsCount) {
            currentInput = new ArrayList<>();
            if (!bonusPhase) {
                bonusInput = new ArrayList<>();
            }
        }
        if (!bonusPhase && !roundOver) {
            bonusInput = new ArrayList<>();
        }
        if (roundOver) {
            bonusInput = parseSymbols(snapshot.get("bonusAttempt"));
        }
        recordRoundStatsIfNeeded();

        applyRoomPlayersFromState(snapshot);
        initializeHeader(
                formatRoundText(currentRound, TOTAL_ROUNDS),
                formatTimeText(cappedRemainingPhaseMillis(longOrZero(snapshot.get("phaseEndsAtMillis")))),
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore)
        );
        updateActivePlayer(roundOver || gameOver ? 0 : (bonusPhase ? bonusPlayerNumber() : activePlayerNumber));
        publishGameState();
        startRemotePhaseTimer(longOrZero(snapshot.get("phaseEndsAtMillis")));
    }

    private void applyRoomPlayers(@NonNull RoomSession room, int firstScore, int secondScore) {
        playerOne = playerOneWithAvatars(room.getHostUsername(), firstScore);
        playerTwo = playerTwoWithAvatars(room.getGuestUsername(), secondScore);
        ensurePlayerAvatars(myUid, room.getHostUid(), room.getGuestUid(), this::refreshHeaderAvatars);
    }

    private void applyRoomPlayersFromState(@NonNull DocumentSnapshot snapshot) {
        String firstName = stringOrDefault(snapshot.getString("playerOneUsername"), "Igrac 1");
        String secondName = stringOrDefault(snapshot.getString("playerTwoUsername"), "Igrac 2");
        playerOne = playerOneWithAvatars(firstName, playerOneScore);
        playerTwo = playerTwoWithAvatars(secondName, playerTwoScore);
        if (roomSession != null) {
            ensurePlayerAvatars(
                    myUid,
                    roomSession.getHostUid(),
                    roomSession.getGuestUid(),
                    this::refreshHeaderAvatars
            );
        }
    }

    private void refreshHeaderAvatars() {
        if (playerOne == null || playerTwo == null) {
            return;
        }
        playerOne = playerOneWithAvatars(playerOne.getUsername(), playerOne.getScore());
        playerTwo = playerTwoWithAvatars(playerTwo.getUsername(), playerTwo.getScore());
        GameHeaderState currentState = getHeaderState().getValue();
        if (currentState != null) {
            setHeaderState(new GameHeaderState(
                    currentState.getRoundText(),
                    currentState.getTimeText(),
                    playerOne,
                    playerTwo,
                    currentState.getActivePlayerNumber()
            ));
        }
    }

    private void submitRoomRoundAttempt() {
        skockoRoomRepository.submitRoundAttempt(
                roomId,
                myUid,
                currentInput,
                errorMessage::setValue
        );
    }

    private void submitRoomBonusAttempt() {
        if (bonusInput.size() != COMBINATION_SIZE) {
            return;
        }
        skockoRoomRepository.submitBonusAttempt(
                roomId,
                myUid,
                bonusInput,
                errorMessage::setValue
        );
    }

    private void startRemotePhaseTimer(long phaseEndsAtMillis) {
        stopRoundTimer();
        if (gameOver || phaseEndsAtMillis <= 0L) {
            updateTime(formatTimeText(0));
            return;
        }

        long remaining = cappedRemainingPhaseMillis(phaseEndsAtMillis);
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

        updateTime(formatTimeText(remaining));
        roundTimer = new CountDownTimer(remaining, TIMER_INTERVAL_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTime(formatTimeText(millisUntilFinished));
            }

            @Override
            public void onFinish() {
                updateTime(formatTimeText(0));
                expireRemotePhase();
            }
        };
        roundTimer.start();
    }

    private void expireRemotePhase() {
        if (!roomMode || roomId.isEmpty()) {
            return;
        }
        skockoRoomRepository.handleExpiredPhase(
                roomId,
                generateSecretCombination(),
                errorMessage::setValue
        );
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
        updateActivePlayer(activePlayerNumber);
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
        updateActivePlayer(getBonusPlayerNumber());
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
        recordRoundStatsIfNeeded();
        updateTime(formatTimeText(0));
        updateActivePlayer(0);
        updateScores(playerOneScore, playerTwoScore);
        publishGameState();

        startResultTimer(() -> {
            if (currentRound < TOTAL_ROUNDS) {
                startRound(currentRound + 1);
            } else {
                gameOver = true;
                publishGameState();
            }
        });
    }

    private void startResultTimer(@NonNull Runnable onFinish) {
        stopRoundTimer();
        updateTime(formatTimeText(ROUND_RESULT_VISIBLE_MILLIS));
        roundTimer = new CountDownTimer(ROUND_RESULT_VISIBLE_MILLIS, TIMER_INTERVAL_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTime(formatTimeText(millisUntilFinished));
            }

            @Override
            public void onFinish() {
                updateTime(formatTimeText(0));
                onFinish.run();
            }
        };
        roundTimer.start();
    }

    private void startRoundTimer() {
        stopRoundTimer();
        updateTime(formatTimeText(ROUND_DURATION_MILLIS));
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
        updateTime(formatTimeText(BONUS_DURATION_MILLIS));
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

    private void recordRoundStatsIfNeeded() {
        if (!roundOver || currentRound <= statsLastRecordedRound) {
            return;
        }
        statsLastRecordedRound = currentRound;

        if (isCurrentUserActiveRoundPlayer()) {
            for (SkockoAttempt attempt : attempts) {
                statsExactMatches += attempt.getResult().getExactMatches();
                statsTotalSlots += COMBINATION_SIZE;
            }
        }

        if (bonusInput.size() == COMBINATION_SIZE && isCurrentUserBonusPlayer()) {
            SkockoAttemptResult result = evaluateAttempt(bonusInput, secretCombination);
            statsExactMatches += result.getExactMatches();
            statsTotalSlots += COMBINATION_SIZE;
        }
    }

    private boolean isCurrentUserActiveRoundPlayer() {
        if (!roomMode) {
            return activePlayerNumber == 1;
        }
        return !myUid.isEmpty() && myUid.equals(activePlayerUid);
    }

    private boolean isCurrentUserBonusPlayer() {
        if (!roomMode) {
            return getBonusPlayerNumber() == 1;
        }
        return !myUid.isEmpty() && myUid.equals(bonusPlayerUid);
    }

    private static long remainingPhaseMillis(long phaseEndsAtMillis) {
        return Math.max(0L, phaseEndsAtMillis - System.currentTimeMillis());
    }

    private long cappedRemainingPhaseMillis(long phaseEndsAtMillis) {
        long remaining = remainingPhaseMillis(phaseEndsAtMillis);
        long maxDuration = currentPhaseDurationMillis();
        return maxDuration > 0L ? Math.min(remaining, maxDuration) : remaining;
    }

    private long currentPhaseDurationMillis() {
        if (SkockoRoomRepository.PHASE_ROUND.equals(phase)) {
            return ROUND_DURATION_MILLIS;
        }
        if (SkockoRoomRepository.PHASE_BONUS.equals(phase)) {
            return BONUS_DURATION_MILLIS;
        }
        if (SkockoRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            return ROUND_RESULT_VISIBLE_MILLIS;
        }
        return 0L;
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

    private int getBonusPlayerNumber() {
        return activePlayerNumber == 1 ? 2 : 1;
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

    private boolean canCurrentUserPlay() {
        if (!roomMode) {
            return true;
        }
        if (bonusPhase) {
            return !myUid.isEmpty() && myUid.equals(bonusPlayerUid);
        }
        return !myUid.isEmpty() && myUid.equals(activePlayerUid);
    }

    private int bonusPlayerNumber() {
        if (roomSession == null) {
            return activePlayerNumber == 1 ? 2 : 1;
        }
        return bonusPlayerUid.equals(roomSession.getHostUid()) ? 1 : 2;
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
        long totalSeconds = Math.max(0, millis / 1000L);
        return String.format(Locale.getDefault(), "Preostalo vreme: 00:%02d", totalSeconds);
    }

    @NonNull
    private static List<SkockoAttempt> parseAttempts(@Nullable Object raw) {
        List<SkockoAttempt> parsedAttempts = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (!(value instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) value;
                List<SkockoSymbol> symbols = parseSymbols(map.get("symbols"));
                int exact = intOrDefault(map.get("exactMatches"), 0);
                int partial = intOrDefault(map.get("partialMatches"), 0);
                if (symbols.size() == COMBINATION_SIZE) {
                    parsedAttempts.add(new SkockoAttempt(symbols, new SkockoAttemptResult(exact, partial)));
                }
            }
        }
        return parsedAttempts;
    }

    @NonNull
    private static List<SkockoSymbol> parseSymbols(@Nullable Object raw) {
        List<SkockoSymbol> symbols = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value == null) {
                    continue;
                }
                try {
                    symbols.add(SkockoSymbol.valueOf(String.valueOf(value)));
                } catch (IllegalArgumentException ignored) {
                    // Unknown remote values are skipped so the UI can keep rendering valid cells.
                }
            }
        }
        return symbols;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static int intOrDefault(@Nullable Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return fallback;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
