package com.example.slagalica.data.repository;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.local.UserPreferences;
import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.data.remote.RegionDataSource;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionPlayerMarker;
import com.example.slagalica.model.RegionRankingSummary;
import com.example.slagalica.model.RegionStats;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.AvatarFrameHelper;
import com.example.slagalica.utils.MonthlyCycleHelper;
import com.example.slagalica.utils.RegionCycleTestConfig;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.function.Consumer;

public final class RegionRepository {

    private static final long ACTIVE_WINDOW_MS = 7L * 24L * 60L * 60L * 1000L;

    private final Context appContext;
    private final UserPreferences preferences;
    private final FireBaseUserDataSource userRemote;
    private final RegionDataSource regionRemote;
    private List<String> cachedTopRegions = Collections.emptyList();

    public RegionRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = new UserPreferences(appContext);
        this.userRemote = new FireBaseUserDataSource();
        this.regionRemote = new RegionDataSource();
        if (RegionCycleTestConfig.USE_HARDCODED_PREVIOUS_TOP_REGIONS) {
            cachedTopRegions = RegionCycleTestConfig.HARDCODED_PREVIOUS_TOP_REGIONS;
        }
    }

    @NonNull
    public UserProfile ensureRegionState(@NonNull UserProfile profile) {
        String currentCycle = MonthlyCycleHelper.currentCycleKey();
        UserProfile.Builder builder = profile.toBuilder();
        boolean changed = false;

        SerbiaRegion resolved = SerbiaRegion.resolve(profile.getRegionKey(), profile.getRegion());
        String effectiveRegionKey = profile.getRegionKey();
        if (resolved != null) {
            effectiveRegionKey = resolved.getKey();
            if (!resolved.getKey().equals(profile.getRegionKey())) {
                builder.regionKey(resolved.getKey());
                changed = true;
            }
            String displayName = resolved.getDisplayName(appContext);
            if (!displayName.equals(profile.getRegion())) {
                builder.region(displayName);
                changed = true;
            }
            if (profile.getMapPointX() <= 0f && profile.getMapPointY() <= 0f) {
                float[] point = resolved.randomMapPoint(new Random(profile.getUsername().hashCode()));
                builder.mapPointX(point[0]).mapPointY(point[1]);
                changed = true;
            }
        }

        if (!currentCycle.equals(profile.getStarsCycleKey())) {
            builder.monthlyStars(0L)
                    .starsCycleKey(currentCycle);
            changed = true;
        }

        String frame = AvatarFrameHelper.resolveRankFrame(effectiveRegionKey, profile.getRegion());
        if (!frame.equals(profile.getRegionRankFrame())) {
            builder.regionRankFrame(frame);
            changed = true;
        }

        long now = System.currentTimeMillis();
        if (now - profile.getLastActiveAt() > 60_000L) {
            builder.lastActiveAt(now);
            changed = true;
        }

        UserProfile updated = builder.build();
        if (changed) {
            preferences.saveProfile(updated);
            persistRemote(updated);
            touchActivity(updated);
        }
        return updated;
    }

    public void loadMapMarkers(
            @NonNull Consumer<List<RegionPlayerMarker>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String currentUid = userRemote.getCurrentUid();
        regionRemote.fetchAllPlayerMarkers(rows -> {
            List<RegionPlayerMarker> markers = new ArrayList<>();
            for (Map<String, Object> row : rows) {
                String uid = stringValue(row.get("uid"));
                String username = stringValue(row.get("username"));
                String regionKey = stringValue(row.get("regionKey"));
                if (regionKey.isEmpty()) {
                    continue;
                }
                float x = floatValue(row.get("mapPointX"));
                float y = floatValue(row.get("mapPointY"));
                if (x <= 0f || y <= 0f) {
                    SerbiaRegion region = SerbiaRegion.fromKey(regionKey);
                    if (region != null) {
                        float[] point = region.randomMapPoint(new Random(uid.hashCode()));
                        x = point[0];
                        y = point[1];
                    }
                }
                markers.add(new RegionPlayerMarker(
                        uid,
                        username,
                        regionKey,
                        x,
                        y,
                        uid.equals(currentUid)
                ));
            }
            onSuccess.accept(markers);
        }, onError);
    }

    public void loadLeaderboard(
            @NonNull Consumer<List<RegionLeaderboardEntry>> onPlayers,
            @NonNull Consumer<List<RegionRankingSummary>> onRegions,
            @NonNull Consumer<String> onError
    ) {
        String currentUid = userRemote.getCurrentUid();
        UserProfile currentProfile = preferences.loadProfile();
        String currentRegionKey = currentProfile.getRegionKey();

        regionRemote.fetchAllProfilesForCycle(rows -> {
            maybeProcessCycle(rows, () -> buildLeaderboard(rows, currentUid, currentRegionKey, onPlayers, onRegions));
        }, onError);
    }

    public void loadRegionStats(
            @NonNull String regionKey,
            @NonNull Consumer<RegionStats> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        SerbiaRegion region = SerbiaRegion.fromKey(regionKey);
        if (region == null) {
            onError.accept("Nepoznat region.");
            return;
        }

        regionRemote.fetchRegionDocument(regionKey, document -> {
            regionRemote.fetchAllProfilesForCycle(rows -> {
                int active = countActivePlayers(rows, regionKey);
                int registered = Math.max(
                        intFromDocument(document, "totalRegistered"),
                        countRegisteredPlayers(rows, regionKey)
                );
                int podiumFirst = RegionCycleTestConfig.mergePodiumFirst(
                        regionKey,
                        intFromDocument(document, "podiumFirst")
                );
                int podiumSecond = RegionCycleTestConfig.mergePodiumSecond(
                        regionKey,
                        intFromDocument(document, "podiumSecond")
                );
                int podiumThird = RegionCycleTestConfig.mergePodiumThird(
                        regionKey,
                        intFromDocument(document, "podiumThird")
                );
                RegionStats stats = new RegionStats(
                        regionKey,
                        region.getDisplayName(appContext),
                        region.getIconRes(),
                        podiumFirst,
                        podiumSecond,
                        podiumThird,
                        active,
                        registered,
                        RegionCycleTestConfig.previousCycleRank(regionKey)
                );
                onSuccess.accept(stats);
            }, onError);
        }, onError);
    }

    public void registerPlayerForRegion(
            @NonNull String uid,
            @NonNull SerbiaRegion region,
            @NonNull UserProfile profile,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        float[] point = region.randomMapPoint(new Random(uid.hashCode()));
        String cycleKey = MonthlyCycleHelper.currentCycleKey();
        UserProfile updated = profile.toBuilder()
                .region(region.getDisplayName(appContext))
                .regionKey(region.getKey())
                .mapPointX(point[0])
                .mapPointY(point[1])
                .monthlyStars(0L)
                .starsCycleKey(cycleKey)
                .regionRankFrame("")
                .lastActiveAt(System.currentTimeMillis())
                .build();

        userRemote.saveUserProfile(uid, updated, () ->
                regionRemote.incrementRegionRegistration(region.getKey(), onSuccess, onError),
                onError
        );
    }

    public void awardMonthlyStars(int stars) {
        if (!userRemote.isRegisteredUser() || stars <= 0) {
            return;
        }
        UserProfile profile = ensureRegionState(preferences.loadProfile());
        UserProfile updated = profile.toBuilder()
                .monthlyStars(profile.getMonthlyStars() + stars)
                .totalStars(profile.getTotalStars() + stars)
                .build();
        preferences.saveProfile(updated);
        persistRemote(updated);
    }

    @NonNull
    public UserProfile createRegistrationProfile(
            @NonNull String username,
            @NonNull String email,
            @NonNull SerbiaRegion region,
            @NonNull String invitePayload
    ) {
        float[] point = region.randomMapPoint(new Random(username.hashCode()));
        return new UserProfile.Builder()
                .username(username)
                .email(email)
                .avatarUri("")
                .tokens(0L)
                .totalStars(0L)
                .leagueName("Liga bronza")
                .leagueTierKey("bronze")
                .region(region.getDisplayName(appContext))
                .regionKey(region.getKey())
                .mapPointX(point[0])
                .mapPointY(point[1])
                .monthlyStars(0L)
                .starsCycleKey(MonthlyCycleHelper.currentCycleKey())
                .regionRankFrame("")
                .lastActiveAt(System.currentTimeMillis())
                .invitePayload(invitePayload)
                .statistics(UserProfileMapper.emptyStatistics())
                .build();
    }

    private void buildLeaderboard(
            @NonNull List<Map<String, Object>> rows,
            @Nullable String currentUid,
            @NonNull String currentRegionKey,
            @NonNull Consumer<List<RegionLeaderboardEntry>> onPlayers,
            @NonNull Consumer<List<RegionRankingSummary>> onRegions
    ) {
        Map<String, Long> regionTotals = new HashMap<>();
        List<RegionLeaderboardEntry> players = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            String regionKey = stringValue(row.get("regionKey"));
            if (regionKey.isEmpty()) {
                continue;
            }
            SerbiaRegion region = SerbiaRegion.fromKey(regionKey);
            if (region == null) {
                continue;
            }
            long monthlyStars = longValue(row.get("monthlyStars"));
            String cycleKey = stringValue(row.get("starsCycleKey"));
            if (!MonthlyCycleHelper.currentCycleKey().equals(cycleKey)) {
                monthlyStars = 0L;
            }
            regionTotals.put(regionKey, regionTotals.getOrDefault(regionKey, 0L) + monthlyStars);

            String uid = stringValue(row.get("uid"));
            players.add(new RegionLeaderboardEntry(
                    uid,
                    stringValue(row.get("username")),
                    regionKey,
                    region.getDisplayName(appContext),
                    monthlyStars,
                    frameForLeaderboard(
                            regionKey,
                            stringValue(row.get("region")),
                            stringValue(row.get("regionRankFrame"))
                    ),
                    uid.equals(currentUid)
            ));
        }

        players.sort(Comparator
                .comparingLong(RegionLeaderboardEntry::getMonthlyStars).reversed()
                .thenComparing(entry -> entry.getUsername().toLowerCase(Locale.ROOT)));

        List<RegionRankingSummary> regions = new ArrayList<>();
        for (SerbiaRegion region : SerbiaRegion.all()) {
            regions.add(new RegionRankingSummary(
                    region.getKey(),
                    region.getDisplayName(appContext),
                    region.getIconRes(),
                    regionTotals.getOrDefault(region.getKey(), 0L),
                    0,
                    region.getKey().equals(currentRegionKey)
            ));
        }
        regions.sort(Comparator
                .comparingLong(RegionRankingSummary::getTotalMonthlyStars).reversed()
                .thenComparing(summary -> summary.getRegionName().toLowerCase(Locale.ROOT)));
        for (int i = 0; i < regions.size(); i++) {
            RegionRankingSummary old = regions.get(i);
            regions.set(i, new RegionRankingSummary(
                    old.getRegionKey(),
                    old.getRegionName(),
                    old.getIconRes(),
                    old.getTotalMonthlyStars(),
                    i + 1,
                    old.isCurrentUserRegion()
            ));
        }

        onRegions.accept(regions);
        onPlayers.accept(players);
    }

    private void maybeProcessCycle(
            @NonNull List<Map<String, Object>> rows,
            @NonNull Runnable onComplete
    ) {
        String currentCycle = MonthlyCycleHelper.currentCycleKey();
        regionRemote.fetchCycleConfig(document -> {
            if (document.exists()) {
                @SuppressWarnings("unchecked")
                List<String> previousTop = (List<String>) document.get("previousTopRegions");
                if (previousTop != null) {
                    cachedTopRegions = previousTop;
                }
            }
            String storedCycle = document.exists()
                    ? stringValue(document.getString("currentCycleKey"))
                    : "";
            if (currentCycle.equals(storedCycle)) {
                onComplete.run();
                return;
            }

            Map<String, Long> totals = new HashMap<>();
            for (Map<String, Object> row : rows) {
                String regionKey = stringValue(row.get("regionKey"));
                if (regionKey.isEmpty()) {
                    continue;
                }
                long monthlyStars = longValue(row.get("monthlyStars"));
                totals.put(regionKey, totals.getOrDefault(regionKey, 0L) + monthlyStars);
            }

            List<String> topRegions = new ArrayList<>();
            SerbiaRegion.all().stream()
                    .sorted((left, right) -> Long.compare(
                            totals.getOrDefault(right.getKey(), 0L),
                            totals.getOrDefault(left.getKey(), 0L)
                    ))
                    .limit(3)
                    .forEach(region -> topRegions.add(region.getKey()));

            String previousCycle = document.exists()
                    ? stringValue(document.getString("previousCycleKey"))
                    : MonthlyCycleHelper.previousCycleKey();

            cachedTopRegions = topRegions;
            regionRemote.processCycleRollover(
                    currentCycle,
                    previousCycle,
                    topRegions,
                    onComplete,
                    error -> onComplete.run()
            );
        }, error -> onComplete.run());
    }

    public void preloadCycleConfig() {
        if (RegionCycleTestConfig.USE_HARDCODED_PREVIOUS_TOP_REGIONS) {
            cachedTopRegions = RegionCycleTestConfig.HARDCODED_PREVIOUS_TOP_REGIONS;
            return;
        }
        regionRemote.fetchCycleConfig(document -> {
            if (!document.exists()) {
                return;
            }
            @SuppressWarnings("unchecked")
            List<String> previousTop = (List<String>) document.get("previousTopRegions");
            if (previousTop != null) {
                cachedTopRegions = previousTop;
            }
        }, error -> { });
    }

    @NonNull
    private List<String> previousTopRegions() {
        return RegionCycleTestConfig.previousTopRegions(cachedTopRegions);
    }

    @NonNull
    private String frameForLeaderboard(
            @NonNull String regionKey,
            @NonNull String legacyRegion,
            @NonNull String storedFrame
    ) {
        if (!storedFrame.isEmpty()) {
            return storedFrame;
        }
        return AvatarFrameHelper.resolveRankFrame(regionKey, legacyRegion);
    }

    private void touchActivity(@NonNull UserProfile profile) {
        String uid = userRemote.getCurrentUid();
        if (uid == null || profile.getRegionKey().isEmpty()) {
            return;
        }
        regionRemote.touchPlayerActivity(
                uid,
                profile.getRegionKey(),
                profile.getLastActiveAt(),
                () -> { },
                error -> { }
        );
    }

    private void persistRemote(@NonNull UserProfile profile) {
        String uid = userRemote.getCurrentUid();
        if (uid == null) {
            return;
        }
        userRemote.saveUserProfile(uid, profile, () -> { }, error -> { });
    }

    private static int countActivePlayers(@NonNull List<Map<String, Object>> rows, @NonNull String regionKey) {
        long threshold = System.currentTimeMillis() - ACTIVE_WINDOW_MS;
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (!regionKey.equals(stringValue(row.get("regionKey")))) {
                continue;
            }
            if (longValue(row.get("lastActiveAt")) >= threshold) {
                count++;
            }
        }
        return count;
    }

    private static int countRegisteredPlayers(@NonNull List<Map<String, Object>> rows, @NonNull String regionKey) {
        int count = 0;
        for (Map<String, Object> row : rows) {
            if (regionKey.equals(stringValue(row.get("regionKey")))) {
                count++;
            }
        }
        return count;
    }

    private static int intFromDocument(@NonNull DocumentSnapshot document, @NonNull String field) {
        Object value = document.get(field);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }

    @NonNull
    private static String stringValue(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static long longValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static float floatValue(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0f;
    }
}
