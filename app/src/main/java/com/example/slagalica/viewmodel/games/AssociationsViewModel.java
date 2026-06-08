package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.AssociationPuzzlesRepository;
import com.example.slagalica.data.repository.AssociationsRoomRepository;
import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.associations.AssociationColumn;
import com.example.slagalica.model.associations.AssociationPuzzle;
import com.example.slagalica.model.associations.AssociationsGameState;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

public class AssociationsViewModel extends GameViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final long ROUND_DURATION_MILLIS = 120_000L;
    private static final long TIMER_INTERVAL_MILLIS = 1_000L;
    private static final long ROUND_RESULT_VISIBLE_MILLIS = 5_000L;
    private static final int COLUMN_BASE_SCORE = 2;
    private static final int FINAL_BASE_SCORE = 7;
    private static final int UNOPENED_COLUMN_SCORE = 6;

    private final MutableLiveData<AssociationsGameState> gameState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final AssociationPuzzlesRepository puzzleRepository = new AssociationPuzzlesRepository();
    private final AssociationsRoomRepository associationsRoomRepository = new AssociationsRoomRepository();

    private CountDownTimer roundTimer;
    private ListenerRegistration roomListener;
    private ListenerRegistration associationsListener;
    private GameHeaderPlayerState playerOne;
    private GameHeaderPlayerState playerTwo;
    private RoomSession roomSession;
    private List<AssociationPuzzle> roundPuzzles = new ArrayList<>();
    private AssociationPuzzle currentPuzzle;
    private boolean[][] revealedFields = AssociationsGameState.emptyRevealedFields();
    private boolean[] solvedColumns = AssociationsGameState.emptySolvedColumns();
    private String roomId = "";
    private String myUid = "";
    private String activePlayerUid = "";
    private String phase = AssociationsRoomRepository.PHASE_ACTIVE;
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private boolean fieldOpenedThisTurn = false;
    private boolean finalAnswerSolved = false;
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean roomMode = false;
    private boolean roomInitializationRequested = false;

    public AssociationsViewModel(@NonNull Application application) {
        super(application);
    }

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
        puzzleRepository.loadPuzzles(
                puzzles -> {
                    roundPuzzles = puzzles.isEmpty() ? shuffledLocalPuzzles() : shuffledRemotePuzzles(puzzles);
                    startRound(1);
                },
                error -> {
                    roundPuzzles = shuffledLocalPuzzles();
                    startRound(1);
                }
        );
    }

    public void startRoomGame(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(this.roomId)) {
            return;
        }

        roomMode = true;
        this.roomId = roomId;
        String uid = associationsRoomRepository.getCurrentUid();
        myUid = uid != null ? uid : "";
        roomListener = roomRepository.listenRoom(
                roomId,
                this::onRoomChanged,
                errorMessage::setValue
        );
    }

    public void openField(int columnIndex, int clueIndex) {
        if (roundOver || gameOver) {
            return;
        }
        if (roomMode) {
            associationsRoomRepository.openField(
                    roomId,
                    myUid,
                    columnIndex,
                    clueIndex,
                    errorMessage::setValue
            );
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
        if (roomMode) {
            associationsRoomRepository.submitColumnGuess(
                    roomId,
                    myUid,
                    columnIndex,
                    guess,
                    errorMessage::setValue
            );
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
        if (roomMode) {
            associationsRoomRepository.submitFinalGuess(
                    roomId,
                    myUid,
                    guess,
                    errorMessage::setValue
            );
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
        if (!fieldOpenedThisTurn) {
            errorMessage.setValue("Prvo otvori jedno polje.");
            return;
        }
        if (roomMode) {
            associationsRoomRepository.finishTurn(
                    roomId,
                    myUid,
                    errorMessage::setValue
            );
            return;
        }
        switchActivePlayer();
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

    @Override
    protected void onCleared() {
        super.onCleared();
        stopRoundTimer();
        handler.removeCallbacksAndMessages(null);
        if (roomListener != null) {
            roomListener.remove();
        }
        if (associationsListener != null) {
            associationsListener.remove();
        }
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;
        applyRoomPlayers(room, playerOneScore, playerTwoScore);
        if (associationsListener == null) {
            associationsListener = associationsRoomRepository.listenState(
                    room.getRoomId(),
                    this::onRemoteStateChanged,
                    errorMessage::setValue
            );
        }
        initializeRoomGameIfNeeded(room);
    }

    private void initializeRoomGameIfNeeded(@NonNull RoomSession room) {
        if (roomInitializationRequested) {
            return;
        }
        roomInitializationRequested = true;
        puzzleRepository.loadPuzzles(
                puzzles -> {
                    if (!puzzles.isEmpty()) {
                        roundPuzzles = new ArrayList<>(puzzles);
                        Collections.shuffle(roundPuzzles, random);
                    }
                    associationsRoomRepository.initializeIfNeeded(
                            room,
                            puzzleForRound(1),
                            errorMessage::setValue
                    );
                },
                error -> {
                    errorMessage.setValue(error);
                    associationsRoomRepository.initializeIfNeeded(
                            room,
                            puzzleForRound(1),
                            errorMessage::setValue
                    );
                }
        );
    }

    private void onRemoteStateChanged(@NonNull DocumentSnapshot snapshot) {
        currentRound = intOrDefault(snapshot.get("currentRound"), 1);
        activePlayerNumber = intOrDefault(snapshot.get("activePlayerNumber"), currentRound);
        playerOneScore = intOrDefault(snapshot.get("playerOneScore"), 0);
        playerTwoScore = intOrDefault(snapshot.get("playerTwoScore"), 0);
        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        phase = stringOrDefault(snapshot.getString("phase"), AssociationsRoomRepository.PHASE_ACTIVE);
        currentPuzzle = parsePuzzle(snapshot);
        revealedFields = parseRevealedFields(snapshot.get("revealedFields"));
        solvedColumns = parseSolvedColumns(snapshot.get("solvedColumns"));
        fieldOpenedThisTurn = boolOrFalse(snapshot.get("fieldOpenedThisTurn"));
        finalAnswerSolved = boolOrFalse(snapshot.get("finalAnswerSolved"));
        roundOver = AssociationsRoomRepository.PHASE_ROUND_OVER.equals(phase)
                || AssociationsRoomRepository.PHASE_GAME_OVER.equals(phase);
        gameOver = AssociationsRoomRepository.PHASE_GAME_OVER.equals(phase);

        applyRoomPlayersFromState(snapshot);
        initializeHeader(
                formatRoundText(currentRound, TOTAL_ROUNDS),
                formatTimeText(Math.max(0L, longOrZero(snapshot.get("phaseEndsAtMillis")) - System.currentTimeMillis())),
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore)
        );
        updateActivePlayer(roundOver || gameOver ? 0 : activePlayerNumber);
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

    private void startRemotePhaseTimer(long phaseEndsAtMillis) {
        stopRoundTimer();
        if (gameOver || phaseEndsAtMillis <= 0L) {
            updateTime(formatTimeText(0));
            return;
        }

        long remaining = Math.max(0L, phaseEndsAtMillis - System.currentTimeMillis());
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

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
        associationsRoomRepository.handleExpiredPhase(
                roomId,
                puzzleForRound(Math.min(currentRound + 1, TOTAL_ROUNDS)),
                errorMessage::setValue
        );
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
            if (solvedColumns[columnIndex]) {
                continue;
            }
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
                canCurrentUserPlay(),
                canCurrentUserPlay() && !roundOver && !gameOver,
                finalAnswerSolved,
                roundOver,
                gameOver
        ));
    }

    @NonNull
    private List<AssociationPuzzle> shuffledLocalPuzzles() {
        List<AssociationPuzzle> puzzles = new ArrayList<>(AssociationPuzzle.defaultPuzzles());
        Collections.shuffle(puzzles, random);
        return puzzles;
    }

    @NonNull
    private List<AssociationPuzzle> shuffledRemotePuzzles(@NonNull List<AssociationPuzzle> puzzles) {
        List<AssociationPuzzle> copy = new ArrayList<>(puzzles);
        Collections.shuffle(copy, random);
        return copy;
    }

    @NonNull
    private AssociationPuzzle puzzleForRound(int roundNumber) {
        if (roundPuzzles.isEmpty()) {
            roundPuzzles = shuffledLocalPuzzles();
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

    private boolean canCurrentUserPlay() {
        if (!roomMode) {
            return true;
        }
        return AssociationsRoomRepository.PHASE_ACTIVE.equals(phase)
                && !myUid.isEmpty()
                && myUid.equals(activePlayerUid);
    }

    @NonNull
    private AssociationPuzzle parsePuzzle(@NonNull DocumentSnapshot snapshot) {
        List<AssociationColumn> columns = new ArrayList<>();
        Object rawColumns = snapshot.get("columns");
        if (rawColumns instanceof List) {
            for (Object rawColumn : (List<?>) rawColumns) {
                if (!(rawColumn instanceof Map)) {
                    continue;
                }
                Map<?, ?> map = (Map<?, ?>) rawColumn;
                List<String> clues = stringList(map.get("clues"));
                String answer = stringOrDefault(asString(map.get("answer")), "");
                if (clues.size() == AssociationColumn.CLUE_COUNT) {
                    columns.add(new AssociationColumn(clues, answer));
                }
            }
        }
        if (columns.size() != AssociationPuzzle.COLUMN_COUNT) {
            return puzzleForRound(currentRound);
        }
        return new AssociationPuzzle(columns, stringOrDefault(snapshot.getString("finalAnswer"), ""));
    }

    @NonNull
    private static boolean[][] parseRevealedFields(@Nullable Object raw) {
        boolean[][] values = AssociationsGameState.emptyRevealedFields();
        if (raw instanceof List) {
            List<?> rawColumns = (List<?>) raw;
            if (!rawColumns.isEmpty() && rawColumns.get(0) instanceof List) {
                for (int column = 0; column < rawColumns.size() && column < AssociationPuzzle.COLUMN_COUNT; column++) {
                    List<?> rawClues = (List<?>) rawColumns.get(column);
                    for (int clue = 0; clue < rawClues.size() && clue < AssociationColumn.CLUE_COUNT; clue++) {
                        values[column][clue] = Boolean.TRUE.equals(rawClues.get(clue));
                    }
                }
            } else {
                for (int i = 0; i < rawColumns.size()
                        && i < AssociationPuzzle.COLUMN_COUNT * AssociationColumn.CLUE_COUNT; i++) {
                    int column = i / AssociationColumn.CLUE_COUNT;
                    int clue = i % AssociationColumn.CLUE_COUNT;
                    values[column][clue] = Boolean.TRUE.equals(rawColumns.get(i));
                }
            }
        }
        return values;
    }

    @NonNull
    private static boolean[] parseSolvedColumns(@Nullable Object raw) {
        boolean[] values = AssociationsGameState.emptySolvedColumns();
        if (raw instanceof List) {
            List<?> rawValues = (List<?>) raw;
            for (int i = 0; i < rawValues.size() && i < values.length; i++) {
                values[i] = Boolean.TRUE.equals(rawValues.get(i));
            }
        }
        return values;
    }

    @NonNull
    private static List<String> stringList(@Nullable Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            }
        }
        return values;
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean boolOrFalse(@Nullable Object value) {
        return value instanceof Boolean && (Boolean) value;
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
