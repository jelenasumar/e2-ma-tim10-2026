package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.MyNumberRoomRepository;
import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.mynumber.MyNumberUiState;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class MyNumberViewModel extends AndroidViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final long TIMER_INTERVAL_MS = 1_000L;

    private final MutableLiveData<MyNumberUiState> uiState = new MutableLiveData<>();
    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final MyNumberRoomRepository myNumberRepository = new MyNumberRoomRepository();

    private CountDownTimer timer;
    private ListenerRegistration roomListener;
    private ListenerRegistration myNumberListener;

    private RoomSession roomSession;
    private String roomId = "";
    private String myUid = "";
    private String phase = MyNumberRoomRepository.PHASE_TARGET;
    private String activePlayerUid = "";

    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private int targetNumber = 0;
    private int secondsLeft = 0;

    private boolean targetRevealed = false;
    private boolean numbersRevealed = false;
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean roomInitializationRequested = false;

    private List<Integer> numbers = new ArrayList<>();
    private String lastStatusMessage = "";

    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
    private boolean baseScoresCaptured = false;
    private boolean currentUserExactHit = false;

    public MyNumberViewModel(@NonNull Application application) {
        super(application);
    }

    @NonNull
    public LiveData<MyNumberUiState> getUiState() {
        return uiState;
    }

    public boolean shouldRecordGameStats() {
        return roomSession == null || !"FRIENDLY".equals(roomSession.getMatchType());
    }

    public void startRoomGame(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(this.roomId)) {
            return;
        }

        this.roomId = roomId;
        baseScoresCaptured = false;
        currentUserExactHit = false;

        String uid = myNumberRepository.getCurrentUid();
        myUid = uid != null ? uid : "";

        roomListener = roomRepository.listenRoom(
                roomId,
                this::onRoomChanged,
                error -> publishState(secondsLeft, error)
        );
    }

    public void stopTarget() {
        if (roomId.isEmpty()) {
            startLocalChallengeNumbersPhase();
            return;
        }

        myNumberRepository.stopTarget(
                roomId,
                myUid,
                error -> publishState(secondsLeft, error)
        );
    }

    public void stopNumbers() {
        if (roomId.isEmpty()) {
            startLocalChallengeSolvingPhase();
            return;
        }

        myNumberRepository.stopNumbers(
                roomId,
                myUid,
                error -> publishState(secondsLeft, error)
        );
    }

    public void submitExpression(@NonNull String expression) {
        if (roomId.isEmpty()) {
            finishLocalChallengeRound(expression);
            return;
        }

        if (expression.trim().isEmpty()) {
            return;
        }

        myNumberRepository.submitExpression(
                roomId,
                myUid,
                expression,
                error -> publishState(secondsLeft, error)
        );
    }

    public void startChallengeGame() {
        roomId = "";
        roomSession = null;
        myUid = myNumberRepository.getCurrentUid() != null ? myNumberRepository.getCurrentUid() : "";
        baseScoresCaptured = true;
        basePlayerOneScore = 0;
        basePlayerTwoScore = 0;
        playerOneScore = 0;
        playerTwoScore = 0;
        currentRound = 1;
        activePlayerNumber = 1;
        activePlayerUid = myUid;
        targetNumber = 0;
        targetRevealed = false;
        numbersRevealed = false;
        roundOver = false;
        gameOver = false;
        currentUserExactHit = false;
        numbers = new ArrayList<>();
        phase = MyNumberRoomRepository.PHASE_TARGET;
        lastStatusMessage = "Zaustavi trazeni broj.";
        publishState(0, lastStatusMessage);
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;

        if (!baseScoresCaptured) {
            baseScoresCaptured = true;
            basePlayerOneScore = room.getHostTotalScore();
            basePlayerTwoScore = room.getGuestTotalScore();
        }

        if (myNumberListener == null) {
            myNumberListener = myNumberRepository.listenState(
                    room.getRoomId(),
                    this::onRemoteStateChanged,
                    error -> publishState(secondsLeft, error)
            );
        }

        if (myUid.equals(room.getHostUid())) {
            initializeRoomGameIfNeeded(room);
        }

        if (!room.getAbandonedByUid().isEmpty()
                && !myUid.isEmpty()
                && !myUid.equals(room.getAbandonedByUid())) {
            myNumberRepository.handleAbandonedPlayer(
                    room,
                    error -> publishState(secondsLeft, error)
            );
        }
    }

    private void initializeRoomGameIfNeeded(@NonNull RoomSession room) {
        if (roomInitializationRequested) {
            return;
        }

        roomInitializationRequested = true;

        myNumberRepository.initializeIfNeeded(
                room,
                error -> publishState(secondsLeft, error)
        );
    }

    private void onRemoteStateChanged(@NonNull DocumentSnapshot snapshot) {
        currentRound = intOrDefault(snapshot.get("currentRound"), 1);
        activePlayerNumber = intOrDefault(snapshot.get("activePlayerNumber"), currentRound);
        playerOneScore = intOrDefault(snapshot.get("playerOneScore"), 0);
        playerTwoScore = intOrDefault(snapshot.get("playerTwoScore"), 0);

        targetNumber = intOrDefault(snapshot.get("targetNumber"), 0);
        targetRevealed = boolOrFalse(snapshot.get("targetRevealed"));
        numbers = intList(snapshot.get("numbers"));
        numbersRevealed = boolOrFalse(snapshot.get("numbersRevealed"));

        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        phase = stringOrDefault(snapshot.getString("phase"), MyNumberRoomRepository.PHASE_TARGET);

        roundOver = MyNumberRoomRepository.PHASE_ROUND_OVER.equals(phase)
                || MyNumberRoomRepository.PHASE_GAME_OVER.equals(phase);
        gameOver = MyNumberRoomRepository.PHASE_GAME_OVER.equals(phase);

        updateCurrentUserExactHit(snapshot);

        long deadline = currentDeadline(snapshot);
        secondsLeft = secondsFromMillis(Math.max(0L, deadline - System.currentTimeMillis()));

        publishState(secondsLeft, buildStatusMessage());
        startRemoteTimer(deadline);
    }

    private void updateCurrentUserExactHit(@NonNull DocumentSnapshot snapshot) {
        if (roomSession == null || targetNumber == 0 || myUid.isEmpty()) {
            return;
        }

        boolean isHost = myUid.equals(roomSession.getHostUid());
        boolean valid = boolOrFalse(snapshot.get(isHost
                ? "playerOneExpressionValid"
                : "playerTwoExpressionValid"));
        double result = doubleOrZero(snapshot.get(isHost
                ? "playerOneResult"
                : "playerTwoResult"));

        if (valid && Math.abs(result - targetNumber) < 0.000001d) {
            currentUserExactHit = true;
        }
    }

    private long currentDeadline(@NonNull DocumentSnapshot snapshot) {
        if (MyNumberRoomRepository.PHASE_TARGET.equals(phase)) {
            return longOrZero(snapshot.get("roundEndsAtMillis"));
        }

        if (MyNumberRoomRepository.PHASE_NUMBERS.equals(phase)) {
            return longOrZero(snapshot.get("numbersAutoRevealAtMillis"));
        }

        if (MyNumberRoomRepository.PHASE_SOLVING.equals(phase)) {
            return longOrZero(snapshot.get("roundEndsAtMillis"));
        }

        if (MyNumberRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            return longOrZero(snapshot.get("phaseEndsAtMillis"));
        }

        return 0L;
    }

    private void startRemoteTimer(long deadlineMillis) {
        stopTimer();

        if (gameOver || deadlineMillis <= 0L) {
            publishState(0, buildStatusMessage());
            return;
        }

        long remaining = Math.max(0L, deadlineMillis - System.currentTimeMillis());
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

        timer = new CountDownTimer(remaining, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                secondsLeft = secondsFromMillis(millisUntilFinished);
                publishState(secondsLeft, buildStatusMessage());
            }

            @Override
            public void onFinish() {
                secondsLeft = 0;
                publishState(0, buildStatusMessage());
                expireRemotePhase();
            }
        };

        timer.start();
    }

    private void expireRemotePhase() {
        if (roomId.isEmpty()) {
            return;
        }

        myNumberRepository.handleExpiredPhase(
                roomId,
                error -> publishState(secondsLeft, error)
        );
    }

    private void publishState(int secondsLeft, @NonNull String statusMessage) {
        if (!statusMessage.isEmpty()) {
            lastStatusMessage = statusMessage;
        }

        boolean myTurn = !myUid.isEmpty() && myUid.equals(activePlayerUid);

        boolean canStopTarget = myTurn
                && MyNumberRoomRepository.PHASE_TARGET.equals(phase)
                && !targetRevealed
                && !roundOver
                && !gameOver;

        boolean canStopNumbers = myTurn
                && MyNumberRoomRepository.PHASE_NUMBERS.equals(phase)
                && targetRevealed
                && !numbersRevealed
                && !roundOver
                && !gameOver;

        boolean canSubmitExpression = MyNumberRoomRepository.PHASE_SOLVING.equals(phase)
                && numbersRevealed
                && !roundOver
                && !gameOver
                && isCurrentUserParticipant();

        uiState.setValue(new MyNumberUiState(
                currentRound,
                TOTAL_ROUNDS,
                activePlayerNumber,
                playerOneScore,
                playerTwoScore,
                secondsLeft,
                targetNumber,
                numbers,
                targetRevealed,
                numbersRevealed,
                roundOver,
                gameOver,
                canStopTarget,
                canStopNumbers,
                canSubmitExpression,
                lastStatusMessage
        ));
    }

    @NonNull
    private String buildStatusMessage() {
        if (gameOver) {
            return "Kraj igre. Igrac 1: " + playerOneScore + ", Igrac 2: " + playerTwoScore + ".";
        }

        if (MyNumberRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            return "Runda je zavrsena. Sledeca runda uskoro.";
        }

        if (MyNumberRoomRepository.PHASE_TARGET.equals(phase)) {
            if (myUid.equals(activePlayerUid)) {
                return "Zaustavi trazeni broj.";
            }
            return "Protivnik zaustavlja trazeni broj.";
        }

        if (MyNumberRoomRepository.PHASE_NUMBERS.equals(phase)) {
            if (myUid.equals(activePlayerUid)) {
                return "Zaustavi ponudjene brojeve. Ako ne stignes, otvaraju se automatski.";
            }
            return "Protivnik zaustavlja ponudjene brojeve.";
        }

        if (MyNumberRoomRepository.PHASE_SOLVING.equals(phase)) {
            return "Sastavi izraz pomocu ponudjenih brojeva.";
        }

        return "";
    }

    private boolean isCurrentUserParticipant() {
        if (roomSession == null || myUid.isEmpty()) {
            return false;
        }

        return myUid.equals(roomSession.getHostUid())
                || myUid.equals(roomSession.getGuestUid());
    }

    public int getCurrentUserGameScore() {
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

    public boolean didCurrentUserHitExact() {
        return currentUserExactHit;
    }

    @Override
    protected void onCleared() {
        stopTimer();

        if (roomListener != null) {
            roomListener.remove();
        }

        if (myNumberListener != null) {
            myNumberListener.remove();
        }

        super.onCleared();
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

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static boolean boolOrFalse(@Nullable Object value) {
        return value instanceof Boolean && (Boolean) value;
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

    private static double doubleOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0d;
    }

    @NonNull
    private static List<Integer> intList(@Nullable Object raw) {
        List<Integer> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value instanceof Number) {
                    values.add(((Number) value).intValue());
                }
            }
        }
        return values;
    }
    private void startLocalChallengeNumbersPhase() {
        targetNumber = 100 + (int) (Math.random() * 900);
        targetRevealed = true;
        phase = MyNumberRoomRepository.PHASE_NUMBERS;
        lastStatusMessage = "Zaustavi ponudjene brojeve.";
        publishState(0, lastStatusMessage);
    }

    private void startLocalChallengeSolvingPhase() {
        numbers = new ArrayList<>();
        numbers.add(1 + (int) (Math.random() * 9));
        numbers.add(1 + (int) (Math.random() * 9));
        numbers.add(1 + (int) (Math.random() * 9));
        numbers.add(10 + (int) (Math.random() * 90));
        numbers.add(25);
        numbers.add(50);

        numbersRevealed = true;
        phase = MyNumberRoomRepository.PHASE_SOLVING;
        lastStatusMessage = "Sastavi izraz pomocu ponudjenih brojeva.";
        publishState(0, lastStatusMessage);
    }

    private void finishLocalChallengeRound(@NonNull String expression) {
        int score = 0;
        if (!expression.trim().isEmpty()) {
            score = 5;
        }

        playerOneScore = score;
        playerTwoScore = 0;
        gameOver = true;
        roundOver = true;
        phase = MyNumberRoomRepository.PHASE_GAME_OVER;
        lastStatusMessage = "Kraj igre. Osvojeno poena: " + score + ".";
        publishState(0, lastStatusMessage);
    }
}