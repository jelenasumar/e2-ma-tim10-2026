package com.example.slagalica.model;

import android.content.Context;
import android.graphics.RectF;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.example.slagalica.R;
import com.example.slagalica.utils.SerbiaMapProjection;
import com.google.android.gms.maps.model.LatLng;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public enum SerbiaRegion {

    BEOGRAD(
            "beograd",
            R.string.region_beograd,
            R.drawable.ic_region_beograd,
            0xFF4CAF50,
            new float[]{0.28f, 0.28f, 0.52f, 0.42f}
    ),
    VOJVODINA(
            "vojvodina",
            R.string.region_vojvodina,
            R.drawable.ic_region_vojvodina,
            0xFF2196F3,
            new float[]{0.08f, 0.02f, 0.95f, 0.30f}
    ),
    SUMADIJA(
            "sumadija",
            R.string.region_sumadija,
            R.drawable.ic_region_sumadija,
            0xFF8BC34A,
            new float[]{0.24f, 0.40f, 0.56f, 0.58f}
    ),
    ZAPAD(
            "zapad",
            R.string.region_zapad,
            R.drawable.ic_region_zapad,
            0xFF795548,
            new float[]{0.02f, 0.26f, 0.30f, 0.62f}
    ),
    JUG(
            "jug",
            R.string.region_jug,
            R.drawable.ic_region_jug,
            0xFFFF9800,
            new float[]{0.16f, 0.56f, 0.72f, 0.96f}
    ),
    ISTOK(
            "istok",
            R.string.region_istok,
            R.drawable.ic_region_istok,
            0xFF9C27B0,
            new float[]{0.52f, 0.26f, 0.98f, 0.68f}
    );

    private static final SerbiaRegion[] HIT_TEST_ORDER = {
            BEOGRAD,
            VOJVODINA,
            ZAPAD,
            SUMADIJA,
            ISTOK,
            JUG
    };

    private final String key;
    @StringRes
    private final int nameRes;
    private final int iconRes;
    private final int fillColor;
    private final RectF bounds;

    SerbiaRegion(
            @NonNull String key,
            @StringRes int nameRes,
            int iconRes,
            int fillColor,
            @NonNull float[] boundsArray
    ) {
        this.key = key;
        this.nameRes = nameRes;
        this.iconRes = iconRes;
        this.fillColor = fillColor;
        this.bounds = new RectF(boundsArray[0], boundsArray[1], boundsArray[2], boundsArray[3]);
    }

    @NonNull
    public String getKey() {
        return key;
    }

    @StringRes
    public int getNameRes() {
        return nameRes;
    }

    @NonNull
    public String getDisplayName(@NonNull Context context) {
        return context.getString(nameRes);
    }

    public int getIconRes() {
        return iconRes;
    }

    public int getFillColor() {
        return fillColor;
    }

    @NonNull
    public RectF getBounds() {
        return bounds;
    }

    @NonNull
    public float[] randomMapPoint(@NonNull Random random) {
        float x = bounds.left + random.nextFloat() * (bounds.right - bounds.left);
        float y = bounds.top + random.nextFloat() * (bounds.bottom - bounds.top);
        return new float[]{x, y};
    }

    public boolean containsNormalizedPoint(float x, float y) {
        return bounds.contains(x, y);
    }

    @Nullable
    public static SerbiaRegion findAtNormalizedPoint(float x, float y) {
        for (SerbiaRegion region : HIT_TEST_ORDER) {
            if (region.containsNormalizedPoint(x, y)) {
                return region;
            }
        }
        return null;
    }

    @Nullable
    public static SerbiaRegion findAtLatLng(@NonNull LatLng latLng) {
        float[] normalized = SerbiaMapProjection.latLngToNormalized(latLng);
        return findAtNormalizedPoint(normalized[0], normalized[1]);
    }

    @NonNull
    public static List<SerbiaRegion> all() {
        return Arrays.asList(values());
    }

    @Nullable
    public static SerbiaRegion fromKey(@Nullable String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        String normalized = key.trim().toLowerCase(Locale.ROOT);
        for (SerbiaRegion region : values()) {
            if (region.key.equals(normalized)) {
                return region;
            }
        }
        for (SerbiaRegion region : values()) {
            if (region.getDisplayNameKey().equals(normalized)) {
                return region;
            }
        }
        return null;
    }

    @NonNull
    private String getDisplayNameKey() {
        switch (this) {
            case BEOGRAD:
                return "beograd";
            case VOJVODINA:
                return "vojvodina";
            case SUMADIJA:
                return "šumadija";
            case ZAPAD:
                return "zapadna srbija";
            case JUG:
                return "južna srbija";
            case ISTOK:
                return "istočna srbija";
            default:
                return key;
        }
    }

    @Nullable
    public static SerbiaRegion resolve(@Nullable String regionKey, @Nullable String legacyRegionName) {
        SerbiaRegion byKey = fromKey(regionKey);
        if (byKey != null) {
            return byKey;
        }
        if (legacyRegionName == null || legacyRegionName.isEmpty()) {
            return null;
        }
        String lower = legacyRegionName.trim().toLowerCase(Locale.ROOT);
        for (SerbiaRegion region : values()) {
            if (lower.contains(region.key) || lower.equals(region.getDisplayNameKey())) {
                return region;
            }
        }
        return null;
    }
}
