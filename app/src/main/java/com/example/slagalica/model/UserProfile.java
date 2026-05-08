package com.example.slagalica.model;

public final class UserProfile {

    private final String username;
    private final String email;
    private final String avatarUri;
    private final long tokens;
    private final long totalStars;
    private final String leagueName;
    private final String leagueTierKey;
    private final String region;
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
        this.invitePayload = invitePayload;
        this.statistics = statistics;
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

    public String getInvitePayload() {
        return invitePayload;
    }

    public PlayerStatistics getStatistics() {
        return statistics;
    }
}
