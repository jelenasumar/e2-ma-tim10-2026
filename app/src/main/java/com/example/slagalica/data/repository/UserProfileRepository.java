package com.example.slagalica.data.repository;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import com.example.slagalica.data.local.UserPreferences;
import com.example.slagalica.data.remote.FireBaseUserDataSource;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.R;
import com.example.slagalica.utils.AvatarFileStorage;

import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

public final class UserProfileRepository {

    private final Context appContext;
    private final UserPreferences preferences;
    private final FireBaseUserDataSource remote;

    public UserProfileRepository(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        this.preferences = new UserPreferences(appContext);
        this.remote = new FireBaseUserDataSource();

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
            preferences.saveProfile(profile);
            onSuccess.accept(profile);
        }, onError);
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
            String localPath = localFile.getAbsolutePath();

            remote.uploadAvatar(uid, imageBytes, downloadUrl -> {
                persistAvatar(downloadUrl, onSuccess);
            }, error -> {
                if ("STORAGE_NOT_AVAILABLE".equals(error) || "STORAGE_PERMISSION_DENIED".equals(error)) {
                    remote.saveAvatarUriToFirestore(uid, localPath, savedUri -> {
                        persistAvatar(savedUri, onSuccess);
                    }, onError);
                    return;
                }
                onError.accept(error);
            });
        } catch (Exception e) {
            onError.accept(e.getMessage() != null ? e.getMessage() : "Avatar save failed.");
        }
    }

    private void persistAvatar(
            @NonNull String avatarUri,
            @NonNull Runnable onSuccess
    ) {
        preferences.saveAvatarUri(avatarUri);
        UserProfile cached = preferences.loadProfile();
        UserProfile updated = new UserProfile(
                cached.getUsername(),
                cached.getEmail(),
                avatarUri,
                cached.getTokens(),
                cached.getTotalStars(),
                cached.getLeagueName(),
                cached.getLeagueTierKey(),
                cached.getRegion(),
                cached.getInvitePayload(),
                cached.getStatistics()
        );
        preferences.saveProfile(updated);
        onSuccess.run();
    }

    public boolean hasRegisteredAccount() {
        return remote.isRegisteredUser();
    }

    public void register(
            @NonNull String email,
            @NonNull String username,
            @NonNull String region,
            @NonNull String password,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String em = normalizeEmail(email);
        String un = username.trim();
        String reg = region.trim();

        remote.createUserWithEmailAndPassword(em, password, firebaseUser -> {
            String uid = firebaseUser.getUid();
            UserProfile profile = createDefaultProfile(un, em, reg);

            remote.saveUserProfile(uid, profile, () -> {
                preferences.saveProfile(profile);
                onSuccess.run();
            }, onError);
        }, onError);
    }

    public void login(
            @NonNull String email,
            @NonNull String password,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String em = normalizeEmail(email);

        remote.signInWithEmailAndPassword(em, password, firebaseUser -> {
            String uid = firebaseUser.getUid();
            remote.fetchUserProfile(uid, profile -> {
                UserProfile merged = mergeWithAuthEmail(profile, em);
                preferences.saveProfile(merged);
                onSuccess.run();
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
                stats.getMatchesLossPercent()
        );

        UserProfile updatedProfile = new UserProfile(
                profile.getUsername(),
                profile.getEmail(),
                profile.getAvatarUri(),
                profile.getTokens(),
                profile.getTotalStars(),
                profile.getLeagueName(),
                profile.getLeagueTierKey(),
                profile.getRegion(),
                profile.getInvitePayload(),
                updatedStats
        );

        preferences.saveProfile(updatedProfile);

        if (remote.isLoggedIn()) {
            String uid = remote.getCurrentUid();
            if (uid != null) {
                remote.saveUserProfile(uid, updatedProfile, () -> { }, error -> { });
            }
        }
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
                stats.getMatchesLossPercent()
        );

        UserProfile updatedProfile = new UserProfile(
                profile.getUsername(),
                profile.getEmail(),
                profile.getAvatarUri(),
                profile.getTokens(),
                profile.getTotalStars(),
                profile.getLeagueName(),
                profile.getLeagueTierKey(),
                profile.getRegion(),
                profile.getInvitePayload(),
                updatedStats
        );

        preferences.setSpojniceGamesPlayed(gamesPlayed + 1);
        preferences.saveProfile(updatedProfile);

        if (remote.isLoggedIn()) {
            String uid = remote.getCurrentUid();
            if (uid != null) {
                remote.saveUserProfile(uid, updatedProfile, () -> { }, error -> { });
            }
        }
    }

    @NonNull
    private UserProfile createDefaultProfile(
            @NonNull String username,
            @NonNull String email,
            @NonNull String region
    ) {
        String inviteCode = UUID.randomUUID().toString();
        String invitePayload = String.format(
                Locale.US,
                "slagalica://invite?user=%s&code=%s",
                username,
                inviteCode
        );

        return new UserProfile(
                username,
                email,
                "",
                0L,
                0L,
                "Liga bronza",
                "bronze",
                region,
                invitePayload,
                UserProfileMapper.emptyStatistics()
        );
    }

    @NonNull
    private static UserProfile mergeWithAuthEmail(@NonNull UserProfile profile, @NonNull String email) {
        if (email.equals(profile.getEmail())) {
            return profile;
        }
        return new UserProfile(
                profile.getUsername(),
                email,
                profile.getAvatarUri(),
                profile.getTokens(),
                profile.getTotalStars(),
                profile.getLeagueName(),
                profile.getLeagueTierKey(),
                profile.getRegion(),
                profile.getInvitePayload(),
                profile.getStatistics()
        );
    }

    @NonNull
    private static String normalizeEmail(@NonNull String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
