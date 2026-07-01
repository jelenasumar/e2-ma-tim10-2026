package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public final class OnlineMatchmakingRepository {

    private static final String USERS = "users";
    private static final String QUEUE = "matchmaking_queue";
    private static final String ROOMS = "rooms";

    private static final int MATCH_ENTRY_FEE = 1;
    private static final String NO_TOKENS = "NO_TOKENS";

    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void startLooking(
            @NonNull Consumer<String> onMatched,
            @NonNull Runnable onWaiting,
            @NonNull Consumer<String> onError
    ) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String uid = currentUser.getUid();
        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(myProfile ->
                        reserveMatchToken(
                                uid,
                                result -> {
                                    if (result.isAlreadyMatched()) {
                                        onMatched.accept(result.getRoomId());
                                        return;
                                    }
                                    if (result.isAlreadyWaiting()) {
                                        onWaiting.run();
                                        return;
                                    }
                                    findOpponentOrWait(uid, myProfile, onMatched, onWaiting, onError);
                                },
                                onError
                        ))
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Matchmaking failed.")));
    }

    public ListenerRegistration listenMyQueue(
            @NonNull Consumer<String> onMatched,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            return null;
        }

        return db.collection(QUEUE)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Matchmaking listener failed."));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String status = snapshot.getString("status");
                        String roomId = snapshot.getString("roomId");
                        if ("MATCHED".equals(status) && roomId != null && !roomId.isEmpty()) {
                            onMatched.accept(roomId);
                        }
                    }
                });
    }

    public void cancelLooking(@NonNull Runnable onDone) {
        String uid = getCurrentUid();
        if (uid == null) {
            onDone.run();
            return;
        }

        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference queueRef = db.collection(QUEUE).document(uid);

        db.runTransaction(transaction -> {
            DocumentSnapshot queueDoc = transaction.get(queueRef);
            if (!queueDoc.exists()) {
                return null;
            }

            String status = queueDoc.getString("status");
            if ("WAITING".equals(status)) {
                transaction.delete(queueRef);
                transaction.update(userRef, "tokens", FieldValue.increment(MATCH_ENTRY_FEE));
            }

            return null;
        }).addOnCompleteListener(task -> onDone.run());
    }

    private void reserveMatchToken(
            @NonNull String uid,
            @NonNull Consumer<ReservationResult> onReserved,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference queueRef = db.collection(QUEUE).document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot queueDoc = transaction.get(queueRef);
                    if (queueDoc.exists()) {
                        String status = stringOrDefault(queueDoc.getString("status"), "");
                        String roomId = stringOrDefault(queueDoc.getString("roomId"), "");

                        if ("MATCHED".equals(status) && !roomId.isEmpty()) {
                            return ReservationResult.alreadyMatched(roomId);
                        }
                        if ("WAITING".equals(status)) {
                            return ReservationResult.alreadyWaiting();
                        }
                    }

                    DocumentSnapshot userDoc = transaction.get(userRef);
                    long tokens = longOrZero(userDoc.get("tokens"));
                    if (tokens < MATCH_ENTRY_FEE) {
                        throw new IllegalStateException(NO_TOKENS);
                    }

                    transaction.update(userRef, "tokens", FieldValue.increment(-MATCH_ENTRY_FEE));
                    return ReservationResult.reserved();
                })
                .addOnSuccessListener(onReserved::accept)
                .addOnFailureListener(e -> {
                    String message = e.getMessage();
                    if (message != null && message.contains(NO_TOKENS)) {
                        onError.accept(NO_TOKENS);
                    } else {
                        onError.accept(messageOrDefault(e, "Token could not be reserved."));
                    }
                });
    }

    private void refundReservedMatchToken(
            @NonNull String uid,
            @NonNull Runnable onDone
    ) {
        db.collection(USERS)
                .document(uid)
                .update("tokens", FieldValue.increment(MATCH_ENTRY_FEE))
                .addOnCompleteListener(task -> onDone.run());
    }

    private void findOpponentOrWait(
            @NonNull String uid,
            @NonNull DocumentSnapshot myProfile,
            @NonNull Consumer<String> onMatched,
            @NonNull Runnable onWaiting,
            @NonNull Consumer<String> onError
    ) {
        db.collection(QUEUE)
                .whereEqualTo("status", "WAITING")
                .limit(5)
                .get()
                .addOnSuccessListener(snapshot -> {
                    DocumentSnapshot opponent = null;
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (!document.getId().equals(uid)) {
                            opponent = document;
                            break;
                        }
                    }

                    if (opponent == null) {
                        createWaitingEntry(uid, myProfile, onWaiting, onError);
                    } else {
                        createRoomFromMatch(uid, myProfile, opponent, onMatched, onError);
                    }
                })
                .addOnFailureListener(e -> refundReservedMatchToken(
                        uid,
                        () -> onError.accept(messageOrDefault(e, "Opponent search failed."))
                ));
    }

    private void createWaitingEntry(
            @NonNull String uid,
            @NonNull DocumentSnapshot myProfile,
            @NonNull Runnable onWaiting,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("uid", uid);
        entry.put("username", stringOrDefault(myProfile.getString("username"), "Igrac"));
        entry.put("status", "WAITING");
        entry.put("roomId", "");
        entry.put("createdAt", FieldValue.serverTimestamp());

        db.collection(QUEUE)
                .document(uid)
                .set(entry)
                .addOnSuccessListener(unused -> onWaiting.run())
                .addOnFailureListener(e -> refundReservedMatchToken(
                        uid,
                        () -> onError.accept(messageOrDefault(e, "Queue entry failed."))
                ));
    }

    private void createRoomFromMatch(
            @NonNull String uid,
            @NonNull DocumentSnapshot myProfile,
            @NonNull DocumentSnapshot opponent,
            @NonNull Consumer<String> onMatched,
            @NonNull Consumer<String> onError
    ) {
        String opponentUid = opponent.getId();
        String opponentUsername = stringOrDefault(opponent.getString("username"), "Protivnik");
        String myUsername = stringOrDefault(myProfile.getString("username"), "Igrac");
        DocumentReference roomRef = db.collection(ROOMS).document();
        DocumentReference myQueueRef = db.collection(QUEUE).document(uid);
        DocumentReference opponentQueueRef = db.collection(QUEUE).document(opponentUid);

        Map<String, Object> room = new HashMap<>();
        room.put("hostUid", opponentUid);
        room.put("guestUid", uid);
        room.put("hostUsername", opponentUsername);
        room.put("guestUsername", myUsername);
        room.put("hostTotalScore", 0);
        room.put("guestTotalScore", 0);
        room.put("currentGame", com.example.slagalica.model.RoomGameKeys.DEFAULT_GAME_ORDER.get(0));
        room.put("currentGameIndex", 0);
        room.put("gameOrder", com.example.slagalica.model.RoomGameKeys.DEFAULT_GAME_ORDER);
        room.put("status", "READY");
        room.put("matchType", "RANDOM");
        room.put("createdAt", FieldValue.serverTimestamp());
        room.put("updatedAt", FieldValue.serverTimestamp());

        Map<String, Object> matchedUpdate = new HashMap<>();
        matchedUpdate.put("status", "MATCHED");
        matchedUpdate.put("roomId", roomRef.getId());
        matchedUpdate.put("matchedAt", FieldValue.serverTimestamp());

        db.runBatch(batch -> {
                    batch.set(roomRef, room);
                    batch.set(myQueueRef, queueEntry(uid, myUsername, roomRef.getId()));
                    batch.update(opponentQueueRef, matchedUpdate);
                }).addOnSuccessListener(unused -> onMatched.accept(roomRef.getId()))
                .addOnFailureListener(e -> refundReservedMatchToken(
                        uid,
                        () -> onError.accept(messageOrDefault(e, "Room creation failed."))
                ));
    }

    @NonNull
    private static Map<String, Object> queueEntry(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String roomId
    ) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("uid", uid);
        entry.put("username", username);
        entry.put("status", "MATCHED");
        entry.put("roomId", roomId);
        entry.put("createdAt", FieldValue.serverTimestamp());
        entry.put("matchedAt", FieldValue.serverTimestamp());
        return entry;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        return error.getMessage() != null ? error.getMessage() : fallback;
    }

    private static final class ReservationResult {
        private final boolean alreadyWaiting;
        private final String roomId;

        private ReservationResult(boolean alreadyWaiting, @NonNull String roomId) {
            this.alreadyWaiting = alreadyWaiting;
            this.roomId = roomId;
        }

        @NonNull
        static ReservationResult reserved() {
            return new ReservationResult(false, "");
        }

        @NonNull
        static ReservationResult alreadyWaiting() {
            return new ReservationResult(true, "");
        }

        @NonNull
        static ReservationResult alreadyMatched(@NonNull String roomId) {
            return new ReservationResult(false, roomId);
        }

        boolean isAlreadyWaiting() {
            return alreadyWaiting;
        }

        boolean isAlreadyMatched() {
            return !roomId.isEmpty();
        }

        @NonNull
        String getRoomId() {
            return roomId;
        }
    }
}