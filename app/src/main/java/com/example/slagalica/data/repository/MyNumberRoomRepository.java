package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.mynumber.MyNumberExpressionResult;
import com.example.slagalica.utils.MyNumberExpressionEvaluator;
import com.example.slagalica.utils.MyNumberGenerator;
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

public final class MyNumberRoomRepository {

    public static final String PHASE_TARGET = "TARGET";
    public static final String PHASE_NUMBERS = "NUMBERS";
    public static final String PHASE_SOLVING = "SOLVING";
    public static final String PHASE_ROUND_OVER = "ROUND_OVER";
    public static final String PHASE_GAME_OVER = "GAME_OVER";

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";
    private static final String MY_NUMBER = "my_number";

    private static final int TOTAL_ROUNDS = 2;
    private static final int EXACT_POINTS = 10;
    private static final int CLOSER_POINTS = 5;

    private static final long ROUND_DURATION_MS = 60_000L;
    private static final long NUMBERS_AUTO_REVEAL_MS = 5_000L;
    private static final long RESULT_VISIBLE_MS = 2_000L;

    private static final double EPSILON = 0.000001d;

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
                        : "Moj broj nije ucitan.");
                return;
            }

            if (snapshot != null && snapshot.exists()) {
                onChanged.accept(snapshot);
            }
        });
    }

    public void initializeIfNeeded(
            @NonNull RoomSession room,
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
                    room.getHostTotalScore(),
                    room.getGuestTotalScore()
            ));

            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Moj broj ne moze da se pokrene.")
        );
    }

    public void stopTarget(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canActivePlayerAct(snapshot, myUid, PHASE_TARGET)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("targetNumber", MyNumberGenerator.generateTargetNumber());
            updates.put("targetRevealed", true);
            updates.put("phase", PHASE_NUMBERS);
            updates.put("numbersAutoRevealAtMillis", System.currentTimeMillis() + NUMBERS_AUTO_REVEAL_MS);
            updates.put("lastMoveMillis", System.currentTimeMillis());

            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Trazeni broj nije zaustavljen.")
        );
    }

    public void stopNumbers(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canActivePlayerAct(snapshot, myUid, PHASE_NUMBERS)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            revealNumbers(updates);

            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Brojevi nisu zaustavljeni.")
        );
    }

    public void submitExpression(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull String expression,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()
                    || !PHASE_SOLVING.equals(stringOrDefault(snapshot.getString("phase"), PHASE_TARGET))) {
                return null;
            }

            String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
            String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));

            if (!myUid.equals(playerOneUid) && !myUid.equals(playerTwoUid)) {
                return null;
            }

            List<Integer> numbers = intList(snapshot.get("numbers"));
            MyNumberExpressionResult result = MyNumberExpressionEvaluator.evaluate(expression, numbers);

            Map<String, Object> updates = new HashMap<>();
            if (myUid.equals(playerOneUid)) {
                updates.put("playerOneExpression", expression);
                updates.put("playerOneResult", result.isValid() ? result.getValue() : 0d);
                updates.put("playerOneSubmitted", true);
                updates.put("playerOneExpressionValid", result.isValid());
            } else {
                updates.put("playerTwoExpression", expression);
                updates.put("playerTwoResult", result.isValid() ? result.getValue() : 0d);
                updates.put("playerTwoSubmitted", true);
                updates.put("playerTwoExpressionValid", result.isValid());
            }

            boolean playerOneSubmitted = boolOrFalse(snapshot.get("playerOneSubmitted"))
                    || myUid.equals(playerOneUid);
            boolean playerTwoSubmitted = boolOrFalse(snapshot.get("playerTwoSubmitted"))
                    || myUid.equals(playerTwoUid);

            String abandonedByUid = stringOrEmpty(snapshot.getString("abandonedByUid"));
            if (playerOneUid.equals(abandonedByUid)) {
                playerOneSubmitted = true;
            }
            if (playerTwoUid.equals(abandonedByUid)) {
                playerTwoSubmitted = true;
            }

            if (playerOneSubmitted && playerTwoSubmitted) {
                applyRoundScoring(snapshot, updates);
            }

            updates.put("lastSubmitMillis", System.currentTimeMillis());

            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Izraz nije poslat.")
        );
    }

    public void handleExpiredPhase(
            @NonNull String roomId,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = stringOrDefault(snapshot.getString("phase"), PHASE_TARGET);

            Map<String, Object> updates = new HashMap<>();

            if (PHASE_GAME_OVER.equals(phase)) {
                return null;
            }

            if (PHASE_TARGET.equals(phase)) {
                long roundEndsAt = longOrZero(snapshot.get("roundEndsAtMillis"));
                if (roundEndsAt > 0L && roundEndsAt <= System.currentTimeMillis()) {
                    applyRoundScoring(snapshot, updates);
                }
            } else if (PHASE_NUMBERS.equals(phase)) {
                long autoRevealAt = longOrZero(snapshot.get("numbersAutoRevealAtMillis"));
                if (autoRevealAt > 0L && autoRevealAt <= System.currentTimeMillis()) {
                    revealNumbers(updates);
                }
            } else if (PHASE_SOLVING.equals(phase)) {
                long roundEndsAt = longOrZero(snapshot.get("roundEndsAtMillis"));
                if (roundEndsAt > 0L && roundEndsAt <= System.currentTimeMillis()) {
                    applyRoundScoring(snapshot, updates);
                }
            } else if (PHASE_ROUND_OVER.equals(phase)) {
                long phaseEndsAt = longOrZero(snapshot.get("phaseEndsAtMillis"));
                if (phaseEndsAt > 0L && phaseEndsAt <= System.currentTimeMillis()) {
                    int currentRound = intOrZero(snapshot.get("currentRound"));

                    if (currentRound >= TOTAL_ROUNDS) {
                        updates.put("phase", PHASE_GAME_OVER);
                        updates.put("phaseEndsAtMillis", 0L);
                    } else {
                        int nextRoundNumber = currentRound + 1;
                        String nextActiveUid = startingPlayerUid(snapshot, nextRoundNumber);
                        updates.putAll(nextRoundState(
                                snapshot,
                                nextRoundNumber,
                                nextActiveUid,
                                playerNumberForUid(snapshot, nextActiveUid)
                        ));
                    }
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
                .document(MY_NUMBER);
    }

    @NonNull
    private static Map<String, Object> baseRoundState(
            @NonNull RoomSession room,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
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
        state.putAll(roundFields(round, activePlayerUid, activePlayerNumber));
        return state;
    }

    @NonNull
    private static Map<String, Object> nextRoundState(
            @NonNull DocumentSnapshot snapshot,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber
    ) {
        Map<String, Object> state = roundFields(round, activePlayerUid, activePlayerNumber);
        state.put("playerOneScore", intOrZero(snapshot.get("playerOneScore")));
        state.put("playerTwoScore", intOrZero(snapshot.get("playerTwoScore")));
        return state;
    }

    @NonNull
    private static Map<String, Object> roundFields(
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currentRound", round);
        fields.put("activePlayerUid", activePlayerUid);
        fields.put("activePlayerNumber", activePlayerNumber);
        fields.put("phase", PHASE_TARGET);
        fields.put("targetNumber", 0);
        fields.put("targetRevealed", false);
        fields.put("numbers", new ArrayList<Integer>());
        fields.put("numbersRevealed", false);
        fields.put("numbersAutoRevealAtMillis", 0L);
        fields.put("roundEndsAtMillis", System.currentTimeMillis() + ROUND_DURATION_MS);
        fields.put("phaseEndsAtMillis", 0L);
        fields.put("playerOneExpression", "");
        fields.put("playerTwoExpression", "");
        fields.put("playerOneResult", 0d);
        fields.put("playerTwoResult", 0d);
        fields.put("playerOneSubmitted", false);
        fields.put("playerTwoSubmitted", false);
        fields.put("playerOneExpressionValid", false);
        fields.put("playerTwoExpressionValid", false);
        fields.put("roundWinnerUid", "");
        fields.put("roundWinnerPoints", 0);
        fields.put("abandonedByUid", "");
        return fields;
    }

    public void handleAbandonedPlayer(
            @NonNull RoomSession room,
            @NonNull Consumer<String> onError
    ) {
        String abandonedUid = room.getAbandonedByUid();
        if (abandonedUid.isEmpty()) {
            return;
        }

        DocumentReference ref = stateRef(room.getRoomId());

        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = stringOrDefault(snapshot.getString("phase"), PHASE_TARGET);
            if (PHASE_GAME_OVER.equals(phase)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("abandonedByUid", abandonedUid);

            String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
            if ((PHASE_TARGET.equals(phase) || PHASE_NUMBERS.equals(phase))
                    && abandonedUid.equals(activeUid)) {
                applyRoundScoring(snapshot, updates);
            } else if (PHASE_SOLVING.equals(phase)) {
                markAbandonedPlayerSubmitted(snapshot, updates, abandonedUid);

                boolean playerOneSubmitted = boolOrFalse(snapshot.get("playerOneSubmitted"))
                        || Boolean.TRUE.equals(updates.get("playerOneSubmitted"));
                boolean playerTwoSubmitted = boolOrFalse(snapshot.get("playerTwoSubmitted"))
                        || Boolean.TRUE.equals(updates.get("playerTwoSubmitted"));

                if (playerOneSubmitted && playerTwoSubmitted) {
                    applyRoundScoring(snapshot, updates);
                }
            }

            if (!updates.isEmpty()) {
                transaction.update(ref, updates);
            }

            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null
                        ? error.getMessage()
                        : "Napušteni igrač nije obrađen.")
        );
    }

    private static void markAbandonedPlayerSubmitted(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates,
            @NonNull String abandonedUid
    ) {
        if (abandonedUid.equals(snapshot.getString("playerOneUid"))) {
            updates.put("playerOneSubmitted", true);
            updates.put("playerOneExpressionValid", false);
            updates.put("playerOneResult", 0d);
        } else if (abandonedUid.equals(snapshot.getString("playerTwoUid"))) {
            updates.put("playerTwoSubmitted", true);
            updates.put("playerTwoExpressionValid", false);
            updates.put("playerTwoResult", 0d);
        }
    }

    private static int playerNumberForUid(@NonNull DocumentSnapshot snapshot, @NonNull String uid) {
        if (uid.equals(snapshot.getString("playerOneUid"))) {
            return 1;
        }
        if (uid.equals(snapshot.getString("playerTwoUid"))) {
            return 2;
        }
        return 1;
    }

    private static boolean canActivePlayerAct(
            @NonNull DocumentSnapshot snapshot,
            @NonNull String myUid,
            @NonNull String expectedPhase
    ) {
        return snapshot.exists()
                && expectedPhase.equals(stringOrDefault(snapshot.getString("phase"), PHASE_TARGET))
                && !myUid.isEmpty()
                && myUid.equals(snapshot.getString("activePlayerUid"));
    }

    private static void revealNumbers(@NonNull Map<String, Object> updates) {
        updates.put("numbers", MyNumberGenerator.generateNumbers());
        updates.put("numbersRevealed", true);
        updates.put("phase", PHASE_SOLVING);
        updates.put("numbersAutoRevealAtMillis", 0L);
        updates.put("lastMoveMillis", System.currentTimeMillis());
    }

    private static void applyRoundScoring(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates
    ) {
        int target = intOrZero(snapshot.get("targetNumber"));
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        String activePlayerUid = stringOrEmpty(snapshot.getString("activePlayerUid"));

        boolean playerOneSubmitted = boolOrFalse(snapshot.get("playerOneSubmitted"));
        boolean playerTwoSubmitted = boolOrFalse(snapshot.get("playerTwoSubmitted"));

        if (updates.containsKey("playerOneSubmitted")) {
            playerOneSubmitted = true;
        }
        if (updates.containsKey("playerTwoSubmitted")) {
            playerTwoSubmitted = true;
        }

        boolean playerOneValid = boolOrFalse(snapshot.get("playerOneExpressionValid"));
        boolean playerTwoValid = boolOrFalse(snapshot.get("playerTwoExpressionValid"));

        if (updates.containsKey("playerOneExpressionValid")) {
            playerOneValid = Boolean.TRUE.equals(updates.get("playerOneExpressionValid"));
        }
        if (updates.containsKey("playerTwoExpressionValid")) {
            playerTwoValid = Boolean.TRUE.equals(updates.get("playerTwoExpressionValid"));
        }

        double playerOneResult = doubleOrZero(snapshot.get("playerOneResult"));
        double playerTwoResult = doubleOrZero(snapshot.get("playerTwoResult"));

        if (updates.containsKey("playerOneResult")) {
            playerOneResult = doubleOrZero(updates.get("playerOneResult"));
        }
        if (updates.containsKey("playerTwoResult")) {
            playerTwoResult = doubleOrZero(updates.get("playerTwoResult"));
        }

        boolean playerOneExact = playerOneSubmitted && playerOneValid && equalsTarget(playerOneResult, target);
        boolean playerTwoExact = playerTwoSubmitted && playerTwoValid && equalsTarget(playerTwoResult, target);

        String winnerUid = "";
        int winnerPoints = 0;

        if (activePlayerUid.equals(playerOneUid)) {
            if (playerOneExact) {
                winnerUid = playerOneUid;
                winnerPoints = EXACT_POINTS;
            } else if (playerTwoExact) {
                winnerUid = playerTwoUid;
                winnerPoints = EXACT_POINTS;
            } else {
                winnerUid = closerWinner(
                        playerOneUid,
                        playerTwoUid,
                        activePlayerUid,
                        playerOneSubmitted && playerOneValid,
                        playerTwoSubmitted && playerTwoValid,
                        playerOneResult,
                        playerTwoResult,
                        target
                );
                winnerPoints = winnerUid.isEmpty() ? 0 : CLOSER_POINTS;
            }
        } else {
            if (playerTwoExact) {
                winnerUid = playerTwoUid;
                winnerPoints = EXACT_POINTS;
            } else if (playerOneExact) {
                winnerUid = playerOneUid;
                winnerPoints = EXACT_POINTS;
            } else {
                winnerUid = closerWinner(
                        playerOneUid,
                        playerTwoUid,
                        activePlayerUid,
                        playerOneSubmitted && playerOneValid,
                        playerTwoSubmitted && playerTwoValid,
                        playerOneResult,
                        playerTwoResult,
                        target
                );
                winnerPoints = winnerUid.isEmpty() ? 0 : CLOSER_POINTS;
            }
        }

        int playerOneScore = intOrZero(snapshot.get("playerOneScore"));
        int playerTwoScore = intOrZero(snapshot.get("playerTwoScore"));

        if (winnerPoints > 0 && winnerUid.equals(playerOneUid)) {
            playerOneScore += winnerPoints;
        } else if (winnerPoints > 0 && winnerUid.equals(playerTwoUid)) {
            playerTwoScore += winnerPoints;
        }

        updates.put("playerOneSubmitted", playerOneSubmitted);
        updates.put("playerTwoSubmitted", playerTwoSubmitted);
        updates.put("playerOneScore", playerOneScore);
        updates.put("playerTwoScore", playerTwoScore);
        updates.put("roundWinnerUid", winnerUid);
        updates.put("roundWinnerPoints", winnerPoints);
        updates.put("phase", PHASE_ROUND_OVER);
        updates.put("phaseEndsAtMillis", System.currentTimeMillis() + RESULT_VISIBLE_MS);
    }

    @NonNull
    private static String closerWinner(
            @NonNull String playerOneUid,
            @NonNull String playerTwoUid,
            @NonNull String activePlayerUid,
            boolean playerOneHasResult,
            boolean playerTwoHasResult,
            double playerOneResult,
            double playerTwoResult,
            int target
    ) {
        if (!playerOneHasResult && !playerTwoHasResult) {
            return "";
        }

        if (playerOneHasResult && !playerTwoHasResult) {
            return playerOneUid;
        }

        if (!playerOneHasResult && playerTwoHasResult) {
            return playerTwoUid;
        }

        double playerOneDiff = Math.abs(playerOneResult - target);
        double playerTwoDiff = Math.abs(playerTwoResult - target);

        if (playerOneDiff < playerTwoDiff) {
            return playerOneUid;
        }

        if (playerTwoDiff < playerOneDiff) {
            return playerTwoUid;
        }

        boolean sameNonZeroResult = Math.abs(playerOneResult - playerTwoResult) < EPSILON
                && Math.abs(playerOneResult) >= EPSILON;

        if (sameNonZeroResult) {
            return activePlayerUid;
        }

        return "";
    }

    private static boolean equalsTarget(double value, int target) {
        return Math.abs(value - target) < EPSILON;
    }

    @NonNull
    private static String startingPlayerUid(@NonNull DocumentSnapshot snapshot, int round) {
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        String abandonedByUid = stringOrEmpty(snapshot.getString("abandonedByUid"));

        String preferred = startingPlayerNumber(round) == 1 ? playerOneUid : playerTwoUid;
        if (!preferred.equals(abandonedByUid)) {
            return preferred;
        }

        return preferred.equals(playerOneUid) ? playerTwoUid : playerOneUid;
    }

    private static int startingPlayerNumber(int round) {
        return round % 2 == 0 ? 2 : 1;
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
}