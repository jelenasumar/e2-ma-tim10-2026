package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.InviteUser;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.SystemNotification;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class GameInviteRepository {

    private static final String USERS = "users";
    private static final String GAME_INVITES = "game_invites";
    private static final String NOTIFICATIONS = "notifications";
    private static final String ROOMS = "rooms";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public GameInviteRepository() {
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
    }

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void loadUsers(
            @NonNull Consumer<List<InviteUser>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String currentUid = getCurrentUid();
        db.collection(USERS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<InviteUser> users = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (document.getId().equals(currentUid)) {
                            continue;
                        }
                        users.add(new InviteUser(
                                document.getId(),
                                stringOrDefault(document.getString("username"), "Korisnik"),
                                stringOrDefault(document.getString("email"), ""),
                                stringOrDefault(document.getString("avatarUri"), "")
                        ));
                    }
                    onSuccess.accept(users);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Users could not be loaded.")));
    }

    public void sendInvite(
            @NonNull InviteUser receiver,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String fromUid = currentUser.getUid();
        db.collection(USERS).document(fromUid).get()
                .addOnSuccessListener(senderDocument -> createInvite(receiver, senderDocument, onSuccess, onError))
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be sent.")));
    }

    public ListenerRegistration listenNotifications(
            @NonNull Consumer<List<SystemNotification>> onChanged,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onChanged.accept(new ArrayList<>());
            return null;
        }

        return db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Notifications could not be loaded."));
                        return;
                    }

                    List<SystemNotification> notifications = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            notifications.add(notificationFromDocument(document));
                        }
                    }
                    onChanged.accept(notifications);
                });
    }

    public void markNotificationAsRead(
            @NonNull String notificationId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notificationId)
                .update("read", true)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Notification could not be updated.")));
    }

    public void markNotificationAsUnread(
            @NonNull String notificationId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notificationId)
                .update("read", false)
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Notification could not be updated.")));
    }

    public void markNotificationActionHandled(
            @NonNull String notificationId,
            @NonNull String actionResult,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notificationId)
                .update(
                        "read", true,
                        "actionHandled", true,
                        "actionResult", actionResult
                )
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Notification could not be updated.")));
    }

    public void acceptInvite(
            @NonNull SystemNotification notification,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        String inviteId = notification.getInviteId();
        if (uid == null || inviteId == null || inviteId.isEmpty()) {
            onError.accept("INVITE_NOT_AVAILABLE");
            return;
        }

        DocumentReference inviteRef = db.collection(GAME_INVITES).document(inviteId);
        DocumentReference notificationRef = db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notification.getId());
        DocumentReference roomRef = db.collection(ROOMS).document();

        db.runTransaction(transaction -> {
            DocumentSnapshot invite = transaction.get(inviteRef);
            if (!invite.exists()) {
                throw new IllegalStateException("Invite does not exist.");
            }
            String status = invite.getString("status");
            if (!"PENDING".equals(status)) {
                String existingRoomId = invite.getString("roomId");
                return existingRoomId != null ? existingRoomId : "";
            }

            String fromUid = stringOrDefault(invite.getString("fromUid"), "");
            String fromUsername = stringOrDefault(invite.getString("fromUsername"), "Igrac");
            String toUsername = stringOrDefault(invite.getString("toUsername"), "Protivnik");

            Map<String, Object> room = new HashMap<>();
            room.put("hostUid", fromUid);
            room.put("guestUid", uid);
            room.put("hostUsername", fromUsername);
            room.put("guestUsername", toUsername);
            room.put("hostTotalScore", 0);
            room.put("guestTotalScore", 0);
            room.put("currentGame", "KO_ZNA_ZNA");
            room.put("currentGameIndex", 0);
            room.put("gameOrder", java.util.Arrays.asList(
                    "KO_ZNA_ZNA",
                    "SPOJNICE",
                    "ASOCIJACIJE",
                    "SKOCKO",
                    "KORAK_PO_KORAK",
                    "MOJ_BROJ"
            ));
            room.put("status", "READY");
            room.put("matchType", "FRIENDLY");
            room.put("createdAt", FieldValue.serverTimestamp());
            room.put("updatedAt", FieldValue.serverTimestamp());

            DocumentReference senderNotificationRef = db.collection(USERS)
                    .document(fromUid)
                    .collection(NOTIFICATIONS)
                    .document();
            Map<String, Object> senderNotification = new HashMap<>();
            senderNotification.put("type", "INVITE_ACCEPTED");
            senderNotification.put("category", "OTHER");
            senderNotification.put("title", "Poziv je prihvacen");
            senderNotification.put("message", toUsername + " je prihvatio/la poziv za partiju.");
            senderNotification.put("read", false);
            senderNotification.put("action", "OPEN_ROOM");
            senderNotification.put("actionLabel", "Otvori sobu");
            senderNotification.put("roomId", roomRef.getId());
            senderNotification.put("createdAt", FieldValue.serverTimestamp());

            transaction.set(roomRef, room);
            transaction.set(senderNotificationRef, senderNotification);
            transaction.update(inviteRef, "status", "ACCEPTED", "roomId", roomRef.getId(), "acceptedAt", FieldValue.serverTimestamp());
            transaction.update(
                    notificationRef,
                    "read", true,
                    "roomId", roomRef.getId(),
                    "actionHandled", true,
                    "actionResult", "Prihvatili ste poziv"
            );
            return roomRef.getId();
        }).addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be accepted.")));
    }

    public void declineInvite(
            @NonNull SystemNotification notification,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        String inviteId = notification.getInviteId();
        if (uid == null || inviteId == null || inviteId.isEmpty()) {
            onError.accept("INVITE_NOT_AVAILABLE");
            return;
        }

        DocumentReference inviteRef = db.collection(GAME_INVITES).document(inviteId);
        DocumentReference notificationRef = db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .document(notification.getId());

        db.runTransaction(transaction -> {
            DocumentSnapshot invite = transaction.get(inviteRef);
            if (!invite.exists()) {
                throw new IllegalStateException("Invite does not exist.");
            }
            String status = stringOrDefault(invite.getString("status"), "PENDING");
            if ("PENDING".equals(status)) {
                transaction.update(inviteRef, "status", "DECLINED", "declinedAt", FieldValue.serverTimestamp());
            }
            transaction.update(
                    notificationRef,
                    "read", true,
                    "actionHandled", true,
                    "actionResult", "Odbili ste poziv"
            );
            return null;
        }).addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be declined.")));
    }

    private void createInvite(
            @NonNull InviteUser receiver,
            @NonNull DocumentSnapshot senderDocument,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String fromUid = currentUser.getUid();
        String fromUsername = stringOrDefault(senderDocument.getString("username"), "Igrac");
        String fromEmail = stringOrDefault(senderDocument.getString("email"), "");
        DocumentReference inviteRef = db.collection(GAME_INVITES).document();
        DocumentReference notificationRef = db.collection(USERS)
                .document(receiver.getUid())
                .collection(NOTIFICATIONS)
                .document();

        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUid", fromUid);
        invite.put("fromUsername", fromUsername);
        invite.put("fromEmail", fromEmail);
        invite.put("toUid", receiver.getUid());
        invite.put("toUsername", receiver.getUsername());
        invite.put("toEmail", receiver.getEmail());
        invite.put("status", "PENDING");
        invite.put("roomId", "");
        invite.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "GAME_INVITE");
        notification.put("category", "OTHER");
        notification.put("title", "Poziv za partiju");
        notification.put("message", fromUsername + " te je pozvao/la na partiju.");
        notification.put("read", false);
        notification.put("action", "ACCEPT_INVITE");
        notification.put("actionLabel", "Prihvati poziv");
        notification.put("actionHandled", false);
        notification.put("actionResult", "");
        notification.put("inviteId", inviteRef.getId());
        notification.put("fromUid", fromUid);
        notification.put("createdAt", FieldValue.serverTimestamp());

        db.runBatch(batch -> {
            batch.set(inviteRef, invite);
            batch.set(notificationRef, notification);
        }).addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be sent.")));
    }

    @NonNull
    private SystemNotification notificationFromDocument(@NonNull DocumentSnapshot document) {
        String category = stringOrDefault(document.getString("category"), "OTHER");
        String action = stringOrDefault(document.getString("action"), "NONE");
        return new SystemNotification(
                document.getId(),
                categoryFromString(category),
                categoryLabel(category),
                stringOrDefault(document.getString("title"), "Notifikacija"),
                stringOrDefault(document.getString("message"), ""),
                dateLabel(document.get("createdAt")),
                Boolean.TRUE.equals(document.getBoolean("read")),
                actionFromString(action),
                document.getString("actionLabel"),
                document.getString("inviteId"),
                document.getString("roomId"),
                Boolean.TRUE.equals(document.getBoolean("actionHandled")),
                stringOrDefault(document.getString("actionResult"), "")
        );
    }

    @NonNull
    private static NotificationCategory categoryFromString(@NonNull String value) {
        try {
            return NotificationCategory.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NotificationCategory.OTHER;
        }
    }

    @NonNull
    private static NotificationAction actionFromString(@NonNull String value) {
        try {
            return NotificationAction.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return NotificationAction.NONE;
        }
    }

    @NonNull
    private static String categoryLabel(@NonNull String category) {
        switch (category) {
            case "CHAT":
                return "Cet";
            case "RANKING":
                return "Rang lista";
            case "REWARD":
                return "Nagrada";
            case "OTHER":
            default:
                return "Ostalo";
        }
    }

    @NonNull
    private static String dateLabel(@Nullable Object createdAt) {
        if (createdAt instanceof Timestamp) {
            Date date = ((Timestamp) createdAt).toDate();
            return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault()).format(date);
        }
        return "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        return error.getMessage() != null ? error.getMessage() : fallback;
    }
}
