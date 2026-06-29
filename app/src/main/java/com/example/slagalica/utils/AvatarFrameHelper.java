package com.example.slagalica.utils;

import android.graphics.Color;
import android.widget.ImageView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
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
            return Color.BLACK;
        }
        switch (frameKey.toLowerCase(Locale.ROOT)) {
            case FRAME_GOLD:
                return 0xFFFFD700;
            case FRAME_SILVER:
                return 0xFFA8A8A8;
            case FRAME_BRONZE:
                return 0xFFCD7F32;
            default:
                return Color.BLACK;
        }
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
        int index = topRegions.indexOf(regionKey.toLowerCase(Locale.ROOT));
        String frame = frameForRegionRank(index + 1);
        return frame != null ? frame : "";
    }

    public static void applyFrame(
            @Nullable ImageView ringView,
            @NonNull MaterialCardView cardView,
            @Nullable String frameKey
    ) {
        String frame = frameKey != null ? frameKey : "";
        int drawableRes = ringDrawable(frame);

        if (ringView != null) {
            if (drawableRes == 0) {
                ringView.setVisibility(ImageView.GONE);
                ringView.setImageDrawable(null);
            } else {
                ringView.setVisibility(ImageView.VISIBLE);
                ringView.setImageResource(drawableRes);
                ringView.bringToFront();
            }
        }

        float density = cardView.getResources().getDisplayMetrics().density;
        if (drawableRes == 0) {
            cardView.setStrokeColor(cardView.getContext().getColor(R.color.black));
            cardView.setStrokeWidth((int) (3f * density));
        } else {
            cardView.setStrokeColor(cardView.getContext().getColor(R.color.white));
            cardView.setStrokeWidth((int) (2f * density));
        }
        cardView.invalidate();
    }

    @DrawableRes
    private static int ringDrawable(@NonNull String frame) {
        if (frame.isEmpty()) {
            return 0;
        }
        switch (frame.toLowerCase(Locale.ROOT)) {
            case FRAME_GOLD:
                return R.drawable.avatar_frame_ring_gold;
            case FRAME_SILVER:
                return R.drawable.avatar_frame_ring_silver;
            case FRAME_BRONZE:
                return R.drawable.avatar_frame_ring_bronze;
            default:
                return 0;
        }
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
