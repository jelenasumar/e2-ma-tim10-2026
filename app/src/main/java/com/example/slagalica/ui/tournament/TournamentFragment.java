package com.example.slagalica.ui.tournament;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.TournamentPlayer;
import com.example.slagalica.model.TournamentState;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.viewmodel.tournament.TournamentViewModel;

import java.util.List;

public class TournamentFragment extends Fragment {

    private TournamentViewModel viewModel;
    private View bracket;
    private View semiOne;
    private View semiTwo;
    private TextView finalStatus;
    private Button joinButton;
    private Button cancelButton;
    private Button enterRoomButton;
    private String currentRoomId = "";

    public TournamentFragment() {
        super(R.layout.fragment_tournament);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(TournamentViewModel.class);
        TextView status = view.findViewById(R.id.tournamentStatus);
        ProgressBar progress = view.findViewById(R.id.tournamentProgress);
        bracket = view.findViewById(R.id.tournamentBracket);
        semiOne = view.findViewById(R.id.tournamentSemiOne);
        semiTwo = view.findViewById(R.id.tournamentSemiTwo);
        finalStatus = view.findViewById(R.id.tournamentFinalStatus);
        joinButton = view.findViewById(R.id.tournamentJoin);
        cancelButton = view.findViewById(R.id.tournamentCancel);
        enterRoomButton = view.findViewById(R.id.tournamentEnterRoom);

        view.findViewById(R.id.tournamentBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        joinButton.setOnClickListener(v -> viewModel.joinTournament());
        cancelButton.setOnClickListener(v -> viewModel.cancelWaiting());
        enterRoomButton.setOnClickListener(v -> openCurrentRoom());

        viewModel.getStatus().observe(getViewLifecycleOwner(), status::setText);
        viewModel.getSearching().observe(getViewLifecycleOwner(), searching -> {
            boolean active = Boolean.TRUE.equals(searching);
            progress.setVisibility(active ? View.VISIBLE : View.GONE);
            cancelButton.setVisibility(active ? View.VISIBLE : View.GONE);
            joinButton.setEnabled(!active);
        });
        viewModel.getTournament().observe(getViewLifecycleOwner(), this::renderTournament);
    }

    private void renderTournament(@NonNull TournamentState state) {
        bracket.setVisibility(View.VISIBLE);
        joinButton.setVisibility(View.GONE);
        cancelButton.setVisibility(View.GONE);

        List<TournamentPlayer> players = state.getPlayers();
        bindPlayer(requireView().findViewById(R.id.tournamentPlayerOne), playerAt(players, 0));
        bindPlayer(requireView().findViewById(R.id.tournamentPlayerTwo), playerAt(players, 1));
        bindPlayer(requireView().findViewById(R.id.tournamentPlayerThree), playerAt(players, 2));
        bindPlayer(requireView().findViewById(R.id.tournamentPlayerFour), playerAt(players, 3));

        markSemiResult(semiOne, state.getSemiOneWinnerUid());
        markSemiResult(semiTwo, state.getSemiTwoWinnerUid());

        if (TournamentState.STATUS_FINAL_READY.equals(state.getStatus())) {
            TournamentPlayer first = state.playerByUid(state.getSemiOneWinnerUid());
            TournamentPlayer second = state.playerByUid(state.getSemiTwoWinnerUid());
            finalStatus.setText(getString(
                    R.string.tournament_final_pair,
                    first != null ? first.getUsername() : "?",
                    second != null ? second.getUsername() : "?"
            ));
            pulse(finalStatus);
        } else if (TournamentState.STATUS_FINISHED.equals(state.getStatus())) {
            TournamentPlayer champion = state.playerByUid(state.getChampionUid());
            finalStatus.setText(getString(
                    R.string.tournament_champion,
                    champion != null ? champion.getUsername() : "?"
            ));
            pulse(finalStatus);
        } else {
            finalStatus.setText(R.string.tournament_final_waiting);
        }

        currentRoomId = viewModel.currentRoomId(state);
        enterRoomButton.setVisibility(currentRoomId.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void bindPlayer(@NonNull View row, @NonNull TournamentPlayer player) {
        ImageView avatar = row.findViewById(R.id.tournamentPlayerAvatar);
        TextView username = row.findViewById(R.id.tournamentPlayerUsername);
        TextView league = row.findViewById(R.id.tournamentPlayerLeague);
        if (player.isEmpty()) {
            username.setText(R.string.tournament_waiting_player);
            league.setText("");
            avatar.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }
        username.setText(player.getUsername());
        league.setText(player.getLeagueName());
        AvatarImageLoader.load(avatar, player.getAvatarUri(), R.drawable.ic_avatar_placeholder);
    }

    private void markSemiResult(@NonNull View matchBlock, @NonNull String winnerUid) {
        if (winnerUid.isEmpty()) {
            matchBlock.setAlpha(1f);
            matchBlock.setScaleX(1f);
            matchBlock.setScaleY(1f);
            return;
        }
        pulse(matchBlock);
    }

    private void pulse(@NonNull View target) {
        target.animate()
                .alpha(1f)
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(180L)
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(180L)
                        .start())
                .start();
    }

    @NonNull
    private static TournamentPlayer playerAt(@NonNull List<TournamentPlayer> players, int index) {
        return index < players.size() ? players.get(index) : TournamentPlayer.empty();
    }

    private void openCurrentRoom() {
        if (currentRoomId.isEmpty()) {
            return;
        }
        Bundle args = new Bundle();
        args.putString("roomId", currentRoomId);
        NavHostFragment.findNavController(this).navigate(R.id.roomSessionFragment, args);
    }
}
