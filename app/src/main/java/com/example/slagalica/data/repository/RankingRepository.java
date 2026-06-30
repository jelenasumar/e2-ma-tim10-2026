package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RankingEntry;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.text.DateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class RankingRepository {

    public enum CycleType {
        WEEKLY,
        MONTHLY
    }

    public static final class RankingResult {
        private final List<RankingEntry> entries;
        private final String cycleLabel;

        RankingResult(@NonNull List<RankingEntry> entries, @NonNull String cycleLabel) {
            this.entries = entries;
            this.cycleLabel = cycleLabel;
        }

        @NonNull
        public List<RankingEntry> getEntries() {
            return entries;
        }

        @NonNull
        public String getCycleLabel() {
            return cycleLabel;
        }
    }

    private static final String MATCH_RESULTS = "match_results";
    private static final String USERS = "users";
    private static final String NOTIFICATIONS = "notifications";
    private static final String CONFIG = "config";
    private static final String RANDOM = "RANDOM";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();
    @SuppressWarnings("unused")
    private final Context appContext;

    public RankingRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
    }

    public void loadRanking(
            @NonNull CycleType type,
            @NonNull Consumer<RankingResult> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        CycleWindow window = currentWindow(type);
        loadRankingForWindow(type, window, onSuccess, onError);
    }

    public void loadRankingForWindow(
            @NonNull CycleType type,
            @NonNull CycleWindow window,
            @NonNull Consumer<RankingResult> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(MATCH_RESULTS)
                .whereEqualTo("matchType", RANDOM)
                .get()
                .addOnSuccessListener(snapshot -> onSuccess.accept(new RankingResult(
                        buildEntries(snapshot.getDocuments(), window),
                        formatRange(window.startMillis, window.endMillis)
                )))
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Rang lista nije ucitana.")));
    }

    @NonNull
    public CycleWindow previousMonthlyWindow() {
        return previousWindow(CycleType.MONTHLY);
    }

    public void processFinishedCycleRewards(
            @NonNull CycleType type,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onReward,
            @NonNull Consumer<String> onError
    ) {
        CycleWindow window = previousWindow(type);
        String rewardDocId = rewardDocId(type, window);
        db.collection(CONFIG)
                .document(rewardDocId)
                .get()
                .addOnSuccessListener(config -> {
                    if (config.exists()) {
                        onSuccess.run();
                        return;
                    }
                    db.collection(MATCH_RESULTS)
                            .whereEqualTo("matchType", RANDOM)
                            .get()
                            .addOnSuccessListener(snapshot -> distributeRewards(
                                    type,
                                    window,
                                    rewardDocId,
                                    buildEntries(snapshot.getDocuments(), window),
                                    onSuccess,
                                    onReward,
                                    onError
                            ))
                            .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Nagrade nisu obradjene.")));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ciklus nagrada nije proveren.")));
    }

    public void processFinishedCyclePlacements(
            @NonNull CycleType type,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        CycleWindow window = previousWindow(type);
        String placementDocId = placementDocId(type, window);
        db.collection(CONFIG)
                .document(placementDocId)
                .get()
                .addOnSuccessListener(config -> {
                    if (config.exists()) {
                        onSuccess.run();
                        return;
                    }
                    db.collection(MATCH_RESULTS)
                            .whereEqualTo("matchType", RANDOM)
                            .get()
                            .addOnSuccessListener(snapshot -> distributePlacementNotifications(
                                    type,
                                    window,
                                    placementDocId,
                                    buildEntries(snapshot.getDocuments(), window),
                                    onSuccess,
                                    onError
                            ))
                            .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Plasmani nisu obradjeni.")));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ciklus plasmana nije proveren.")));
    }

    private void distributeRewards(
            @NonNull CycleType type,
            @NonNull CycleWindow window,
            @NonNull String rewardDocId,
            @NonNull List<RankingEntry> entries,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onReward,
            @NonNull Consumer<String> onError
    ) {
        List<RankingEntry> winners = new ArrayList<>();
        for (RankingEntry entry : entries) {
            if (entry.getRank() <= 10 && rewardTokens(type, entry.getRank()) > 0) {
                winners.add(entry);
            }
        }
        if (winners.isEmpty()) {
            markCycleProcessed(rewardDocId, window, 0, onSuccess, onError);
            return;
        }

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        String currentUid = currentUid();
        String currentRewardMessage = "";
        for (RankingEntry winner : winners) {
            int tokens = rewardTokens(type, winner.getRank());
            batch.update(db.collection(USERS).document(winner.getUid()), "tokens", FieldValue.increment(tokens));
            String message = rewardMessage(type, winner.getRank(), tokens);
            if (winner.getUid().equals(currentUid)) {
                currentRewardMessage = message;
            }

            Map<String, Object> notification = baseRankingNotification(
                    type,
                    window,
                    currentUid,
                    winner.getRank()
            );
            notification.put("type", "RANKING_REWARD");
            notification.put("category", "REWARD");
            notification.put("title", "Nagrada sa rang liste");
            notification.put("message", message);
            notification.put("tokens", tokens);
            batch.set(db.collection(USERS)
                    .document(winner.getUid())
                    .collection(NOTIFICATIONS)
                    .document(), notification);
        }

        Map<String, Object> processed = new HashMap<>();
        processed.put("cycleType", type.name());
        processed.put("cycleStart", window.startMillis);
        processed.put("cycleEnd", window.endMillis);
        processed.put("winnerCount", winners.size());
        processed.put("processedBy", currentUid);
        processed.put("processedAt", FieldValue.serverTimestamp());
        batch.set(db.collection(CONFIG).document(rewardDocId), processed, SetOptions.merge());

        String rewardMessage = currentRewardMessage;
        batch.commit()
                .addOnSuccessListener(unused -> {
                    if (!rewardMessage.isEmpty()) {
                        onReward.accept(rewardMessage);
                    }
                    onSuccess.run();
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Nagrade nisu dodeljene.")));
    }

    private void distributePlacementNotifications(
            @NonNull CycleType type,
            @NonNull CycleWindow window,
            @NonNull String placementDocId,
            @NonNull List<RankingEntry> entries,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (entries.isEmpty()) {
            markCycleProcessed(placementDocId, window, 0, onSuccess, onError);
            return;
        }

        com.google.firebase.firestore.WriteBatch batch = db.batch();
        String currentUid = currentUid();
        for (RankingEntry entry : entries) {
            Map<String, Object> notification = baseRankingNotification(
                    type,
                    window,
                    currentUid,
                    entry.getRank()
            );
            notification.put("type", "RANKING_PLACEMENT");
            notification.put("category", "RANKING");
            notification.put("title", "Plasman na rang listi");
            notification.put("message", rankingMessage(type, entry.getRank()));
            batch.set(db.collection(USERS)
                    .document(entry.getUid())
                    .collection(NOTIFICATIONS)
                    .document(), notification);
        }

        Map<String, Object> processed = new HashMap<>();
        processed.put("cycleType", type.name());
        processed.put("cycleStart", window.startMillis);
        processed.put("cycleEnd", window.endMillis);
        processed.put("rankedCount", entries.size());
        processed.put("processedBy", currentUid);
        processed.put("processedAt", FieldValue.serverTimestamp());
        batch.set(db.collection(CONFIG).document(placementDocId), processed, SetOptions.merge());

        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Plasmani nisu sacuvani.")));
    }

    private void markCycleProcessed(
            @NonNull String rewardDocId,
            @NonNull CycleWindow window,
            int winnerCount,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> processed = new HashMap<>();
        processed.put("cycleStart", window.startMillis);
        processed.put("cycleEnd", window.endMillis);
        processed.put("winnerCount", winnerCount);
        processed.put("processedBy", currentUid());
        processed.put("processedAt", FieldValue.serverTimestamp());
        db.collection(CONFIG).document(rewardDocId)
                .set(processed, SetOptions.merge())
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ciklus nije sacuvan.")));
    }

    @NonNull
    private List<RankingEntry> buildEntries(
            @NonNull List<DocumentSnapshot> documents,
            @NonNull CycleWindow window
    ) {
        Map<String, MutableRanking> byUser = new HashMap<>();
        for (DocumentSnapshot document : documents) {
            Long finishedAt = millis(document.get("finishedAt"));
            if (finishedAt == null || finishedAt < window.startMillis || finishedAt >= window.endMillis) {
                continue;
            }
            addPlayer(
                    byUser,
                    stringValue(document.get("hostUid")),
                    stringValue(document.get("hostUsername")),
                    rankingStars(longValue(document.get("hostStarsDelta")))
            );
            addPlayer(
                    byUser,
                    stringValue(document.get("guestUid")),
                    stringValue(document.get("guestUsername")),
                    rankingStars(longValue(document.get("guestStarsDelta")))
            );
        }

        List<MutableRanking> mutable = new ArrayList<>(byUser.values());
        mutable.sort(Comparator
                .comparingLong((MutableRanking item) -> item.stars).reversed()
                .thenComparing(item -> item.username.toLowerCase(Locale.ROOT)));

        List<RankingEntry> entries = new ArrayList<>();
        for (int i = 0; i < mutable.size(); i++) {
            MutableRanking item = mutable.get(i);
            entries.add(new RankingEntry(item.uid, item.username, i + 1, item.stars, item.matchesPlayed));
        }
        return entries;
    }

    private static void addPlayer(
            @NonNull Map<String, MutableRanking> byUser,
            @NonNull String uid,
            @NonNull String username,
            long stars
    ) {
        if (uid.isEmpty()) {
            return;
        }
        MutableRanking current = byUser.get(uid);
        if (current == null) {
            current = new MutableRanking(uid, username.isEmpty() ? "Igrac" : username);
            byUser.put(uid, current);
        }
        current.stars += stars;
        current.matchesPlayed++;
    }

    private static int rewardTokens(@NonNull CycleType type, int rank) {
        if (rank == 1) {
            return type == CycleType.WEEKLY ? 5 : 10;
        }
        if (rank == 2) {
            return type == CycleType.WEEKLY ? 3 : 6;
        }
        if (rank == 3) {
            return type == CycleType.WEEKLY ? 2 : 4;
        }
        if (rank >= 4 && rank <= 10) {
            return type == CycleType.WEEKLY ? 1 : 2;
        }
        return 0;
    }

    @NonNull
    private static Map<String, Object> baseRankingNotification(
            @NonNull CycleType type,
            @NonNull CycleWindow window,
            @NonNull String currentUid,
            int rank
    ) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("read", false);
        notification.put("action", "OPEN_RANKING");
        notification.put("actionLabel", "Otvori rang listu");
        notification.put("fromUid", currentUid);
        notification.put("createdAt", FieldValue.serverTimestamp());
        notification.put("cycleType", type.name());
        notification.put("cycleStart", window.startMillis);
        notification.put("cycleEnd", window.endMillis);
        notification.put("rank", rank);
        return notification;
    }

    @NonNull
    private static String rankingMessage(@NonNull CycleType type, int rank) {
        String cycle = type == CycleType.WEEKLY ? "nedeljnoj" : "mesecnoj";
        return "Zavrsili ste ciklus na " + rank + ". mestu na " + cycle + " rang listi.";
    }

    @NonNull
    private static String rewardMessage(@NonNull CycleType type, int rank, int tokens) {
        String cycle = type == CycleType.WEEKLY ? "nedeljnoj" : "mesecnoj";
        return "Osvojili ste " + rank + ". mesto na " + cycle + " rang listi i dobili " + tokens + " tokena.";
    }

    @NonNull
    private static CycleWindow currentWindow(@NonNull CycleType type) {
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        if (type == CycleType.WEEKLY) {
            LocalDate start = today.with(DayOfWeek.MONDAY);
            return window(start, start.plusWeeks(1));
        }
        LocalDate start = today.withDayOfMonth(1);
        return window(start, start.plusMonths(1));
    }

    @NonNull
    private static CycleWindow previousWindow(@NonNull CycleType type) {
        CycleWindow current = currentWindow(type);
        ZonedDateTime currentStart = ZonedDateTime.ofInstant(
                java.time.Instant.ofEpochMilli(current.startMillis),
                ZoneId.systemDefault()
        );
        if (type == CycleType.WEEKLY) {
            LocalDate start = currentStart.toLocalDate().minusWeeks(1);
            return window(start, start.plusWeeks(1));
        }
        LocalDate start = currentStart.toLocalDate().minusMonths(1);
        return window(start, start.plusMonths(1));
    }

    @NonNull
    private static CycleWindow window(@NonNull LocalDate start, @NonNull LocalDate end) {
        ZoneId zone = ZoneId.systemDefault();
        return new CycleWindow(
                start.atStartOfDay(zone).toInstant().toEpochMilli(),
                end.atStartOfDay(zone).toInstant().toEpochMilli()
        );
    }

    @NonNull
    private static String formatRange(long startMillis, long endMillis) {
        DateFormat format = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault());
        long inclusiveEnd = Math.max(startMillis, endMillis - 1L);
        return format.format(new Date(startMillis)) + " - " + format.format(new Date(inclusiveEnd));
    }

    @NonNull
    private static String rewardDocId(@NonNull CycleType type, @NonNull CycleWindow window) {
        return "ranking_reward_" + type.name().toLowerCase(Locale.ROOT) + "_" + window.startMillis;
    }

    @NonNull
    private static String placementDocId(@NonNull CycleType type, @NonNull CycleWindow window) {
        return "ranking_placement_" + type.name().toLowerCase(Locale.ROOT) + "_" + window.startMillis;
    }

    @NonNull
    private String currentUid() {
        return auth.getCurrentUser() != null ? auth.getCurrentUser().getUid() : "";
    }

    @Nullable
    private static Long millis(@Nullable Object value) {
        if (value instanceof Timestamp) {
            return ((Timestamp) value).toDate().getTime();
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private static long longValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static long rankingStars(long starsDelta) {
        return Math.max(0L, starsDelta);
    }

    @NonNull
    private static String stringValue(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        return error.getMessage() != null ? error.getMessage() : fallback;
    }

    private static final class MutableRanking {
        final String uid;
        final String username;
        long stars;
        int matchesPlayed;

        MutableRanking(@NonNull String uid, @NonNull String username) {
            this.uid = uid;
            this.username = username;
        }
    }

    public static final class CycleWindow {
        public final long startMillis;
        public final long endMillis;

        public CycleWindow(long startMillis, long endMillis) {
            this.startMillis = startMillis;
            this.endMillis = endMillis;
        }
    }
}
