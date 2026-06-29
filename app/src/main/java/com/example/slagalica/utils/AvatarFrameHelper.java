package com.example.slagalica.utils;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;
import com.example.slagalica.model.SerbiaRegion;
import com.google.android.material.card.MaterialCardView;

import java.util.Collections;
import java.util.List;
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
        return 8f;
    }

    @Nullable
    public static String resolveRankFrame(@Nullable String regionKey, @Nullable String legacyRegionName) {
        SerbiaRegion region = SerbiaRegion.resolve(regionKey, legacyRegionName);
        if (region == null) {
            return "";
        }
        return frameForRegionKey(region.getKey());
    }

    @NonNull
    public static String frameForRegionKey(@NonNull String regionKey) {
        if (regionKey.isEmpty()) {
            return "";
        }
        List<String> topRegions = RegionCycleTestConfig.previousTopRegions(Collections.emptyList());
        int index = topRegions.indexOf(regionKey);
        String frame = frameForRegionRank(index + 1);
        return frame != null ? frame : "";
    }

    public static void applyFrame(@NonNull MaterialCardView cardView, @Nullable String frameKey) {
        String frame = frameKey != null ? frameKey : "";
        if (frame.isEmpty()) {
            cardView.setStrokeColor(cardView.getContext().getColor(R.color.black));
            cardView.setStrokeWidth((int) (3f * cardView.getResources().getDisplayMetrics().density));
            return;
        }
        cardView.setStrokeColor(frameColor(frame));
        cardView.setStrokeWidth((int) (frameStrokeDp(frame) * cardView.getResources().getDisplayMetrics().density));
        cardView.invalidate();
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
