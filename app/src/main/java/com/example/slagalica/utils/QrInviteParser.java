package com.example.slagalica.utils;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class QrInviteParser {

    private QrInviteParser() {
    }

    @Nullable
    public static ParsedInvite parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        if (!value.contains("://")) {
            value = "slagalica://invite?" + value;
        }
        Uri uri = Uri.parse(value);
        if (!"slagalica".equalsIgnoreCase(uri.getScheme())) {
            return null;
        }
        if (!"invite".equalsIgnoreCase(uri.getHost())) {
            return null;
        }
        String username = uri.getQueryParameter("user");
        String code = uri.getQueryParameter("code");
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        if (code == null || code.trim().isEmpty()) {
            return null;
        }
        return new ParsedInvite(username.trim(), code.trim());
    }

    public static final class ParsedInvite {
        private final String username;
        private final String code;

        public ParsedInvite(@NonNull String username, @NonNull String code) {
            this.username = username;
            this.code = code;
        }

        @NonNull
        public String getUsername() {
            return username;
        }

        @NonNull
        public String getCode() {
            return code;
        }
    }
}
