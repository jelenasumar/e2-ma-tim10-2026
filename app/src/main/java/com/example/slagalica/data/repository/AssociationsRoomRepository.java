package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.associations.AssociationColumn;
import com.example.slagalica.model.associations.AssociationPuzzle;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class AssociationsRoomRepository {

    public static final String PHASE_ACTIVE = "ACTIVE";
    public static final String PHASE_ROUND_OVER = "ROUND_OVER";
    public static final String PHASE_GAME_OVER = "GAME_OVER";

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";
    private static final String ASSOCIATIONS = "associations";
    private static final int TOTAL_ROUNDS = 2;
    private static final long ROUND_DURATION_MILLIS = 120_000L;
    private static final long RESULT_VISIBLE_MILLIS = 5_000L;
    private static final int COLUMN_BASE_SCORE = 2;
    private static final int FINAL_BASE_SCORE = 7;
    private static final int UNOPENED_COLUMN_SCORE = 6;

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
                onError.accept(error.getMessage() != null ? error.getMessage() : "Asocijacije nisu ucitane.");
                return;
            }
            if (snapshot != null && snapshot.exists()) {
                onChanged.accept(snapshot);
            }
        });
    }

    public void initializeIfNeeded(
            @NonNull RoomSession room,
            @NonNull AssociationPuzzle puzzle,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(room.getRoomId());
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (snapshot.exists()) {
                return null;
            }
            transaction.set(ref, baseRoundState(room, 1, room.getHostUid(), 1, puzzle, 0, 0));
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Asocijacije ne mogu da se pokrenu.")
        );
    }

    public void openField(
            @NonNull String roomId,
            @NonNull String myUid,
            int columnIndex,
            int clueIndex,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canPlay(snapshot, myUid)) {
                return null;
            }
            if (!isValidField(columnIndex, clueIndex)
                    || boolOrFalse(snapshot.get("fieldOpenedThisTurn"))) {
                return null;
            }

            List<Boolean> revealed = revealedFields(snapshot.get("revealedFields"));
            List<Boolean> solved = boolList(snapshot.get("solvedColumns"), AssociationPuzzle.COLUMN_COUNT);
            int fieldIndex = fieldIndex(columnIndex, clueIndex);
            if (solved.get(columnIndex) || revealed.get(fieldIndex)) {
                return null;
            }

            revealed.set(fieldIndex, true);
            Map<String, Object> updates = new HashMap<>();
            updates.put("revealedFields", revealed);
            updates.put("fieldOpenedThisTurn", true);
            updates.put("lastMoveMillis", System.currentTimeMillis());
            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Polje nije otvoreno.")
        );
    }

    public void submitColumnGuess(
            @NonNull String roomId,
            @NonNull String myUid,
            int columnIndex,
            @NonNull String guess,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canPlay(snapshot, myUid) || !boolOrFalse(snapshot.get("fieldOpenedThisTurn"))
                    || !isValidColumn(columnIndex)) {
                return null;
            }

            List<Map<String, Object>> columns = columnMaps(snapshot.get("columns"));
            List<Boolean> solved = boolList(snapshot.get("solvedColumns"), AssociationPuzzle.COLUMN_COUNT);
            if (columns.size() != AssociationPuzzle.COLUMN_COUNT || solved.get(columnIndex)) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (answersMatch(guess, stringOrEmpty((String) columns.get(columnIndex).get("answer")))) {
                solved.set(columnIndex, true);
                updates.put("solvedColumns", solved);
                addScore(snapshot, updates, scoreForColumn(
                        revealedFields(snapshot.get("revealedFields")),
                        columnIndex
                ));
            } else {
                applyNextTurn(snapshot, updates);
            }
            updates.put("lastMoveMillis", System.currentTimeMillis());
            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Odgovor nije poslat.")
        );
    }

    public void submitFinalGuess(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull String guess,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canPlay(snapshot, myUid) || !boolOrFalse(snapshot.get("fieldOpenedThisTurn"))) {
                return null;
            }

            Map<String, Object> updates = new HashMap<>();
            if (answersMatch(guess, stringOrEmpty(snapshot.getString("finalAnswer")))) {
                updates.put("finalAnswerSolved", true);
                addScore(snapshot, updates, scoreForFinalAnswer(
                        revealedFields(snapshot.get("revealedFields")),
                        boolList(snapshot.get("solvedColumns"), AssociationPuzzle.COLUMN_COUNT)
                ));
                applyRoundOver(updates);
            } else {
                applyNextTurn(snapshot, updates);
            }
            updates.put("lastMoveMillis", System.currentTimeMillis());
            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Konacno resenje nije poslato.")
        );
    }

    public void finishTurn(
            @NonNull String roomId,
            @NonNull String myUid,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = stateRef(roomId);
        db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!canPlay(snapshot, myUid) || !boolOrFalse(snapshot.get("fieldOpenedThisTurn"))) {
                return null;
            }
            Map<String, Object> updates = new HashMap<>();
            applyNextTurn(snapshot, updates);
            updates.put("lastMoveMillis", System.currentTimeMillis());
            transaction.update(ref, updates);
            return null;
        }).addOnFailureListener(error ->
                onError.accept(error.getMessage() != null ? error.getMessage() : "Potez nije zavrsen.")
        );
    }

    public void handleExpiredPhase(
            @NonNull String roomId,
            @NonNull AssociationPuzzle nextPuzzle,
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
                applyRoundOver(updates);
            } else if (PHASE_ROUND_OVER.equals(phase)) {
                int currentRound = intOrZero(snapshot.get("currentRound"));
                if (currentRound >= TOTAL_ROUNDS) {
                    updates.put("phase", PHASE_GAME_OVER);
                    updates.put("phaseEndsAtMillis", 0L);
                } else {
                    String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
                    updates.putAll(nextRoundState(snapshot, currentRound + 1, playerTwoUid, 2, nextPuzzle));
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
                .document(ASSOCIATIONS);
    }

    @NonNull
    private static Map<String, Object> baseRoundState(
            @NonNull RoomSession room,
            int round,
            @NonNull String activePlayerUid,
            int activePlayerNumber,
            @NonNull AssociationPuzzle puzzle,
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
            @NonNull AssociationPuzzle puzzle
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
            @NonNull AssociationPuzzle puzzle
    ) {
        Map<String, Object> fields = new HashMap<>();
        fields.put("currentRound", round);
        fields.put("activePlayerUid", activePlayerUid);
        fields.put("activePlayerNumber", activePlayerNumber);
        fields.put("phase", PHASE_ACTIVE);
        fields.put("phaseEndsAtMillis", System.currentTimeMillis() + ROUND_DURATION_MILLIS);
        fields.put("columns", columnMaps(puzzle));
        fields.put("finalAnswer", puzzle.getFinalAnswer());
        fields.put("revealedFields", emptyRevealedFields());
        fields.put("solvedColumns", emptySolvedColumns());
        fields.put("fieldOpenedThisTurn", false);
        fields.put("finalAnswerSolved", false);
        return fields;
    }

    private static boolean canPlay(@NonNull DocumentSnapshot snapshot, @NonNull String myUid) {
        return snapshot.exists()
                && PHASE_ACTIVE.equals(stringOrDefault(snapshot.getString("phase"), PHASE_ACTIVE))
                && !myUid.isEmpty()
                && myUid.equals(snapshot.getString("activePlayerUid"));
    }

    private static void applyNextTurn(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates
    ) {
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        String activeUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        boolean playerOneActive = activeUid.equals(playerOneUid);
        updates.put("activePlayerUid", playerOneActive ? playerTwoUid : playerOneUid);
        updates.put("activePlayerNumber", playerOneActive ? 2 : 1);
        updates.put("fieldOpenedThisTurn", false);
    }

    private static void applyRoundOver(@NonNull Map<String, Object> updates) {
        updates.put("phase", PHASE_ROUND_OVER);
        updates.put("phaseEndsAtMillis", System.currentTimeMillis() + RESULT_VISIBLE_MILLIS);
    }

    private static void addScore(
            @NonNull DocumentSnapshot snapshot,
            @NonNull Map<String, Object> updates,
            int score
    ) {
        String scoringUid = stringOrEmpty(snapshot.getString("activePlayerUid"));
        String playerOneUid = stringOrEmpty(snapshot.getString("playerOneUid"));
        String playerTwoUid = stringOrEmpty(snapshot.getString("playerTwoUid"));
        int playerOneScore = intOrZero(snapshot.get("playerOneScore"));
        int playerTwoScore = intOrZero(snapshot.get("playerTwoScore"));
        if (score > 0 && scoringUid.equals(playerOneUid)) {
            playerOneScore += score;
        } else if (score > 0 && scoringUid.equals(playerTwoUid)) {
            playerTwoScore += score;
        }
        updates.put("playerOneScore", playerOneScore);
        updates.put("playerTwoScore", playerTwoScore);
    }

    private static int scoreForColumn(
            @NonNull List<Boolean> revealed,
            int columnIndex
    ) {
        return COLUMN_BASE_SCORE + unopenedFieldsInColumn(revealed, columnIndex);
    }

    private static int scoreForFinalAnswer(
            @NonNull List<Boolean> revealed,
            @NonNull List<Boolean> solvedColumns
    ) {
        int score = FINAL_BASE_SCORE;
        for (int columnIndex = 0; columnIndex < AssociationPuzzle.COLUMN_COUNT; columnIndex++) {
            if (Boolean.TRUE.equals(solvedColumns.get(columnIndex))) {
                continue;
            }
            if (isColumnCompletelyUnopened(revealed, columnIndex)) {
                score += UNOPENED_COLUMN_SCORE;
            } else {
                score += scoreForColumn(revealed, columnIndex);
            }
        }
        return score;
    }

    private static boolean isColumnCompletelyUnopened(
            @NonNull List<Boolean> revealed,
            int columnIndex
    ) {
        for (int clueIndex = 0; clueIndex < AssociationColumn.CLUE_COUNT; clueIndex++) {
            if (Boolean.TRUE.equals(revealed.get(fieldIndex(columnIndex, clueIndex)))) {
                return false;
            }
        }
        return true;
    }

    private static int unopenedFieldsInColumn(
            @NonNull List<Boolean> revealed,
            int columnIndex
    ) {
        int opened = 0;
        for (int clueIndex = 0; clueIndex < AssociationColumn.CLUE_COUNT; clueIndex++) {
            if (Boolean.TRUE.equals(revealed.get(fieldIndex(columnIndex, clueIndex)))) {
                opened++;
            }
        }
        return AssociationColumn.CLUE_COUNT - opened;
    }

    @NonNull
    private static List<Map<String, Object>> columnMaps(@NonNull AssociationPuzzle puzzle) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (AssociationColumn column : puzzle.getColumns()) {
            Map<String, Object> map = new HashMap<>();
            map.put("clues", column.getClues());
            map.put("answer", column.getAnswer());
            values.add(map);
        }
        return values;
    }

    @NonNull
    private static List<Boolean> emptyRevealedFields() {
        List<Boolean> fields = new ArrayList<>();
        for (int i = 0; i < AssociationPuzzle.COLUMN_COUNT * AssociationColumn.CLUE_COUNT; i++) {
            fields.add(false);
        }
        return fields;
    }

    @NonNull
    private static List<Boolean> emptySolvedColumns() {
        List<Boolean> values = new ArrayList<>();
        for (int i = 0; i < AssociationPuzzle.COLUMN_COUNT; i++) {
            values.add(false);
        }
        return values;
    }

    @NonNull
    private static List<Map<String, Object>> columnMaps(@Nullable Object raw) {
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
    private static List<Boolean> revealedFields(@Nullable Object raw) {
        List<Boolean> values = emptyRevealedFields();
        if (raw instanceof List) {
            List<?> rawValues = (List<?>) raw;
            if (!rawValues.isEmpty() && rawValues.get(0) instanceof List) {
                for (int column = 0; column < rawValues.size() && column < AssociationPuzzle.COLUMN_COUNT; column++) {
                    List<?> rawClues = (List<?>) rawValues.get(column);
                    for (int clue = 0; clue < rawClues.size() && clue < AssociationColumn.CLUE_COUNT; clue++) {
                        values.set(fieldIndex(column, clue), Boolean.TRUE.equals(rawClues.get(clue)));
                    }
                }
            } else {
                for (int i = 0; i < rawValues.size() && i < values.size(); i++) {
                    values.set(i, Boolean.TRUE.equals(rawValues.get(i)));
                }
            }
        }
        return values;
    }

    private static int fieldIndex(int columnIndex, int clueIndex) {
        return columnIndex * AssociationColumn.CLUE_COUNT + clueIndex;
    }

    @NonNull
    private static List<Boolean> boolList(@Nullable Object raw, int expectedSize) {
        List<Boolean> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                values.add(Boolean.TRUE.equals(value));
            }
        }
        while (values.size() < expectedSize) {
            values.add(false);
        }
        return values;
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

    private static boolean isValidField(int columnIndex, int clueIndex) {
        return isValidColumn(columnIndex)
                && clueIndex >= 0
                && clueIndex < AssociationColumn.CLUE_COUNT;
    }

    private static boolean isValidColumn(int columnIndex) {
        return columnIndex >= 0 && columnIndex < AssociationPuzzle.COLUMN_COUNT;
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
