package com.example.slagalica.ui.ranking;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;

import com.example.slagalica.R;

public final class RewardIconHelper {

    private RewardIconHelper() {
    }

    @DrawableRes
    public static int iconForRank(int rank) {
        if (rank == 1) {
            return R.drawable.ic_trophy_reward;
        }
        if (rank == 2) {
            return R.drawable.ic_trophy_silver_reward;
        }
        if (rank == 3) {
            return R.drawable.ic_trophy_bronze_reward;
        }
        return R.drawable.ic_league_badge;
    }

    public static int rankFromMessage(@NonNull String message) {
        int dotIndex = message.indexOf('.');
        if (dotIndex <= 0) {
            return 0;
        }
        try {
            return Integer.parseInt(message.substring(0, dotIndex).replaceAll("[^0-9]", ""));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
