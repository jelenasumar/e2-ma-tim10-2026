package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class Friend {

    private final String uid;
    private final String username;
    private final String avatarUri;
    private final long totalStars;
    private final long monthlyStars;
    private final int monthlyRank;
    private final String leagueTierKey;
    private final String leagueName;
    private final boolean inActiveGame;
    private final boolean invitePending;

    public Friend(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String avatarUri,
            long totalStars,
            long monthlyStars,
            int monthlyRank,
            @NonNull String leagueTierKey,
            @NonNull String leagueName,
            boolean inActiveGame,
            boolean invitePending
    ) {
        this.uid = uid;
        this.username = username;
        this.avatarUri = avatarUri;
        this.totalStars = totalStars;
        this.monthlyStars = monthlyStars;
        this.monthlyRank = monthlyRank;
        this.leagueTierKey = leagueTierKey;
        this.leagueName = leagueName;
        this.inActiveGame = inActiveGame;
        this.invitePending = invitePending;
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
    public String getAvatarUri() {
        return avatarUri;
    }

    public long getTotalStars() {
        return totalStars;
    }

    public long getMonthlyStars() {
        return monthlyStars;
    }

    public int getMonthlyRank() {
        return monthlyRank;
    }

    @NonNull
    public String getLeagueTierKey() {
        return leagueTierKey;
    }

    @NonNull
    public String getLeagueName() {
        return leagueName;
    }

    public boolean isInActiveGame() {
        return inActiveGame;
    }

    public boolean isInvitePending() {
        return invitePending;
    }

    public boolean canInvite() {
        return !inActiveGame && !invitePending;
    }

    @NonNull
    public InviteUser toInviteUser() {
        return new InviteUser(uid, username, "", avatarUri);
    }
}
