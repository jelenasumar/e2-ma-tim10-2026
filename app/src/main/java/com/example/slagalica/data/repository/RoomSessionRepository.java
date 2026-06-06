package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.model.RoomSession;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.function.Consumer;

public final class RoomSessionRepository {

    private static final String ROOMS = "rooms";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

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
}
