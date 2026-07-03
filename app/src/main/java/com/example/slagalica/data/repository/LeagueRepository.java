package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;
import com.example.slagalica.data.local.UserPreferences;
import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.LeagueChangeEvent;
import com.example.slagalica.model.LeagueTier;
import com.example.slagalica.model.RankingEntry;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.LeagueChangeNotifier;
import com.example.slagalica.utils.LeagueHelper;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class LeagueRepository {

    public static final class SyncResult {
        private final UserProfile profile;
        @Nullable
        private final LeagueChangeEvent leagueChange;

        SyncResult(@NonNull UserProfile profile, @Nullable LeagueChangeEvent leagueChange) {
            this.profile = profile;
            this.leagueChange = leagueChange;
        }

        @NonNull
        public UserProfile getProfile() {
            return profile;
        }

        @Nullable
        public LeagueChangeEvent getLeagueChange() {
            return leagueChange;
        }
    }

    private static final String USERS = "users";
    private static final String NOTIFICATIONS = "notifications";
    private static final String CONFIG = "config";

    private final Context appContext;
    private final UserPreferences preferences;
    private final FireBaseUserDataSource remote;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final RankingRepository rankingRepository;

    public LeagueRepository(@NonNull Context context) {
        appContext = context.getApplicationContext();
        preferences = new UserPreferences(appContext);
        remote = new FireBaseUserDataSource();
        rankingRepository = new RankingRepository(appContext);
    }

    @NonNull
    public SyncResult syncProfile(
            @NonNull UserProfile profile,
            @NonNull UserProfile changeBaseline,
            boolean notifyLeagueChange
    ) {
        UserProfile withTokens = grantDailyTokensIfNeeded(profile);
        UserProfile afterLeague = LeagueHelper.withLeagueForStars(appContext, withTokens);
        LeagueChangeEvent change = LeagueHelper.detectChange(appContext, changeBaseline, afterLeague);
        if (change != null && notifyLeagueChange) {
            publishLeagueChange(change);
        }
        return new SyncResult(afterLeague, change);
    }

    @NonNull
    public UserProfile applyStarUpdate(
            @NonNull UserProfile profile,
            long newTotalStars,
            boolean notifyLeagueChange
    ) {
        UserProfile before = profile;
        UserProfile updated = profile.toBuilder()
                .totalStars(Math.max(0L, newTotalStars))
                .build();
        UserProfile afterLeague = LeagueHelper.withLeagueForStars(appContext, updated);
        LeagueChangeEvent change = LeagueHelper.detectChange(appContext, before, afterLeague);
        if (change != null && notifyLeagueChange) {
            publishLeagueChange(change);
        }
        return afterLeague;
    }

    public void processMonthlyPenaltyForCurrentUser(
            @NonNull Consumer<String> onPenaltyApplied,
            @NonNull Consumer<String> onError
    ) {
        processMonthlyPenaltyForCurrentUser(null, onPenaltyApplied, onError);
    }

    public void processMonthlyPenaltyForCurrentUser(
            @Nullable UserProfile currentProfile,
            @NonNull Consumer<String> onPenaltyApplied,
            @NonNull Consumer<String> onError
    ) {
        if (!remote.isRegisteredUser()) {
            return;
        }
        String uid = remote.getCurrentUid();
        if (uid == null) {
            return;
        }

        RankingRepository.CycleWindow window = rankingRepository.previousMonthlyWindow();
        String penaltyDocId = penaltyDocId(window.startMillis, uid);

        db.collection(CONFIG)
                .document(penaltyDocId)
                .get()
                .addOnSuccessListener(configDoc -> {
                    if (configDoc.exists()) {
                        preferences.markMonthlyPenaltyProcessed(window.startMillis);
                        return;
                    }
                    if (preferences.wasMonthlyPenaltyProcessed(window.startMillis)) {
                        preferences.clearMonthlyPenaltyProcessed(window.startMillis);
                    }
                    loadProfileAndApplyPenalty(uid, window, penaltyDocId, currentProfile, onPenaltyApplied, onError);
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Provera kazne nije uspela."
                ));
    }

    private void loadProfileAndApplyPenalty(
            @NonNull String uid,
            @NonNull RankingRepository.CycleWindow window,
            @NonNull String penaltyDocId,
            @Nullable UserProfile currentProfile,
            @NonNull Consumer<String> onPenaltyApplied,
            @NonNull Consumer<String> onError
    ) {
        if (currentProfile != null) {
            evaluateMonthlyPenalty(uid, window, penaltyDocId, currentProfile, onPenaltyApplied, onError);
            return;
        }
        remote.fetchUserProfile(
                uid,
                profile -> evaluateMonthlyPenalty(uid, window, penaltyDocId, profile, onPenaltyApplied, onError),
                onError
        );
    }

    private void evaluateMonthlyPenalty(
            @NonNull String uid,
            @NonNull RankingRepository.CycleWindow window,
            @NonNull String penaltyDocId,
            @NonNull UserProfile profile,
            @NonNull Consumer<String> onPenaltyApplied,
            @NonNull Consumer<String> onError
    ) {
        rankingRepository.loadRankingForWindow(
                RankingRepository.CycleType.MONTHLY,
                window,
                result -> {
                    int rank = findRank(result.getEntries(), uid);
                    if (rank > 0 && rank <= 10) {
                        preferences.markMonthlyPenaltyProcessed(window.startMillis);
                        markPenaltyProcessedRemote(penaltyDocId, uid, window, false, 0L, 0L);
                        return;
                    }

                    long previousStars = profile.getTotalStars();
                    if (previousStars <= 0L) {
                        return;
                    }

                    long newStars = LeagueHelper.starsAfterMonthlyPenalty(previousStars);
                    UserProfile before = profile;
                    UserProfile updated = LeagueHelper.withLeagueForStars(
                            appContext,
                            profile.toBuilder().totalStars(newStars).build()
                    );
                    preferences.saveProfile(updated);
                    persistRemote(updated);
                    preferences.markMonthlyPenaltyProcessed(window.startMillis);
                    markPenaltyProcessedRemote(
                            penaltyDocId,
                            uid,
                            window,
                            true,
                            previousStars,
                            newStars
                    );

                    String message = appContext.getString(
                            R.string.league_monthly_penalty_message,
                            previousStars,
                            newStars
                    );
                    LeagueTier previousTier = LeagueTier.fromKey(before.getLeagueTierKey());
                    LeagueTier newTier = LeagueTier.fromKey(updated.getLeagueTierKey());
                    LeagueChangeEvent.Direction direction = newTier.getLevel() < previousTier.getLevel()
                            ? LeagueChangeEvent.Direction.DEMOTED
                            : LeagueChangeEvent.Direction.PROMOTED;
                    LeagueChangeNotifier.get().notifyChange(new LeagueChangeEvent(
                            previousTier,
                            newTier,
                            direction,
                            updated.getTotalStars(),
                            message
                    ));
                    createLeagueNotification(message, "OPEN_LEAGUE");
                    onPenaltyApplied.accept(message);
                },
                onError
        );
    }

    @NonNull
    private static String penaltyDocId(long cycleStartMillis, @NonNull String uid) {
        return "league_penalty_monthly_" + cycleStartMillis + "_" + uid;
    }

    @NonNull
    private UserProfile grantDailyTokensIfNeeded(@NonNull UserProfile profile) {
        String today = LocalDate.now(ZoneId.systemDefault())
                .format(DateTimeFormatter.ISO_LOCAL_DATE);
        if (today.equals(preferences.getLastDailyTokenGrantDate())) {
            return profile;
        }

        LeagueTier tier = LeagueTier.fromStars(profile.getTotalStars());
        long grant = tier.getDailyTokenTotal();
        UserProfile updated = profile.toBuilder()
                .tokens(profile.getTokens() + grant)
                .build();
        preferences.setLastDailyTokenGrantDate(today);
        return updated;
    }

    private void publishLeagueChange(@NonNull LeagueChangeEvent change) {
        LeagueChangeNotifier.get().notifyChange(change);
        createLeagueNotification(change.getMessage(), "OPEN_LEAGUE");
    }

    private void createLeagueNotification(@NonNull String message, @NonNull String action) {
        String uid = remote.getCurrentUid();
        if (uid == null) {
            return;
        }
        Map<String, Object> notification = new HashMap<>();
        notification.put("type", "LEAGUE_CHANGE");
        notification.put("category", "RANKING");
        notification.put("title", appContext.getString(R.string.notification_league_title));
        notification.put("message", message);
        notification.put("read", false);
        notification.put("action", action);
        notification.put("actionLabel", appContext.getString(R.string.open_league));
        notification.put("fromUid", uid);
        notification.put("createdAt", FieldValue.serverTimestamp());
        db.collection(USERS)
                .document(uid)
                .collection(NOTIFICATIONS)
                .add(notification);
    }

    private void persistRemote(@NonNull UserProfile profile) {
        String uid = remote.getCurrentUid();
        if (uid != null && remote.isLoggedIn()) {
            remote.saveUserProfile(uid, profile, () -> { }, error -> { });
        }
    }

    private void markPenaltyProcessedRemote(
            @NonNull String penaltyDocId,
            @NonNull String uid,
            @NonNull RankingRepository.CycleWindow window,
            boolean penalized,
            long starsBefore,
            long starsAfter
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("cycleStart", window.startMillis);
        data.put("cycleEnd", window.endMillis);
        data.put("penalized", penalized);
        data.put("starsBefore", starsBefore);
        data.put("starsAfter", starsAfter);
        data.put("processedAt", FieldValue.serverTimestamp());
        db.collection(CONFIG)
                .document(penaltyDocId)
                .set(data, SetOptions.merge());
    }

    private static int findRank(@NonNull List<RankingEntry> entries, @NonNull String uid) {
        for (RankingEntry entry : entries) {
            if (uid.equals(entry.getUid())) {
                return entry.getRank();
            }
        }
        return -1;
    }
}
