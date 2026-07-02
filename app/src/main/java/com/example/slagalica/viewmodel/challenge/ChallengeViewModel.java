package com.example.slagalica.viewmodel.challenge;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.ChallengeRepository;
import com.example.slagalica.data.repository.RegionChatRepository;
import com.example.slagalica.model.Challenge;
import com.example.slagalica.model.ChallengeParticipant;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.List;

public class ChallengeViewModel extends AndroidViewModel {

    private final ChallengeRepository challengeRepository;
    private final RegionChatRepository regionRepository;

    private final MutableLiveData<List<Challenge>> challenges = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<ChallengeParticipant>> participants = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> regionKey = new MutableLiveData<>("");
    private final MutableLiveData<String> selectedChallengeId = new MutableLiveData<>("");
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>("");
    private final MutableLiveData<String> successMessage = new MutableLiveData<>("");
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

    private ListenerRegistration challengesListener;
    private ListenerRegistration participantsListener;

    public ChallengeViewModel(@NonNull Application application) {
        super(application);
        challengeRepository = new ChallengeRepository();
        regionRepository = new RegionChatRepository();
    }

    @NonNull
    public LiveData<List<Challenge>> getChallenges() {
        return challenges;
    }

    @NonNull
    public LiveData<List<ChallengeParticipant>> getParticipants() {
        return participants;
    }

    @NonNull
    public LiveData<String> getRegionKey() {
        return regionKey;
    }

    @NonNull
    public LiveData<String> getSelectedChallengeId() {
        return selectedChallengeId;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<String> getSuccessMessage() {
        return successMessage;
    }

    @NonNull
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    @NonNull
    public String getCurrentUid() {
        String uid = challengeRepository.getCurrentUid();
        return uid != null ? uid : "";
    }

    public void start() {
        loading.setValue(true);
        regionRepository.loadMyRegion(
                loadedRegionKey -> {
                    regionKey.setValue(loadedRegionKey);
                    listenChallenges(loadedRegionKey);
                    loading.setValue(false);
                },
                error -> {
                    errorMessage.setValue(error);
                    loading.setValue(false);
                }
        );
    }

    public void createChallenge(int stakeStars, int stakeTokens) {
        loading.setValue(true);
        challengeRepository.createChallenge(
                stakeStars,
                stakeTokens,
                challengeId -> {
                    successMessage.setValue("Izazov je postavljen.");
                    selectChallenge(challengeId);
                    loading.setValue(false);
                },
                error -> {
                    errorMessage.setValue(error);
                    loading.setValue(false);
                }
        );
    }

    public void joinChallenge(@NonNull String challengeId) {
        if (challengeId.isEmpty()) {
            errorMessage.setValue("Izazov nije izabran.");
            return;
        }

        loading.setValue(true);
        challengeRepository.joinChallenge(
                challengeId,
                () -> {
                    successMessage.setValue("Prihvatio/la si izazov.");
                    selectChallenge(challengeId);
                    loading.setValue(false);
                },
                error -> {
                    errorMessage.setValue(error);
                    loading.setValue(false);
                }
        );
    }

    public void submitResult(@NonNull String challengeId, int score) {
        submitResult(
                challengeId,
                score,
                () -> successMessage.setValue("Rezultat je sacuvan."),
                errorMessage::setValue
        );
    }

    public void loadMyParticipation(
            @NonNull String challengeId,
            @NonNull java.util.function.Consumer<ChallengeParticipant> onSuccess,
            @NonNull Runnable onMissing
    ) {
        challengeRepository.loadMyParticipation(
                challengeId,
                onSuccess,
                onMissing,
                errorMessage::setValue
        );
    }

    public void submitResult(
            @NonNull String challengeId,
            int score,
            @NonNull Runnable onSuccess,
            @NonNull java.util.function.Consumer<String> onError
    ) {
        if (challengeId.isEmpty()) {
            onError.accept("Izazov nije izabran.");
            return;
        }

        loading.setValue(true);
        challengeRepository.submitResult(
                challengeId,
                score,
                () -> {
                    loading.setValue(false);
                    onSuccess.run();
                },
                error -> {
                    loading.setValue(false);
                    onError.accept(error);
                }
        );
    }

    public void selectChallenge(@NonNull String challengeId) {
        selectedChallengeId.setValue(challengeId);

        if (participantsListener != null) {
            participantsListener.remove();
            participantsListener = null;
        }

        if (challengeId.isEmpty()) {
            participants.setValue(new ArrayList<>());
            return;
        }

        participantsListener = challengeRepository.listenParticipants(
                challengeId,
                participants::setValue,
                errorMessage::setValue
        );
    }

    public boolean isCurrentUserParticipant(@NonNull List<ChallengeParticipant> participantList) {
        String uid = getCurrentUid();
        if (uid.isEmpty()) {
            return false;
        }

        for (ChallengeParticipant participant : participantList) {
            if (uid.equals(participant.getUid())) {
                return true;
            }
        }

        return false;
    }

    public boolean isCurrentUserFinished(@NonNull List<ChallengeParticipant> participantList) {
        String uid = getCurrentUid();
        if (uid.isEmpty()) {
            return false;
        }

        for (ChallengeParticipant participant : participantList) {
            if (uid.equals(participant.getUid())) {
                return participant.isFinished();
            }
        }

        return false;
    }

    private void listenChallenges(@NonNull String loadedRegionKey) {
        if (challengesListener != null) {
            challengesListener.remove();
            challengesListener = null;
        }

        challengesListener = challengeRepository.listenRegionChallenges(
                loadedRegionKey,
                challenges::setValue,
                errorMessage::setValue
        );
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (challengesListener != null) {
            challengesListener.remove();
            challengesListener = null;
        }

        if (participantsListener != null) {
            participantsListener.remove();
            participantsListener = null;
        }
    }
}