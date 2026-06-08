package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.skocko.SkockoAttemptResult;
import com.example.slagalica.model.skocko.SkockoSymbol;
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

public final class SkockoRoomRepository {

    public static final String PHASE_ROUND = "ROUND";
    public static final String PHASE_BONUS = "BONUS";
    public static final String PHASE_ROUND_OVER = "ROUND_OVER";
    public static final String PHASE_GAME_OVER = "GAME_OVER";

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";
    private static final String SKOCKO = "skocko";
    private static final int COMBINATION_SIZE = 4;
    private static final int MAX_ATTEMPTS = 6;
    private static final int TOTAL_ROUNDS = 2;
    private static final int BONUS_SCORE = 10;
    private static final long ROUND_DURATION_MILLIS = 30_000L;
    private static final long BONUS_DURATION_MILLIS = 10_000L;
    private static final long RESULT_VISIBLE_MILLIS = 5_000L;

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
                onError.accept(error.getMessage() != null ? error.getMessage() : "Skočko nije učitan.");
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                onChanged.accept(snapshot);
            }
        });
    }

    public void initializeIfNeeded(
            @NonNull RoomSession room,
            @NonNull List<SkockoSymbol> secretCombination,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(room.getRoomId());
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (snapshot.exists()) {
                return null;
            }
            Map<String, Object> state = baseRoundState(
                    room,
                    1,
                    room.getHostUid(),
                    1,
                    secretCombination,
                    0,
                    0
            );
            transaction.set(ref, state);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Skočko ne može da se pokrene.")
        );
    }

    public void submitRoundAttempt(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull List<SkockoSymbol> attempt,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists() || !PHASE_ROUND.equals(snapshot.getString("phase"))) {
                return null;
            }
            if (!myUid.equals(snapshot.getString("activePlayerUid"))) {
                return null;
            }

            List<String> secret = stringList(snapshot.get("secretCombination"));
            SkockoAttemptResult result = evaluateAttempt(symbolNames(attempt), secret);
            List<Map<String, Object>> attempts = mapList(snapshot.get("attempts"));
            attempts.add(attemptMap(attempt, result));

            Map<String, Object> updates = new HashMap<>();
            updates.put("attempts", attempts);
            if (result.isSolved()) {
                int score = scoreForAttempt(attempts.size());
                completeRound(snapshot, updates, score, true);
            } else if (attempts.size() >= MAX_ATTEMPTS) {
                updates.put("phase", PHASE_BONUS);
                updates.put("bonusPlayerUid", bonusPlayerUid(snapshot));
                updates.put("phaseEndsAtMillis", System.currentTimeMillis() + BONUS_DURATION_MILLIS);
            }

            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Pokušaj nije poslat.")
        );
    }

    public void submitBonusAttempt(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull List<SkockoSymbol> attempt,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists() || !PHASE_BONUS.equals(snapshot.getString("phase"))) {
                return null;
            }
            if (!myUid.equals(snapshot.getString("bonusPlayerUid"))) {
                return null;
            }

            List<String> secret = stringList(snapshot.get("secretCombination"));
            SkockoAttemptResult result = evaluateAttempt(symbolNames(attempt), secret);
            Map<String, Object> updates = new HashMap<>();
            updates.put("bonusAttempt", symbolNames(attempt));
            completeRound(snapshot, updates, result.isSolved() ? BONUS_SCORE : 0, false);
            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Bonus pokušaj nije poslat.")
        );
    }

    public void handleExpiredPhase(
            @NonNull String roomId,
            @NonNull List<SkockoSymbol> nextSecretCombination,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                return null;
            }

            String phase = snapshot.getString("phase");
            long endsAt = longOrZero(snapshot.get("phaseEndsAtMillis"));
            if (PHASE_GAME_OVER.equals(phase) || endsAt > System.currentTimeMillis()) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (PHASE_ROUND.equals(phase)) {
                updates.put("phase", PHASE_BONUS);
                updates.put("bonusPlayerUid", bonusPlayerUid(snapshot));
                updates.put("phaseEndsAtMillis", System.currentTimeMillis() + BONUS_DURATION_MILLIS);
            } else if (PHASE_BONUS.equals(phase)) {
                updates.put("bonusAttempt", new ArrayList<String>());
                completeRound(snapshot, updates, 0, false);
            } else if (PHASE_ROUND_OVER.equals(phase)) {
                int currentRound = intOrZero(snapshot.get("currentRound"));
                if (currentRound >= TOTAL_ROUNDS) {
                    updates.put("phase", PHASE_GAME_OVER);
                    updates.put("phaseEndsAtMillis", 0L);
                } else {
                    String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
                    updates.putAll(nextRoundState(snapshot, currentRound + 1, playerTwoUid, 2, nextSecretCombination));
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
                .document(SKOCKO);
    }

    @NonNull
    private static Map<String, Object> baseRoundState(
            @NonNull RoomSession room,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull List<SkockoSymbol> secretCombination,
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
        state.putAll(roundFields(round, activePlayerUid, activePlayerNumber, secretCombination));
        return state;
    }

    @NonNull
    private static Map<String, Object> nextRoundState(
            @NonNull DocumentSnapshot snapshot,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull List<SkockoSymbol> secretCombination
    ) {
        Map<String, Object> state = roundFields(round, activePlayerUid, activePlayerNumber, secretCombination);
        state.put("playerOneScore", intOrZero(snapshot.get("playerOneScore")));
        state.put("playerTwoScore", intOrZero(snapshot.get("playerTwoScore")));
        return state;
    }

    @NonNull
    private static Map<String, Object> roundFields(
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull List<SkockoSymbol> secretCombination
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currentRound", round);
        fields.put("activePlayerUid", activePlayerUid);
        fields.put("activePlayerNumber", activePlayerNumber);
        fields.put("bonusPlayerUid", "");
        fields.put("phase", PHASE_ROUND);
        fields.put("phaseEndsAtMillis", System.currentTimeMillis() + ROUND_DURATION_MILLIS);
        fields.put("secretCombination", symbolNames(secretCombination));
        fields.put("attempts", new ArrayList<Map<String, Object>>());
        fields.put("bonusAttempt", new ArrayList<String>());
        return fields;
    }

    private static void completeRound(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates,
            int score,
            boolean activePlayerScored
    ) {
        String scoringUid = activePlayerScored
                ? stringOrEmpty(snapshot.getString("activePlayerUid"))
                : stringOrEmpty(snapshot.getString("bonusPlayerUid"));
        int playerOneScore = intOrZero(snapshot.get("playerOneScore"));
        int playerTwoScore = intOrZero(snapshot.get("playerTwoScore"));

        if (score > 0 && scoringUid.equals(snapshot.getString("playerOneUid"))) {
            playerOneScore += score;
        } else if (score > 0 && scoringUid.equals(snapshot.getString("playerTwoUid"))) {
            playerTwoScore += score;
        }

        updates.put("playerOneScore", playerOneScore);
        updates.put("playerTwoScore", playerTwoScore);
        updates.put("phase", PHASE_ROUND_OVER);
        updates.put("phaseEndsAtMillis", System.currentTimeMillis() + RESULT_VISIBLE_MILLIS);
    }

    @NonNull
    private static String bonusPlayerUid(@NonNull DocumentSnapshot snapshot) {
        String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        return activeUid.equals(playerOneUid) ? playerTwoUid : playerOneUid;
    }

    @NonNull
    private static Map<String, Object> attemptMap(
            @NonNull List<SkockoSymbol> symbols,
            @NonNull SkockoAttemptResult result
    ) {
        Map<String, Object> attempt = new HashMap<>();
        attempt.put("symbols", symbolNames(symbols));
        attempt.put("exactMatches", result.getExactMatches());
        attempt.put("partialMatches", result.getPartialMatches());
        return attempt;
    }

    @NonNull
    private static SkockoAttemptResult evaluateAttempt(
            @NonNull List<String> attempt,
            @NonNull List<String> secret
    ) {
        int exact = 0;
        int partial = 0;
        boolean[] usedAttempt = new boolean[COMBINATION_SIZE];
        boolean[] usedSecret = new boolean[COMBINATION_SIZE];

        for (int i = 0; i < COMBINATION_SIZE; i++) {
            if (attempt.get(i).equals(secret.get(i))) {
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
                if (!usedSecret[j] && attempt.get(i).equals(secret.get(j))) {
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
    private static List<String> symbolNames(@NonNull List<SkockoSymbol> symbols) {
        List<String> values = new ArrayList<>();
        for (SkockoSymbol symbol : symbols) {
            values.add(symbol.name());
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

    @NonNull
    private static List<Map<String, Object>> mapList(@Nullable Object raw) {
        List<Map<String, Object>> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value instanceof Map) {
                    Map<String, Object> copy = new HashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                        if (entry.getKey() != null) {
                            copy.put(String.valueOf(entry.getKey()), entry.getValue());
                        }
                    }
                    values.add(copy);
                }
            }
        }
        return values;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
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
