package com.example.slagalica.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.KoZnaZnaMatch;
import com.example.slagalica.model.KoZnaZnaScoring;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public final class KoZnaZnaMatchDataSource {

    private static final String LOBBIES = "kzz_lobbies";
    private static final String MATCHES = "kzz_matches";
    private static final String ROOMS = "rooms";

    public static final int QUESTION_MS = 5_000;
    public static final int QUESTIONS_PER_MATCH = 5;
    public static final int ROUND_MS = QUESTIONS_PER_MATCH * QUESTION_MS;
    private static final int DEFAULT_QUESTIONS_PER_MATCH = QUESTIONS_PER_MATCH;

    private final FirebaseFirestore db;
    private final FirebaseAuth auth;
    private final Random random = new Random();

    public KoZnaZnaMatchDataSource() {
        this.db = FirebaseFirestore.getInstance();
        this.auth = FirebaseAuth.getInstance();
    }

    @Nullable
    public String getCurrentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : null;
    }

    public void createLobby(
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String code = generateLobbyCode();
        Map<String, Object> data = new HashMap<>();
        data.put("hostUid", hostUid);
        data.put("hostUsername", hostUsername);
        data.put("status", "WAITING");
        data.put("createdAt", System.currentTimeMillis());

        db.collection(LOBBIES).document(code).set(data)
                .addOnSuccessListener(unused -> onSuccess.accept(code))
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void joinLobby(
            @NonNull String lobbyCode,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = db.collection(LOBBIES).document(lobbyCode);
        db.runTransaction((Transaction transaction) -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new IllegalStateException("LOBBY_NOT_FOUND");
            }
            String hostUid = snapshot.getString("hostUid");
            if (hostUid == null || hostUid.equals(guestUid)) {
                throw new IllegalStateException("LOBBY_INVALID");
            }
            String existingGuest = snapshot.getString("guestUid");
            if (existingGuest != null && !existingGuest.equals(guestUid)) {
                throw new IllegalStateException("LOBBY_FULL");
            }
            Map<String, Object> updates = new HashMap<>();
            updates.put("guestUid", guestUid);
            updates.put("guestUsername", guestUsername);
            updates.put("status", "READY");
            transaction.update(ref, updates);
            return null;
        }).addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(mapLobbyError(e)));
    }

    @NonNull
    public ListenerRegistration listenLobby(
            @NonNull String lobbyCode,
            @NonNull Consumer<DocumentSnapshot> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return db.collection(LOBBIES).document(lobbyCode)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(errorMessage(error));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        onChanged.accept(snapshot);
                    }
                });
    }

    public void createMatchFromRoom(
            @NonNull String roomId,
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull List<Integer> questionOrder,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference roomRef = db.collection(ROOMS).document(roomId);
        DocumentReference matchRef = db.collection(MATCHES).document();
        String matchId = matchRef.getId();
        long now = System.currentTimeMillis();

        Map<String, Object> match = newMatchPayload(
                hostUid,
                hostUsername,
                guestUid,
                guestUsername,
                now,
                questionOrder
        );

        db.runTransaction((Transaction transaction) -> {
            DocumentSnapshot roomSnap = transaction.get(roomRef);
            if (!roomSnap.exists()) {
                throw new IllegalStateException("ROOM_NOT_FOUND");
            }
            String existingMatchId = roomSnap.getString("koZnaZnaMatchId");
            if (existingMatchId != null && !existingMatchId.isEmpty()) {
                return existingMatchId;
            }
            transaction.set(matchRef, match);
            Map<String, Object> roomUpdates = new HashMap<>();
            roomUpdates.put("koZnaZnaMatchId", matchId);
            roomUpdates.put("currentGame", "KO_ZNA_ZNA");
            transaction.update(roomRef, roomUpdates);
            return matchId;
        }).addOnSuccessListener(result -> onSuccess.accept(String.valueOf(result)))
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void createMatchFromLobby(
            @NonNull String lobbyCode,
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull List<Integer> questionOrder,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String matchId = db.collection(MATCHES).document().getId();
        long now = System.currentTimeMillis();
        Map<String, Object> match = newMatchPayload(
                hostUid,
                hostUsername,
                guestUid,
                guestUsername,
                now,
                questionOrder
        );

        DocumentReference lobbyRef = db.collection(LOBBIES).document(lobbyCode);
        DocumentReference matchRef = db.collection(MATCHES).document(matchId);

        db.runTransaction((Transaction transaction) -> {
            DocumentSnapshot lobbySnap = transaction.get(lobbyRef);
            if (!lobbySnap.exists()) {
                throw new IllegalStateException("LOBBY_NOT_FOUND");
            }
            String existingMatchId = lobbySnap.getString("matchId");
            if (existingMatchId != null && !existingMatchId.isEmpty()) {
                return existingMatchId;
            }
            transaction.set(matchRef, match);
            Map<String, Object> lobbyUpdates = new HashMap<>();
            lobbyUpdates.put("matchId", matchId);
            lobbyUpdates.put("status", "PLAYING");
            transaction.update(lobbyRef, lobbyUpdates);
            return matchId;
        }).addOnSuccessListener(result -> onSuccess.accept(String.valueOf(result)))
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    @NonNull
    public ListenerRegistration listenMatch(
            @NonNull String matchId,
            @NonNull Consumer<KoZnaZnaMatch> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return db.collection(MATCHES).document(matchId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(errorMessage(error));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        onChanged.accept(KoZnaZnaMatch.fromMap(matchId, snapshot.getData()));
                    }
                });
    }

    public void submitAnswer(
            @NonNull String matchId,
            boolean isHost,
            int answerIndex,
            long answeredAtMs,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        if (isHost) {
            updates.put("hostAnswerIndex", answerIndex);
            updates.put("hostAnsweredAtMs", answeredAtMs);
        } else {
            updates.put("guestAnswerIndex", answerIndex);
            updates.put("guestAnsweredAtMs", answeredAtMs);
        }
        db.collection(MATCHES).document(matchId).update(updates)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void tryResolveQuestion(
            @NonNull String matchId,
            int correctIndex,
            @NonNull Consumer<KoZnaZnaMatch> onResolved,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = db.collection(MATCHES).document(matchId);
        db.runTransaction((Transaction transaction) -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new IllegalStateException("MATCH_NOT_FOUND");
            }
            KoZnaZnaMatch match = KoZnaZnaMatch.fromMap(matchId, snapshot.getData());
            if (match.isQuestionResolved() || KoZnaZnaMatch.STATUS_FINISHED.equals(match.getStatus())) {
                return match;
            }

            long now = System.currentTimeMillis();
            boolean hostPending = match.getHostAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
            boolean guestPending = match.getGuestAnswerIndex() == KoZnaZnaScoring.ANSWER_PENDING;
            boolean timeUp = now >= match.getQuestionEndsAtMs()
                    || now >= match.getRoundEndsAtMs();

            if (hostPending && guestPending && !timeUp) {
                return match;
            }

            if (!hostPending && !guestPending && !timeUp) {
                // both answered early - resolve now
            } else if (hostPending && guestPending && timeUp) {
                // timeout with no answers
            } else if (!hostPending && guestPending && !timeUp) {
                return match;
            } else if (hostPending && !guestPending && !timeUp) {
                return match;
            }

            int hostIndex = hostPending ? KoZnaZnaScoring.ANSWER_SKIP : match.getHostAnswerIndex();
            int guestIndex = guestPending ? KoZnaZnaScoring.ANSWER_SKIP : match.getGuestAnswerIndex();

            KoZnaZnaScoring.PlayerAnswer hostAnswer = toPlayerAnswer(hostIndex, correctIndex, match.getHostAnsweredAtMs());
            KoZnaZnaScoring.PlayerAnswer guestAnswer = toPlayerAnswer(guestIndex, correctIndex, match.getGuestAnsweredAtMs());

            int[] scores = KoZnaZnaScoring.resolveQuestion(
                    match.getHostScore(),
                    match.getGuestScore(),
                    hostAnswer,
                    guestAnswer
            );

            Map<String, Object> updates = new HashMap<>();
            updates.put("hostScore", scores[0]);
            updates.put("guestScore", scores[1]);
            updates.put("questionResolved", true);
            if (hostPending) {
                updates.put("hostAnswerIndex", KoZnaZnaScoring.ANSWER_SKIP);
            }
            if (guestPending) {
                updates.put("guestAnswerIndex", KoZnaZnaScoring.ANSWER_SKIP);
            }
            transaction.update(ref, updates);
            return new KoZnaZnaMatch(
                    match.getMatchId(),
                    match.getHostUid(),
                    match.getGuestUid(),
                    match.getHostUsername(),
                    match.getGuestUsername(),
                    scores[0],
                    scores[1],
                    match.getCurrentQuestionIndex(),
                    match.getStatus(),
                    match.getRoundEndsAtMs(),
                    match.getQuestionStartedAtMs(),
                    match.getQuestionEndsAtMs(),
                    true,
                    "",
                    hostPending ? KoZnaZnaScoring.ANSWER_SKIP : match.getHostAnswerIndex(),
                    guestPending ? KoZnaZnaScoring.ANSWER_SKIP : match.getGuestAnswerIndex(),
                    match.getHostAnsweredAtMs(),
                    match.getGuestAnsweredAtMs(),
                    match.getQuestionOrder()
            );
        }).addOnSuccessListener(result -> onResolved.accept((KoZnaZnaMatch) result))
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void advanceQuestion(
            @NonNull String matchId,
            @NonNull Consumer<KoZnaZnaMatch> onAdvanced,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference ref = db.collection(MATCHES).document(matchId);
        db.runTransaction((Transaction transaction) -> {
            DocumentSnapshot snapshot = transaction.get(ref);
            if (!snapshot.exists()) {
                throw new IllegalStateException("MATCH_NOT_FOUND");
            }
            KoZnaZnaMatch match = KoZnaZnaMatch.fromMap(matchId, snapshot.getData());
            if (!match.isQuestionResolved()) {
                return match;
            }

            long now = System.currentTimeMillis();
            int nextIndex = match.getCurrentQuestionIndex() + 1;
            int totalQuestions = Math.max(1, match.getQuestionOrder().size());
            Map<String, Object> updates = new HashMap<>();

            if (nextIndex >= totalQuestions) {
                updates.put("status", KoZnaZnaMatch.STATUS_FINISHED);
                updates.put("currentQuestionIndex", Math.min(nextIndex, totalQuestions - 1));
                updates.put("questionResolved", false);
                updates.put("statusMessage", "");
            } else if (now >= match.getRoundEndsAtMs()) {
                updates.put("status", KoZnaZnaMatch.STATUS_FINISHED);
                updates.put("currentQuestionIndex", match.getCurrentQuestionIndex());
                updates.put("questionResolved", false);
                updates.put("statusMessage", "");
            } else {
                updates.put("currentQuestionIndex", nextIndex);
                updates.put("questionStartedAtMs", now);
                updates.put("questionEndsAtMs", now + QUESTION_MS);
                updates.put("questionResolved", false);
                updates.put("statusMessage", "");
                updates.put("hostAnswerIndex", KoZnaZnaScoring.ANSWER_PENDING);
                updates.put("guestAnswerIndex", KoZnaZnaScoring.ANSWER_PENDING);
                updates.put("hostAnsweredAtMs", 0L);
                updates.put("guestAnsweredAtMs", 0L);
            }
            transaction.update(ref, updates);
            return KoZnaZnaMatch.fromMap(matchId, applyUpdates(snapshot.getData(), updates));
        }).addOnSuccessListener(result -> onAdvanced.accept((KoZnaZnaMatch) result))
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void updateStatusMessage(
            @NonNull String matchId,
            @NonNull String message,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("statusMessage", message);
        db.collection(MATCHES).document(matchId).update(updates)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(errorMessage(e)));
    }

    public void leaveLobby(@NonNull String lobbyCode) {
        db.collection(LOBBIES).document(lobbyCode).delete();
    }

    @NonNull
    private static KoZnaZnaScoring.PlayerAnswer toPlayerAnswer(
            int answerIndex,
            int correctIndex,
            long answerMillis
    ) {
        if (answerIndex == KoZnaZnaScoring.ANSWER_SKIP || answerIndex == KoZnaZnaScoring.ANSWER_PENDING) {
            return new KoZnaZnaScoring.PlayerAnswer(true, null, answerMillis);
        }
        return new KoZnaZnaScoring.PlayerAnswer(
                true,
                KoZnaZnaScoring.correctness(answerIndex, correctIndex),
                answerMillis
        );
    }

    @NonNull
    private static Map<String, Object> applyUpdates(
            @Nullable Map<String, Object> original,
            @NonNull Map<String, Object> updates
    ) {
        Map<String, Object> merged = new HashMap<>();
        if (original != null) {
            merged.putAll(original);
        }
        merged.putAll(updates);
        return merged;
    }

    @NonNull
    private static Map<String, Object> newMatchPayload(
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            long now,
            @NonNull List<Integer> questionOrder
    ) {
        List<Integer> order = questionOrder.isEmpty()
                ? shuffledQuestionOrderStatic(DEFAULT_QUESTIONS_PER_MATCH)
                : new ArrayList<>(questionOrder);
        int totalQuestions = order.size();
        long roundMs = (long) totalQuestions * QUESTION_MS;
        Map<String, Object> match = new HashMap<>();
        match.put("hostUid", hostUid);
        match.put("guestUid", guestUid);
        match.put("hostUsername", hostUsername);
        match.put("guestUsername", guestUsername);
        match.put("hostScore", 0);
        match.put("guestScore", 0);
        match.put("currentQuestionIndex", 0);
        match.put("status", KoZnaZnaMatch.STATUS_PLAYING);
        match.put("roundEndsAtMs", now + roundMs);
        match.put("questionStartedAtMs", now);
        match.put("questionEndsAtMs", now + QUESTION_MS);
        match.put("questionResolved", false);
        match.put("statusMessage", "");
        match.put("hostAnswerIndex", KoZnaZnaScoring.ANSWER_PENDING);
        match.put("guestAnswerIndex", KoZnaZnaScoring.ANSWER_PENDING);
        match.put("hostAnsweredAtMs", 0L);
        match.put("guestAnsweredAtMs", 0L);
        match.put("questionOrder", order);
        return match;
    }

    @NonNull
    public static List<Integer> shuffledQuestionOrderStatic(int questionCount) {
        int count = Math.max(1, questionCount);
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random());
        return order;
    }

    @NonNull
    private String generateLobbyCode() {
        int code = 100000 + random.nextInt(900000);
        return String.valueOf(code);
    }

    @NonNull
    private static String errorMessage(@NonNull Exception error) {
        String message = error.getMessage();
        if (message != null
                && (message.contains("PERMISSION_DENIED")
                || message.contains("Missing or insufficient permissions"))) {
            return "PERMISSION_DENIED";
        }
        return message != null ? message : "Unknown error";
    }

    @NonNull
    private static String mapLobbyError(@NonNull Exception error) {
        String message = error.getMessage();
        if (message != null) {
            if (message.contains("LOBBY_NOT_FOUND")) {
                return "LOBBY_NOT_FOUND";
            }
            if (message.contains("LOBBY_FULL")) {
                return "LOBBY_FULL";
            }
            if (message.contains("LOBBY_INVALID")) {
                return "LOBBY_INVALID";
            }
        }
        return errorMessage(error);
    }
}
