package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.model.RoomSession;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class RoomSessionRepository {

    private static final String ROOMS = "rooms";
    private static final String GAMES = "games";

    private static final String FINISH_REASON_ABANDONED = "ABANDONED";
    private static final String MATCH_TYPE_TOURNAMENT = "TOURNAMENT";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    @Nullable
    public String getCurrentUid() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            return null;
        }
        return FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public ListenerRegistration listenRoom(
            @NonNull String roomId,
            @NonNull Consumer<RoomSession> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return db.collection(ROOMS)
                .document(roomId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(error.getMessage() != null ? error.getMessage() : "Room could not be loaded.");
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        onChanged.accept(RoomSession.fromDocument(snapshot));
                    } else {
                        onError.accept("Room does not exist.");
                    }
                });
    }

    public void fetchRoom(
            @NonNull String roomId,
            @NonNull Consumer<RoomSession> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(ROOMS)
                .document(roomId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        onSuccess.accept(RoomSession.fromDocument(snapshot));
                    } else {
                        onError.accept("Room does not exist.");
                    }
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Room could not be loaded."
                ));
    }

    public void ensureSessionStarted(
            @NonNull RoomSession room,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        if (!RoomGameKeys.STATUS_READY.equals(room.getStatus()) || !room.hasBothPlayers()) {
            onDone.run();
            return;
        }
        String uid = getCurrentUid();
        if (uid == null || !uid.equals(room.getHostUid())) {
            onDone.run();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", RoomGameKeys.STATUS_PLAYING);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(ROOMS)
                .document(room.getRoomId())
                .update(updates)
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Session could not be started."
                ));
    }

    public void abandonRoom(
            @NonNull RoomSession room,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null || (!uid.equals(room.getHostUid()) && !uid.equals(room.getGuestUid()))) {
            onDone.run();
            return;
        }

        if (RoomGameKeys.STATUS_FINISHED.equals(room.getStatus())) {
            onDone.run();
            return;
        }

        if (!room.getAbandonedByUid().isEmpty()) {
            onDone.run();
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("finishReason", FINISH_REASON_ABANDONED);
        updates.put("abandonedByUid", uid);
        updates.put("abandonedAt", FieldValue.serverTimestamp());
        updates.put("updatedAt", FieldValue.serverTimestamp());

        if (MATCH_TYPE_TOURNAMENT.equals(room.getMatchType())) {
            updates.put("status", RoomGameKeys.STATUS_FINISHED);
            updates.put("breakEndsAtMillis", FieldValue.delete());
        } else if (RoomGameKeys.STATUS_BREAK.equals(room.getStatus())) {
            updates.put("breakEndsAtMillis", System.currentTimeMillis());
        }

        db.collection(ROOMS)
                .document(room.getRoomId())
                .update(updates)
                .addOnSuccessListener(unused -> markCurrentGameAbandoned(room, uid, onDone))
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Room could not be abandoned."
                ));
    }

    private void markCurrentGameAbandoned(
            @NonNull RoomSession room,
            @NonNull String abandonedUid,
            @NonNull Runnable onDone
    ) {
        String gameDocumentId = gameDocumentId(room.getCurrentGame());
        String survivorUid = survivorUid(room, abandonedUid);
        if (gameDocumentId.isEmpty() || survivorUid.isEmpty()) {
            onDone.run();
            return;
        }

        db.collection(ROOMS)
                .document(room.getRoomId())
                .collection(GAMES)
                .document(gameDocumentId)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (!snapshot.exists()) {
                        onDone.run();
                        return;
                    }

                    Map<String, Object> gameUpdates = new HashMap<>();
                    gameUpdates.put("abandonedByUid", abandonedUid);
                    if (abandonedUid.equals(snapshot.getString("activePlayerUid"))) {
                        gameUpdates.put("activePlayerUid", survivorUid);
                        gameUpdates.put("activePlayerNumber", playerNumberForRoomUid(room, survivorUid));
                    }
                    if (abandonedUid.equals(snapshot.getString("bonusPlayerUid"))) {
                        gameUpdates.put("bonusPlayerUid", survivorUid);
                    }
                    if (abandonedUid.equals(snapshot.getString("followupPlayerUid"))) {
                        gameUpdates.put("followupPlayerUid", survivorUid);
                    }

                    db.collection(ROOMS)
                            .document(room.getRoomId())
                            .collection(GAMES)
                            .document(gameDocumentId)
                            .update(gameUpdates)
                            .addOnCompleteListener(task -> onDone.run());
                })
                .addOnFailureListener(error -> onDone.run());
    }

    @NonNull
    private static String gameDocumentId(@NonNull String currentGame) {
        switch (currentGame) {
            case RoomGameKeys.SPOJNICE:
                return "spojnice";
            case RoomGameKeys.ASOCIJACIJE:
                return "associations";
            case RoomGameKeys.SKOCKO:
                return "skocko";
            case RoomGameKeys.KORAK_PO_KORAK:
                return "korak_po_korak";
            case RoomGameKeys.MOJ_BROJ:
                return "my_number";
            default:
                return "";
        }
    }

    @NonNull
    private static String survivorUid(@NonNull RoomSession room, @NonNull String abandonedUid) {
        if (abandonedUid.equals(room.getHostUid())) {
            return room.getGuestUid();
        }
        if (abandonedUid.equals(room.getGuestUid())) {
            return room.getHostUid();
        }
        return "";
    }

    private static int playerNumberForRoomUid(@NonNull RoomSession room, @NonNull String uid) {
        if (uid.equals(room.getHostUid())) {
            return 1;
        }
        if (uid.equals(room.getGuestUid())) {
            return 2;
        }
        return 1;
    }

    public void startBreakAfterGame(
            @NonNull String roomId,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("status", RoomGameKeys.STATUS_BREAK);
        updates.put("breakEndsAtMillis", System.currentTimeMillis() + RoomGameKeys.BREAK_DURATION_MS);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(ROOMS)
                .document(roomId)
                .update(updates)
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Break could not be started."
                ));
    }

    public void advanceToNextGame(
            @NonNull RoomSession room,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        advanceToNextGame(room, room.getHostTotalScore(), room.getGuestTotalScore(), onDone, onError);
    }

    public void advanceToNextGame(
            @NonNull RoomSession room,
            int hostTotalScore,
            int guestTotalScore,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        List<String> gameOrder = room.getGameOrder();
        int nextIndex = room.getCurrentGameIndex() + 1;
        if (nextIndex >= gameOrder.size()) {
            Map<String, Object> updates = new HashMap<>();
            updates.put("hostTotalScore", hostTotalScore);
            updates.put("guestTotalScore", guestTotalScore);
            updates.put("status", RoomGameKeys.STATUS_FINISHED);
            updates.put("breakEndsAtMillis", FieldValue.delete());
            updates.put("updatedAt", FieldValue.serverTimestamp());
            db.collection(ROOMS)
                    .document(room.getRoomId())
                    .update(updates)
                    .addOnSuccessListener(unused -> onDone.run())
                    .addOnFailureListener(e -> onError.accept(
                            e.getMessage() != null ? e.getMessage() : "Session could not be finished."
                    ));
            return;
        }

        String nextGame = gameOrder.get(nextIndex);
        Map<String, Object> updates = new HashMap<>();
        updates.put("hostTotalScore", hostTotalScore);
        updates.put("guestTotalScore", guestTotalScore);
        updates.put("currentGameIndex", nextIndex);
        updates.put("currentGame", nextGame);
        updates.put("status", RoomGameKeys.STATUS_PLAYING);
        updates.put("breakEndsAtMillis", FieldValue.delete());
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(ROOMS)
                .document(room.getRoomId())
                .update(updates)
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Next game could not be started."
                ));
    }

    public void startBreakAfterGame(
            @NonNull String roomId,
            int hostTotalScore,
            int guestTotalScore,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("hostTotalScore", hostTotalScore);
        updates.put("guestTotalScore", guestTotalScore);
        updates.put("status", RoomGameKeys.STATUS_BREAK);
        updates.put("breakEndsAtMillis", System.currentTimeMillis() + RoomGameKeys.BREAK_DURATION_MS);
        updates.put("updatedAt", FieldValue.serverTimestamp());
        db.collection(ROOMS)
                .document(roomId)
                .update(updates)
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Break could not be started."
                ));
    }
}
