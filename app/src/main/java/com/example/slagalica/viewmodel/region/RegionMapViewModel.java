package com.example.slagalica.viewmodel.region;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.RegionRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.RegionPlayerMarker;
import com.example.slagalica.model.RegionStats;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.model.UserProfile;

import java.util.List;

public class RegionMapViewModel extends AndroidViewModel {

    private final RegionRepository regionRepository;
    private final UserProfileRepository profileRepository;
    private final MutableLiveData<List<RegionPlayerMarker>> markers = new MutableLiveData<>();
    private final MutableLiveData<UserProfile> profile = new MutableLiveData<>();
    private final MutableLiveData<RegionStats> selectedRegionStats = new MutableLiveData<>();
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    public RegionMapViewModel(@NonNull Application application) {
        super(application);
        regionRepository = new RegionRepository(application);
        profileRepository = new UserProfileRepository(application);
        regionRepository.preloadCycleConfig();
    }

    @NonNull
    public LiveData<List<RegionPlayerMarker>> getMarkers() {
        return markers;
    }

    @NonNull
    public LiveData<UserProfile> getProfile() {
        return profile;
    }

    @NonNull
    public LiveData<RegionStats> getSelectedRegionStats() {
        return selectedRegionStats;
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
        UserProfile current = regionRepository.ensureRegionState(profileRepository.loadProfile());
        profile.setValue(current);
        regionRepository.loadMapMarkers(
                loadedMarkers -> {
                    markers.setValue(loadedMarkers);
                    loading.setValue(false);
                },
                error -> {
                    errorMessage.setValue(error);
                    loading.setValue(false);
                }
        );
    }

    public void onRegionSelected(@NonNull SerbiaRegion region) {
        regionRepository.loadRegionStats(
                region.getKey(),
                selectedRegionStats::setValue,
                errorMessage::setValue
        );
    }
}
