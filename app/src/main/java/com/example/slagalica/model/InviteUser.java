package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class InviteUser {

    private final String uid;
    private final String username;
    private final String email;
    private final String avatarUri;

    public InviteUser(
            @NonNull String uid,
            @NonNull String username,
            @NonNull String email,
            @NonNull String avatarUri
    ) {
        this.uid = uid;
        this.username = username;
        this.email = email;
        this.avatarUri = avatarUri;
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
    public String getEmail() {
        return email;
    }

    @NonNull
    public String getAvatarUri() {
        return avatarUri;
    }
}
