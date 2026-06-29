package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class RegionPlayerMarker {

    private final String uid;
    private final String username;
    private final String regionKey;
    private final float mapPointX;
    private final float mapPointY;
    private final boolean currentUser;

    public RegionPlayerMarker(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String regionKey,
            float mapPointX,
            float mapPointY,
            boolean currentUser
    ) {
        this.uid = uid;
        this.username = username;
        this.regionKey = regionKey;
        this.mapPointX = mapPointX;
        this.mapPointY = mapPointY;
        this.currentUser = currentUser;
    }

    @NonNull
    public String getUid() {
        return uid;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    @NonNull
    public String getRegionKey() {
        return regionKey;
    }

    public float getMapPointX() {
        return mapPointX;
    }

    public float getMapPointY() {
        return mapPointY;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }
}
