package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.Friend;
import com.example.slagalica.util.ActiveRoomHelper;
import com.example.slagalica.model.LeagueTier;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.MonthlyCycleHelper;
import com.example.slagalica.utils.QrInviteParser;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Source;
import com.google.android.gms.tasks.Tasks;

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
    private static final String ACTIVE_ROOM_ID_FIELD = "activeRoomId";
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

        db.collection(USERS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(userDocument -> handleLoadedFriendIds(userDocument, onSuccess, onError))
                .addOnFailureListener(serverError -> db.collection(USERS).document(uid).get()
                        .addOnSuccessListener(userDocument -> handleLoadedFriendIds(userDocument, onSuccess, onError))
                        .addOnFailureListener(e -> onError.accept(mapFirestoreError(e, "Prijatelji nisu ucitani."))));
    }

    private void handleLoadedFriendIds(
            @NonNull DocumentSnapshot userDocument,
            @NonNull Consumer<List<Friend>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        List<String> friendUids = readFriendIds(userDocument);
        if (friendUids.isEmpty()) {
            onSuccess.accept(Collections.emptyList());
            return;
        }
        loadFriendProfiles(friendUids, onSuccess, onError);
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

        db.collection(USERS).document(uid).get(Source.SERVER)
                .addOnSuccessListener(userDocument -> {
                    if (readFriendIds(userDocument).contains(friendUid)) {
                        addSelfToFriendList(friendUid, uid, onSuccess, onError);
                        return;
                    }
                    appendFriendId(uid, friendUid, () ->
                            addSelfToFriendList(friendUid, uid, onSuccess, onError),
                            onError
                    );
                })
                .addOnFailureListener(e -> appendFriendId(uid, friendUid, () ->
                        addSelfToFriendList(friendUid, uid, onSuccess, onError),
                        onError
                ));
    }

    private void appendFriendId(
            @NonNull String userId,
            @NonNull String friendUid,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(USERS).document(userId)
                .update(FRIEND_IDS_FIELD, FieldValue.arrayUnion(friendUid))
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> {
                    Map<String, Object> update = new HashMap<>();
                    update.put(FRIEND_IDS_FIELD, Collections.singletonList(friendUid));
                    db.collection(USERS).document(userId)
                            .set(update, SetOptions.merge())
                            .addOnSuccessListener(unused -> onSuccess.run())
                            .addOnFailureListener(setError ->
                                    onError.accept(mapFirestoreError(setError, "Cuvanje prijatelja nije uspelo.")));
                });
    }

    private void addSelfToFriendList(
            @NonNull String friendUid,
            @NonNull String myUid,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(USERS).document(friendUid).get(Source.SERVER)
                .addOnSuccessListener(friendDocument -> {
                    if (readFriendIds(friendDocument).contains(myUid)) {
                        onSuccess.run();
                        return;
                    }
                    appendFriendId(friendUid, myUid, onSuccess, onError);
                })
                .addOnFailureListener(e -> appendFriendId(friendUid, myUid, onSuccess, onError));
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
                    loadBusyUids(friendUids, usersById, busyUids -> loadPendingInviteTargets(targets -> {
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

    private void loadBusyUids(
            @NonNull List<String> friendUids,
            @NonNull Map<String, DocumentSnapshot> usersById,
            @NonNull Consumer<Set<String>> onResult
    ) {
        if (friendUids.isEmpty()) {
            onResult.accept(Collections.emptySet());
            return;
        }

        Map<String, String> friendRoomIds = new HashMap<>();
        List<DocumentReference> roomRefs = new ArrayList<>();
        for (String friendUid : friendUids) {
            DocumentSnapshot userDocument = usersById.get(friendUid);
            if (userDocument == null || !userDocument.exists()) {
                continue;
            }
            String activeRoomId = stringOrDefault(userDocument.getString(ACTIVE_ROOM_ID_FIELD), "");
            if (activeRoomId.isEmpty()) {
                continue;
            }
            friendRoomIds.put(friendUid, activeRoomId);
            DocumentReference roomRef = db.collection(ROOMS).document(activeRoomId);
            if (!roomRefs.contains(roomRef)) {
                roomRefs.add(roomRef);
            }
        }

        if (roomRefs.isEmpty()) {
            onResult.accept(Collections.emptySet());
            return;
        }

        List<com.google.android.gms.tasks.Task<DocumentSnapshot>> roomTasks = new ArrayList<>();
        for (DocumentReference roomRef : roomRefs) {
            roomTasks.add(roomRef.get());
        }

        Tasks.whenAllSuccess(roomTasks)
                .addOnSuccessListener(results -> {
                    Map<String, DocumentSnapshot> roomsById = new HashMap<>();
                    for (Object result : results) {
                        DocumentSnapshot roomDocument = (DocumentSnapshot) result;
                        roomsById.put(roomDocument.getId(), roomDocument);
                    }

                    Set<String> busyUids = new HashSet<>();
                    for (Map.Entry<String, String> entry : friendRoomIds.entrySet()) {
                        DocumentSnapshot roomDocument = roomsById.get(entry.getValue());
                        if (roomDocument != null
                                && ActiveRoomHelper.isUserInActiveRoom(roomDocument, entry.getKey())) {
                            busyUids.add(entry.getKey());
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
