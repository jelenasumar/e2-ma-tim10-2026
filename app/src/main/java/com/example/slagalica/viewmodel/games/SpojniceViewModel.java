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
import com.example.slagalica.utils.AvatarImageLoader;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SpojniceViewModel extends AndroidViewModel {

    private static final String ROUND_ONE_CRITERION =
            "Poveži izvođače sa nazivima njihovih pesama";
    private static final String ROUND_TWO_CRITERION =
            "Poveži glavne gradove sa državama";
    private static final int TOTAL_ROUNDS = 2;
    private static final int PAIRS_PER_ROUND = 5;
    private static final int TOTAL_PAIRS_PER_GAME = TOTAL_ROUNDS * PAIRS_PER_ROUND;
    private static final long TIMER_INTERVAL_MS = 1_000L;
    private static final long ROUND_DURATION_MS = 30_000L;
    private static final long RESULT_VISIBLE_MS = 2_500L;

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
    private String playerOneUid = "";
    private String playerTwoUid = "";
    private int currentRound = 1;
    private int activePlayerNumber = 1;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;
    private int basePlayerOneScore = 0;
    private int basePlayerTwoScore = 0;
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
    private String hostAvatarUri = "";
    private String guestAvatarUri = "";
    private boolean avatarsLoadRequested;
    private String observedHostUid = "";
    private String observedGuestUid = "";
    private ListenerRegistration hostAvatarListener;
    private ListenerRegistration guestAvatarListener;
    private String playerOneLabel = "Igrač 1";
    private String playerTwoLabel = "Igrač 2";
    private boolean roundOver = false;
    private boolean gameOver = false;
    private boolean statsRecorded = false;
    private List<SpojnicePuzzle> roundPuzzles = new ArrayList<>();
    private boolean roomInitializationRequested = false;
    private String displayedPhaseKey = "";
    private long displayedPhaseEndsAtMillis = 0L;

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
        loadRoundPuzzles(() -> { });
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
        removeAvatarListeners();
    }

    private void onRoomChanged(@NonNull RoomSession room) {
        roomSession = room;
        basePlayerOneScore = room.getHostTotalScore();
        basePlayerTwoScore = room.getGuestTotalScore();
        playerOneLabel = room.getHostUsername();
        playerTwoLabel = room.getGuestUsername();
        ensurePlayerAvatars(room.getHostUid(), room.getGuestUid());
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
        loadRoundPuzzles(() -> startSpojniceRound(room, 1));
    }

    private void loadRoundPuzzles(@NonNull Runnable onReady) {
        if (roundPuzzles.size() >= TOTAL_ROUNDS) {
            onReady.run();
            return;
        }
        puzzlesRepository.loadPuzzles(
                puzzles -> {
                    roundPuzzles = buildFixedRoundPuzzles(puzzles);
                    onReady.run();
                },
                error -> {
                    roundPuzzles = buildFixedRoundPuzzles(List.of());
                    onReady.run();
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
        playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        followupPlayerUid = stringOrEmpty(snapshot.getString("followupPlayerUid"));
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(newPhase)
                && (followupPlayerUid.isEmpty() || !followupPlayerUid.equals(followupPlayerUidForRound(newRound)))) {
            followupPlayerUid = followupPlayerUidForRound(newRound);
        }
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

        if (roundChanged) {
            selectedRow = SpojniceUiState.NO_ROW;
        }
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
        } else if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)
                && (phaseChanged || selectedRow < 0
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
        updateDisplayedPhaseClock(phaseEndsAt);
        publishUiState(secondsFromMillis(displayedRemainingPhaseMillis()));
        startRemotePhaseTimer(phaseEndsAt);
    }

    private void ensurePlayerAvatars(@NonNull String hostUid, @NonNull String guestUid) {
        if (avatarsLoadRequested && hostUid.equals(observedHostUid) && guestUid.equals(observedGuestUid)) {
            applyLocalAvatarOverride(hostUid, guestUid);
            return;
        }
        avatarsLoadRequested = true;
        observedHostUid = hostUid;
        observedGuestUid = guestUid;
        profileRepository.ensurePublicAvatarUri(
                unused -> listenRemoteAvatars(hostUid, guestUid),
                error -> listenRemoteAvatars(hostUid, guestUid)
        );
    }

    private void listenRemoteAvatars(@NonNull String hostUid, @NonNull String guestUid) {
        removeAvatarListeners();
        hostAvatarListener = profileRepository.listenAvatarUriForUser(
                hostUid,
                uri -> {
                    hostAvatarUri = displayAvatarUri(uri, myUid.equals(hostUid));
                    applyLocalAvatarOverride(hostUid, guestUid);
                    publishUiState(uiState.getValue() != null ? uiState.getValue().getSecondsLeft() : 0);
                },
                error -> { }
        );
        guestAvatarListener = profileRepository.listenAvatarUriForUser(
                guestUid,
                uri -> {
                    guestAvatarUri = displayAvatarUri(uri, myUid.equals(guestUid));
                    applyLocalAvatarOverride(hostUid, guestUid);
                    publishUiState(uiState.getValue() != null ? uiState.getValue().getSecondsLeft() : 0);
                },
                error -> { }
        );
    }

    private void removeAvatarListeners() {
        if (hostAvatarListener != null) {
            hostAvatarListener.remove();
            hostAvatarListener = null;
        }
        if (guestAvatarListener != null) {
            guestAvatarListener.remove();
            guestAvatarListener = null;
        }
    }

    private void applyLocalAvatarOverride(@NonNull String hostUid, @NonNull String guestUid) {
        String localAvatar = profileRepository.loadProfile().getAvatarUri();
        if (localAvatar == null) {
            localAvatar = "";
        }
        if (myUid.equals(hostUid)) {
            hostAvatarUri = localAvatar;
        } else if (myUid.equals(guestUid)) {
            guestAvatarUri = localAvatar;
        }
    }

    @NonNull
    private static String displayAvatarUri(@Nullable String avatarUri, boolean isCurrentUser) {
        if (avatarUri == null || avatarUri.isEmpty()) {
            return "";
        }
        if (isCurrentUser || AvatarImageLoader.isSharedAvatarUri(avatarUri)) {
            return avatarUri;
        }
        return "";
    }

    private void recordStatsIfNeeded() {
        if (statsRecorded || roomSession == null) {
            return;
        }
        statsRecorded = true;
        int myScore = myUid.equals(roomSession.getHostUid())
                ? playerOneScore - basePlayerOneScore
                : playerTwoScore - basePlayerTwoScore;
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
                hostAvatarUri,
                guestAvatarUri,
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
            return playerNumberForUid(followupPlayerUidForRound(currentRound));
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            return startingPlayerNumber(currentRound);
        }
        return activePlayerNumber;
    }

    private int playerNumberForUid(@NonNull String uid) {
        if (!uid.isEmpty() && uid.equals(playerOneUid)) {
            return 1;
        }
        if (roomSession != null && uid.equals(roomSession.getHostUid())) {
            return 1;
        }
        return 2;
    }

    @NonNull
    private String startingPlayerUid(int round) {
        if (!playerOneUid.isEmpty() || !playerTwoUid.isEmpty()) {
            return startingPlayerNumber(round) == 1 ? playerOneUid : playerTwoUid;
        }
        if (roomSession != null) {
            return startingPlayerNumber(round) == 1
                    ? roomSession.getHostUid()
                    : roomSession.getGuestUid();
        }
        return activePlayerUid;
    }

    private static int startingPlayerNumber(int round) {
        return round % 2 == 0 ? 2 : 1;
    }

    @NonNull
    private String followupPlayerUidForRound(int round) {
        return startingPlayerNumber(round) == 1 ? playerTwoUid : playerOneUid;
    }

    private boolean canCurrentUserPlay() {
        if (roundOver || gameOver || myUid.isEmpty()) {
            return false;
        }
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)) {
            if (currentLeftIndex >= PAIRS_PER_ROUND) {
                return false;
            }
            return myUid.equals(startingPlayerUid(currentRound));
        }
        if (SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            return myUid.equals(followupPlayerUidForRound(currentRound));
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

        long remaining = displayedRemainingPhaseMillis();
        if (remaining == 0L) {
            expireRemotePhase();
            return;
        }

        publishUiState(secondsFromMillis(remaining));
        phaseTimer = new CountDownTimer(remaining, TIMER_INTERVAL_MS) {
            @Override
            public void onTick(long millisUntilFinished) {
                publishUiState(secondsFromMillis(millisUntilFinished));
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
        if (SpojniceRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            int nextRoundNumber = currentRound + 1;
            if (nextRoundNumber > TOTAL_ROUNDS) {
                spojniceRepository.handleExpiredPhase(
                        roomId,
                        puzzleForRound(TOTAL_ROUNDS).shuffled(random),
                        puzzleForRound(TOTAL_ROUNDS).getCriterion(),
                        errorMessage::setValue
                );
                return;
            }
            SpojnicePuzzle puzzle = puzzleForRound(nextRoundNumber);
            spojniceRepository.handleExpiredPhase(
                    roomId,
                    puzzle.shuffled(random),
                    puzzle.getCriterion(),
                    errorMessage::setValue
            );
            return;
        }
        spojniceRepository.handleExpiredPhase(
                roomId,
                puzzleForRound(currentRound).shuffled(random),
                puzzleForRound(currentRound).getCriterion(),
                errorMessage::setValue
        );
    }

    private int remainingSeconds() {
        SpojniceUiState state = uiState.getValue();
        return state != null ? state.getSecondsLeft() : 0;
    }

    private void updateDisplayedPhaseClock(long phaseEndsAtMillis) {
        String phaseKey = currentRound + "|" + phase;
        long maxDuration = currentPhaseDurationMillis();
        if (phaseEndsAtMillis <= 0L || maxDuration <= 0L) {
            if (!phaseKey.equals(displayedPhaseKey)) {
                displayedPhaseKey = phaseKey;
                displayedPhaseEndsAtMillis = 0L;
            }
            return;
        }
        if (phaseKey.equals(displayedPhaseKey) && displayedPhaseEndsAtMillis > 0L) {
            return;
        }
        displayedPhaseKey = phaseKey;
        displayedPhaseEndsAtMillis = System.currentTimeMillis() + maxDuration;
    }

    private long displayedRemainingPhaseMillis() {
        if (displayedPhaseEndsAtMillis <= 0L) {
            return 0L;
        }
        return Math.min(
                Math.max(0L, displayedPhaseEndsAtMillis - System.currentTimeMillis()),
                currentPhaseDurationMillis()
        );
    }

    private long currentPhaseDurationMillis() {
        if (SpojniceRoomRepository.PHASE_ACTIVE.equals(phase)
                || SpojniceRoomRepository.PHASE_FOLLOWUP.equals(phase)) {
            return ROUND_DURATION_MS;
        }
        if (SpojniceRoomRepository.PHASE_ROUND_OVER.equals(phase)) {
            return RESULT_VISIBLE_MS;
        }
        return 0L;
    }

    private static int secondsFromMillis(long millis) {
        return (int) Math.max(0, Math.ceil(millis / 1000.0));
    }

    @NonNull
    private SpojnicePuzzle puzzleForRound(int round) {
        if (roundPuzzles.size() < TOTAL_ROUNDS) {
            roundPuzzles = buildFixedRoundPuzzles(List.of());
        }
        int index = round - 1;
        if (index < 0 || index >= roundPuzzles.size()) {
            index = 0;
        }
        return roundPuzzles.get(index);
    }

    @NonNull
    private List<SpojnicePuzzle> buildFixedRoundPuzzles(@NonNull List<SpojnicePuzzle> remotePuzzles) {
        List<SpojnicePuzzle> defaults = SpojnicePuzzle.defaultPuzzles();
        List<SpojnicePuzzle> pool = new ArrayList<>(TOTAL_ROUNDS);
        pool.add(resolvePuzzle(remotePuzzles, defaults, ROUND_ONE_CRITERION, 0));
        pool.add(resolvePuzzle(remotePuzzles, defaults, ROUND_TWO_CRITERION, 1));
        return pool;
    }

    @NonNull
    private static SpojnicePuzzle resolvePuzzle(
            @NonNull List<SpojnicePuzzle> remotePuzzles,
            @NonNull List<SpojnicePuzzle> defaultPuzzles,
            @NonNull String criterion,
            int defaultIndex
    ) {
        for (SpojnicePuzzle puzzle : remotePuzzles) {
            if (criterion.equals(puzzle.getCriterion())) {
                return puzzle;
            }
        }
        for (SpojnicePuzzle puzzle : defaultPuzzles) {
            if (criterion.equals(puzzle.getCriterion())) {
                return puzzle;
            }
        }
        return defaultPuzzles.get(defaultIndex);
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
