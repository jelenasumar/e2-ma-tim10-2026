package com.example.slagalica.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.widget.ImageView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;

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

        int presetRes = presetDrawableRes(avatarUri);
        if (presetRes != 0) {
            imageView.setImageResource(presetRes);
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

    public static boolean isSharedAvatarUri(@Nullable String avatarUri) {
        return avatarUri != null
                && (avatarUri.startsWith("preset:")
                || avatarUri.startsWith("http://")
                || avatarUri.startsWith("https://"));
    }

    @DrawableRes
    private static int presetDrawableRes(@NonNull String avatarUri) {
        switch (avatarUri) {
            case "preset:avatar_preset_1":
                return R.drawable.avatar_preset_1;
            case "preset:avatar_preset_2":
                return R.drawable.avatar_preset_2;
            case "preset:avatar_preset_3":
                return R.drawable.avatar_preset_3;
            case "preset:avatar_preset_4":
                return R.drawable.avatar_preset_4;
            case "preset:avatar_preset_5":
                return R.drawable.avatar_preset_5;
            case "preset:avatar_preset_6":
                return R.drawable.avatar_preset_6;
            default:
                return 0;
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
