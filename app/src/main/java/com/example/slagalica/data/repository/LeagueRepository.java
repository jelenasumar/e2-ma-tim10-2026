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
        if (!remote.isRegisteredUser()) {
            return;
        }
        String uid = remote.getCurrentUid();
        if (uid == null) {
            return;
        }

        RankingRepository.CycleWindow window = rankingRepository.previousMonthlyWindow();
        String penaltyDocId = "league_penalty_monthly_" + window.startMillis;
        if (preferences.wasMonthlyPenaltyProcessed(window.startMillis)) {
            return;
        }

        rankingRepository.loadRankingForWindow(
                RankingRepository.CycleType.MONTHLY,
                window,
                result -> {
                    int rank = findRank(result.getEntries(), uid);
                    if (rank > 0 && rank <= 10) {
                        preferences.markMonthlyPenaltyProcessed(window.startMillis);
                        markPenaltyProcessedRemote(penaltyDocId, uid, window, false);
                        return;
                    }

                    UserProfile profile = preferences.loadProfile();
                    long previousStars = profile.getTotalStars();
                    if (previousStars <= 0L) {
                        preferences.markMonthlyPenaltyProcessed(window.startMillis);
                        markPenaltyProcessedRemote(penaltyDocId, uid, window, false);
                        return;
                    }

                    long newStars = LeagueHelper.starsAfterMonthlyPenalty(previousStars);
                    UserProfile before = profile;
                    UserProfile updated = applyStarUpdate(
                            profile.toBuilder().totalStars(newStars).build(),
                            newStars,
                            false
                    );
                    preferences.saveProfile(updated);
                    persistRemote(updated);
                    preferences.markMonthlyPenaltyProcessed(window.startMillis);
                    markPenaltyProcessedRemote(penaltyDocId, uid, window, true);

                    LeagueChangeEvent change = LeagueHelper.detectChange(appContext, before, updated);
                    String message = appContext.getString(
                            R.string.league_monthly_penalty_message,
                            previousStars,
                            newStars
                    );
                    if (change != null) {
                        publishLeagueChange(change);
                    } else {
                        LeagueChangeNotifier.get().notifyChange(new LeagueChangeEvent(
                                LeagueTier.fromKey(before.getLeagueTierKey()),
                                LeagueTier.fromKey(updated.getLeagueTierKey()),
                                LeagueChangeEvent.Direction.DEMOTED,
                                updated.getTotalStars(),
                                message
                        ));
                    }
                    createLeagueNotification(message, "OPEN_LEAGUE");
                    onPenaltyApplied.accept(message);
                },
                onError
        );
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
        String action = change.getDirection() == LeagueChangeEvent.Direction.PROMOTED
                ? "OPEN_LEAGUE"
                : "OPEN_LEAGUE";
        createLeagueNotification(change.getMessage(), action);
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
            boolean penalized
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("uid", uid);
        data.put("cycleStart", window.startMillis);
        data.put("cycleEnd", window.endMillis);
        data.put("penalized", penalized);
        data.put("processedAt", FieldValue.serverTimestamp());
        db.collection(CONFIG)
                .document(penaltyDocId + "_" + uid)
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
