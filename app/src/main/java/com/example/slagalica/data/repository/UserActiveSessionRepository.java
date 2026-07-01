package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks which room the current user is actively participating in.
 * Used for reliable friend availability instead of scanning all room documents.
 */
public final class UserActiveSessionRepository {

    private static final String USERS = "users";
    private static final String ACTIVE_ROOM_ID = "activeRoomId";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void bindActiveRoom(@NonNull String roomId) {
        String uid = getCurrentUid();
        if (uid == null || roomId.isEmpty()) {
            return;
        }
        Map<String, Object> update = new HashMap<>();
        update.put(ACTIVE_ROOM_ID, roomId);
        db.collection(USERS)
                .document(uid)
                .set(update, SetOptions.merge());
    }

    public void clearActiveRoomIfMatches(@NonNull String roomId) {
        String uid = getCurrentUid();
        if (uid == null || roomId.isEmpty()) {
            return;
        }
        db.collection(USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    String currentRoomId = document.getString(ACTIVE_ROOM_ID);
                    if (roomId.equals(currentRoomId)) {
                        db.collection(USERS)
                                .document(uid)
                                .update(ACTIVE_ROOM_ID, FieldValue.delete());
                    }
                });
    }

    @Nullable
    private String getCurrentUid() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        return user != null ? user.getUid() : null;
    }
}
