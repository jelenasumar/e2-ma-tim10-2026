package com.example.slagalica.data.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.local.UserPreferences;
import com.example.slagalica.data.remote.RegionDataSource;
import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.R;
import com.example.slagalica.utils.AvatarFileStorage;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.utils.MonthlyCycleHelper;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class UserProfileRepository {

    private static final String MATCH_RESULTS = "match_results";
    private static final String MATCH_TYPE_RANDOM = "RANDOM";

    private final Context appContext;
    private final UserPreferences preferences;
    private final FireBaseUserDataSource remote;
    private final LeagueRepository leagueRepository;
    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public UserProfileRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = new UserPreferences(appContext);
        this.remote = new FireBaseUserDataSource();
        this.leagueRepository = new LeagueRepository(appContext);

        if (!remote.isLoggedIn()) {
            preferences.clearSessionFields();
        }
    }

    public boolean isRegisteredPlayer() {
        return remote.isRegisteredUser();
    }

    @NonNull
    public String getDisplayNameForGames() {
        if (remote.isAnonymousUser()) {
            String uid = remote.getCurrentUid();
            if (uid != null && uid.length() >= 4) {
                return appContext.getString(
                        R.string.guest_player_name,
                        uid.substring(uid.length() - 4)
                );
            }
            return appContext.getString(R.string.guest_player_default);
        }
        UserProfile profile = loadProfile();
        String username = profile.getUsername();
        return username != null && !username.isEmpty() ? username : appContext.getString(R.string.player_default);
    }

    public void ensureAuthenticated(
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (remote.isLoggedIn()) {
            onSuccess.run();
            return;
        }
        remote.signInAnonymously(onSuccess, onError);
    }

    @NonNull
    public UserProfile loadProfile() {
        return preferences.loadProfile();
    }

    public void fetchProfile(
            @NonNull Consumer<UserProfile> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (!remote.isLoggedIn()) {
            onSuccess.accept(preferences.loadProfile());
            return;
        }

        String uid = remote.getCurrentUid();
        if (uid == null) {
            onSuccess.accept(preferences.loadProfile());
            return;
        }

        remote.fetchUserProfile(uid, profile -> {
            UserProfile synced = syncProfileState(profile);
            preferences.saveProfile(synced);
            leagueRepository.processMonthlyPenaltyForCurrentUser(
                    synced,
                    message -> { },
                    error -> { }
            );
            onSuccess.accept(synced);
        }, onError);
    }

    @Nullable
    public ListenerRegistration listenCurrentProfile(
            @NonNull Consumer<UserProfile> onChanged,
            @NonNull Consumer<String> onError
    ) {
        String uid = remote.getCurrentUid();
        if (!remote.isLoggedIn() || uid == null) {
            onChanged.accept(preferences.loadProfile());
            return null;
        }
        return remote.listenUserProfile(uid, profile -> {
            UserProfile synced = syncProfileState(profile);
            preferences.saveProfile(synced);
            onChanged.accept(synced);
        }, onError);
    }

    public void ensurePublicAvatarUri(
            @NonNull Consumer<String> onReady,
            @NonNull Consumer<String> onError
    ) {
        UserProfile profile = loadProfile();
        String avatarUri = profile.getAvatarUri();
        if (avatarUri != null
                && AvatarImageLoader.isSharedAvatarUri(avatarUri)) {
            onReady.accept(avatarUri);
            return;
        }
        if (!remote.isLoggedIn()) {
            onReady.accept("");
            return;
        }
        String uid = remote.getCurrentUid();
        if (uid == null) {
            onReady.accept("");
            return;
        }
        File localFile = resolveAvatarFile(avatarUri, uid);
        if (localFile == null || !localFile.exists()) {
            onReady.accept("");
            return;
        }
        try {
            byte[] imageBytes = AvatarFileStorage.readBytes(localFile);
            remote.uploadAvatar(uid, imageBytes, downloadUrl -> {
                persistAvatar(downloadUrl, () -> onReady.accept(downloadUrl));
            }, onError);
        } catch (IOException e) {
            onError.accept(e.getMessage() != null ? e.getMessage() : "Avatar upload failed.");
        }
    }

    @Nullable
    private File resolveAvatarFile(@Nullable String avatarUri, @NonNull String uid) {
        if (avatarUri != null && !avatarUri.isEmpty()) {
            if (avatarUri.startsWith("file:")) {
                String path = Uri.parse(avatarUri).getPath();
                if (path != null) {
                    File filePath = new File(path);
                    if (filePath.exists()) {
                        return filePath;
                    }
                }
            }
            File directPath = new File(avatarUri);
            if (directPath.exists()) {
                return directPath;
            }
        }
        File defaultFile = AvatarFileStorage.avatarFile(appContext, uid);
        return defaultFile.exists() ? defaultFile : null;
    }

    public void fetchAvatarUriForUser(
            @NonNull String uid,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String currentUid = remote.getCurrentUid();
        if (currentUid != null && currentUid.equals(uid)) {
            String localUri = loadProfile().getAvatarUri();
            onSuccess.accept(localUri != null ? localUri : "");
            return;
        }
        remote.fetchUserProfile(
                uid,
                profile -> {
                    String avatarUri = profile.getAvatarUri();
                    onSuccess.accept(avatarUri != null ? avatarUri : "");
                },
                onError
        );
    }

    @Nullable
    public ListenerRegistration listenAvatarUriForUser(
            @NonNull String uid,
            @NonNull Consumer<String> onChanged,
            @NonNull Consumer<String> onError
    ) {
        if (uid.isEmpty()) {
            onChanged.accept("");
            return null;
        }
        String currentUid = remote.getCurrentUid();
        if (currentUid == null || !remote.isLoggedIn()) {
            fetchAvatarUriForUser(uid, onChanged, onError);
            return null;
        }
        return remote.listenUserProfile(uid, profile -> {
            if (currentUid.equals(uid)) {
                preferences.saveProfile(profile);
            }
            String avatarUri = profile.getAvatarUri();
            onChanged.accept(avatarUri != null ? avatarUri : "");
        }, onError);
    }

    public void saveAvatarUri(
            @NonNull Uri pickedImageUri,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (!remote.isLoggedIn()) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String uid = remote.getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        try {
            java.io.File localFile = AvatarFileStorage.copyToInternalStorage(appContext, uid, pickedImageUri);
            byte[] imageBytes = AvatarFileStorage.readBytes(localFile);

            remote.uploadAvatar(uid, imageBytes, downloadUrl -> {
                persistAvatar(downloadUrl, onSuccess);
            }, onError);
        } catch (Exception e) {
            onError.accept(e.getMessage() != null ? e.getMessage() : "Avatar save failed.");
        }
    }

    public void saveAvatarPreset(
            @NonNull String presetAvatarUri,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (!remote.isLoggedIn()) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String uid = remote.getCurrentUid();
        if (uid == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }
        if (!presetAvatarUri.startsWith("preset:")) {
            onError.accept("INVALID_AVATAR");
            return;
        }

        remote.saveAvatarUriToFirestore(
                uid,
                presetAvatarUri,
                savedUri -> persistAvatar(savedUri, onSuccess),
                onError
        );
    }

    private void persistAvatar(
            @NonNull String avatarUri,
            @NonNull Runnable onSuccess
    ) {
        preferences.saveAvatarUri(avatarUri);
        UserProfile cached = preferences.loadProfile();
        UserProfile updated = cached.toBuilder().avatarUri(avatarUri).build();
        preferences.saveProfile(updated);
        onSuccess.run();
    }

    public boolean hasRegisteredAccount() {
        return remote.isRegisteredUser();
    }

    public void register(
            @NonNull String email,
            @NonNull String username,
            @NonNull String regionKey,
            @NonNull String password,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String em = normalizeEmail(email);
        String un = username.trim();
        SerbiaRegion region = SerbiaRegion.fromKey(regionKey);
        if (region == null) {
            onError.accept("Izaberi region.");
            return;
        }

        RegionRepository regionRepository = new RegionRepository(appContext);
        RegionDataSource regionRemote = new RegionDataSource();

        remote.createUserWithEmailAndPassword(em, password, firebaseUser -> {
            String uid = firebaseUser.getUid();
            String inviteCode = UUID.randomUUID().toString();
            String invitePayload = String.format(
                    Locale.US,
                    "slagalica://invite?user=%s&code=%s",
                    un,
                    inviteCode
            );
            UserProfile profile = regionRepository.createRegistrationProfile(un, em, region, invitePayload);

            remote.saveUserProfile(uid, profile, () ->
                    regionRemote.incrementRegionRegistration(region.getKey(), () ->
                            remote.saveUsernameLookup(uid, un, em, () -> {
                                remote.sendEmailVerification(firebaseUser, () -> {
                                    remote.signOut();
                                    preferences.clearSessionFields();
                                    onSuccess.run();
                                }, onError);
                            }, onError),
                            onError
                    ),
                    onError
            );
        }, onError);
    }

    public void login(
            @NonNull String identifier,
            @NonNull String password,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String id = identifier.trim();

        if (id.contains("@")) {
            loginWithEmail(normalizeEmail(id), password, onSuccess, onError);
        } else {
            remote.findEmailByUsername(id, email ->
                            loginWithEmail(email, password, onSuccess, onError),
                    onError
            );
        }
    }

    private void loginWithEmail(
            @NonNull String email,
            @NonNull String password,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String em = normalizeEmail(email);

        remote.signInWithEmailAndPassword(em, password, firebaseUser -> {
            remote.requireVerifiedEmail(firebaseUser, () -> {
                String uid = firebaseUser.getUid();

                remote.fetchUserProfile(uid, profile -> {
                    UserProfile merged = mergeWithAuthEmail(profile, em);
                    UserProfile synced = new RegionRepository(appContext).ensureRegionState(merged);
                    preferences.saveProfile(synced);
                    onSuccess.run();
                }, onError);
            }, onError);
        }, onError);
    }

    public void clearSession() {
        remote.signOut();
        preferences.clearSessionFields();
    }

    public void changePassword(
            @NonNull String currentPassword,
            @NonNull String newPassword,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (!remote.isLoggedIn()) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }
        remote.changePassword(currentPassword, newPassword, onSuccess, onError);
    }

    public void recordKoZnaZnaRound(int roundScore, int hits, int misses) {
        if (!isRegisteredPlayer()) {
            return;
        }
        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int newHits = stats.getKoZnaZnaHits() + hits;
        int newMisses = stats.getKoZnaZnaMisses() + misses;
        float newAvg;
        if (stats.getKoZnaZnaHits() == 0 && stats.getKoZnaZnaMisses() == 0 && stats.getAvgScoreKoZnaZna() == 0f) {
            newAvg = roundScore;
        } else {
            int previousRounds = Math.max(1, (stats.getKoZnaZnaHits() + stats.getKoZnaZnaMisses() + 4) / 5);
            newAvg = ((stats.getAvgScoreKoZnaZna() * previousRounds) + roundScore) / (previousRounds + 1f);
        }

        PlayerStatistics updatedStats = new PlayerStatistics(
                newAvg,
                stats.getAvgScoreSpojnice(),
                stats.getAvgScoreMojBroj(),
                stats.getAvgScoreKorakPoKorak(),
                stats.getAvgScoreAsocijacije(),
                stats.getAvgScoreSkocko(),
                newHits,
                newMisses,
                stats.getMojBrojCorrectPercent(),
                stats.getKorakPoKorakStepPercents(),
                stats.getAsocijacijeSolved(),
                stats.getAsocijacijeUnsolved(),
                stats.getSkockoComboPercent(),
                stats.getSpojniceLinkedPercent(),
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profile.toBuilder().statistics(updatedStats).build();
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    public void recordSpojniceGame(int gameScore, int correctPairs, int totalPairsInGame) {
        if (!isRegisteredPlayer()) {
            return;
        }
        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int gamesPlayed = preferences.getSpojniceGamesPlayed();
        float gamePercent = totalPairsInGame > 0
                ? (correctPairs * 100f) / totalPairsInGame
                : 0f;
        float newAvg = gamesPlayed == 0
                ? gameScore
                : ((stats.getAvgScoreSpojnice() * gamesPlayed) + gameScore) / (gamesPlayed + 1f);
        float newPercent = gamesPlayed == 0
                ? gamePercent
                : ((stats.getSpojniceLinkedPercent() * gamesPlayed) + gamePercent) / (gamesPlayed + 1f);

        PlayerStatistics updatedStats = new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                newAvg,
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
                newPercent,
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profile.toBuilder().statistics(updatedStats).build();
        preferences.setSpojniceGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    public void recordKorakPoKorakGame(int gameScore, int solvedStepIndex) {
        if (!isRegisteredPlayer()) {
            return;
        }

        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int gamesPlayed = preferences.getKorakPoKorakGamesPlayed();

        float newAvg = gamesPlayed == 0
                ? gameScore
                : ((stats.getAvgScoreKorakPoKorak() * gamesPlayed) + gameScore) / (gamesPlayed + 1f);

        java.util.List<Float> oldStepPercents = stats.getKorakPoKorakStepPercents();
        java.util.List<Float> newStepPercents = new java.util.ArrayList<>();

        for (int i = 0; i < 7; i++) {
            float oldPercent = i < oldStepPercents.size() ? oldStepPercents.get(i) : 0f;
            float currentGameValue = solvedStepIndex == i ? 100f : 0f;

            float newPercent = gamesPlayed == 0
                    ? currentGameValue
                    : ((oldPercent * gamesPlayed) + currentGameValue) / (gamesPlayed + 1f);

            newStepPercents.add(newPercent);
        }

        PlayerStatistics updatedStats = new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                stats.getAvgScoreSpojnice(),
                stats.getAvgScoreMojBroj(),
                newAvg,
                stats.getAvgScoreAsocijacije(),
                stats.getAvgScoreSkocko(),
                stats.getKoZnaZnaHits(),
                stats.getKoZnaZnaMisses(),
                stats.getMojBrojCorrectPercent(),
                newStepPercents,
                stats.getAsocijacijeSolved(),
                stats.getAsocijacijeUnsolved(),
                stats.getSkockoComboPercent(),
                stats.getSpojniceLinkedPercent(),
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profileWithStatistics(profile, updatedStats);
        preferences.setKorakPoKorakGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    public void recordMojBrojGame(int gameScore, boolean exactHit) {
        if (!isRegisteredPlayer()) {
            return;
        }

        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int gamesPlayed = preferences.getMojBrojGamesPlayed();

        float newAvg = gamesPlayed == 0
                ? gameScore
                : ((stats.getAvgScoreMojBroj() * gamesPlayed) + gameScore) / (gamesPlayed + 1f);

        float currentExactPercent = exactHit ? 100f : 0f;
        float newExactPercent = gamesPlayed == 0
                ? currentExactPercent
                : ((stats.getMojBrojCorrectPercent() * gamesPlayed) + currentExactPercent) / (gamesPlayed + 1f);

        PlayerStatistics updatedStats = new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                stats.getAvgScoreSpojnice(),
                newAvg,
                stats.getAvgScoreKorakPoKorak(),
                stats.getAvgScoreAsocijacije(),
                stats.getAvgScoreSkocko(),
                stats.getKoZnaZnaHits(),
                stats.getKoZnaZnaMisses(),
                newExactPercent,
                stats.getKorakPoKorakStepPercents(),
                stats.getAsocijacijeSolved(),
                stats.getAsocijacijeUnsolved(),
                stats.getSkockoComboPercent(),
                stats.getSpojniceLinkedPercent(),
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profileWithStatistics(profile, updatedStats);
        preferences.setMojBrojGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    public void recordAsocijacijeGame(int gameScore, int solvedRounds, int unsolvedRounds) {
        if (!isRegisteredPlayer()) {
            return;
        }
        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int gamesPlayed = preferences.getAsocijacijeGamesPlayed();
        float newAvg = gamesPlayed == 0
                ? gameScore
                : ((stats.getAvgScoreAsocijacije() * gamesPlayed) + gameScore) / (gamesPlayed + 1f);

        PlayerStatistics updatedStats = new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                stats.getAvgScoreSpojnice(),
                stats.getAvgScoreMojBroj(),
                stats.getAvgScoreKorakPoKorak(),
                newAvg,
                stats.getAvgScoreSkocko(),
                stats.getKoZnaZnaHits(),
                stats.getKoZnaZnaMisses(),
                stats.getMojBrojCorrectPercent(),
                stats.getKorakPoKorakStepPercents(),
                stats.getAsocijacijeSolved() + solvedRounds,
                stats.getAsocijacijeUnsolved() + unsolvedRounds,
                stats.getSkockoComboPercent(),
                stats.getSpojniceLinkedPercent(),
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profileWithStatistics(profile, updatedStats);
        preferences.setAsocijacijeGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    public void recordRoomMatchResult(int myTotalScore, int opponentTotalScore) {
        if (!isRegisteredPlayer()) {
            return;
        }
        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int totalMatches = stats.getTotalMatches() + 1;
        int matchesWon = stats.getMatchesWon();
        int matchesLost = stats.getMatchesLost();
        if (myTotalScore > opponentTotalScore) {
            matchesWon++;
        } else if (myTotalScore < opponentTotalScore) {
            matchesLost++;
        }
        float winPercent = totalMatches > 0 ? (matchesWon * 100f) / totalMatches : 0f;
        float lossPercent = totalMatches > 0 ? (matchesLost * 100f) / totalMatches : 0f;

        PlayerStatistics updatedStats = new PlayerStatistics(
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

        UserProfile updatedProfile = profileWithStatistics(profile, updatedStats);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);

        if (myTotalScore > opponentTotalScore) {
            new RegionRepository(appContext).awardMonthlyStars(3);
        } else if (myTotalScore == opponentTotalScore) {
            new RegionRepository(appContext).awardMonthlyStars(1);
        }
    }

    public void processFinishedRoomResult(
            @NonNull RoomSession room,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        if (!isRegisteredPlayer()) {
            onSuccess.run();
            return;
        }
        String uid = remote.getCurrentUid();
        if (uid == null || (!uid.equals(room.getHostUid()) && !uid.equals(room.getGuestUid()))) {
            onSuccess.run();
            return;
        }

        String winnerUid = winnerUid(room);
        String loserUid = loserUid(room);
        boolean randomMatch = MATCH_TYPE_RANDOM.equals(room.getMatchType());
        int hostStarsDelta = randomMatch
                ? roomPlayerStarsDelta(
                room,
                room.getHostUid(),
                room.getHostTotalScore(),
                room.getHostUid().equals(winnerUid)
        )
                : 0;
        int guestStarsDelta = randomMatch
                ? roomPlayerStarsDelta(
                room,
                room.getGuestUid(),
                room.getGuestTotalScore(),
                room.getGuestUid().equals(winnerUid)
        )
                : 0;

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
        result.put("matchType", room.getMatchType());
        result.put("finishReason", room.getFinishReason());
        result.put("abandonedByUid", room.getAbandonedByUid());
        result.put("hostStarsDelta", hostStarsDelta);
        result.put("guestStarsDelta", guestStarsDelta);
        result.put("processedBy_" + uid, true);
        result.put("finishedAt", FieldValue.serverTimestamp());
        result.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(MATCH_RESULTS)
                .document(room.getRoomId())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists() && Boolean.TRUE.equals(snapshot.getBoolean("processedBy_" + uid))) {
                        onSuccess.run();
                        return;
                    }
                    db.collection(MATCH_RESULTS)
                            .document(room.getRoomId())
                            .set(result, SetOptions.merge())
                            .addOnSuccessListener(unused -> {
                                if (!randomMatch) {
                                    onSuccess.run();
                                    return;
                                }

                                int myScore = uid.equals(room.getHostUid()) ? room.getHostTotalScore() : room.getGuestTotalScore();
                                int opponentScore = uid.equals(room.getHostUid()) ? room.getGuestTotalScore() : room.getHostTotalScore();
                                int myStarsDelta = uid.equals(room.getHostUid()) ? hostStarsDelta : guestStarsDelta;
                                UserProfile updated = profileAfterFinishedMatch(
                                        preferences.loadProfile(),
                                        myScore,
                                        opponentScore,
                                        myStarsDelta,
                                        true
                                );
                                preferences.saveProfile(updated);
                                saveRemoteProfile(updated);
                                onSuccess.run();
                            })
                            .addOnFailureListener(e -> onError.accept(
                                    e.getMessage() != null ? e.getMessage() : "Match result could not be saved."
                            ));
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Match result could not be loaded."
                ));
    }

    @NonNull
    private UserProfile profileAfterFinishedMatch(
            @NonNull UserProfile profile,
            int myTotalScore,
            int opponentTotalScore,
            int starsDelta,
            boolean affectsStars
    ) {
        PlayerStatistics stats = profile.getStatistics();

        int totalMatches = stats.getTotalMatches() + 1;
        int matchesWon = stats.getMatchesWon();
        int matchesLost = stats.getMatchesLost();
        if (myTotalScore > opponentTotalScore) {
            matchesWon++;
        } else if (myTotalScore < opponentTotalScore) {
            matchesLost++;
        }
        float winPercent = totalMatches > 0 ? (matchesWon * 100f) / totalMatches : 0f;
        float lossPercent = totalMatches > 0 ? (matchesLost * 100f) / totalMatches : 0f;

        PlayerStatistics updatedStats = new PlayerStatistics(
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

        UserProfile.Builder builder = profile.toBuilder().statistics(updatedStats);
        if (affectsStars) {
            String currentCycle = MonthlyCycleHelper.currentCycleKey();
            String cycleKey = currentCycle;
            long monthlyStars = currentCycle.equals(profile.getStarsCycleKey()) ? profile.getMonthlyStars() : 0L;
            long previousTotalStars = profile.getTotalStars();
            long newTotalStars = Math.max(0L, previousTotalStars + starsDelta);
            long earnedTokens = earnedTokensFromStarMilestones(previousTotalStars, newTotalStars);

            builder.totalStars(newTotalStars)
                    .monthlyStars(Math.max(0L, monthlyStars + starsDelta))
                    .starsCycleKey(cycleKey)
                    .tokens(profile.getTokens() + earnedTokens);
        }
        UserProfile withStats = builder.build();
        if (!affectsStars) {
            return withStats;
        }
        LeagueRepository.SyncResult syncResult = leagueRepository.syncProfile(
                withStats,
                profile,
                true
        );
        return syncResult.getProfile();
    }

    private static long earnedTokensFromStarMilestones(long previousTotalStars, long newTotalStars) {
        if (newTotalStars <= previousTotalStars) {
            return 0L;
        }
        long previousMilestones = previousTotalStars / 50L;
        long newMilestones = newTotalStars / 50L;
        return Math.max(0L, newMilestones - previousMilestones);
    }

    @NonNull
    private UserProfile syncProfileState(@NonNull UserProfile profile) {
        UserProfile regionSynced = new RegionRepository(appContext).ensureRegionState(profile);
        LeagueRepository.SyncResult syncResult = leagueRepository.syncProfile(
                regionSynced,
                regionSynced,
                false
        );
        UserProfile synced = syncResult.getProfile();
        if (leagueStateChanged(regionSynced, synced)) {
            saveRemoteProfile(synced);
        }
        return synced;
    }

    private static boolean leagueStateChanged(@NonNull UserProfile before, @NonNull UserProfile after) {
        return before.getTokens() != after.getTokens()
                || before.getTotalStars() != after.getTotalStars()
                || !before.getLeagueTierKey().equals(after.getLeagueTierKey())
                || !before.getLeagueName().equals(after.getLeagueName());
    }

    @NonNull
    private static String winnerUid(@NonNull RoomSession room) {
        if (!room.getAbandonedByUid().isEmpty()) {
            if (room.getAbandonedByUid().equals(room.getHostUid())) {
                return room.getGuestUid();
            }
            if (room.getAbandonedByUid().equals(room.getGuestUid())) {
                return room.getHostUid();
            }
        }
        if (room.getHostTotalScore() > room.getGuestTotalScore()) {
            return room.getHostUid();
        }
        if (room.getGuestTotalScore() > room.getHostTotalScore()) {
            return room.getGuestUid();
        }
        return "";
    }

    @NonNull
    private static String loserUid(@NonNull RoomSession room) {
        if (!room.getAbandonedByUid().isEmpty()) {
            return room.getAbandonedByUid();
        }
        if (room.getHostTotalScore() > room.getGuestTotalScore()) {
            return room.getGuestUid();
        }
        if (room.getGuestTotalScore() > room.getHostTotalScore()) {
            return room.getHostUid();
        }
        return "";
    }

    private static int roomPlayerStarsDelta(
            @NonNull RoomSession room,
            @NonNull String playerUid,
            int score,
            boolean won
    ) {
        if (playerUid.equals(room.getAbandonedByUid())) {
            return -10;
        }
        return starsDelta(score, won);
    }

    private static int starsDelta(int score, boolean won) {
        int scoreBonus = Math.max(0, score) / 40;
        return (won ? 10 : -10) + scoreBonus;
    }

    public void recordSkockoGame(int gameScore, float comboPercent) {
        if (!isRegisteredPlayer()) {
            return;
        }
        UserProfile profile = preferences.loadProfile();
        PlayerStatistics stats = profile.getStatistics();

        int gamesPlayed = preferences.getSkockoGamesPlayed();
        float newAvg = gamesPlayed == 0
                ? gameScore
                : ((stats.getAvgScoreSkocko() * gamesPlayed) + gameScore) / (gamesPlayed + 1f);
        float newComboPercent = gamesPlayed == 0
                ? comboPercent
                : ((stats.getSkockoComboPercent() * gamesPlayed) + comboPercent) / (gamesPlayed + 1f);

        PlayerStatistics updatedStats = new PlayerStatistics(
                stats.getAvgScoreKoZnaZna(),
                stats.getAvgScoreSpojnice(),
                stats.getAvgScoreMojBroj(),
                stats.getAvgScoreKorakPoKorak(),
                stats.getAvgScoreAsocijacije(),
                newAvg,
                stats.getKoZnaZnaHits(),
                stats.getKoZnaZnaMisses(),
                stats.getMojBrojCorrectPercent(),
                stats.getKorakPoKorakStepPercents(),
                stats.getAsocijacijeSolved(),
                stats.getAsocijacijeUnsolved(),
                newComboPercent,
                stats.getSpojniceLinkedPercent(),
                stats.getTotalMatches(),
                stats.getMatchesWinPercent(),
                stats.getMatchesLossPercent(),
                stats.getMatchesWon(),
                stats.getMatchesLost()
        );

        UserProfile updatedProfile = profileWithStatistics(profile, updatedStats);
        preferences.setSkockoGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);
        saveRemoteProfile(updatedProfile);
    }

    @NonNull
    private static UserProfile profileWithStatistics(
            @NonNull UserProfile profile,
            @NonNull PlayerStatistics statistics
    ) {
        return profile.toBuilder().statistics(statistics).build();
    }

    private void saveRemoteProfile(@NonNull UserProfile profile) {
        if (remote.isLoggedIn()) {
            String uid = remote.getCurrentUid();
            if (uid != null) {
                remote.saveUserProfile(uid, profile, () -> { }, error -> { });
            }
        }
    }

    @NonNull
    private static UserProfile mergeWithAuthEmail(@NonNull UserProfile profile, @NonNull String email) {
        if (email.equals(profile.getEmail())) {
            return profile;
        }
        return profile.toBuilder().email(email).build();
    }

    @NonNull
    private static String normalizeEmail(@NonNull String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
