package com.example.slagalica.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.File;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public final class AvatarImageLoader {

    private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

    private AvatarImageLoader() {
    }

    public static void load(
            @NonNull ImageView imageView,
            @Nullable String avatarUri,
            @DrawableRes int placeholderRes
    ) {
        if (avatarUri == null || avatarUri.isEmpty()) {
            imageView.setImageResource(placeholderRes);
            return;
        }

        if (avatarUri.startsWith("http://") || avatarUri.startsWith("https://")) {
            imageView.setImageResource(placeholderRes);
            EXECUTOR.execute(() -> {
                Bitmap bitmap = downloadBitmap(avatarUri);
                imageView.post(() -> {
                    if (bitmap != null) {
                        imageView.setImageBitmap(bitmap);
                    } else {
                        imageView.setImageResource(placeholderRes);
                    }
                });
            });
            return;
        }

        try {
            Uri uri = avatarUri.startsWith("file:") || avatarUri.startsWith("content:")
                    ? Uri.parse(avatarUri)
                    : Uri.fromFile(new File(avatarUri));
            imageView.setImageURI(uri);
            if (imageView.getDrawable() == null) {
                imageView.setImageResource(placeholderRes);
            }
        } catch (Throwable ignored) {
            imageView.setImageResource(placeholderRes);
        }
    }

    @Nullable
    private static Bitmap downloadBitmap(@NonNull String url) {
        try {
            return BitmapFactory.decodeStream(new URL(url).openStream());
        } catch (Exception ignored) {
            return null;
        }
    }
}
