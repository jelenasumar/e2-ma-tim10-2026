package com.example.slagalica.ui.room;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RoomSession;
import com.example.slagalica.viewmodel.room.RoomSessionViewModel;

public class RoomSessionFragment extends Fragment {

    private RoomSessionViewModel viewModel;
    private String roomId = "";
    private TextView roomIdView;

    public RoomSessionFragment() {
        super(R.layout.fragment_room_session);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(RoomSessionViewModel.class);
        roomIdView = view.findViewById(R.id.roomSessionId);

        view.findViewById(R.id.roomSessionBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        view.findViewById(R.id.roomSessionKoZnaZna).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.koZnaZnaMatchmakingFragment)
        );
        view.findViewById(R.id.roomSessionSpojnice).setOnClickListener(v ->
                navigateToGame(R.id.spojniceFragment)
        );
        view.findViewById(R.id.roomSessionAsocijacije).setOnClickListener(v ->
                navigateToGame(R.id.associationsFragment)
        );
        view.findViewById(R.id.roomSessionSkocko).setOnClickListener(v ->
                navigateToGame(R.id.skockoFragment)
        );
        view.findViewById(R.id.roomSessionKorakPoKorak).setOnClickListener(v ->
                navigateToGame(R.id.stepByStepFragment)
        );
        view.findViewById(R.id.roomSessionMojBroj).setOnClickListener(v ->
                navigateToGame(R.id.mojBrojFragment)
        );

        viewModel.getRoom().observe(getViewLifecycleOwner(), this::renderRoom);

        Bundle args = getArguments();
        roomId = args != null ? args.getString("roomId", "") : "";
        viewModel.start(roomId);
    }

    private void renderRoom(@NonNull RoomSession room) {
        roomIdView.setText(getString(R.string.room_session_id, room.getRoomId()));
    }

    private void navigateToGame(int destinationId) {
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        NavHostFragment.findNavController(this).navigate(destinationId, args);
    }
}
