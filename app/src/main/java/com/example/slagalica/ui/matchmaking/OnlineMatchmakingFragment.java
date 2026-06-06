package com.example.slagalica.ui.matchmaking;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.viewmodel.matchmaking.OnlineMatchmakingViewModel;

public class OnlineMatchmakingFragment extends Fragment {

    private OnlineMatchmakingViewModel viewModel;

    public OnlineMatchmakingFragment() {
        super(R.layout.fragment_online_matchmaking);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(OnlineMatchmakingViewModel.class);
        TextView status = view.findViewById(R.id.onlineMatchmakingStatus);
        TextView room = view.findViewById(R.id.onlineMatchmakingRoom);
        ProgressBar progress = view.findViewById(R.id.onlineMatchmakingProgress);
        Button cancel = view.findViewById(R.id.onlineMatchmakingCancel);

        view.findViewById(R.id.onlineMatchmakingBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        cancel.setOnClickListener(v -> viewModel.cancelLooking());

        viewModel.getStatus().observe(getViewLifecycleOwner(), status::setText);
        viewModel.getSearching().observe(getViewLifecycleOwner(), searching ->
                progress.setVisibility(Boolean.TRUE.equals(searching) ? View.VISIBLE : View.GONE)
        );
        viewModel.getRoomId().observe(getViewLifecycleOwner(), roomId -> {
            if (roomId != null && !roomId.isEmpty()) {
                room.setText(getString(R.string.online_matchmaking_room, roomId));
                room.setVisibility(View.VISIBLE);
                cancel.setEnabled(false);
            }
        });

        viewModel.startLooking();
    }
}
