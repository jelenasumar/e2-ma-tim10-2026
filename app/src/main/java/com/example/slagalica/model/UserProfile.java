package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class UserProfile {

    private final String username;
    private final String email;
    private final String avatarUri;
    private final long tokens;
    private final long totalStars;
    private final String leagueName;
    private final String leagueTierKey;
    private final String region;
    private final String regionKey;
    private final float mapPointX;
    private final float mapPointY;
    private final long monthlyStars;
    private final String starsCycleKey;
    private final String regionRankFrame;
    private final long lastActiveAt;
    private final String invitePayload;
    private final PlayerStatistics statistics;

    public UserProfile(
            String username,
            String email,
            String avatarUri,
            long tokens,
            long totalStars,
            String leagueName,
            String leagueTierKey,
            String region,
            String regionKey,
            float mapPointX,
            float mapPointY,
            long monthlyStars,
            String starsCycleKey,
            String regionRankFrame,
            long lastActiveAt,
            String invitePayload,
            PlayerStatistics statistics
    ) {
        this.username = username;
        this.email = email;
        this.avatarUri = avatarUri;
        this.tokens = tokens;
        this.totalStars = totalStars;
        this.leagueName = leagueName;
        this.leagueTierKey = leagueTierKey;
        this.region = region;
        this.regionKey = regionKey;
        this.mapPointX = mapPointX;
        this.mapPointY = mapPointY;
        this.monthlyStars = monthlyStars;
        this.starsCycleKey = starsCycleKey;
        this.regionRankFrame = regionRankFrame;
        this.lastActiveAt = lastActiveAt;
        this.invitePayload = invitePayload;
        this.statistics = statistics;
    }

    @NonNull
    public Builder toBuilder() {
        return new Builder(this);
    }

    public String getUsername() {
        return username;
    }

    public String getEmail() {
        return email;
    }

    public String getAvatarUri() {
        return avatarUri;
    }

    public long getTokens() {
        return tokens;
    }

    public long getTotalStars() {
        return totalStars;
    }

    public String getLeagueName() {
        return leagueName;
    }

    public String getLeagueTierKey() {
        return leagueTierKey;
    }

    public String getRegion() {
        return region;
    }

    public String getRegionKey() {
        return regionKey;
    }

    public float getMapPointX() {
        return mapPointX;
    }

    public float getMapPointY() {
        return mapPointY;
    }

    public long getMonthlyStars() {
        return monthlyStars;
    }

    public String getStarsCycleKey() {
        return starsCycleKey;
    }

    public String getRegionRankFrame() {
        return regionRankFrame;
    }

    public long getLastActiveAt() {
        return lastActiveAt;
    }

    public String getInvitePayload() {
        return invitePayload;
    }

    public PlayerStatistics getStatistics() {
        return statistics;
    }

    public static final class Builder {
        private String username;
        private String email;
        private String avatarUri;
        private long tokens;
        private long totalStars;
        private String leagueName;
        private String leagueTierKey;
        private String region;
        private String regionKey;
        private float mapPointX;
        private float mapPointY;
        private long monthlyStars;
        private String starsCycleKey;
        private String regionRankFrame;
        private long lastActiveAt;
        private String invitePayload;
        private PlayerStatistics statistics;

        public Builder() {
        }

        public Builder(@NonNull UserProfile profile) {
            this.username = profile.username;
            this.email = profile.email;
            this.avatarUri = profile.avatarUri;
            this.tokens = profile.tokens;
            this.totalStars = profile.totalStars;
            this.leagueName = profile.leagueName;
            this.leagueTierKey = profile.leagueTierKey;
            this.region = profile.region;
            this.regionKey = profile.regionKey;
            this.mapPointX = profile.mapPointX;
            this.mapPointY = profile.mapPointY;
            this.monthlyStars = profile.monthlyStars;
            this.starsCycleKey = profile.starsCycleKey;
            this.regionRankFrame = profile.regionRankFrame;
            this.lastActiveAt = profile.lastActiveAt;
            this.invitePayload = profile.invitePayload;
            this.statistics = profile.statistics;
        }

        public Builder username(String value) {
            username = value;
            return this;
        }

        public Builder email(String value) {
            email = value;
            return this;
        }

        public Builder avatarUri(String value) {
            avatarUri = value;
            return this;
        }

        public Builder tokens(long value) {
            tokens = value;
            return this;
        }

        public Builder totalStars(long value) {
            totalStars = value;
            return this;
        }

        public Builder leagueName(String value) {
            leagueName = value;
            return this;
        }

        public Builder leagueTierKey(String value) {
            leagueTierKey = value;
            return this;
        }

        public Builder region(String value) {
            region = value;
            return this;
        }

        public Builder regionKey(String value) {
            regionKey = value;
            return this;
        }

        public Builder mapPointX(float value) {
            mapPointX = value;
            return this;
        }

        public Builder mapPointY(float value) {
            mapPointY = value;
            return this;
        }

        public Builder monthlyStars(long value) {
            monthlyStars = value;
            return this;
        }

        public Builder starsCycleKey(String value) {
            starsCycleKey = value;
            return this;
        }

        public Builder regionRankFrame(String value) {
            regionRankFrame = value;
            return this;
        }

        public Builder lastActiveAt(long value) {
            lastActiveAt = value;
            return this;
        }

        public Builder invitePayload(String value) {
            invitePayload = value;
            return this;
        }

        public Builder statistics(PlayerStatistics value) {
            statistics = value;
            return this;
        }

        @NonNull
        public UserProfile build() {
            return new UserProfile(
                    username,
                    email,
                    avatarUri,
                    tokens,
                    totalStars,
                    leagueName,
                    leagueTierKey,
                    region,
                    regionKey,
                    mapPointX,
                    mapPointY,
                    monthlyStars,
                    starsCycleKey,
                    regionRankFrame,
                    lastActiveAt,
                    invitePayload,
                    statistics
            );
        }
    }
}
