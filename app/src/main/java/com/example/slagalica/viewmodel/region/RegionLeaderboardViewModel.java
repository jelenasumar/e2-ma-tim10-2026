package com.example.slagalica.viewmodel.region;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.RegionRepository;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionRankingSummary;
import com.example.slagalica.utils.MonthlyCycleHelper;

import java.util.List;

public class RegionLeaderboardViewModel extends AndroidViewModel {

    private final RegionRepository regionRepository;
    private final MutableLiveData<List<RegionRankingSummary>> regionRankings = new MutableLiveData<>();
    private final MutableLiveData<List<RegionLeaderboardEntry>> playerRankings = new MutableLiveData<>();
    private final MutableLiveData<String> cycleLabel = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public RegionLeaderboardViewModel(@NonNull Application application) {
        super(application);
        regionRepository = new RegionRepository(application);
        cycleLabel.setValue(MonthlyCycleHelper.formatCycleLabel(MonthlyCycleHelper.currentCycleKey()));
    }

    @NonNull
    public LiveData<List<RegionRankingSummary>> getRegionRankings() {
        return regionRankings;
    }

    @NonNull
    public LiveData<List<RegionLeaderboardEntry>> getPlayerRankings() {
        return playerRankings;
    }

    @NonNull
    public LiveData<String> getCycleLabel() {
        return cycleLabel;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void load() {
        loading.setValue(true);
        regionRepository.loadLeaderboard(
                players -> playerRankings.setValue(players),
                regions -> {
                    regionRankings.setValue(regions);
                    loading.setValue(false);
                },
                error -> {
                    errorMessage.setValue(error);
                    loading.setValue(false);
                }
        );
    }
}
