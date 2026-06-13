package com.example.slagalica.utils;

import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class AvatarFileStorage {

    private AvatarFileStorage() {
    }

    @NonNull
    public static File avatarFile(@NonNull Context context, @NonNull String uid) {
        File dir = new File(context.getFilesDir(), "avatars");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, uid + ".jpg");
    }

    @NonNull
    public static File copyToInternalStorage(
            @NonNull Context context,
            @NonNull String uid,
            @NonNull Uri sourceUri
    ) throws IOException {
        File dest = avatarFile(context, uid);
        try (InputStream in = context.getContentResolver().openInputStream(sourceUri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("Cannot open image.");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        if (!dest.exists() || dest.length() == 0L) {
            throw new IOException("Image copy failed.");
        }
        return dest;
    }

    @NonNull
    public static byte[] readBytes(@NonNull File file) throws IOException {
        try (FileInputStream in = new FileInputStream(file);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
