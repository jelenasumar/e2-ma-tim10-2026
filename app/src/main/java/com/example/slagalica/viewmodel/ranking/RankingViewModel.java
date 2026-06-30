package com.example.slagalica.viewmodel.ranking;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.LeagueRepository;
import com.example.slagalica.data.repository.RankingRepository;
import com.example.slagalica.model.RankingEntry;

import java.util.List;

public class RankingViewModel extends AndroidViewModel {

    private static final long REFRESH_INTERVAL_MS = 120_000L;

    private final RankingRepository repository;
    private final LeagueRepository leagueRepository;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final MutableLiveData<List<RankingEntry>> weeklyEntries = new MutableLiveData<>();
    private final MutableLiveData<List<RankingEntry>> monthlyEntries = new MutableLiveData<>();
    private final MutableLiveData<String> weeklyCycleLabel = new MutableLiveData<>("");
    private final MutableLiveData<String> monthlyCycleLabel = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    private final MutableLiveData<String> infoMessage = new MutableLiveData<>("");

    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            load();
            handler.postDelayed(this, REFRESH_INTERVAL_MS);
        }
    };

    public RankingViewModel(@NonNull Application application) {
        super(application);
        repository = new RankingRepository(application);
        leagueRepository = new LeagueRepository(application);
    }

    @NonNull
    public LiveData<List<RankingEntry>> getWeeklyEntries() {
        return weeklyEntries;
    }

    @NonNull
    public LiveData<List<RankingEntry>> getMonthlyEntries() {
        return monthlyEntries;
    }

    @NonNull
    public LiveData<String> getWeeklyCycleLabel() {
        return weeklyCycleLabel;
    }

    @NonNull
    public LiveData<String> getMonthlyCycleLabel() {
        return monthlyCycleLabel;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<String> getInfoMessage() {
        return infoMessage;
    }

    public void start() {
        processRewards();
        load();
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MS);
    }

    public void load() {
        repository.loadRanking(
                RankingRepository.CycleType.WEEKLY,
                result -> {
                    weeklyEntries.setValue(result.getEntries());
                    weeklyCycleLabel.setValue(result.getCycleLabel());
                },
                errorMessage::setValue
        );
        repository.loadRanking(
                RankingRepository.CycleType.MONTHLY,
                result -> {
                    monthlyEntries.setValue(result.getEntries());
                    monthlyCycleLabel.setValue(result.getCycleLabel());
                },
                errorMessage::setValue
        );
    }

    public void processRewards() {
        repository.processFinishedCycleRewards(
                RankingRepository.CycleType.WEEKLY,
                () -> { },
                infoMessage::setValue,
                errorMessage::setValue
        );
        repository.processFinishedCycleRewards(
                RankingRepository.CycleType.MONTHLY,
                () -> leagueRepository.processMonthlyPenaltyForCurrentUser(
                        infoMessage::setValue,
                        errorMessage::setValue
                ),
                infoMessage::setValue,
                errorMessage::setValue
        );
    }

    public void manualRefresh() {
        load();
        infoMessage.setValue("Rang lista je osvezena.");
    }

    @Override
    protected void onCleared() {
        handler.removeCallbacks(refreshRunnable);
        super.onCleared();
    }
}
