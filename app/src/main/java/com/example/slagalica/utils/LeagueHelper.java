package com.example.slagalica.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;
import com.example.slagalica.model.LeagueChangeEvent;
import com.example.slagalica.model.LeagueTier;
import com.example.slagalica.model.UserProfile;

public final class LeagueHelper {

    private LeagueHelper() {
    }

    @NonNull
    public static LeagueTier resolveTier(@NonNull UserProfile profile) {
        LeagueTier stored = LeagueTier.fromKey(profile.getLeagueTierKey());
        LeagueTier fromStars = LeagueTier.fromStars(profile.getTotalStars());
        if (stored != fromStars) {
            return fromStars;
        }
        return stored;
    }

    @NonNull
    public static UserProfile withLeagueForStars(@NonNull Context context, @NonNull UserProfile profile) {
        LeagueTier tier = LeagueTier.fromStars(profile.getTotalStars());
        return profile.toBuilder()
                .leagueTierKey(tier.getKey())
                .leagueName(leagueName(context, tier))
                .build();
    }

    @NonNull
    public static String leagueName(@NonNull Context context, @NonNull LeagueTier tier) {
        return context.getString(tier.getNameRes());
    }

    public static long starsAfterMonthlyPenalty(long totalStars) {
        if (totalStars <= 0L) {
            return 0L;
        }
        return (long) Math.floor(totalStars * LeagueTier.MONTHLY_PENALTY_RETAIN_RATIO);
    }

    @Nullable
    public static LeagueChangeEvent detectChange(
            @NonNull Context context,
            @NonNull UserProfile before,
            @NonNull UserProfile after
    ) {
        LeagueTier previousTier = LeagueTier.fromKey(before.getLeagueTierKey());
        LeagueTier newTier = LeagueTier.fromKey(after.getLeagueTierKey());
        if (previousTier == newTier) {
            return null;
        }
        LeagueChangeEvent.Direction direction = newTier.getLevel() > previousTier.getLevel()
                ? LeagueChangeEvent.Direction.PROMOTED
                : LeagueChangeEvent.Direction.DEMOTED;
        String message = direction == LeagueChangeEvent.Direction.PROMOTED
                ? context.getString(
                R.string.league_promoted_message,
                leagueName(context, newTier),
                after.getTotalStars()
        )
                : context.getString(
                R.string.league_demoted_message,
                leagueName(context, newTier),
                after.getTotalStars()
        );
        return new LeagueChangeEvent(previousTier, newTier, direction, after.getTotalStars(), message);
    }
}
