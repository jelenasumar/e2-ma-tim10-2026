package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.spojnice.SpojnicePuzzle;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class SpojniceRoomRepository {

    public static final String PHASE_ACTIVE = "ACTIVE";
    public static final String PHASE_FOLLOWUP = "FOLLOWUP";
    public static final String PHASE_ROUND_OVER = "ROUND_OVER";
    public static final String PHASE_GAME_OVER = "GAME_OVER";

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";
    private static final String SPOJNICE = "spojnice";

    private static final int PAIRS_PER_ROUND = 5;
    private static final int POINTS_PER_PAIR = 2;
    private static final int TOTAL_ROUNDS = 2;
    private static final long ROUND_DURATION_MS = 30_000L;
    private static final long RESULT_VISIBLE_MS = 2_500L;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public ListenerRegistration listenState(
            @NonNull String roomId,
            @NonNull Consumer<DocumentSnapshot> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return stateRef(roomId).addSnapshotListener((snapshot, error) -> {
            if (error != null) {
                onError.accept(error.getMessage() != null ? error.getMessage() : "Spojnice nisu učitane.");
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                onChanged.accept(snapshot);
            }
        });
    }

    public void initializeIfNeeded(
            @NonNull RoomSession room,
            @NonNull SpojnicePuzzle.ShuffledRound round,
            @NonNull String criterion,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(room.getRoomId());
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (snapshot.exists()) {
                return null;
            }
            transaction.set(ref, baseRoundState(
                    room,
                    1,
                    room.getHostUid(),
                    1,
                    round,
                    criterion,
                    room.getHostTotalScore(),
                    room.getGuestTotalScore()
            ));
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Spojnice ne mogu da se pokrenu.")
        );
    }

    public void submitPair(
            @NonNull String roomId,
            @NonNull String myUid,
            int leftIndex,
            int selectedRightIndex,
            @NonNull Consumer<String> onError,
            @NonNull Consumer<Boolean> onCorrect
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = stringOrDefault(snapshot.getString("phase"), PHASE_ACTIVE);
            if (!PHASE_ACTIVE.equals(phase) && !PHASE_FOLLOWUP.equals(phase)) {
                return null;
            }

            String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
            String followupUid = stringOrEmpty(snapshot.getString("followupPlayerUid"));
            if (PHASE_ACTIVE.equals(phase) && !myUid.equals(activeUid)) {
                return null;
            }
            if (PHASE_FOLLOWUP.equals(phase) && !myUid.equals(followupUid)) {
                return null;
            }

            List<Integer> connected = intList(snapshot.get("connectedLeft"));
            List<Integer> attempted = intList(snapshot.get("attemptedLeft"));
            List<Integer> usedRight = intList(snapshot.get("usedRightIndices"));
            List<Integer> followupLocked = intList(snapshot.get("followupLockedLeft"));
            List<Integer> answers = intList(snapshot.get("answers"));
            int currentLeftIndex = intOrZero(snapshot.get("currentLeftIndex"));

            if (leftIndex < 0 || leftIndex >= PAIRS_PER_ROUND || connected.contains(leftIndex)) {
                return null;
            }

            if (PHASE_ACTIVE.equals(phase)) {
                if (leftIndex != currentLeftIndex || attempted.contains(leftIndex)) {
                    return null;
                }
            } else if (PHASE_FOLLOWUP.equals(phase) && followupLocked.contains(leftIndex)) {
                return null;
            }

            boolean correct = leftIndex < answers.size() && answers.get(leftIndex) == selectedRightIndex;
            int playerOneScore = intOrZero(snapshot.get("playerOneScore"));
            int playerTwoScore = intOrZero(snapshot.get("playerTwoScore"));
            String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
            String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));

            if (PHASE_ACTIVE.equals(phase) && !attempted.contains(leftIndex)) {
                attempted = new ArrayList<>(attempted);
                attempted.add(leftIndex);
            }
            if (PHASE_FOLLOWUP.equals(phase) && !correct) {
                followupLocked = new ArrayList<>(followupLocked);
                if (!followupLocked.contains(leftIndex)) {
                    followupLocked.add(leftIndex);
                }
            }
            if (correct) {
                connected = new ArrayList<>(connected);
                connected.add(leftIndex);
                if (!usedRight.contains(selectedRightIndex)) {
                    usedRight = new ArrayList<>(usedRight);
                    usedRight.add(selectedRightIndex);
                }
                if (myUid.equals(playerOneUid)) {
                    playerOneScore += POINTS_PER_PAIR;
                } else if (myUid.equals(playerTwoUid)) {
                    playerTwoScore += POINTS_PER_PAIR;
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("connectedLeft", connected);
            updates.put("attemptedLeft", attempted);
            updates.put("usedRightIndices", usedRight);
            updates.put("followupLockedLeft", followupLocked);
            updates.put("playerOneScore", playerOneScore);
            updates.put("playerTwoScore", playerTwoScore);
            updates.put("lastSubmitMillis", System.currentTimeMillis());

            if (PHASE_ACTIVE.equals(phase)) {
                int nextLeftIndex = currentLeftIndex + 1;
                updates.put("currentLeftIndex", nextLeftIndex);
                if (nextLeftIndex >= PAIRS_PER_ROUND) {
                    applyFollowupOrRoundOver(snapshot, updates, connected, attempted);
                }
            } else if (allRemainingConnected(connected)) {
                applyRoundOver(updates);
            } else if (allRemainingLockedOrConnected(connected, followupLocked)) {
                applyRoundOver(updates);
            }

            transaction.update(ref, updates);
            return correct;
        }).addOnSuccessListener(correct -> {
            if (correct != null) {
                onCorrect.accept(correct);
            }
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Spojnica nije poslata.")
        );
    }

    public void handleExpiredPhase(
            @NonNull String roomId,
            @NonNull SpojnicePuzzle.ShuffledRound nextRound,
            @NonNull String nextCriterion,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = stringOrDefault(snapshot.getString("phase"), PHASE_ACTIVE);
            long endsAt = longOrZero(snapshot.get("phaseEndsAtMillis"));
            if (PHASE_GAME_OVER.equals(phase)) {
                return null;
            }
            if (endsAt > 0L && endsAt > System.currentTimeMillis()) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (PHASE_ACTIVE.equals(phase)) {
                List<Integer> connected = intList(snapshot.get("connectedLeft"));
                List<Integer> attempted = intList(snapshot.get("attemptedLeft"));
                applyFollowupOrRoundOver(snapshot, updates, connected, attempted);
            } else if (PHASE_FOLLOWUP.equals(phase)) {
                applyRoundOver(updates);
            } else if (PHASE_ROUND_OVER.equals(phase)) {
                int currentRound = intOrZero(snapshot.get("currentRound"));
                if (currentRound >= TOTAL_ROUNDS) {
                    updates.put("phase", PHASE_GAME_OVER);
                    updates.put("phaseEndsAtMillis", 0L);
                } else {
                    String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
                    updates.putAll(nextRoundState(snapshot, currentRound + 1, playerTwoUid, 2, nextRound, nextCriterion));
                }
            }

            if (!updates.isEmpty()) {
                transaction.update(ref, updates);
            }
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Faza igre nije promenjena.")
        );
    }

    @NonNull
    private DocumentReference stateRef(@NonNull String roomId) {
        return db.collection(ROOMS)
                .document(roomId)
                .collection(GAMES)
                .document(SPOJNICE);
    }

    private static void applyFollowupOrRoundOver(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates,
            @NonNull List<Integer> connected,
            @NonNull List<Integer> attempted
    ) {
        boolean hasUnconnected = false;
        for (int i = 0; i < PAIRS_PER_ROUND; i++) {
            if (!connected.contains(i)) {
                hasUnconnected = true;
                break;
            }
        }
        if (!hasUnconnected) {
            applyRoundOver(updates);
        } else {
            updates.put("phase", PHASE_FOLLOWUP);
            updates.put("followupPlayerUid", followupPlayerUid(snapshot));
            updates.put("followupLockedLeft", new ArrayList<Integer>());
            updates.put("phaseEndsAtMillis", System.currentTimeMillis() + ROUND_DURATION_MS);
        }
    }

    private static void applyRoundOver(@NonNull Map<String, Object> updates) {
        updates.put("phase", PHASE_ROUND_OVER);
        updates.put("phaseEndsAtMillis", System.currentTimeMillis() + RESULT_VISIBLE_MS);
    }

    @NonNull
    private static String followupPlayerUid(@NonNull DocumentSnapshot snapshot) {
        String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        return activeUid.equals(playerOneUid) ? playerTwoUid : playerOneUid;
    }

    private static boolean allRemainingConnected(@NonNull List<Integer> connected) {
        return connected.size() >= PAIRS_PER_ROUND;
    }

    private static boolean allRemainingLockedOrConnected(
            @NonNull List<Integer> connected,
            @NonNull List<Integer> followupLocked
    ) {
        for (int i = 0; i < PAIRS_PER_ROUND; i++) {
            if (!connected.contains(i) && !followupLocked.contains(i)) {
                return false;
            }
        }
        return true;
    }

    @NonNull
    private static Map<String, Object> baseRoundState(
            @NonNull RoomSession room,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull SpojnicePuzzle.ShuffledRound puzzleRound,
            @NonNull String criterion,
            int playerOneScore,
            int playerTwoScore
    ) {
        Map<String, Object> state = new HashMap<>();
        state.put("playerOneUid", room.getHostUid());
        state.put("playerTwoUid", room.getGuestUid());
        state.put("playerOneUsername", room.getHostUsername());
        state.put("playerTwoUsername", room.getGuestUsername());
        state.put("playerOneScore", playerOneScore);
        state.put("playerTwoScore", playerTwoScore);
        state.putAll(roundFields(round, activePlayerUid, activePlayerNumber, puzzleRound, criterion));
        return state;
    }

    @NonNull
    private static Map<String, Object> nextRoundState(
            @NonNull DocumentSnapshot snapshot,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull SpojnicePuzzle.ShuffledRound puzzleRound,
            @NonNull String criterion
    ) {
        Map<String, Object> state = roundFields(round, activePlayerUid, activePlayerNumber, puzzleRound, criterion);
        state.put("playerOneScore", intOrZero(snapshot.get("playerOneScore")));
        state.put("playerTwoScore", intOrZero(snapshot.get("playerTwoScore")));
        return state;
    }

    @NonNull
    private static Map<String, Object> roundFields(
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull SpojnicePuzzle.ShuffledRound puzzleRound,
            @NonNull String criterion
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currentRound", round);
        fields.put("activePlayerUid", activePlayerUid);
        fields.put("activePlayerNumber", activePlayerNumber);
        fields.put("followupPlayerUid", "");
        fields.put("phase", PHASE_ACTIVE);
        fields.put("phaseEndsAtMillis", System.currentTimeMillis() + ROUND_DURATION_MS);
        fields.put("criterion", criterion);
        fields.put("leftTerms", puzzleRound.getLeftTerms());
        fields.put("rightTerms", puzzleRound.getRightTerms());
        fields.put("answers", puzzleRound.getAnswers());
        fields.put("connectedLeft", new ArrayList<Integer>());
        fields.put("attemptedLeft", new ArrayList<Integer>());
        fields.put("usedRightIndices", new ArrayList<Integer>());
        fields.put("followupLockedLeft", new ArrayList<Integer>());
        fields.put("currentLeftIndex", 0);
        return fields;
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

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static int intOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }
}
