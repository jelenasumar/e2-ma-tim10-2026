package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.viewmodel.games.KoZnaZnaMatchmakingViewModel;
import com.google.android.material.button.MaterialButton;

public class KoZnaZnaMatchmakingFragment extends Fragment {

    private KoZnaZnaMatchmakingViewModel viewModel;

    public KoZnaZnaMatchmakingFragment() {
        super(R.layout.fragment_ko_zna_zna_matchmaking);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        viewModel = new ViewModelProvider(this).get(KoZnaZnaMatchmakingViewModel.class);

        MaterialButton createBtn = view.findViewById(R.id.kzzCreateLobbyButton);
        MaterialButton joinBtn = view.findViewById(R.id.kzzJoinLobbyButton);
        EditText codeInput = view.findViewById(R.id.kzzJoinCodeInput);
        TextView statusView = view.findViewById(R.id.kzzMatchmakingStatus);
        TextView lobbyCodeView = view.findViewById(R.id.kzzLobbyCodeLabel);

        createBtn.setOnClickListener(v -> viewModel.createLobby());
        joinBtn.setOnClickListener(v -> viewModel.joinLobby(codeInput.getText().toString()));

        viewModel.getStatusMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                statusView.setText(message);
            }
        });

        viewModel.getLobbyCode().observe(getViewLifecycleOwner(), code -> {
            if (code != null && !code.isEmpty()) {
                lobbyCodeView.setVisibility(View.VISIBLE);
                lobbyCodeView.setText(getString(R.string.kzz_lobby_code_display, code));
            }
        });

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            createBtn.setEnabled(!busy);
            joinBtn.setEnabled(!busy);
        });

        viewModel.getStartGame().observe(getViewLifecycleOwner(), event -> {
            if (event == null) {
                return;
            }
            Bundle args = new Bundle();
            args.putString("matchId", event.getMatchId());
            args.putString("myUid", event.getMyUid());
            NavHostFragment.findNavController(this).navigate(R.id.action_matchmaking_to_game, args);
        });
    }

    @Override
    public void onDestroyView() {
        viewModel.cleanup();
        super.onDestroyView();
    }
}
