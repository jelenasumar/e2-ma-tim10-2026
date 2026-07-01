package com.example.slagalica.ui.room;

import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.RoomSessionRepository;
import com.example.slagalica.data.repository.TournamentRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.viewmodel.room.RoomSessionViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;
import androidx.appcompat.app.AlertDialog;

public class RoomSessionFragment extends Fragment {

    private RoomSessionViewModel viewModel;
    private RoomSessionRepository repository;
    private String roomId = "";
    private String myUid = "";
    private TextView roomIdView;
    private TextView playersView;
    private TextView currentGameView;
    private TextView breakStatusView;
    private CountDownTimer breakTimer;
    private boolean navigatedToCurrentGame = false;
    private boolean roomMatchStatsRecorded = false;
    private String lastHandledGame = "";
    private RoomSession latestRoom;
    private final Handler handler = new Handler(Looper.getMainLooper());

    public RoomSessionFragment() {
        super(R.layout.fragment_room_session);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        repository = new RoomSessionRepository();
        viewModel = new ViewModelProvider(this).get(RoomSessionViewModel.class);
        roomIdView = view.findViewById(R.id.roomSessionId);
        playersView = view.findViewById(R.id.roomSessionPlayers);
        currentGameView = view.findViewById(R.id.roomSessionCurrentGame);
        breakStatusView = view.findViewById(R.id.roomSessionBreakStatus);

        view.findViewById(R.id.roomSessionBack).setOnClickListener(v -> confirmLeaveRoom());

        view.findViewById(R.id.roomSessionGamesContainer).setVisibility(View.GONE);
        view.findViewById(R.id.roomSessionGamesTitle).setVisibility(View.GONE);

        viewModel.getRoom().observe(getViewLifecycleOwner(), this::renderRoom);

        Bundle args = getArguments();
        roomId = args != null ? args.getString("roomId", "") : "";
        myUid = repository.getCurrentUid() != null ? repository.getCurrentUid() : "";
        viewModel.start(roomId);
    }

    @Override
    public void onDestroyView() {
        stopBreakTimer();
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void renderRoom(@NonNull RoomSession room) {

        latestRoom = room;

        roomIdView.setText(getString(R.string.room_session_id, room.getRoomId()));

        if (room.hasBothPlayers()) {
            playersView.setVisibility(View.VISIBLE);
            playersView.setText(getString(
                    R.string.room_session_players,
                    room.getHostUsername(),
                    room.getGuestUsername()
            ));
        } else {
            playersView.setVisibility(View.GONE);
        }

        if (RoomGameKeys.STATUS_FINISHED.equals(room.getStatus())) {
            stopBreakTimer();
            navigatedToCurrentGame = false;
            recordRoomMatchStatsIfNeeded(room);
            currentGameView.setVisibility(View.VISIBLE);
            currentGameView.setText(getString(
                    R.string.room_session_finished,
                    room.getHostUsername(),
                    room.getHostTotalScore(),
                    room.getGuestUsername(),
                    room.getGuestTotalScore()
            ));
            breakStatusView.setVisibility(View.GONE);
            return;
        }

        if (RoomGameKeys.STATUS_BREAK.equals(room.getStatus())) {
            navigatedToCurrentGame = false;
            currentGameView.setVisibility(View.GONE);
            breakStatusView.setVisibility(View.VISIBLE);
            startBreakCountdown(room);
            scheduleAdvanceWhenBreakEnds(room);
            return;
        }

        stopBreakTimer();
        breakStatusView.setVisibility(View.GONE);

        if (RoomGameKeys.STATUS_READY.equals(room.getStatus()) && room.hasBothPlayers()) {
            repository.ensureSessionStarted(room, () -> { }, error -> { });
            currentGameView.setVisibility(View.VISIBLE);
            currentGameView.setText(R.string.room_session_starting);
            return;
        }

        if (RoomGameKeys.STATUS_PLAYING.equals(room.getStatus())) {
            currentGameView.setVisibility(View.VISIBLE);
            currentGameView.setText(getString(
                    R.string.room_session_current_game,
                    room.getCurrentGameIndex() + 1,
                    room.getGameOrder().size(),
                    room.currentGameLabel()
            ));

            if (!navigatedToCurrentGame || !room.getCurrentGame().equals(lastHandledGame)) {
                navigatedToCurrentGame = true;
                lastHandledGame = room.getCurrentGame();
                handler.post(() -> RoomGameFlow.navigateToCurrentGame(this, room));
            }
        }
    }

    private void confirmLeaveRoom() {
        if (latestRoom == null
                || RoomGameKeys.STATUS_FINISHED.equals(latestRoom.getStatus())
                || myUid.isEmpty()
                || (!myUid.equals(latestRoom.getHostUid()) && !myUid.equals(latestRoom.getGuestUid()))) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        new AlertDialog.Builder(requireContext())
                .setTitle("Napustiti partiju?")
                .setMessage("Ako napustis partiju, automatski gubis mec.")
                .setNegativeButton("Ostani", null)
                .setPositiveButton("Napusti", (dialog, which) -> abandonCurrentRoom())
                .show();
    }

    private void abandonCurrentRoom() {
        if (latestRoom == null) {
            NavHostFragment.findNavController(this).navigateUp();
            return;
        }

        repository.abandonRoom(
                latestRoom,
                () -> {
                    if (isAdded()) {
                        NavHostFragment.findNavController(this).navigateUp();
                    }
                },
                error -> {
                    if (isAdded()) {
                        NavHostFragment.findNavController(this).navigateUp();
                    }
                }
        );
    }

    private void startBreakCountdown(@NonNull RoomSession room) {
        stopBreakTimer();
        long remaining = Math.max(0L, room.getBreakEndsAtMillis() - System.currentTimeMillis());
        if (remaining == 0L) {
            breakStatusView.setText(R.string.room_session_break_starting);
            return;
        }

        breakTimer = new CountDownTimer(remaining, 500L) {
            @Override
            public void onTick(long millisUntilFinished) {
                int seconds = (int) Math.ceil(millisUntilFinished / 1000.0);
                String nextGame = RoomGameKeys.nextGame(room.getGameOrder(), room.getCurrentGameIndex());
                if (nextGame != null) {
                    breakStatusView.setText(getString(R.string.room_session_break_countdown, seconds));
                } else {
                    breakStatusView.setText(getString(R.string.room_session_break_final, seconds));
                }
            }

            @Override
            public void onFinish() {
                breakStatusView.setText(R.string.room_session_break_starting);
            }
        };
        breakTimer.start();
    }

    private void scheduleAdvanceWhenBreakEnds(@NonNull RoomSession room) {
        if (myUid.isEmpty() || !canControlRoomProgress(room)) {
            return;
        }
        long delay = Math.max(0L, room.getBreakEndsAtMillis() - System.currentTimeMillis());
        handler.removeCallbacksAndMessages(null);
        handler.postDelayed(() -> repository.fetchRoom(
                roomId,
                latest -> {
                    if (RoomGameKeys.STATUS_BREAK.equals(latest.getStatus())
                            && System.currentTimeMillis() >= latest.getBreakEndsAtMillis()) {
                        repository.advanceToNextGame(latest, () -> { }, error -> { });
                    }
                },
                error -> { }
        ), delay + 200L);
    }

    private boolean canControlRoomProgress(@NonNull RoomSession room) {
        if (myUid.equals(room.getHostUid())) {
            return true;
        }

        return !room.getAbandonedByUid().isEmpty()
                && !myUid.equals(room.getAbandonedByUid())
                && (myUid.equals(room.getHostUid()) || myUid.equals(room.getGuestUid()));
    }

    private void stopBreakTimer() {
        if (breakTimer != null) {
            breakTimer.cancel();
            breakTimer = null;
        }
    }

    private void recordRoomMatchStatsIfNeeded(@NonNull RoomSession room) {
        if (roomMatchStatsRecorded || myUid.isEmpty()) {
            return;
        }
        roomMatchStatsRecorded = true;
        if (!myUid.equals(room.getHostUid()) && !myUid.equals(room.getGuestUid())) {
            return;
        }
        if ("TOURNAMENT".equals(room.getMatchType())) {
            new TournamentRepository(requireContext()).processFinishedTournamentRoom(room, () -> { }, error -> { });
        } else {
            new UserProfileRepository(requireContext()).processFinishedRoomResult(room, () -> { }, error -> { });
        }
    }
}
