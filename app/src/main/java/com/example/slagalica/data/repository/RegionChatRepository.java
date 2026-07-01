package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RegionChatMessage;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class RegionChatRepository {

    private static final String USERS = "users";
    private static final String NOTIFICATIONS = "notifications";
    private static final String REGION_CHATS = "region_chats";
    private static final String MESSAGES = "messages";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void loadMyRegion(
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za cet.");
            return;
        }

        db.collection(USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    String regionKey = stringOrEmpty(document.getString("regionKey"));
                    if (regionKey.isEmpty()) {
                        onError.accept("Region nije izabran.");
                        return;
                    }
                    onSuccess.accept(regionKey);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Region nije ucitan.")));
    }

    @Nullable
    public ListenerRegistration listenMessages(
            @NonNull String regionKey,
            @NonNull Consumer<List<RegionChatMessage>> onMessages,
            @NonNull Consumer<String> onError
    ) {
        if (regionKey.isEmpty()) {
            onError.accept("Region nije izabran.");
            return null;
        }

        return db.collection(REGION_CHATS)
                .document(regionKey)
                .collection(MESSAGES)
                .orderBy("createdAt", Query.Direction.ASCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Poruke nisu ucitane."));
                        return;
                    }
                    List<RegionChatMessage> messages = new ArrayList<>();
                    if (snapshot != null) {
                        for (com.google.firebase.firestore.DocumentSnapshot document : snapshot.getDocuments()) {
                            messages.add(RegionChatMessage.fromDocument(document));
                        }
                    }
                    onMessages.accept(messages);
                });
    }

    public void sendMessage(
            @NonNull String regionKey,
            @NonNull String text,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za cet.");
            return;
        }

        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            onError.accept("Unesi poruku.");
            return;
        }

        db.collection(USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(userDocument -> {
                    String myRegionKey = stringOrEmpty(userDocument.getString("regionKey"));
                    if (!regionKey.equals(myRegionKey)) {
                        onError.accept("Mozes pisati samo u cet svog regiona.");
                        return;
                    }

                    String senderUsername = stringOrDefault(userDocument.getString("username"), "Igrac");

                    Map<String, Object> message = new HashMap<>();
                    message.put("regionKey", regionKey);
                    message.put("senderUid", uid);
                    message.put("senderUsername", senderUsername);
                    message.put("text", trimmed);
                    message.put("createdAt", FieldValue.serverTimestamp());

                    db.collection(REGION_CHATS)
                            .document(regionKey)
                            .collection(MESSAGES)
                            .add(message)
                            .addOnSuccessListener(unused ->
                                    notifyRegionMembers(regionKey, uid, senderUsername, trimmed, onSuccess, onError)
                            )
                            .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Poruka nije poslata.")));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Profil nije ucitan.")));
    }

    private void notifyRegionMembers(
            @NonNull String regionKey,
            @NonNull String senderUid,
            @NonNull String senderUsername,
            @NonNull String messageText,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(USERS)
                .whereEqualTo("regionKey", regionKey)
                .get()
                .addOnSuccessListener(snapshot -> {
                    WriteBatch batch = db.batch();
                    int count = 0;

                    for (com.google.firebase.firestore.DocumentSnapshot userDocument : snapshot.getDocuments()) {
                        String receiverUid = userDocument.getId();
                        if (receiverUid.equals(senderUid)) {
                            continue;
                        }

                        DocumentReference notificationRef = db.collection(USERS)
                                .document(receiverUid)
                                .collection(NOTIFICATIONS)
                                .document();

                        Map<String, Object> notification = new HashMap<>();
                        notification.put("type", "CHAT_MESSAGE");
                        notification.put("category", "CHAT");
                        notification.put("title", "Nova poruka u cetu");
                        notification.put("message", senderUsername + ": " + shortMessage(messageText));
                        notification.put("read", false);
                        notification.put("action", "OPEN_CHAT");
                        notification.put("actionLabel", "Udji u cet");
                        notification.put("actionHandled", false);
                        notification.put("actionResult", "");
                        notification.put("fromUid", senderUid);
                        notification.put("regionKey", regionKey);
                        notification.put("createdAt", FieldValue.serverTimestamp());

                        batch.set(notificationRef, notification);
                        count++;
                    }

                    if (count == 0) {
                        onSuccess.run();
                        return;
                    }

                    batch.commit()
                            .addOnSuccessListener(unused -> onSuccess.run())
                            .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Notifikacije nisu poslate.")));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Korisnici regiona nisu ucitani.")));
    }

    @NonNull
    private static String shortMessage(@NonNull String value) {
        if (value.length() <= 80) {
            return value;
        }
        return value.substring(0, 77) + "...";
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        String message = error.getMessage();
        return message != null && !message.isEmpty() ? message : fallback;
    }
}