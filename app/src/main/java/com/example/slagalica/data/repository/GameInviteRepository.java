package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.InviteUser;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.model.SentGameInvite;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.util.ActiveRoomHelper;
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
import java.util.Collections;
import java.util.Date;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class GameInviteRepository {

    public static final long INVITE_EXPIRE_MS = 10_000L;

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
            @NonNull Consumer<SentGameInvite> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        FirebaseUser currentUser = auth.getCurrentUser();
        if (currentUser == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String fromUid = currentUser.getUid();
        isUserInActiveGame(receiver.getUid(), inGame -> {
            if (inGame) {
                onError.accept("FRIEND_IN_GAME");
                return;
            }
            db.collection(USERS).document(fromUid).get()
                    .addOnSuccessListener(senderDocument -> createInvite(receiver, senderDocument, onSuccess, onError))
                    .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be sent.")));
        }, error -> onError.accept(error));
    }

    public void loadPendingSentInviteTargets(
            @NonNull Consumer<Set<String>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onSuccess.accept(Collections.emptySet());
            return;
        }

        db.collection(GAME_INVITES)
                .whereEqualTo("fromUid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(snapshot -> {
                    Set<String> targets = new HashSet<>();
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (isExpired(document, now)) {
                            expireInviteDocument(document.getId(), null);
                            continue;
                        }
                        targets.add(stringOrDefault(document.getString("toUid"), ""));
                    }
                    onSuccess.accept(targets);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Pozivi nisu ucitani.")));
    }

    public void loadPendingSentInvites(
            @NonNull Consumer<List<SentGameInvite>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onSuccess.accept(new ArrayList<>());
            return;
        }

        db.collection(GAME_INVITES)
                .whereEqualTo("fromUid", uid)
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SentGameInvite> invites = new ArrayList<>();
                    long now = System.currentTimeMillis();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        if (isExpired(document, now)) {
                            expireInviteDocument(document.getId(), stringValue(document.get("notificationId")));
                            continue;
                        }
                        invites.add(new SentGameInvite(
                                document.getId(),
                                stringOrDefault(document.getString("toUid"), ""),
                                stringOrDefault(document.getString("toUsername"), "Korisnik"),
                                stringOrDefault(document.getString("notificationId"), "")
                        ));
                    }
                    onSuccess.accept(invites);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Pozivi nisu ucitani.")));
    }

    @Nullable
    public ListenerRegistration listenPendingSentInvitesChanged(@NonNull Runnable onChanged) {
        String uid = getCurrentUid();
        if (uid == null) {
            return null;
        }

        return db.collection(GAME_INVITES)
                .whereEqualTo("fromUid", uid)
                .whereEqualTo("status", "PENDING")
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null || snapshot == null) {
                        return;
                    }
                    onChanged.run();
                });
    }

    public void cancelInvite(
            @NonNull String inviteId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        DocumentReference inviteRef = db.collection(GAME_INVITES).document(inviteId);
        inviteRef.get().addOnSuccessListener(invite -> {
            if (!invite.exists()) {
                onError.accept("INVITE_NOT_AVAILABLE");
                return;
            }
            if (!uid.equals(invite.getString("fromUid"))) {
                onError.accept("INVITE_NOT_AVAILABLE");
                return;
            }
            if (!"PENDING".equals(stringOrDefault(invite.getString("status"), ""))) {
                onError.accept("INVITE_NOT_AVAILABLE");
                return;
            }

            String toUid = stringOrDefault(invite.getString("toUid"), "");
            String fromUsername = stringOrDefault(invite.getString("fromUsername"), "Igrac");
            String notificationId = stringOrDefault(invite.getString("notificationId"), "");
            DocumentReference receiverNotificationRef = notificationId.isEmpty() || toUid.isEmpty()
                    ? null
                    : db.collection(USERS).document(toUid).collection(NOTIFICATIONS).document(notificationId);

            db.runTransaction(transaction -> {
                DocumentSnapshot freshInvite = transaction.get(inviteRef);
                if (!freshInvite.exists() || !"PENDING".equals(stringOrDefault(freshInvite.getString("status"), ""))) {
                    throw new IllegalStateException("Invite is no longer pending.");
                }
                transaction.update(inviteRef, "status", "CANCELLED", "cancelledAt", FieldValue.serverTimestamp());
                if (receiverNotificationRef != null) {
                    Map<String, Object> notificationUpdate = new HashMap<>();
                    notificationUpdate.put("read", true);
                    notificationUpdate.put("actionHandled", true);
                    notificationUpdate.put("actionResult", "Poziv je otkazan");
                    notificationUpdate.put(
                            "message",
                            fromUsername + " je otkazao/la poziv za partiju."
                    );
                    transaction.update(receiverNotificationRef, notificationUpdate);
                }
                return null;
            }).addOnSuccessListener(unused -> onSuccess.run())
                    .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Poziv nije otkazan.")));
        }).addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Poziv nije otkazan.")));
    }

    public void expireInviteIfPending(
            @NonNull String inviteId,
            @Nullable String notificationId,
            @NonNull Runnable onComplete
    ) {
        expireInviteDocument(inviteId, notificationId, onComplete);
    }

    public void expireInviteForNotification(@NonNull SystemNotification notification) {
        String inviteId = notification.getInviteId();
        if (inviteId == null || inviteId.isEmpty()) {
            return;
        }
        expireInviteDocument(inviteId, notification.getId(), () -> {
        });
    }

    public void isUserInActiveGame(
            @NonNull String uid,
            @NonNull Consumer<Boolean> onResult,
            @NonNull Consumer<String> onError
    ) {
        if (uid.isEmpty()) {
            onResult.accept(false);
            return;
        }

        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(userDocument -> {
                    String activeRoomId = stringOrDefault(userDocument.getString("activeRoomId"), "");
                    if (activeRoomId.isEmpty()) {
                        onResult.accept(false);
                        return;
                    }
                    db.collection(ROOMS).document(activeRoomId).get()
                            .addOnSuccessListener(roomDocument -> onResult.accept(
                                    ActiveRoomHelper.isUserInActiveRoom(roomDocument, uid)
                            ))
                            .addOnFailureListener(e -> onResult.accept(false));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Status nije proveren.")));
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

    public void loadNotification(
            @NonNull String notificationId,
            @NonNull Consumer<SystemNotification> onSuccess,
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
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        onSuccess.accept(notificationFromDocument(document));
                    } else {
                        onError.accept("NOTIFICATION_NOT_FOUND");
                    }
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Notification could not be loaded.")));
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
            if (isExpired(invite, System.currentTimeMillis())) {
                throw new IllegalStateException("Invite expired.");
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
            room.put("currentGame", com.example.slagalica.model.RoomGameKeys.DEFAULT_GAME_ORDER.get(0));
            room.put("currentGameIndex", 0);
            room.put("gameOrder", com.example.slagalica.model.RoomGameKeys.DEFAULT_GAME_ORDER);
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
            senderNotification.put("fromUid", uid);
            senderNotification.put("inviteId", inviteId);
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
                if (isExpired(invite, System.currentTimeMillis())) {
                    throw new IllegalStateException("Invite expired.");
                }
                String fromUid = stringOrDefault(invite.getString("fromUid"), "");
                String toUsername = stringOrDefault(invite.getString("toUsername"), "Protivnik");
                transaction.update(inviteRef, "status", "DECLINED", "declinedAt", FieldValue.serverTimestamp());
                if (!fromUid.isEmpty()) {
                    DocumentReference senderNotificationRef = db.collection(USERS)
                            .document(fromUid)
                            .collection(NOTIFICATIONS)
                            .document();
                    Map<String, Object> senderNotification = new HashMap<>();
                    senderNotification.put("type", "INVITE_DECLINED");
                    senderNotification.put("category", "OTHER");
                    senderNotification.put("title", "Poziv odbijen");
                    senderNotification.put(
                            "message",
                            toUsername + " je odbio/la poziv za partiju."
                    );
                    senderNotification.put("read", false);
                    senderNotification.put("action", "NONE");
                    senderNotification.put("actionHandled", false);
                    senderNotification.put("actionResult", "");
                    senderNotification.put("inviteId", inviteId);
                    senderNotification.put("fromUid", uid);
                    senderNotification.put("createdAt", FieldValue.serverTimestamp());
                    transaction.set(senderNotificationRef, senderNotification);
                }
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
            @NonNull Consumer<SentGameInvite> onSuccess,
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

        Timestamp expiresAt = new Timestamp(new Date(System.currentTimeMillis() + INVITE_EXPIRE_MS));

        Map<String, Object> invite = new HashMap<>();
        invite.put("fromUid", fromUid);
        invite.put("fromUsername", fromUsername);
        invite.put("fromEmail", fromEmail);
        invite.put("toUid", receiver.getUid());
        invite.put("toUsername", receiver.getUsername());
        invite.put("toEmail", receiver.getEmail());
        invite.put("status", "PENDING");
        invite.put("roomId", "");
        invite.put("notificationId", notificationRef.getId());
        invite.put("expiresAt", expiresAt);
        invite.put("createdAt", FieldValue.serverTimestamp());

        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "GAME_INVITE");
        notification.put("category", "OTHER");
        notification.put("title", "Poziv za partiju");
        notification.put("message", fromUsername + " te je pozvao/la na partiju. Imate 10 sekundi da odgovorite.");
        notification.put("read", false);
        notification.put("action", "ACCEPT_INVITE");
        notification.put("actionLabel", "Prihvati poziv");
        notification.put("actionHandled", false);
        notification.put("actionResult", "");
        notification.put("inviteId", inviteRef.getId());
        notification.put("fromUid", fromUid);
        notification.put("expiresAt", expiresAt);
        notification.put("createdAt", FieldValue.serverTimestamp());

        db.runBatch(batch -> {
            batch.set(inviteRef, invite);
            batch.set(notificationRef, notification);
        }).addOnSuccessListener(unused -> onSuccess.accept(new SentGameInvite(
                inviteRef.getId(),
                receiver.getUid(),
                receiver.getUsername(),
                notificationRef.getId()
        ))).addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Invite could not be sent.")));
    }

    private void expireInviteDocument(@NonNull String inviteId, @Nullable String notificationId) {
        expireInviteDocument(inviteId, notificationId, () -> {
        });
    }

    private void expireInviteDocument(
            @NonNull String inviteId,
            @Nullable String notificationId,
            @NonNull Runnable onComplete
    ) {
        DocumentReference inviteRef = db.collection(GAME_INVITES).document(inviteId);
        inviteRef.get().addOnSuccessListener(invite -> {
            if (!invite.exists()) {
                onComplete.run();
                return;
            }
            if (!"PENDING".equals(stringOrDefault(invite.getString("status"), ""))) {
                onComplete.run();
                return;
            }
            String toUid = stringOrDefault(invite.getString("toUid"), "");
            String effectiveNotificationId = notificationId != null && !notificationId.isEmpty()
                    ? notificationId
                    : stringOrDefault(invite.getString("notificationId"), "");
            DocumentReference notificationRef = effectiveNotificationId.isEmpty() || toUid.isEmpty()
                    ? null
                    : db.collection(USERS).document(toUid).collection(NOTIFICATIONS).document(effectiveNotificationId);

            db.runTransaction(transaction -> {
                DocumentSnapshot freshInvite = transaction.get(inviteRef);
                if (!freshInvite.exists() || !"PENDING".equals(stringOrDefault(freshInvite.getString("status"), ""))) {
                    return null;
                }
                transaction.update(inviteRef, "status", "EXPIRED", "expiredAt", FieldValue.serverTimestamp());
                if (notificationRef != null) {
                    Map<String, Object> notificationUpdate = new HashMap<>();
                    notificationUpdate.put("read", true);
                    notificationUpdate.put("actionHandled", true);
                    notificationUpdate.put("actionResult", "Poziv je istekao");
                    notificationUpdate.put("message", "Poziv za partiju je istekao.");
                    transaction.update(notificationRef, notificationUpdate);
                }
                return null;
            }).addOnCompleteListener(task -> onComplete.run());
        }).addOnFailureListener(e -> onComplete.run());
    }

    private static boolean isExpired(@NonNull DocumentSnapshot invite, long nowMillis) {
        Object expiresAt = invite.get("expiresAt");
        if (expiresAt instanceof Timestamp) {
            return ((Timestamp) expiresAt).toDate().getTime() <= nowMillis;
        }
        Object createdAt = invite.get("createdAt");
        if (createdAt instanceof Timestamp) {
            return ((Timestamp) createdAt).toDate().getTime() + INVITE_EXPIRE_MS <= nowMillis;
        }
        return false;
    }

    @NonNull
    private SystemNotification notificationFromDocument(@NonNull DocumentSnapshot document) {
        String category = stringOrDefault(stringValue(document.get("category")), "OTHER");
        String action = stringOrDefault(stringValue(document.get("action")), "NONE");
        return new SystemNotification(
                document.getId(),
                categoryFromString(category),
                categoryLabel(category),
                stringOrDefault(stringValue(document.get("title")), "Notifikacija"),
                stringOrDefault(stringValue(document.get("message")), ""),
                dateLabel(document.get("createdAt")),
                boolValue(document.get("read")),
                actionFromString(action),
                stringValue(document.get("actionLabel")),
                stringValue(document.get("inviteId")),
                stringValue(document.get("roomId")),
                boolValue(document.get("actionHandled")),
                stringOrDefault(stringValue(document.get("actionResult")), ""),
                intValue(document.get("rank"))
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

    @Nullable
    private static String stringValue(@Nullable Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private static boolean boolValue(@Nullable Object value) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return false;
    }

    private static int intValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
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
