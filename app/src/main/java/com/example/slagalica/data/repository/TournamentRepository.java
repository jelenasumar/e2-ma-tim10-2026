package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.local.UserPreferences;
import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.TournamentPlayer;
import com.example.slagalica.model.TournamentState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.MonthlyCycleHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TournamentRepository {

    private static final int ENTRY_FEE = 3;
    private static final int SEMI_WIN_TOKENS = 2;
    private static final int FINAL_WIN_TOKENS = 3;
    private static final int FINAL_BONUS_STARS = 10;

    private static final String USERS = "users";
    private static final String ROOMS = "rooms";
    private static final String MATCH_RESULTS = "match_results";
    private static final String TOURNAMENTS = "tournaments";
    private static final String TOURNAMENT_QUEUE = "tournament_queue";
    private static final String MATCH_TYPE_TOURNAMENT = "TOURNAMENT";
    private static final String ROUND_SEMI_ONE = "SEMI_ONE";
    private static final String ROUND_SEMI_TWO = "SEMI_TWO";
    private static final String ROUND_FINAL = "FINAL";

    private final Context appContext;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    private final FireBaseUserDataSource remote = new FireBaseUserDataSource();
    private final UserPreferences preferences;
    private final LeagueRepository leagueRepository;

    public TournamentRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = new UserPreferences(appContext);
        leagueRepository = new LeagueRepository(appContext);
    }

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void joinTournament(
            @NonNull Runnable onWaiting,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za turnir.");
            return;
        }

        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference queueRef = db.collection(TOURNAMENT_QUEUE).document(uid);
        db.runTransaction(transaction -> {
                    DocumentSnapshot userDoc = transaction.get(userRef);
                    DocumentSnapshot queueDoc = transaction.get(queueRef);
                    if (queueDoc.exists()) {
                        String status = stringOrDefault(queueDoc.getString("status"), "WAITING");
                        if ("WAITING".equals(status)) {
                            return null;
                        }
                        if ("MATCHED".equals(status)) {
                            String tournamentId = stringOrDefault(queueDoc.getString("tournamentId"), "");
                            if (!canCurrentPlayerStartFresh(transaction, uid, tournamentId)) {
                                return null;
                            }
                        }
                    }

                    long tokens = longOrZero(userDoc.get("tokens"));
                    if (tokens < ENTRY_FEE) {
                        throw new IllegalStateException("Za turnir su potrebna 3 tokena.");
                    }

                    TournamentPlayer player = TournamentPlayer.fromUserDocument(userDoc);
                    Map<String, Object> entry = player.toMap();
                    entry.put("status", "WAITING");
                    entry.put("tournamentId", "");
                    entry.put("roomId", "");
                    entry.put("createdAt", FieldValue.serverTimestamp());
                    transaction.update(userRef, "tokens", FieldValue.increment(-ENTRY_FEE));
                    transaction.set(queueRef, entry);
                    return null;
                })
                .addOnSuccessListener(unused -> findOrCreateTournament(onWaiting, onError))
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ulazak u turnir nije uspeo.")));
    }

    private boolean canCurrentPlayerStartFresh(
            @NonNull com.google.firebase.firestore.Transaction transaction,
            @NonNull String uid,
            @NonNull String tournamentId
    ) throws com.google.firebase.firestore.FirebaseFirestoreException {
        if (tournamentId.isEmpty()) {
            return false;
        }
        DocumentSnapshot tournamentDoc = transaction.get(db.collection(TOURNAMENTS).document(tournamentId));
        if (!tournamentDoc.exists()) {
            return true;
        }
        TournamentState state = TournamentState.fromDocument(tournamentDoc);
        if (TournamentState.STATUS_FINISHED.equals(state.getStatus())) {
            return true;
        }
        if (TournamentState.STATUS_FINAL_READY.equals(state.getStatus())) {
            return !uid.equals(state.getSemiOneWinnerUid()) && !uid.equals(state.getSemiTwoWinnerUid());
        }
        if (TournamentState.STATUS_SEMIS_READY.equals(state.getStatus())) {
            String roomId = state.roomForPlayer(uid);
            if (roomId.equals(state.getSemiOneRoomId()) && !state.getSemiOneWinnerUid().isEmpty()) {
                return !uid.equals(state.getSemiOneWinnerUid());
            }
            if (roomId.equals(state.getSemiTwoRoomId()) && !state.getSemiTwoWinnerUid().isEmpty()) {
                return !uid.equals(state.getSemiTwoWinnerUid());
            }
        }
        return false;
    }

    public void cancelWaiting(@NonNull Runnable onDone) {
        String uid = getCurrentUid();
        if (uid == null) {
            onDone.run();
            return;
        }
        db.collection(TOURNAMENT_QUEUE)
                .document(uid)
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && "WAITING".equals(snapshot.getString("status"))) {
                        snapshot.getReference().delete().addOnCompleteListener(task -> onDone.run());
                    } else {
                        onDone.run();
                    }
                })
                .addOnFailureListener(e -> onDone.run());
    }

    @Nullable
    public ListenerRegistration listenMyQueue(
            @NonNull Consumer<String> onTournament,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            return null;
        }
        return db.collection(TOURNAMENT_QUEUE)
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Turnir nije ucitan."));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        String tournamentId = snapshot.getString("tournamentId");
                        if (tournamentId != null && !tournamentId.isEmpty()) {
                            onTournament.accept(tournamentId);
                        }
                    }
                });
    }

    @Nullable
    public ListenerRegistration listenTournament(
            @NonNull String tournamentId,
            @NonNull Consumer<TournamentState> onChanged,
            @NonNull Consumer<String> onError
    ) {
        if (tournamentId.isEmpty()) {
            return null;
        }
        return db.collection(TOURNAMENTS)
                .document(tournamentId)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Turnir nije ucitan."));
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        onChanged.accept(TournamentState.fromDocument(snapshot));
                    }
                });
    }

    public void processFinishedTournamentRoom(
            @NonNull RoomSession room,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (room.getTournamentId().isEmpty() || room.getTournamentRound().isEmpty()) {
            onSuccess.run();
            return;
        }
        String uid = getCurrentUid();
        if (uid == null || (!uid.equals(room.getHostUid()) && !uid.equals(room.getGuestUid()))) {
            onSuccess.run();
            return;
        }
        db.runTransaction(transaction -> {
                    DocumentReference tournamentRef = db.collection(TOURNAMENTS).document(room.getTournamentId());
                    DocumentReference resultRef = db.collection(MATCH_RESULTS).document(room.getRoomId());
                    DocumentSnapshot tournamentDoc = transaction.get(tournamentRef);
                    DocumentSnapshot resultDoc = transaction.get(resultRef);
                    if (!tournamentDoc.exists()
                            || (resultDoc.exists() && Boolean.TRUE.equals(resultDoc.getBoolean("processedBy_" + uid)))) {
                        return null;
                    }

                    TournamentState tournament = TournamentState.fromDocument(tournamentDoc);
                    String winnerUid = tournamentWinnerUid(room);
                    String loserUid = room.getHostUid().equals(winnerUid) ? room.getGuestUid() : room.getHostUid();
                    int hostRegularStars = starsDelta(room.getHostTotalScore(), room.getHostUid().equals(winnerUid));
                    int guestRegularStars = starsDelta(room.getGuestTotalScore(), room.getGuestUid().equals(winnerUid));
                    int hostStarsDelta = starsForTournament(room, ROUND_FINAL.equals(room.getTournamentRound()), true, hostRegularStars);
                    int guestStarsDelta = starsForTournament(room, ROUND_FINAL.equals(room.getTournamentRound()), false, guestRegularStars);
                    int hostTokensDelta = tokensForTournament(room, ROUND_FINAL.equals(room.getTournamentRound()), true);
                    int guestTokensDelta = tokensForTournament(room, ROUND_FINAL.equals(room.getTournamentRound()), false);

                    boolean currentIsHost = uid.equals(room.getHostUid());
                    DocumentReference userRef = db.collection(USERS).document(uid);
                    DocumentSnapshot userDoc = transaction.get(userRef);
                    transaction.set(userRef, UserProfileMapper.toMap(profileAfterTournamentMatch(
                            UserProfileMapper.fromDocument(userDoc),
                            currentIsHost ? room.getHostTotalScore() : room.getGuestTotalScore(),
                            currentIsHost ? room.getGuestTotalScore() : room.getHostTotalScore(),
                            currentIsHost ? hostStarsDelta : guestStarsDelta,
                            currentIsHost ? hostTokensDelta : guestTokensDelta,
                            shouldUpdateStats(room, uid)
                    )), SetOptions.merge());

                    Map<String, Object> result = matchResult(room, winnerUid, loserUid, hostStarsDelta, guestStarsDelta);
                    result.put("processedBy_" + uid, true);
                    transaction.set(resultRef, result, SetOptions.merge());

                    Map<String, Object> tournamentUpdates = new HashMap<>();
                    applyRoundUpdate(transaction, tournament, tournamentRef, tournamentUpdates, room, winnerUid);
                    transaction.set(tournamentRef, tournamentUpdates, SetOptions.merge());
                    return null;
                })
                .addOnSuccessListener(unused -> {
                    syncCurrentProfile();
                    onSuccess.run();
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Turnirski rezultat nije obradjen.")));
    }

    private void findOrCreateTournament(
            @NonNull Runnable onWaiting,
            @NonNull Consumer<String> onError
    ) {
        db.collection(TOURNAMENT_QUEUE)
                .whereEqualTo("status", "WAITING")
                .limit(8)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<DocumentSnapshot> waiting = new ArrayList<>(snapshot.getDocuments());
                    if (waiting.size() < 4) {
                        onWaiting.run();
                        return;
                    }
                    Collections.shuffle(waiting);
                    createTournament(waiting.subList(0, 4), onWaiting, onError);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Turnirski red nije ucitan.")));
    }

    private void createTournament(
            @NonNull List<DocumentSnapshot> queueEntries,
            @NonNull Runnable onDone,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference tournamentRef = db.collection(TOURNAMENTS).document();
        DocumentReference semiOneRoom = db.collection(ROOMS).document();
        DocumentReference semiTwoRoom = db.collection(ROOMS).document();
        List<TournamentPlayer> players = new ArrayList<>();
        for (DocumentSnapshot entry : queueEntries) {
            players.add(TournamentPlayer.fromMap(entry.getData()));
        }

        db.runTransaction(transaction -> {
                    for (DocumentSnapshot entry : queueEntries) {
                        DocumentSnapshot latest = transaction.get(entry.getReference());
                        if (!latest.exists() || !"WAITING".equals(latest.getString("status"))) {
                            throw new IllegalStateException("Neko je vec uparen. Pokusaj ponovo.");
                        }
                    }

                    Map<String, Object> tournament = new HashMap<>();
                    tournament.put("status", TournamentState.STATUS_SEMIS_READY);
                    tournament.put("players", playerMaps(players));
                    tournament.put("semiOneRoomId", semiOneRoom.getId());
                    tournament.put("semiTwoRoomId", semiTwoRoom.getId());
                    tournament.put("finalRoomId", "");
                    tournament.put("semiOneWinnerUid", "");
                    tournament.put("semiTwoWinnerUid", "");
                    tournament.put("championUid", "");
                    tournament.put("createdAt", FieldValue.serverTimestamp());
                    tournament.put("updatedAt", FieldValue.serverTimestamp());
                    transaction.set(tournamentRef, tournament);

                    transaction.set(semiOneRoom, roomMap(players.get(0), players.get(1), tournamentRef.getId(), ROUND_SEMI_ONE));
                    transaction.set(semiTwoRoom, roomMap(players.get(2), players.get(3), tournamentRef.getId(), ROUND_SEMI_TWO));

                    updateQueue(transaction, players.get(0), tournamentRef.getId(), semiOneRoom.getId());
                    updateQueue(transaction, players.get(1), tournamentRef.getId(), semiOneRoom.getId());
                    updateQueue(transaction, players.get(2), tournamentRef.getId(), semiTwoRoom.getId());
                    updateQueue(transaction, players.get(3), tournamentRef.getId(), semiTwoRoom.getId());
                    return null;
                })
                .addOnSuccessListener(unused -> onDone.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Turnir nije kreiran.")));
    }

    private void applyRoundUpdate(
            @NonNull com.google.firebase.firestore.Transaction transaction,
            @NonNull TournamentState tournament,
            @NonNull DocumentReference tournamentRef,
            @NonNull Map<String, Object> updates,
            @NonNull RoomSession room,
            @NonNull String winnerUid
    ) {
        updates.put("updatedAt", FieldValue.serverTimestamp());
        if (ROUND_SEMI_ONE.equals(room.getTournamentRound())) {
            updates.put("semiOneWinnerUid", winnerUid);
            maybeCreateFinal(transaction, tournament, tournamentRef, updates, winnerUid, tournament.getSemiTwoWinnerUid());
            return;
        }
        if (ROUND_SEMI_TWO.equals(room.getTournamentRound())) {
            updates.put("semiTwoWinnerUid", winnerUid);
            maybeCreateFinal(transaction, tournament, tournamentRef, updates, tournament.getSemiOneWinnerUid(), winnerUid);
            return;
        }
        if (ROUND_FINAL.equals(room.getTournamentRound())) {
            updates.put("status", TournamentState.STATUS_FINISHED);
            updates.put("championUid", winnerUid);
            updates.put("finishedAt", FieldValue.serverTimestamp());
        }
    }

    private void maybeCreateFinal(
            @NonNull com.google.firebase.firestore.Transaction transaction,
            @NonNull TournamentState tournament,
            @NonNull DocumentReference tournamentRef,
            @NonNull Map<String, Object> updates,
            @NonNull String firstWinnerUid,
            @NonNull String secondWinnerUid
    ) {
        if (firstWinnerUid.isEmpty() || secondWinnerUid.isEmpty() || !tournament.getFinalRoomId().isEmpty()) {
            return;
        }
        TournamentPlayer first = tournament.playerByUid(firstWinnerUid);
        TournamentPlayer second = tournament.playerByUid(secondWinnerUid);
        if (first == null || second == null) {
            return;
        }
        DocumentReference finalRoom = db.collection(ROOMS).document();
        transaction.set(finalRoom, roomMap(first, second, tournamentRef.getId(), ROUND_FINAL));
        updates.put("status", TournamentState.STATUS_FINAL_READY);
        updates.put("finalRoomId", finalRoom.getId());
        updateQueue(transaction, first, tournamentRef.getId(), finalRoom.getId());
        updateQueue(transaction, second, tournamentRef.getId(), finalRoom.getId());
    }

    private UserProfile profileAfterTournamentMatch(
            @NonNull UserProfile profile,
            int myScore,
            int opponentScore,
            int starsDelta,
            int tokensDelta,
            boolean updateStats
    ) {
        UserProfile.Builder builder = profile.toBuilder().tokens(Math.max(0L, profile.getTokens() + tokensDelta));
        if (updateStats) {
            builder.statistics(statsAfterMatch(profile.getStatistics(), myScore, opponentScore));
        }
        if (starsDelta != 0) {
            String currentCycle = MonthlyCycleHelper.currentCycleKey();
            long monthlyStars = currentCycle.equals(profile.getStarsCycleKey()) ? profile.getMonthlyStars() : 0L;
            builder.totalStars(Math.max(0L, profile.getTotalStars() + starsDelta))
                    .monthlyStars(Math.max(0L, monthlyStars + starsDelta))
                    .starsCycleKey(currentCycle);
        }
        UserProfile updated = builder.build();
        if (starsDelta == 0) {
            return updated;
        }
        return leagueRepository.syncProfile(updated, profile, true).getProfile();
    }

    @NonNull
    private static PlayerStatistics statsAfterMatch(
            @NonNull PlayerStatistics stats,
            int myScore,
            int opponentScore
    ) {
        int totalMatches = stats.getTotalMatches() + 1;
        int matchesWon = stats.getMatchesWon();
        int matchesLost = stats.getMatchesLost();
        if (myScore > opponentScore) {
            matchesWon++;
        } else if (myScore < opponentScore) {
            matchesLost++;
        }
        float winPercent = totalMatches > 0 ? (matchesWon * 100f) / totalMatches : 0f;
        float lossPercent = totalMatches > 0 ? (matchesLost * 100f) / totalMatches : 0f;
        return new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                stats.getAvgScoreSpojnice(),
                stats.getAvgScoreMojBroj(),
                stats.getAvgScoreKorakPoKorak(),
                stats.getAvgScoreAsocijacije(),
                stats.getAvgScoreSkocko(),
                stats.getKoZnaZnaHits(),
                stats.getKoZnaZnaMisses(),
                stats.getMojBrojCorrectPercent(),
                stats.getKorakPoKorakStepPercents(),
                stats.getAsocijacijeSolved(),
                stats.getAsocijacijeUnsolved(),
                stats.getSkockoComboPercent(),
                stats.getSpojniceLinkedPercent(),
                totalMatches,
                winPercent,
                lossPercent,
                matchesWon,
                matchesLost
        );
    }

    private boolean shouldUpdateStats(@NonNull RoomSession room, @NonNull String uid) {
        if (ROUND_FINAL.equals(room.getTournamentRound())) {
            return true;
        }
        return uid.equals(tournamentWinnerUid(room));
    }

    private int starsForTournament(@NonNull RoomSession room, boolean finalRound, boolean host, int regularStars) {
        String uid = host ? room.getHostUid() : room.getGuestUid();
        boolean winner = uid.equals(tournamentWinnerUid(room));
        if (!finalRound) {
            return winner ? regularStars : 0;
        }
        return winner ? regularStars + FINAL_BONUS_STARS : regularStars;
    }

    private int tokensForTournament(@NonNull RoomSession room, boolean finalRound, boolean host) {
        String uid = host ? room.getHostUid() : room.getGuestUid();
        boolean winner = uid.equals(tournamentWinnerUid(room));
        if (!winner) {
            return 0;
        }
        return finalRound ? FINAL_WIN_TOKENS : SEMI_WIN_TOKENS;
    }

    @NonNull
    private static String tournamentWinnerUid(@NonNull RoomSession room) {
        if (room.getGuestTotalScore() > room.getHostTotalScore()) {
            return room.getGuestUid();
        }
        return room.getHostUid();
    }

    private static int starsDelta(int score, boolean won) {
        int scoreBonus = Math.max(0, score) / 40;
        return (won ? 10 : -10) + scoreBonus;
    }

    @NonNull
    private static Map<String, Object> matchResult(
            @NonNull RoomSession room,
            @NonNull String winnerUid,
            @NonNull String loserUid,
            int hostStarsDelta,
            int guestStarsDelta
    ) {
        Map<String, Object> result = new HashMap<>();
        result.put("roomId", room.getRoomId());
        result.put("hostUid", room.getHostUid());
        result.put("guestUid", room.getGuestUid());
        result.put("hostUsername", room.getHostUsername());
        result.put("guestUsername", room.getGuestUsername());
        result.put("hostScore", room.getHostTotalScore());
        result.put("guestScore", room.getGuestTotalScore());
        result.put("winnerUid", winnerUid);
        result.put("loserUid", loserUid);
        result.put("matchType", MATCH_TYPE_TOURNAMENT);
        result.put("tournamentId", room.getTournamentId());
        result.put("tournamentRound", room.getTournamentRound());
        result.put("hostStarsDelta", hostStarsDelta);
        result.put("guestStarsDelta", guestStarsDelta);
        result.put("finishedAt", FieldValue.serverTimestamp());
        result.put("updatedAt", FieldValue.serverTimestamp());
        return result;
    }

    @NonNull
    private static Map<String, Object> roomMap(
            @NonNull TournamentPlayer host,
            @NonNull TournamentPlayer guest,
            @NonNull String tournamentId,
            @NonNull String round
    ) {
        Map<String, Object> room = new HashMap<>();
        room.put("hostUid", host.getUid());
        room.put("guestUid", guest.getUid());
        room.put("hostUsername", host.getUsername());
        room.put("guestUsername", guest.getUsername());
        room.put("hostTotalScore", 0);
        room.put("guestTotalScore", 0);
        room.put("currentGame", RoomGameKeys.DEFAULT_GAME_ORDER.get(0));
        room.put("currentGameIndex", 0);
        room.put("gameOrder", RoomGameKeys.DEFAULT_GAME_ORDER);
        room.put("status", RoomGameKeys.STATUS_READY);
        room.put("matchType", MATCH_TYPE_TOURNAMENT);
        room.put("tournamentId", tournamentId);
        room.put("tournamentRound", round);
        room.put("createdAt", FieldValue.serverTimestamp());
        room.put("updatedAt", FieldValue.serverTimestamp());
        return room;
    }

    private static void updateQueue(
            @NonNull com.google.firebase.firestore.Transaction transaction,
            @NonNull TournamentPlayer player,
            @NonNull String tournamentId,
            @NonNull String roomId
    ) {
        Map<String, Object> update = new HashMap<>();
        update.put("status", "MATCHED");
        update.put("tournamentId", tournamentId);
        update.put("roomId", roomId);
        update.put("matchedAt", FieldValue.serverTimestamp());
        transaction.set(
                FirebaseFirestore.getInstance().collection(TOURNAMENT_QUEUE).document(player.getUid()),
                update,
                SetOptions.merge()
        );
    }

    @NonNull
    private static List<Map<String, Object>> playerMaps(@NonNull List<TournamentPlayer> players) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (TournamentPlayer player : players) {
            maps.add(player.toMap());
        }
        return maps;
    }

    private void syncCurrentProfile() {
        String uid = getCurrentUid();
        if (uid == null || !remote.isLoggedIn()) {
            return;
        }
        remote.fetchUserProfile(uid, profile -> preferences.saveProfile(profile), error -> { });
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        String message = error.getMessage();
        if (message != null && !message.trim().isEmpty()) {
            return message;
        }
        return fallback;
    }
}
