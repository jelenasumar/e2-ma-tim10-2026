package com.example.slagalica.model;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;

import com.example.slagalica.R;

public enum LeagueTier {
    STARTER(0, "starter", R.string.league_name_starter, R.drawable.ic_league_starter, 0xFF9E9E9E),
    BRONZE(1, "bronze", R.string.league_name_bronze, R.drawable.ic_league_bronze, 0xFFCD7F32),
    SILVER(2, "silver", R.string.league_name_silver, R.drawable.ic_league_silver, 0xFFC0C0C0),
    GOLD(3, "gold", R.string.league_name_gold, R.drawable.ic_league_gold, 0xFFFFD700),
    PLATINUM(4, "platinum", R.string.league_name_platinum, R.drawable.ic_league_platinum, 0xFFE5E4E2),
    DIAMOND(5, "diamond", R.string.league_name_diamond, R.drawable.ic_league_diamond, 0xFF00BCD4);

    public static final int BASE_DAILY_TOKENS = 5;
    public static final int FIRST_LEAGUE_STARS = 100;
    public static final double MONTHLY_PENALTY_RETAIN_RATIO = 0.7;

    private final int level;
    private final String key;
    @StringRes
    private final int nameRes;
    @DrawableRes
    private final int iconRes;
    private final int color;

    LeagueTier(int level, String key, @StringRes int nameRes, @DrawableRes int iconRes, int color) {
        this.level = level;
        this.key = key;
        this.nameRes = nameRes;
        this.iconRes = iconRes;
        this.color = color;
    }

    public int getLevel() {
        return level;
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @StringRes
    public int getNameRes() {
        return nameRes;
    }

    @DrawableRes
    public int getIconRes() {
        return iconRes;
    }

    public int getColor() {
        return color;
    }

    public int getDailyTokenBonus() {
        return level;
    }

    public int getDailyTokenTotal() {
        return BASE_DAILY_TOKENS + level;
    }

    public long getRequiredStars() {
        if (level <= 0) {
            return 0L;
        }
        long threshold = FIRST_LEAGUE_STARS;
        for (int i = 1; i < level; i++) {
            threshold *= 2L;
        }
        return threshold;
    }

    @NonNull
    public static LeagueTier fromStars(long stars) {
        if (stars < FIRST_LEAGUE_STARS) {
            return STARTER;
        }
        LeagueTier tier = BRONZE;
        long threshold = FIRST_LEAGUE_STARS;
        while (tier.level < DIAMOND.level && stars >= threshold * 2L) {
            threshold *= 2L;
            tier = tier.next();
        }
        return tier;
    }

    @NonNull
    public static LeagueTier fromKey(@NonNull String key) {
        String normalized = key.trim().toLowerCase();
        for (LeagueTier tier : values()) {
            if (tier.key.equals(normalized)) {
                return tier;
            }
        }
        return STARTER;
    }

    @NonNull
    public LeagueTier next() {
        if (level >= DIAMOND.level) {
            return DIAMOND;
        }
        return values()[level + 1];
    }

    @NonNull
    public LeagueTier previous() {
        if (level <= STARTER.level) {
            return STARTER;
        }
        return values()[level - 1];
    }
}
