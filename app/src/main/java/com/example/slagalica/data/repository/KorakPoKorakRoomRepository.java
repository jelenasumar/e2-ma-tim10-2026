package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.korakpokorak.KorakPoKorakPuzzle;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.Normalizer;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class KorakPoKorakRoomRepository {

    public static final String PHASE_ACTIVE = "ACTIVE";
    public static final String PHASE_BONUS = "BONUS";
    public static final String PHASE_ROUND_OVER = "ROUND_OVER";
    public static final String PHASE_GAME_OVER = "GAME_OVER";

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";
    private static final String KORAK_PO_KORAK = "korak_po_korak";

    private static final int TOTAL_ROUNDS = 2;
    private static final int STEP_COUNT = 7;
    private static final int BONUS_POINTS = 5;
    private static final int[] STEP_POINTS = {20, 18, 16, 14, 12, 10, 8};

    private static final long STEP_DURATION_MS = 10_000L;
    private static final long BONUS_DURATION_MS = 10_000L;
    private static final long RESULT_VISIBLE_MS = 2_000L;

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
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Korak po korak nije ucitan.");
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                onChanged.accept(snapshot);
            }
        });
    }

    public void initializeIfNeeded(
            @NonNull RoomSession room,
            @NonNull KorakPoKorakPuzzle puzzle,
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
                    puzzle,
                    room.getHostTotalScore(),
                    room.getGuestTotalScore()
            ));

            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Korak po korak ne moze da se pokrene.")
        );
    }

    public void submitAnswer(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull String answer,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = stringOrDefault(snapshot.getString("phase"), PHASE_ACTIVE);
            if (!PHASE_ACTIVE.equals(phase) && !PHASE_BONUS.equals(phase)) {
                return null;
            }

            String expectedPlayerUid = PHASE_BONUS.equals(phase)
                    ? stringOrEmpty(snapshot.getString("bonusPlayerUid"))
                    : stringOrEmpty(snapshot.getString("activePlayerUid"));

            if (myUid.isEmpty() || !myUid.equals(expectedPlayerUid)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("lastSubmitMillis", System.currentTimeMillis());

            boolean correct = answersMatch(answer, stringOrEmpty(snapshot.getString("answer")));

            if (correct) {
                int points;
                if (PHASE_BONUS.equals(phase)) {
                    points = BONUS_POINTS;
                } else {
                    int currentStepIndex = intOrZero(snapshot.get("currentStepIndex"));
                    points = pointsForStep(currentStepIndex);
                    updates.put("solvedStepIndex", currentStepIndex);
                }

                addScore(snapshot, updates, expectedPlayerUid, points);
                updates.put("lastAnswerCorrect", true);
                applyRoundOver(updates);
            } else {
                updates.put("lastAnswerCorrect", false);

                if (PHASE_BONUS.equals(phase)) {
                    applyRoundOver(updates);
                }
            }

            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Odgovor nije poslat.")
        );
    }

    public void handleExpiredPhase(
            @NonNull String roomId,
            @NonNull KorakPoKorakPuzzle nextPuzzle,
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

            if (PHASE_GAME_OVER.equals(phase) || endsAt > System.currentTimeMillis()) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();

            if (PHASE_ACTIVE.equals(phase)) {
                int currentStepIndex = intOrZero(snapshot.get("currentStepIndex"));

                if (currentStepIndex < STEP_COUNT - 1) {
                    updates.put("currentStepIndex", currentStepIndex + 1);
                    updates.put("phaseEndsAtMillis", System.currentTimeMillis() + STEP_DURATION_MS);
                } else {
                    updates.put("phase", PHASE_BONUS);
                    updates.put("bonusPlayerUid", bonusPlayerUid(snapshot));
                    updates.put("phaseEndsAtMillis", System.currentTimeMillis() + BONUS_DURATION_MS);
                }
            } else if (PHASE_BONUS.equals(phase)) {
                applyRoundOver(updates);
            } else if (PHASE_ROUND_OVER.equals(phase)) {
                int currentRound = intOrZero(snapshot.get("currentRound"));

                if (currentRound >= TOTAL_ROUNDS) {
                    updates.put("phase", PHASE_GAME_OVER);
                    updates.put("phaseEndsAtMillis", 0L);
                } else {
                    int nextRoundNumber = currentRound + 1;
                    updates.putAll(nextRoundState(
                            snapshot,
                            nextRoundNumber,
                            startingPlayerUid(snapshot, nextRoundNumber),
                            startingPlayerNumber(nextRoundNumber),
                            nextPuzzle
                    ));
                }
            }

            if (!updates.isEmpty()) {
                transaction.update(ref, updates);
            }

            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Faza igre nije promenjena.")
        );
    }

    @NonNull
    private DocumentReference stateRef(@NonNull String roomId) {
        return db.collection(ROOMS)
                .document(roomId)
                .collection(GAMES)
                .document(KORAK_PO_KORAK);
    }

    @NonNull
    private static Map<String, Object> baseRoundState(
            @NonNull RoomSession room,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull KorakPoKorakPuzzle puzzle,
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
        state.putAll(roundFields(round, activePlayerUid, activePlayerNumber, puzzle));
        return state;
    }

    @NonNull
    private static Map<String, Object> nextRoundState(
            @NonNull DocumentSnapshot snapshot,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull KorakPoKorakPuzzle puzzle
    ) {
        Map<String, Object> state = roundFields(round, activePlayerUid, activePlayerNumber, puzzle);
        state.put("playerOneScore", intOrZero(snapshot.get("playerOneScore")));
        state.put("playerTwoScore", intOrZero(snapshot.get("playerTwoScore")));
        return state;
    }

    @NonNull
    private static Map<String, Object> roundFields(
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull KorakPoKorakPuzzle puzzle
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currentRound", round);
        fields.put("activePlayerUid", activePlayerUid);
        fields.put("activePlayerNumber", activePlayerNumber);
        fields.put("bonusPlayerUid", "");
        fields.put("phase", PHASE_ACTIVE);
        fields.put("phaseEndsAtMillis", System.currentTimeMillis() + STEP_DURATION_MS);
        fields.put("answer", puzzle.getAnswer());
        fields.put("steps", puzzle.getSteps());
        fields.put("currentStepIndex", 0);
        fields.put("solvedStepIndex", -1);
        fields.put("lastAnswerCorrect", false);
        return fields;
    }

    private static void applyRoundOver(@NonNull Map<String, Object> updates) {
        updates.put("phase", PHASE_ROUND_OVER);
        updates.put("phaseEndsAtMillis", System.currentTimeMillis() + RESULT_VISIBLE_MS);
    }

    private static void addScore(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates,
            @NonNull String scoringUid,
            int points
    ) {
        int playerOneScore = intOrZero(snapshot.get("playerOneScore"));
        int playerTwoScore = intOrZero(snapshot.get("playerTwoScore"));

        if (points > 0 && scoringUid.equals(snapshot.getString("playerOneUid"))) {
            playerOneScore += points;
        } else if (points > 0 && scoringUid.equals(snapshot.getString("playerTwoUid"))) {
            playerTwoScore += points;
        }

        updates.put("playerOneScore", playerOneScore);
        updates.put("playerTwoScore", playerTwoScore);
    }

    @NonNull
    private static String bonusPlayerUid(@NonNull DocumentSnapshot snapshot) {
        String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));

        return activeUid.equals(playerOneUid) ? playerTwoUid : playerOneUid;
    }

    @NonNull
    private static String startingPlayerUid(@NonNull DocumentSnapshot snapshot, int round) {
        return startingPlayerNumber(round) == 1
                ? stringOrEmpty(snapshot.getString("playerOneUid"))
                : stringOrEmpty(snapshot.getString("playerTwoUid"));
    }

    private static int startingPlayerNumber(int round) {
        return round % 2 == 0 ? 2 : 1;
    }

    private static int pointsForStep(int stepIndex) {
        if (stepIndex < 0 || stepIndex >= STEP_POINTS.length) {
            return 0;
        }
        return STEP_POINTS[stepIndex];
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