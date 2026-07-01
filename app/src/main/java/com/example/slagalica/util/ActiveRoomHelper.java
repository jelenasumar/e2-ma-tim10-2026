package com.example.slagalica.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.RoomGameKeys;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;

/**
 * Determines whether a user is currently participating in an active room session.
 * Ignores finished and stale rooms so abandoned READY lobbies do not block invites.
 */
public final class ActiveRoomHelper {

  private static final long PLAYING_IDLE_MS = 4L * 60L * 60L * 1000L;
  private static final long READY_LOBBY_IDLE_MS = 30L * 60L * 1000L;

  private ActiveRoomHelper() {
  }

  public static boolean isUserInActiveRoom(@NonNull DocumentSnapshot room, @NonNull String uid) {
    if (uid.isEmpty() || !isRoomActivelyBlocking(room)) {
      return false;
    }
    String hostUid = stringOrEmpty(room.getString("hostUid"));
    String guestUid = stringOrEmpty(room.getString("guestUid"));
    return uid.equals(hostUid) || uid.equals(guestUid);
  }

  public static boolean isRoomActivelyBlocking(@NonNull DocumentSnapshot room) {
    String status = stringOrDefault(room.getString("status"), "");
    if (RoomGameKeys.STATUS_FINISHED.equals(status)) {
      return false;
    }
    if (!RoomGameKeys.STATUS_READY.equals(status)
        && !RoomGameKeys.STATUS_PLAYING.equals(status)
        && !RoomGameKeys.STATUS_BREAK.equals(status)) {
      return false;
    }

    String hostUid = stringOrEmpty(room.getString("hostUid"));
    String guestUid = stringOrEmpty(room.getString("guestUid"));
    if (RoomGameKeys.STATUS_READY.equals(status)) {
      if (hostUid.isEmpty() || guestUid.isEmpty()) {
        return false;
      }
      return isRecent(room, READY_LOBBY_IDLE_MS);
    }
    if (hostUid.isEmpty() && guestUid.isEmpty()) {
      return false;
    }
    return isRecent(room, PLAYING_IDLE_MS);
  }

  private static boolean isRecent(@NonNull DocumentSnapshot room, long maxIdleMs) {
    long now = System.currentTimeMillis();
    long updatedAt = timestampMillis(room.get("updatedAt"));
    if (updatedAt <= 0L) {
      updatedAt = timestampMillis(room.get("createdAt"));
    }
    if (updatedAt <= 0L) {
      return false;
    }
    return now - updatedAt <= maxIdleMs;
  }

  private static long timestampMillis(@Nullable Object value) {
    if (value instanceof Timestamp) {
      return ((Timestamp) value).toDate().getTime();
    }
    if (value instanceof Long) {
      return (Long) value;
    }
    return 0L;
  }

  @NonNull
  private static String stringOrEmpty(@Nullable String value) {
    return value != null ? value.trim() : "";
  }

  @NonNull
  private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
    return value != null && !value.trim().isEmpty() ? value.trim() : fallback;
  }
}
