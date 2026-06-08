package com.example.slagalica.viewmodel.games;

import android.app.Application;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.data.repository.SpojnicePuzzlesRepository;
import com.example.slagalica.data.repository.SpojniceRoomRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.spojnice.SpojnicePuzzle;
import com.example.slagalica.model.spojnice.SpojniceUiState;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SpojniceViewModel extends AndroidViewModel {

    private static final int TOTAL_ROUNDS = 2;
    private static final int PAIRS_PER_ROUND = 5;
    private static final int TOTAL_PAIRS_PER_GAME = TOTAL_ROUNDS * PAIRS_PER_ROUND;
    private static final long TIMER_INTERVAL_MS = 1_000L;

    private final MutableLiveData<SpojniceUiState> uiState = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final RoomSessionRepository roomRepository = new RoomSessionRepository();
    private final SpojnicePuzzlesRepository puzzlesRepository = new SpojnicePuzzlesRepository();
    private final SpojniceRoomRepository spojniceRepository = new SpojniceRoomRepository();
    private final UserProfileRepository profileRepository;

    private CountDownTimer phaseTimer;
    private ListenerRegistration roomListener;
    private ListenerRegistration spojniceListener;
    private RoomSession roomSession;
    private String roomId = "";
    private String myUid = "";
    private String phase = SpojniceRoomRepository.PHASE_ACTIVE;
    private String activePlayerUid = "";
    private String followupPlayerUid = "";
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private int currentLeftIndex = 0;
    private int selectedRow = SpojniceUiState.NO_ROW;
    private int selectedRightIndex = SpojniceUiState.NO_SELECTION;
    private int selectedSpinnerPosition = 0;
    private List<String> leftTerms = new ArrayList<>();
    private List<String> rightTerms = new ArrayList<>();
    private List<Integer> connectedLeft = new ArrayList<>();
    private List<Integer> attemptedLeft = new ArrayList<>();
    private List<Integer> usedRightIndices = new ArrayList<>();
    private List<Integer> followupLockedLeft = new ArrayList<>();
    private String criterion = "";
    private String playerOneLabel = "Igrač 1";
    private String playerTwoLabel = "Igrač 2";
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean statsRecorded = false;
    private List<SpojnicePuzzle> roundPuzzles = new ArrayList<>();
    private boolean roomInitializationRequested = false;

    public SpojniceViewModel(@NonNull Application application) {
        super(application);
        profileRepository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<SpojniceUiState> getUiState() {
        return uiState;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    public void startRoomGame(@NonNull String roomId) {
        if (roomId.isEmpty() || roomId.equals(this.roomId)) {
            return;
        }

        this.roomId = roomId;
        String uid = spojniceRepository.getCurrentUid();
        myUid = uid != null ? uid : "";
        roomListener = roomRepository.listenRoom(
                roomId,
                this::onRoomChanged,
                errorMessage::setValue
        );
    }

    public void selectFollowupRow(int rowIndex) {
        if (!SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase) || !canCurrentUserPlay()) {
            return;
        }
        if (connectedLeft.contains(rowIndex) || followupLockedLeft.contains(rowIndex)) {
            return;
        }
        selectedRow = rowIndex;
        selectedRightIndex = SpojniceUiState.NO_SELECTION;
        selectedSpinnerPosition = 0;
        publishUiState(remainingSeconds());
    }

    public void selectRightOption(int rowIndex, int spinnerPosition) {
        if (!canCurrentUserPlay()) {
            return;
        }
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            if (rowIndex != selectedRow || selectedRow < 0 || connectedLeft.contains(rowIndex)
                    || followupLockedLeft.contains(rowIndex)) {
                return;
            }
        } else if (!isRowSelectable(rowIndex)) {
            return;
        }

        List<Integer> available = buildAvailableRightIndices();
        int availableIndex = spinnerPosition - 1;
        selectedSpinnerPosition = spinnerPosition;
        if (availableIndex < 0) {
            selectedRightIndex = SpojniceUiState.NO_SELECTION;
            publishUiState(remainingSeconds());
            return;
        }
        if (availableIndex >= available.size()) {
            return;
        }
        selectedRightIndex = available.get(availableIndex);
        publishUiState(remainingSeconds());
    }

    public void submitPair() {
        if (!canCurrentUserPlay() || selectedRightIndex < 0) {
            return;
        }
        int leftIndex = SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)
                ? currentLeftIndex
                : selectedRow;
        if (leftIndex < 0) {
            return;
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            if (!isRowSelectable(leftIndex)) {
                return;
            }
        } else if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            if (selectedRow < 0 || connectedLeft.contains(leftIndex)
                    || followupLockedLeft.contains(leftIndex)) {
                return;
            }
        } else {
            return;
        }

        final int submittedRightIndex = selectedRightIndex;
        final boolean followupSubmit = SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase);
        spojniceRepository.submitPair(
                roomId,
                myUid,
                leftIndex,
                submittedRightIndex,
                errorMessage::setValue,
                correct -> {
                    if (followupSubmit && !correct) {
                        if (!followupLockedLeft.contains(leftIndex)) {
                            followupLockedLeft = new ArrayList<>(followupLockedLeft);
                            followupLockedLeft.add(leftIndex);
                        }
                        selectedRow = firstAvailableFollowupRow();
                        selectedRightIndex = SpojniceUiState.NO_SELECTION;
                        selectedSpinnerPosition = 0;
                        errorMessage.setValue(getApplication().getString(
                                com.example.slagalica.R.string.spojnice_wrong_next_term
                        ));
                        publishUiState(remainingSeconds());
                    } else if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase) && !correct) {
                        selectedRightIndex = SpojniceUiState.NO_SELECTION;
                        selectedSpinnerPosition = 0;
                        publishUiState(remainingSeconds());
                    }
                }
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopPhaseTimer();
        handler.removeCallbacksAndMessages(null);
        if (roomListener != null) {
            roomListener.remove();
        }
        if (spojniceListener != null) {
            spojniceListener.remove();
        }
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;
        playerOneLabel = room.getHostUsername();
        playerTwoLabel = room.getGuestUsername();
        if (spojniceListener == null) {
            spojniceListener = spojniceRepository.listenState(
                    room.getRoomId(),
                    this::onRemoteStateChanged,
                    errorMessage::setValue
            );
        }
        if (myUid.equals(room.getHostUid())) {
            initializeRoomGameIfNeeded(room);
        }
    }

    private void initializeRoomGameIfNeeded(@NonNull RoomSession room) {
        if (roomInitializationRequested) {
            return;
        }
        roomInitializationRequested = true;
        puzzlesRepository.loadPuzzles(
                puzzles -> {
                    if (!puzzles.isEmpty()) {
                        roundPuzzles = new ArrayList<>(puzzles);
                        Collections.shuffle(roundPuzzles, random);
                    }
                    startSpojniceRound(room, 1);
                },
                error -> {
                    errorMessage.setValue(error);
                    startSpojniceRound(room, 1);
                }
        );
    }

    private void startSpojniceRound(@NonNull RoomSession room, int round) {
        SpojnicePuzzle puzzle = puzzleForRound(round);
        SpojnicePuzzle.ShuffledRound shuffled = puzzle.shuffled(random);
        spojniceRepository.initializeIfNeeded(
                room,
                shuffled,
                puzzle.getCriterion(),
                errorMessage::setValue
        );
    }

    private void onRemoteStateChanged(@NonNull DocumentSnapshot snapshot) {
        int newRound = intOrDefault(snapshot.get("currentRound"), 1);
        int newLeftIndex = intOrDefault(snapshot.get("currentLeftIndex"), 0);
        String newPhase = stringOrDefault(snapshot.getString("phase"), SpojniceRoomRepository.PHASE_ACTIVE);
        List<Integer> newConnected = intList(snapshot.get("connectedLeft"));
        List<Integer> newUsedRight = intList(snapshot.get("usedRightIndices"));

        List<Integer> newFollowupLocked = intList(snapshot.get("followupLockedLeft"));
        boolean followupLockedChanged = !newFollowupLocked.equals(followupLockedLeft);

        boolean phaseChanged = !newPhase.equals(phase);
        boolean leftAdvanced = newLeftIndex != currentLeftIndex;
        boolean roundChanged = newRound != currentRound;
        boolean connectionMade = newConnected.size() > connectedLeft.size();
        boolean usedRightChanged = !newUsedRight.equals(usedRightIndices);

        currentRound = newRound;
        activePlayerNumber = intOrDefault(snapshot.get("activePlayerNumber"), currentRound);
        playerOneScore = intOrDefault(snapshot.get("playerOneScore"), 0);
        playerTwoScore = intOrDefault(snapshot.get("playerTwoScore"), 0);
        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        followupPlayerUid = stringOrEmpty(snapshot.getString("followupPlayerUid"));
        phase = newPhase;
        currentLeftIndex = newLeftIndex;
        criterion = stringOrDefault(snapshot.getString("criterion"), "");
        leftTerms = stringList(snapshot.get("leftTerms"));
        rightTerms = stringList(snapshot.get("rightTerms"));
        connectedLeft = newConnected;
        attemptedLeft = intList(snapshot.get("attemptedLeft"));
        usedRightIndices = newUsedRight;
        followupLockedLeft = newFollowupLocked;
        roundOver = SpojniceRoomRepository.PHASE_ROUND_OVER.equals(phase)
                || SpojniceRoomRepository.PHASE_GAME_OVER.equals(phase);
        gameOver = SpojniceRoomRepository.PHASE_GAME_OVER.equals(phase);

        playerOneLabel = stringOrDefault(snapshot.getString("playerOneUsername"), playerOneLabel);
        playerTwoLabel = stringOrDefault(snapshot.getString("playerTwoUsername"), playerTwoLabel);

        if (phaseChanged || leftAdvanced || roundChanged || connectionMade || usedRightChanged
                || followupLockedChanged) {
            selectedRightIndex = SpojniceUiState.NO_SELECTION;
            selectedSpinnerPosition = 0;
        }
        if (connectionMade && SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            selectedRow = SpojniceUiState.NO_ROW;
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            selectedRow = currentLeftIndex;
        } else if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase) && phaseChanged) {
            selectedRow = SpojniceUiState.NO_ROW;
        } else if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)
                && (selectedRow < 0
                || connectedLeft.contains(selectedRow)
                || followupLockedLeft.contains(selectedRow))) {
            selectedRow = firstAvailableFollowupRow();
        } else if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)
                && followupLockedChanged
                && followupLockedLeft.contains(selectedRow)) {
            selectedRow = firstAvailableFollowupRow();
        }

        if (gameOver) {
            recordStatsIfNeeded();
        }

        long phaseEndsAt = longOrZero(snapshot.get("phaseEndsAtMillis"));
        publishUiState(Math.max(0, (int) Math.ceil((phaseEndsAt - System.currentTimeMillis()) / 1000.0)));
        startRemotePhaseTimer(phaseEndsAt);
    }

    private void recordStatsIfNeeded() {
        if (statsRecorded || roomSession == null) {
            return;
        }
        statsRecorded = true;
        int myScore = myUid.equals(roomSession.getHostUid()) ? playerOneScore : playerTwoScore;
        int correctPairs = myScore / 2;
        profileRepository.recordSpojniceGame(myScore, correctPairs, TOTAL_PAIRS_PER_GAME);
    }

    @NonNull
    private List<Integer> buildAvailableRightIndices() {
        List<Integer> available = new ArrayList<>();
        for (int i = 0; i < rightTerms.size(); i++) {
            if (!usedRightIndices.contains(i)) {
                available.add(i);
            }
        }
        return available;
    }

    private int availableListIndexForSelection() {
        if (selectedRightIndex < 0) {
            return SpojniceUiState.NO_SELECTION;
        }
        int index = buildAvailableRightIndices().indexOf(selectedRightIndex);
        return index >= 0 ? index + 1 : SpojniceUiState.NO_SELECTION;
    }

    private void publishUiState(int secondsLeft) {
        boolean myTurn = canCurrentUserPlay();
        boolean canSubmit = myTurn
                && !roundOver
                && !gameOver
                && selectedRightIndex >= 0
                && selectedSpinnerPosition > 0
                && (!SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase) || selectedRow >= 0);
        uiState.setValue(new SpojniceUiState(
                currentRound,
                TOTAL_ROUNDS,
                secondsLeft,
                playerOneScore,
                playerTwoScore,
                playerOneLabel,
                playerTwoLabel,
                criterion,
                leftTerms,
                rightTerms,
                connectedLeft,
                attemptedLeft,
                usedRightIndices,
                followupLockedLeft,
                currentLeftIndex,
                displayActivePlayerNumber(),
                myTurn,
                myTurn && !roundOver && !gameOver,
                gameOver,
                roundOver,
                buildStatusMessage(myTurn),
                phase,
                selectedRow,
                availableListIndexForSelection(),
                canSubmit
        ));
    }

    @NonNull
    private String buildStatusMessage(boolean myTurn) {
        if (gameOver) {
            return String.format(
                    Locale.getDefault(),
                    "Kraj igre. %s: %d, %s: %d",
                    playerOneLabel,
                    playerOneScore,
                    playerTwoLabel,
                    playerTwoScore
            );
        }
        if (roundOver) {
            return "Runda je završena. Sledeća runda uskoro…";
        }
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            if (myTurn) {
                return "Klikni preostali pojam levo, zatim izaberi odgovor desno i potvrdi.";
            }
            return "Protivnik povezuje preostale pojmove…";
        }
        if (myTurn) {
            return String.format(
                    Locale.getDefault(),
                    "Tvoj red — poveži pojam %d/%d (2 boda po tačnoj spojnici).",
                    Math.min(currentLeftIndex + 1, PAIRS_PER_ROUND),
                    PAIRS_PER_ROUND
            );
        }
        return "Protivnik igra…";
    }

    private int displayActivePlayerNumber() {
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            return playerNumberForUid(followupPlayerUid);
        }
        return activePlayerNumber;
    }

    private int playerNumberForUid(@NonNull String uid) {
        if (roomSession != null && uid.equals(roomSession.getHostUid())) {
            return 1;
        }
        return 2;
    }

    private boolean canCurrentUserPlay() {
        if (roundOver || gameOver || myUid.isEmpty()) {
            return false;
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            return myUid.equals(activePlayerUid);
        }
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            return myUid.equals(followupPlayerUid);
        }
        return false;
    }

    private boolean isRowSelectable(int rowIndex) {
        if (connectedLeft.contains(rowIndex)) {
            return false;
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            return rowIndex == currentLeftIndex;
        }
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            return rowIndex == selectedRow && selectedRow >= 0;
        }
        return false;
    }

    private int firstAvailableFollowupRow() {
        for (int i = 0; i < PAIRS_PER_ROUND; i++) {
            if (!connectedLeft.contains(i) && !followupLockedLeft.contains(i)) {
                return i;
            }
        }
        return SpojniceUiState.NO_ROW;
    }

    private void startRemotePhaseTimer(long phaseEndsAtMillis) {
        stopPhaseTimer();
        if (gameOver || phaseEndsAtMillis <= 0L) {
            publishUiState(0);
            return;
        }

        long remaining = Math.max(0L, phaseEndsAtMillis - System.currentTimeMillis());
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

        phaseTimer = new CountDownTimer(remaining, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                publishUiState((int) Math.ceil(millisUntilFinished / 1000.0));
            }

            @Override
            public void onFinish() {
                publishUiState(0);
                expireRemotePhase();
            }
        };
        phaseTimer.start();
    }

    private void expireRemotePhase() {
        if (roomId.isEmpty()) {
            return;
        }
        int nextRound = Math.min(currentRound + 1, TOTAL_ROUNDS);
        SpojnicePuzzle puzzle = puzzleForRound(nextRound);
        SpojnicePuzzle.ShuffledRound shuffled = puzzle.shuffled(random);
        spojniceRepository.handleExpiredPhase(
                roomId,
                shuffled,
                puzzle.getCriterion(),
                errorMessage::setValue
        );
    }

    private int remainingSeconds() {
        SpojniceUiState state = uiState.getValue();
        return state != null ? state.getSecondsLeft() : 0;
    }

    @NonNull
    private SpojnicePuzzle puzzleForRound(int round) {
        if (roundPuzzles.isEmpty()) {
            roundPuzzles = new ArrayList<>(SpojnicePuzzle.defaultPuzzles());
            Collections.shuffle(roundPuzzles, random);
        }
        int index = Math.max(0, Math.min(round - 1, roundPuzzles.size() - 1));
        return roundPuzzles.get(index);
    }

    private void stopPhaseTimer() {
        if (phaseTimer != null) {
            phaseTimer.cancel();
            phaseTimer = null;
        }
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
}
