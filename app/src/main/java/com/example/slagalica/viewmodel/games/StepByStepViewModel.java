package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.KorakPoKorakPuzzlesRepository;
import com.example.slagalica.model.korakpokorak.KorakPoKorakPuzzle;
import com.example.slagalica.model.korakpokorak.KorakPoKorakUiState;

import com.example.slagalica.data.repository.KorakPoKorakRoomRepository;
import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.model.RoomSession;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import androidx.annotation.Nullable;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import java.util.Random;

public class StepByStepViewModel extends AndroidViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final int STEP_COUNT = 7;
    private static final long TIMER_INTERVAL_MS = 1_000L;

    private final MutableLiveData<KorakPoKorakUiState> uiState = new MutableLiveData<>();
    private final KorakPoKorakPuzzlesRepository puzzlesRepository = new KorakPoKorakPuzzlesRepository();
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

    private String lastStatusMessage = "";

    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final KorakPoKorakRoomRepository roomGameRepository = new KorakPoKorakRoomRepository();

    private ListenerRegistration roomListener;
    private ListenerRegistration korakPoKorakListener;

    private RoomSession roomSession;
    private String roomId = "";
    private String myUid = "";
    private String phase = KorakPoKorakRoomRepository.PHASE_ACTIVE;
    private String activePlayerUid = "";
    private String bonusPlayerUid = "";
    private boolean roomInitializationRequested = false;
    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
    private boolean baseScoresCaptured = false;
    private int currentUserOwnRoundSolvedStepIndex = -1;

    public StepByStepViewModel(@NonNull Application application) {
        super(application);
    }

    @NonNull
    public LiveData<KorakPoKorakUiState> getUiState() {
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
        currentUserOwnRoundSolvedStepIndex = -1;

        String uid = roomGameRepository.getCurrentUid();
        myUid = uid != null ? uid : "";

        loadRoundPuzzles(() -> {
            roomListener = roomRepository.listenRoom(
                    roomId,
                    this::onRoomChanged,
                    error -> publishState(remainingSeconds(), error)
            );
        });
    }

    private void loadRoundPuzzles(@NonNull Runnable onReady) {
        if (roundPuzzles.size() >= TOTAL_ROUNDS) {
            onReady.run();
            return;
        }

        puzzlesRepository.loadPuzzles(
                puzzles -> {
                    List<KorakPoKorakPuzzle> source = puzzles.isEmpty()
                            ? KorakPoKorakPuzzle.defaultPuzzles()
                            : puzzles;
                    preparePuzzles(source);
                    onReady.run();
                },
                error -> {
                    preparePuzzles(KorakPoKorakPuzzle.defaultPuzzles());
                    onReady.run();
                }
        );
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;

        if (!baseScoresCaptured) {
            baseScoresCaptured = true;
            basePlayerOneScore = room.getHostTotalScore();
            basePlayerTwoScore = room.getGuestTotalScore();
        }

        if (korakPoKorakListener == null) {
            korakPoKorakListener = roomGameRepository.listenState(
                    room.getRoomId(),
                    this::onRemoteStateChanged,
                    error -> publishState(remainingSeconds(), error)
            );
        }

        if (myUid.equals(room.getHostUid())) {
            initializeRoomGameIfNeeded(room);
        }

        if (!room.getAbandonedByUid().isEmpty()
                && !myUid.isEmpty()
                && !myUid.equals(room.getAbandonedByUid())) {
            roomGameRepository.handleAbandonedPlayer(
                    room,
                    error -> publishState(remainingSeconds(), error)
            );
        }
    }

    private void initializeRoomGameIfNeeded(@NonNull RoomSession room) {
        if (roomInitializationRequested) {
            return;
        }

        roomInitializationRequested = true;

        loadRoundPuzzles(() -> roomGameRepository.initializeIfNeeded(
                room,
                puzzleForRound(1),
                error -> publishState(remainingSeconds(), error)
        ));
    }

    private void onRemoteStateChanged(@NonNull DocumentSnapshot snapshot) {
        currentRound = intOrDefault(snapshot.get("currentRound"), 1);
        activePlayerNumber = intOrDefault(snapshot.get("activePlayerNumber"), currentRound);
        playerOneScore = intOrDefault(snapshot.get("playerOneScore"), 0);
        playerTwoScore = intOrDefault(snapshot.get("playerTwoScore"), 0);
        currentStepIndex = intOrDefault(snapshot.get("currentStepIndex"), 0);

        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        bonusPlayerUid = stringOrEmpty(snapshot.getString("bonusPlayerUid"));
        phase = stringOrDefault(snapshot.getString("phase"), KorakPoKorakRoomRepository.PHASE_ACTIVE);

        bonusPhase = KorakPoKorakRoomRepository.PHASE_BONUS.equals(phase);
        roundOver = KorakPoKorakRoomRepository.PHASE_ROUND_OVER.equals(phase)
                || KorakPoKorakRoomRepository.PHASE_GAME_OVER.equals(phase);
        gameOver = KorakPoKorakRoomRepository.PHASE_GAME_OVER.equals(phase);

        answeringPlayerNumber = bonusPhase
                ? playerNumberForUid(bonusPlayerUid)
                : activePlayerNumber;

        String answer = stringOrEmpty(snapshot.getString("answer"));
        List<String> steps = stringList(snapshot.get("steps"));
        if (!answer.isEmpty() && steps.size() == STEP_COUNT) {
            currentPuzzle = new KorakPoKorakPuzzle(answer, steps);
        }

        int solvedStepIndex = snapshot.contains("solvedStepIndex")
                ? intOrDefault(snapshot.get("solvedStepIndex"), -1)
                : -1;

        if (!myUid.isEmpty()
                && myUid.equals(activePlayerUid)
                && solvedStepIndex >= 0) {
            currentUserOwnRoundSolvedStepIndex = solvedStepIndex;
        }

        long phaseEndsAtMillis = longOrZero(snapshot.get("phaseEndsAtMillis"));
        int secondsLeft = secondsFromMillis(Math.max(0L, phaseEndsAtMillis - System.currentTimeMillis()));

        publishState(secondsLeft, buildRemoteStatusMessage());
        startRemoteTimer(phaseEndsAtMillis);
    }

    private void startRemoteTimer(long phaseEndsAtMillis) {
        stopTimer();

        if (gameOver || phaseEndsAtMillis <= 0L) {
            publishState(0, buildRemoteStatusMessage());
            return;
        }

        long remaining = Math.max(0L, phaseEndsAtMillis - System.currentTimeMillis());
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

        timer = new CountDownTimer(remaining, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                publishState(secondsFromMillis(millisUntilFinished), buildRemoteStatusMessage());
            }

            @Override
            public void onFinish() {
                publishState(0, buildRemoteStatusMessage());
                expireRemotePhase();
            }
        };

        timer.start();
    }

    private void expireRemotePhase() {
        if (roomId.isEmpty()) {
            return;
        }

        roomGameRepository.handleExpiredPhase(
                roomId,
                puzzleForRound(Math.min(currentRound + 1, TOTAL_ROUNDS)),
                error -> publishState(remainingSeconds(), error)
        );
    }

    @NonNull
    private KorakPoKorakPuzzle puzzleForRound(int round) {
        if (roundPuzzles.isEmpty()) {
            preparePuzzles(KorakPoKorakPuzzle.defaultPuzzles());
        }

        int index = round - 1;
        if (index < 0 || index >= roundPuzzles.size()) {
            index = 0;
        }

        return roundPuzzles.get(index);
    }

    public void submitAnswer(@NonNull String answer) {
        if (answer.trim().isEmpty() || roomId.isEmpty()) {
            return;
        }

        roomGameRepository.submitAnswer(
                roomId,
                myUid,
                answer,
                error -> publishState(remainingSeconds(), error)
        );
    }

    private void preparePuzzles(@NonNull List<KorakPoKorakPuzzle> puzzles) {
        roundPuzzles = new ArrayList<>(puzzles);
        Collections.shuffle(roundPuzzles, random);

        while (roundPuzzles.size() < TOTAL_ROUNDS) {
            roundPuzzles.addAll(KorakPoKorakPuzzle.defaultPuzzles());
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

        String expectedUid = bonusPhase ? bonusPlayerUid : activePlayerUid;
        boolean canSubmit = !roundOver && !gameOver && !myUid.isEmpty() && myUid.equals(expectedUid);

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
                canSubmit,
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


    @Override
    protected void onCleared() {
        stopTimer();

        if (roomListener != null) {
            roomListener.remove();
        }

        if (korakPoKorakListener != null) {
            korakPoKorakListener.remove();
        }

        super.onCleared();
    }


    @NonNull
    private String buildRemoteStatusMessage() {
        if (gameOver) {
            return "Kraj igre. Igrac 1: " + playerOneScore + ", Igrac 2: " + playerTwoScore + ".";
        }

        if (KorakPoKorakRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            return "Runda je zavrsena. Sledeca runda uskoro.";
        }

        if (bonusPhase) {
            if (myUid.equals(bonusPlayerUid)) {
                return "Bonus sansa. Imas 10 sekundi za odgovor.";
            }
            return "Protivnik ima bonus sansu.";
        }

        if (myUid.equals(activePlayerUid)) {
            return "Tvoj red. Pogodi pojam u sto manje koraka.";
        }

        return "Protivnik igra ovu rundu.";
    }

    private int playerNumberForUid(@NonNull String uid) {
        if (roomSession == null || uid.isEmpty()) {
            return activePlayerNumber;
        }

        if (uid.equals(roomSession.getHostUid())) {
            return 1;
        }

        if (uid.equals(roomSession.getGuestUid())) {
            return 2;
        }

        return activePlayerNumber;
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

    public int getCurrentUserOwnRoundSolvedStepIndex() {
        return currentUserOwnRoundSolvedStepIndex;
    }

}