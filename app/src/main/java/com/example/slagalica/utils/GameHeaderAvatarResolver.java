package com.example.slagalica.utils;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.repository.UserProfileRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class GameHeaderAvatarResolver {

    private final UserProfileRepository profileRepository;
    private final Map<String, String> cache = new HashMap<>();

    public GameHeaderAvatarResolver(@NonNull Context context) {
        profileRepository = new UserProfileRepository(context);
    }

    public void resolvePair(
            @NonNull String firstUid,
            @NonNull String secondUid,
            @NonNull BiConsumer<String, String> onResolved
    ) {
        final String[] result = {"", ""};
        AtomicInteger pending = new AtomicInteger(2);
        Runnable finish = () -> {
            if (pending.decrementAndGet() == 0) {
                onResolved.accept(result[0], result[1]);
            }
        };
        loadUri(firstUid, uri -> {
            result[0] = uri;
            finish.run();
        });
        loadUri(secondUid, uri -> {
            result[1] = uri;
            finish.run();
        });
    }

    private void loadUri(@NonNull String uid, @NonNull java.util.function.Consumer<String> onLoaded) {
        if (uid.isEmpty()) {
            onLoaded.accept("");
            return;
        }
        if (cache.containsKey(uid)) {
            onLoaded.accept(cache.get(uid));
            return;
        }
        profileRepository.fetchAvatarUriForUser(
                uid,
                uri -> {
                    String safeUri = uri != null ? uri : "";
                    cache.put(uid, safeUri);
                    onLoaded.accept(safeUri);
                },
                error -> {
                    cache.put(uid, "");
                    onLoaded.accept("");
                }
        );
    }

    public void invalidate(@Nullable String uid) {
        if (uid != null && !uid.isEmpty()) {
            cache.remove(uid);
        }
    }
}
