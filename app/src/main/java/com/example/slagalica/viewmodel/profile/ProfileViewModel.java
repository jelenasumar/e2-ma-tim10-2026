package com.example.slagalica.viewmodel.profile;

import android.app.Application;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.SingleLiveEvent;

import java.util.List;
import java.util.Locale;

public class ProfileViewModel extends AndroidViewModel {

    private final UserProfileRepository repository;

    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<String> statsText = new MutableLiveData<>();
    private final MutableLiveData<String> matchSummaryText = new MutableLiveData<>();
    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final SingleLiveEvent<Boolean> logoutCompleted = new SingleLiveEvent<>();
    private final SingleLiveEvent<Boolean> qrGenerationFailed = new SingleLiveEvent<>();

    public ProfileViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    @NonNull
    public LiveData<String> getStatsText() {
        return statsText;
    }

    @NonNull
    public LiveData<String> getMatchSummaryText() {
        return matchSummaryText;
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<Boolean> getLogoutCompleted() {
        return logoutCompleted;
    }

    @NonNull
    public LiveData<Boolean> getQrGenerationFailed() {
        return qrGenerationFailed;
    }

    public void loadProfile() {
        isLoading.setValue(true);
        errorMessage.setValue(null);

        repository.fetchProfile(
                loadedProfile -> {
                    isLoading.setValue(false);
                    profile.setValue(loadedProfile);
                    statsText.setValue(buildStatsText(loadedProfile));
                    matchSummaryText.setValue(buildMatchSummaryText(loadedProfile));
                },
                error -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(error);
                    UserProfile cached = repository.loadProfile();
                    profile.setValue(cached);
                    statsText.setValue(buildStatsText(cached));
                    matchSummaryText.setValue(buildMatchSummaryText(cached));
                }
        );
    }

    public void updateAvatar(@NonNull Uri imageUri) {
        isLoading.setValue(true);
        repository.saveAvatarUri(
                imageUri,
                () -> loadProfile(),
                error -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(mapAvatarError(error));
                }
        );
    }

    public void logout() {
        repository.clearSession();
        logoutCompleted.setValue(true);
    }

    public void notifyQrGenerationFailed() {
        qrGenerationFailed.setValue(true);
    }

    @NonNull
    private String mapAvatarError(@NonNull String errorCode) {
        switch (errorCode) {
            case "STORAGE_NOT_AVAILABLE":
                return getApplication().getString(R.string.error_storage_not_available);
            case "STORAGE_PERMISSION_DENIED":
                return getApplication().getString(R.string.error_storage_permission_denied);
            case "NOT_LOGGED_IN":
                return getApplication().getString(R.string.error_not_logged_in);
            default:
                return errorCode;
        }
    }

    public static int leagueColor(@NonNull String tier) {
        switch (tier.toLowerCase(Locale.US)) {
            case "silver":
                return android.graphics.Color.rgb(192, 192, 192);
            case "gold":
                return android.graphics.Color.rgb(255, 215, 0);
            case "bronze":
            default:
                return android.graphics.Color.rgb(205, 127, 50);
        }
    }

    @NonNull
    private String buildStatsText(@NonNull UserProfile profile) {
        PlayerStatistics s = profile.getStatistics();
        StringBuilder sb = new StringBuilder();

        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_ko_zna_zna), s.getAvgScoreKoZnaZna())).append('\n');
        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_spojnice), s.getAvgScoreSpojnice())).append('\n');
        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_moj_broj), s.getAvgScoreMojBroj())).append('\n');
        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_korak_po_korak), s.getAvgScoreKorakPoKorak())).append('\n');
        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_asocijacije), s.getAvgScoreAsocijacije())).append('\n');
        sb.append(getApplication().getString(R.string.profile_stat_avg_points, getApplication().getString(R.string.game_skocko), s.getAvgScoreSkocko())).append("\n\n");

        sb.append(getApplication().getString(R.string.profile_stat_kzz_ratio, s.getKoZnaZnaHits(), s.getKoZnaZnaMisses())).append("\n\n");
        sb.append(getApplication().getString(R.string.profile_stat_moj_broj, s.getMojBrojCorrectPercent())).append("\n\n");

        sb.append(getApplication().getString(R.string.profile_stat_kpk_header)).append('\n');
        List<Float> steps = s.getKorakPoKorakStepPercents();
        for (int i = 0; i < steps.size(); i++) {
            sb.append(
                    getApplication().getString(
                            R.string.profile_stat_kpk_step,
                            i + 1,
                            steps.get(i)
                    )
            ).append('\n');
        }
        sb.append('\n');

        sb.append(getApplication().getString(R.string.profile_stat_asoc, s.getAsocijacijeSolved(), s.getAsocijacijeUnsolved())).append("\n\n");
        sb.append(getApplication().getString(R.string.profile_stat_skocko, s.getSkockoComboPercent())).append("\n\n");
        sb.append(getApplication().getString(R.string.profile_stat_spojnice, s.getSpojniceLinkedPercent()));

        return sb.toString();
    }

    @NonNull
    private String buildMatchSummaryText(@NonNull UserProfile profile) {
        PlayerStatistics s = profile.getStatistics();
        return getApplication().getString(
                R.string.profile_match_summary,
                s.getTotalMatches(),
                s.getMatchesWon(),
                s.getMatchesLost()
        );
    }
}
