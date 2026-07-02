package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;

import com.example.slagalica.model.DailyMissionProgress;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.MonthlyCycleHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class DailyMissionRewardHelper {

    private static final int STARS_PER_MISSION = 3;
    private static final int ALL_COMPLETED_BONUS_STARS = 3;
    private static final int ALL_COMPLETED_BONUS_TOKENS = 2;

    private DailyMissionRewardHelper() {
    }

    @NonNull
    public static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    @NonNull
    static UserProfile applyCompletedMissions(
            @NonNull UserProfile profile,
            @NonNull List<String> missionKeys
    ) {
        if (missionKeys.isEmpty()) {
            return profile;
        }

        DailyMissionProgress progress = profile.getDailyMissionProgress().forDate(todayKey());
        int newMissionCount = 0;
        for (String missionKey : missionKeys) {
            if (!progress.isCompleted(missionKey)) {
                progress = progress.markCompleted(missionKey);
                newMissionCount++;
            }
        }

        int starsDelta = newMissionCount * STARS_PER_MISSION;
        int tokensDelta = 0;
        if (progress.isAllCompleted() && !progress.isCompletionBonusClaimed()) {
            progress = progress.withCompletionBonusClaimed();
            starsDelta += ALL_COMPLETED_BONUS_STARS;
            tokensDelta += ALL_COMPLETED_BONUS_TOKENS;
        }

        if (starsDelta == 0 && tokensDelta == 0) {
            return profile.toBuilder().dailyMissionProgress(progress).build();
        }

        String currentCycle = MonthlyCycleHelper.currentCycleKey();
        long monthlyStars = currentCycle.equals(profile.getStarsCycleKey()) ? profile.getMonthlyStars() : 0L;
        long previousTotalStars = profile.getTotalStars();
        long newTotalStars = previousTotalStars + starsDelta;
        tokensDelta += earnedTokensFromStarMilestones(previousTotalStars, newTotalStars);

        return profile.toBuilder()
                .dailyMissionProgress(progress)
                .totalStars(newTotalStars)
                .monthlyStars(monthlyStars + starsDelta)
                .starsCycleKey(currentCycle)
                .tokens(profile.getTokens() + tokensDelta)
                .build();
    }

    private static long earnedTokensFromStarMilestones(long previousTotalStars, long newTotalStars) {
        if (newTotalStars <= previousTotalStars) {
            return 0L;
        }
        long previousMilestones = previousTotalStars / 50L;
        long newMilestones = newTotalStars / 50L;
        return Math.max(0L, newMilestones - previousMilestones);
    }
}
