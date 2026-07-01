package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.Friend;
import com.example.slagalica.model.LeagueTier;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.MonthlyCycleHelper;
import com.example.slagalica.utils.QrInviteParser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public final class FriendsRepository {

    private static final String USERS = "users";
    private static final String FRIEND_IDS_FIELD = "friendIds";
    private static final String USERNAME_LOOKUP = "username_lookup";
    private static final String ROOMS = "rooms";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final FireBaseUserDataSource userRemote;
    private final GameInviteRepository inviteRepository;

    public FriendsRepository(@NonNull Context context) {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.userRemote = new FireBaseUserDataSource();
        this.inviteRepository = new GameInviteRepository();
    }

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void loadFriends(
            @NonNull Consumer<List<Friend>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(userDocument -> {
                    List<String> friendUids = readFriendIds(userDocument);
                    if (friendUids.isEmpty()) {
                        onSuccess.accept(Collections.emptyList());
                        return;
                    }
                    loadFriendProfiles(friendUids, onSuccess, onError);
                })
                .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Prijatelji nisu ucitani.")));
    }

    public void searchByUsername(
            @NonNull String username,
            @NonNull BiProfileConsumer onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String usernameLower = username.trim().toLowerCase(Locale.ROOT);
        db.collection(USERNAME_LOOKUP).document(usernameLower).get()
                .addOnSuccessListener(lookup -> {
                    if (!lookup.exists()) {
                        onError.accept("USERNAME_NOT_FOUND");
                        return;
                    }
                    String friendUid = lookup.getString("uid");
                    if (friendUid == null || friendUid.isEmpty()) {
                        onError.accept("USERNAME_NOT_FOUND");
                        return;
                    }
                    if (friendUid.equals(uid)) {
                        onError.accept("CANNOT_ADD_SELF");
                        return;
                    }
                    userRemote.fetchUserProfile(
                            friendUid,
                            profile -> onSuccess.accept(friendUid, profile),
                            error -> onError.accept(mapErrorMessage(error))
                    );
                })
                .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Pretraga nije uspela.")));
    }

    public interface BiProfileConsumer {
        void accept(@NonNull String uid, @NonNull UserProfile profile);
    }

    public void addFriend(
            @NonNull String friendUid,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }
        if (friendUid.equals(uid)) {
            onError.accept("CANNOT_ADD_SELF");
            return;
        }

        db.collection(USERS).document(uid).get()
                .addOnSuccessListener(userDocument -> {
                    List<String> existing = readFriendIds(userDocument);
                    Map<String, Object> theirUpdate = new HashMap<>();
                    theirUpdate.put(FRIEND_IDS_FIELD, FieldValue.arrayUnion(uid));

                    if (existing.contains(friendUid)) {
                        db.collection(USERS).document(friendUid)
                                .set(theirUpdate, SetOptions.merge())
                                .addOnSuccessListener(unused -> onSuccess.run())
                                .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Sinhronizacija nije uspela.")));
                        return;
                    }

                    Map<String, Object> myUpdate = new HashMap<>();
                    myUpdate.put(FRIEND_IDS_FIELD, FieldValue.arrayUnion(friendUid));

                    WriteBatch batch = db.batch();
                    batch.set(db.collection(USERS).document(uid), myUpdate, SetOptions.merge());
                    batch.set(db.collection(USERS).document(friendUid), theirUpdate, SetOptions.merge());
                    batch.commit()
                            .addOnSuccessListener(unused -> onSuccess.run())
                            .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Dodavanje nije uspelo.")));
                })
                .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Dodavanje nije uspelo.")));
    }

    public void addFriendFromQr(
            @NonNull String qrContent,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        QrInviteParser.ParsedInvite parsed = QrInviteParser.parse(qrContent);
        if (parsed == null) {
            onError.accept("INVALID_QR");
            return;
        }

        searchByUsername(parsed.getUsername(), (friendUid, profile) -> {
            if (!inviteCodeMatches(profile, parsed.getCode())) {
                onError.accept("INVALID_QR");
                return;
            }
            addFriend(friendUid, onSuccess, onError);
        }, onError);
    }

    private void loadFriendProfiles(
            @NonNull List<String> friendUids,
            @NonNull Consumer<List<Friend>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(USERS).get()
                .addOnSuccessListener(usersSnapshot -> {
                    Map<String, DocumentSnapshot> usersById = new HashMap<>();
                    for (DocumentSnapshot document : usersSnapshot.getDocuments()) {
                        usersById.put(document.getId(), document);
                    }

                    Map<String, Integer> monthlyRanks = computeMonthlyRanks(usersSnapshot.getDocuments());
                    loadBusyUids(busyUids -> loadPendingInviteTargets(targets -> {
                        List<Friend> friends = new ArrayList<>();
                        for (String friendUid : friendUids) {
                            DocumentSnapshot document = usersById.get(friendUid);
                            if (document == null || !document.exists()) {
                                continue;
                            }
                            friends.add(buildFriend(
                                    document,
                                    monthlyRanks.getOrDefault(friendUid, 0),
                                    busyUids.contains(friendUid),
                                    targets.contains(friendUid)
                            ));
                        }
                        Collections.sort(friends, Comparator.comparing(Friend::getUsername, String.CASE_INSENSITIVE_ORDER));
                        onSuccess.accept(friends);
                    }));
                })
                .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Prijatelji nisu ucitani.")));
    }

    private void loadBusyUids(@NonNull Consumer<Set<String>> onResult) {
        db.collection(ROOMS).get()
                .addOnSuccessListener(roomsSnapshot -> {
                    Set<String> busyUids = new HashSet<>();
                    for (DocumentSnapshot room : roomsSnapshot.getDocuments()) {
                        String status = stringOrDefault(room.getString("status"), "");
                        if (isActiveRoomStatus(status)) {
                            busyUids.add(stringOrDefault(room.getString("hostUid"), ""));
                            busyUids.add(stringOrDefault(room.getString("guestUid"), ""));
                        }
                    }
                    onResult.accept(busyUids);
                })
                .addOnFailureListener(e -> onResult.accept(Collections.emptySet()));
    }

    private void loadPendingInviteTargets(@NonNull Consumer<Set<String>> onResult) {
        inviteRepository.loadPendingSentInviteTargets(
                onResult,
                error -> onResult.accept(Collections.emptySet())
        );
    }

    @NonNull
    private List<String> readFriendIds(@NonNull DocumentSnapshot userDocument) {
        Object raw = userDocument.get(FRIEND_IDS_FIELD);
        List<String> friendIds = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value != null) {
                    String friendId = String.valueOf(value).trim();
                    if (!friendId.isEmpty()) {
                        friendIds.add(friendId);
                    }
                }
            }
        }
        return friendIds;
    }

    @NonNull
    private Friend buildFriend(
            @NonNull DocumentSnapshot document,
            int monthlyRank,
            boolean inActiveGame,
            boolean invitePending
    ) {
        String currentCycle = MonthlyCycleHelper.currentCycleKey();
        long monthlyStars = longValue(document.get("monthlyStars"));
        String starsCycleKey = stringOrDefault(document.getString("starsCycleKey"), "");
        if (!currentCycle.equals(starsCycleKey)) {
            monthlyStars = 0L;
        }

        long totalStars = longValue(document.get("totalStars"));
        LeagueTier tier = LeagueTier.fromStars(totalStars);
        String leagueTierKey = stringOrDefault(document.getString("leagueTierKey"), tier.getKey());
        String leagueName = stringOrDefault(document.getString("leagueName"), tier.getKey());

        return new Friend(
                document.getId(),
                stringOrDefault(document.getString("username"), "Korisnik"),
                stringOrDefault(document.getString("avatarUri"), ""),
                totalStars,
                monthlyStars,
                monthlyRank,
                leagueTierKey,
                leagueName,
                inActiveGame,
                invitePending
        );
    }

    @NonNull
    private Map<String, Integer> computeMonthlyRanks(@NonNull List<DocumentSnapshot> documents) {
        String currentCycle = MonthlyCycleHelper.currentCycleKey();
        List<MonthlyRankRow> rows = new ArrayList<>();
        for (DocumentSnapshot document : documents) {
            long monthlyStars = longValue(document.get("monthlyStars"));
            String starsCycleKey = stringOrDefault(document.getString("starsCycleKey"), "");
            if (!currentCycle.equals(starsCycleKey)) {
                monthlyStars = 0L;
            }
            rows.add(new MonthlyRankRow(document.getId(), monthlyStars));
        }
        rows.sort((a, b) -> Long.compare(b.monthlyStars, a.monthlyStars));

        Map<String, Integer> ranks = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            ranks.put(rows.get(i).uid, i + 1);
        }
        return ranks;
    }

    private static boolean isActiveRoomStatus(@NonNull String status) {
        return "READY".equals(status) || "PLAYING".equals(status) || "BREAK".equals(status);
    }

    private static boolean inviteCodeMatches(@NonNull UserProfile profile, @NonNull String code) {
        String payload = profile.getInvitePayload();
        if (payload == null || payload.isEmpty()) {
            return false;
        }
        return payload.contains("code=" + code);
    }

    @NonNull
    private static String mapFirestoreError(@NonNull Exception error, @NonNull String fallback) {
        return mapErrorMessage(messageOrDefault(error.getMessage(), fallback));
    }

    @NonNull
    private static String mapErrorMessage(@NonNull String message) {
        String lower = message.toLowerCase(Locale.ROOT);
        if (lower.contains("permission_denied") || lower.contains("insufficient permissions")) {
            return "PERMISSION_DENIED";
        }
        return message;
    }

    @NonNull
    private static String messageOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static long longValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static final class MonthlyRankRow {
        private final String uid;
        private final long monthlyStars;

        private MonthlyRankRow(@NonNull String uid, long monthlyStars) {
            this.uid = uid;
            this.monthlyStars = monthlyStars;
        }
    }
}
