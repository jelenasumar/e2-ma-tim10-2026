package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;

public final class SerbiaMapProjection {

    private static final double NORTH = 46.181;
    private static final double SOUTH = 42.231;
    private static final double WEST = 18.817;
    private static final double EAST = 22.986;

    private SerbiaMapProjection() {
    }

    @NonNull
    public static LatLng normalizedToLatLng(float x, float y) {
        double lng = WEST + x * (EAST - WEST);
        double lat = NORTH - y * (NORTH - SOUTH);
        return new LatLng(lat, lng);
    }

    @NonNull
    public static float[] latLngToNormalized(@NonNull LatLng latLng) {
        float x = (float) ((latLng.longitude - WEST) / (EAST - WEST));
        float y = (float) ((NORTH - latLng.latitude) / (NORTH - SOUTH));
        return new float[]{x, y};
    }

    @NonNull
    public static LatLngBounds getSerbiaBounds() {
        return new LatLngBounds(
                new LatLng(SOUTH, WEST),
                new LatLng(NORTH, EAST)
        );
    }
}
