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
import android.widget.Toast;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AlertDialog;
import com.example.slagalica.R;

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
                        if (!canControlRoomProgress(room, uid)
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

    private static boolean canControlRoomProgress(
            @NonNull RoomSession room,
            @NonNull String uid
    ) {
        if (uid.equals(room.getHostUid())) {
            return true;
        }

        return !room.getAbandonedByUid().isEmpty()
                && !uid.equals(room.getAbandonedByUid())
                && (uid.equals(room.getHostUid()) || uid.equals(room.getGuestUid()));
    }

    public static void registerRoomBackHandler(@NonNull Fragment fragment, @NonNull String roomId) {
        if (roomId.trim().isEmpty()) {
            return;
        }

        fragment.requireActivity()
                .getOnBackPressedDispatcher()
                .addCallback(fragment.getViewLifecycleOwner(), new OnBackPressedCallback(true) {
                    @Override
                    public void handleOnBackPressed() {
                        confirmAbandonOrNavigateUp(fragment, roomId);
                    }
                });
    }

    public static void confirmAbandonOrNavigateUp(@NonNull Fragment fragment, @NonNull String roomId) {
        if (roomId.trim().isEmpty()) {
            navigateUpIfAdded(fragment);
            return;
        }

        new AlertDialog.Builder(fragment.requireContext())
                .setTitle(R.string.room_abandon_title)
                .setMessage(R.string.room_abandon_message)
                .setPositiveButton(R.string.room_abandon_positive, (dialog, which) ->
                        abandonRoomAndExit(fragment, roomId)
                )
                .setNegativeButton(R.string.room_abandon_negative, null)
                .show();
    }

    private static void abandonRoomAndExit(@NonNull Fragment fragment, @NonNull String roomId) {
        RoomSessionRepository repository = new RoomSessionRepository();
        repository.fetchRoom(
                roomId,
                room -> repository.abandonRoom(
                        room,
                        () -> navigateUpIfAdded(fragment),
                        error -> showAbandonError(fragment)
                ),
                error -> showAbandonError(fragment)
        );
    }

    private static void navigateUpIfAdded(@NonNull Fragment fragment) {
        if (!fragment.isAdded()) {
            return;
        }
        NavHostFragment.findNavController(fragment).navigateUp();
    }

    private static void showAbandonError(@NonNull Fragment fragment) {
        if (!fragment.isAdded()) {
            return;
        }
        Toast.makeText(
                fragment.requireContext(),
                R.string.room_abandon_error,
                Toast.LENGTH_SHORT
        ).show();
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
