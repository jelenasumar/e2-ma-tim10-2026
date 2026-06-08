package com.example.slagalica.ui.room;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.model.RoomSession;

public final class RoomGameFlow {

    private static final long RETURN_TO_ROOM_DELAY_MS = 1_500L;

    private RoomGameFlow() {
    }

    public static void onGameFinished(@NonNull Fragment fragment, @NonNull String roomId) {
        onGameFinished(fragment, roomId, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    public static void onGameFinished(
            @NonNull Fragment fragment,
            @NonNull String roomId,
            int hostTotalScore,
            int guestTotalScore
    ) {
        RoomSessionRepository repository = new RoomSessionRepository();
        String uid = repository.getCurrentUid();
        if (uid != null) {
            repository.fetchRoom(
                    roomId,
                    room -> {
                        if (!uid.equals(room.getHostUid())
                                || !RoomGameKeys.STATUS_PLAYING.equals(room.getStatus())) {
                            return;
                        }
                        int finalHostScore = hostTotalScore == Integer.MIN_VALUE
                                ? room.getHostTotalScore()
                                : hostTotalScore;
                        int finalGuestScore = guestTotalScore == Integer.MIN_VALUE
                                ? room.getGuestTotalScore()
                                : guestTotalScore;
                        if (RoomGameKeys.nextGame(room.getGameOrder(), room.getCurrentGameIndex()) == null) {
                            repository.advanceToNextGame(room, finalHostScore, finalGuestScore, () -> { }, error -> { });
                        } else {
                            repository.startBreakAfterGame(roomId, finalHostScore, finalGuestScore, () -> { }, error -> { });
                        }
                    },
                    error -> { }
            );
        }

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (!fragment.isAdded()) {
                return;
            }
            NavHostFragment.findNavController(fragment).navigateUp();
        }, RETURN_TO_ROOM_DELAY_MS);
    }

    public static void navigateToCurrentGame(@NonNull Fragment fragment, @NonNull RoomSession room) {
        int destinationId = RoomGameKeys.destinationForGame(room.getCurrentGame());
        if (destinationId == 0) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("roomId", room.getRoomId());
        NavHostFragment.findNavController(fragment).navigate(destinationId, args);
    }
}
