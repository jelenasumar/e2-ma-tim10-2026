package com.example.slagalica.utils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;
import com.google.android.material.card.MaterialCardView;

import java.util.Locale;

public final class AvatarFrameHelper {

    public static final String FRAME_GOLD = "gold";
    public static final String FRAME_SILVER = "silver";
    public static final String FRAME_BRONZE = "bronze";

    private AvatarFrameHelper() {
    }

    @ColorInt
    public static int frameColor(@Nullable String frameKey) {
        if (frameKey == null) {
            return 0xFF000000;
        }
        switch (frameKey.toLowerCase(Locale.ROOT)) {
            case FRAME_GOLD:
                return 0xFFFFD700;
            case FRAME_SILVER:
                return 0xFFC0C0C0;
            case FRAME_BRONZE:
                return 0xFFCD7F32;
            default:
                return 0xFF000000;
        }
    }

    public static float frameStrokeDp(@Nullable String frameKey) {
        if (frameKey == null || frameKey.isEmpty()) {
            return 3f;
        }
        return 5f;
    }

    public static void applyFrame(@NonNull MaterialCardView cardView, @Nullable String frameKey) {
        if (frameKey == null || frameKey.isEmpty()) {
            cardView.setStrokeColor(cardView.getContext().getColor(R.color.black));
            cardView.setStrokeWidth((int) (3f * cardView.getResources().getDisplayMetrics().density));
            return;
        }
        cardView.setStrokeColor(frameColor(frameKey));
        cardView.setStrokeWidth((int) (frameStrokeDp(frameKey) * cardView.getResources().getDisplayMetrics().density));
    }

    @Nullable
    public static String frameForRegionRank(int rank) {
        if (rank == 1) {
            return FRAME_GOLD;
        }
        if (rank == 2) {
            return FRAME_SILVER;
        }
        if (rank == 3) {
            return FRAME_BRONZE;
        }
        return "";
    }
}
