package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class GameHeaderPlayerState {

    private final String username;
    private final int score;
    private final String avatarUri;

    public GameHeaderPlayerState(
            @NonNull String username,
            int score,
            @Nullable String avatarUri
    ) {
        this.username = username;
        this.score = score;
        this.avatarUri = avatarUri;
    }

    @NonNull
    public String getUsername() {
        return username;
    }

    public int getScore() {
        return score;
    }

    @Nullable
    public String getAvatarUri() {
        return avatarUri;
    }

    @NonNull
    public GameHeaderPlayerState withScore(int newScore) {
        return new GameHeaderPlayerState(username, newScore, avatarUri);
    }
}
