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
import com.example.slagalica.ui.ranking.RewardConfettiView;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.viewmodel.tournament.TournamentViewModel;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TournamentFragment extends Fragment {

    private TournamentViewModel viewModel;
    private View bracket;
    private View semiOne;
    private View semiTwo;
    private TextView finalStatus;
    private RewardConfettiView confetti;
    private Button joinButton;
    private Button cancelButton;
    private Button enterRoomButton;
    private final Set<String> celebratedWins = new HashSet<>();
    private final Set<String> animatedWins = new HashSet<>();
    private final Set<String> animatedLosses = new HashSet<>();
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
        confetti = view.findViewById(R.id.tournamentConfetti);
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
        joinButton.setVisibility(viewModel.canStartAnotherTournament(state) ? View.VISIBLE : View.GONE);
        cancelButton.setVisibility(View.GONE);

        List<TournamentPlayer> players = state.getPlayers();
        View playerOneRow = requireView().findViewById(R.id.tournamentPlayerOne);
        View playerTwoRow = requireView().findViewById(R.id.tournamentPlayerTwo);
        View playerThreeRow = requireView().findViewById(R.id.tournamentPlayerThree);
        View playerFourRow = requireView().findViewById(R.id.tournamentPlayerFour);
        TournamentPlayer playerOne = playerAt(players, 0);
        TournamentPlayer playerTwo = playerAt(players, 1);
        TournamentPlayer playerThree = playerAt(players, 2);
        TournamentPlayer playerFour = playerAt(players, 3);
        bindPlayer(playerOneRow, playerOne);
        bindPlayer(playerTwoRow, playerTwo);
        bindPlayer(playerThreeRow, playerThree);
        bindPlayer(playerFourRow, playerFour);

        markSemiResult(semiOne, state.getSemiOneWinnerUid());
        markSemiResult(semiTwo, state.getSemiTwoWinnerUid());
        markPlayerResult(playerOneRow, playerOne, state.getSemiOneWinnerUid(), "semi_one");
        markPlayerResult(playerTwoRow, playerTwo, state.getSemiOneWinnerUid(), "semi_one");
        markPlayerResult(playerThreeRow, playerThree, state.getSemiTwoWinnerUid(), "semi_two");
        markPlayerResult(playerFourRow, playerFour, state.getSemiTwoWinnerUid(), "semi_two");
        markFinalistResult(playerOneRow, playerOne, state);
        markFinalistResult(playerTwoRow, playerTwo, state);
        markFinalistResult(playerThreeRow, playerThree, state);
        markFinalistResult(playerFourRow, playerFour, state);
        celebrateMyWinOnce("semi_one", state.getSemiOneWinnerUid());
        celebrateMyWinOnce("semi_two", state.getSemiTwoWinnerUid());

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
            celebrateMyWinOnce("final", state.getChampionUid());
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
        TextView result = row.findViewById(R.id.tournamentPlayerResult);
        resetPlayerResult(row, result);
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

    private void markPlayerResult(
            @NonNull View row,
            @NonNull TournamentPlayer player,
            @NonNull String winnerUid,
            @NonNull String phase
    ) {
        if (player.isEmpty() || winnerUid.isEmpty()) {
            return;
        }
        TextView result = row.findViewById(R.id.tournamentPlayerResult);
        boolean winner = player.getUid().equals(winnerUid);
        result.setVisibility(View.VISIBLE);
        result.setText(winner ? R.string.tournament_win_label : R.string.tournament_loss_label);
        result.setTextColor(winner ? 0xFF1B5E20 : 0xFF8E1B1B);
        row.setAlpha(winner ? 1f : 0.55f);
        if (winner) {
            row.setTranslationX(0f);
            pulseWinOnce(row, phase, player.getUid());
        } else {
            shakeLossOnce(row, phase, player.getUid());
        }
    }

    private void markFinalistResult(
            @NonNull View row,
            @NonNull TournamentPlayer player,
            @NonNull TournamentState state
    ) {
        if (!TournamentState.STATUS_FINISHED.equals(state.getStatus()) || player.isEmpty()) {
            return;
        }
        boolean finalist = player.getUid().equals(state.getSemiOneWinnerUid())
                || player.getUid().equals(state.getSemiTwoWinnerUid());
        if (finalist) {
            markPlayerResult(row, player, state.getChampionUid(), "final");
        }
    }

    private void resetPlayerResult(@NonNull View row, @NonNull TextView result) {
        row.animate().cancel();
        row.setAlpha(1f);
        row.setScaleX(1f);
        row.setScaleY(1f);
        row.setTranslationX(0f);
        result.setVisibility(View.GONE);
        result.setText("");
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

    private void celebrateMyWinOnce(@NonNull String phase, @NonNull String winnerUid) {
        String myUid = viewModel.getCurrentUid();
        if (winnerUid.isEmpty() || !winnerUid.equals(myUid)) {
            return;
        }
        String key = phase + ":" + winnerUid;
        if (celebratedWins.add(key)) {
            confetti.start();
        }
    }

    private void pulseWinOnce(@NonNull View target, @NonNull String phase, @NonNull String playerUid) {
        String key = phase + ":" + playerUid;
        if (animatedWins.add(key)) {
            pulse(target);
        }
    }

    private void shakeLossOnce(@NonNull View target, @NonNull String phase, @NonNull String playerUid) {
        String key = phase + ":" + playerUid;
        if (!animatedLosses.add(key)) {
            return;
        }
        target.animate()
                .translationX(-14f)
                .setDuration(60L)
                .withEndAction(() -> target.animate()
                        .translationX(14f)
                        .setDuration(90L)
                        .withEndAction(() -> target.animate()
                                .translationX(0f)
                                .setDuration(70L)
                                .start())
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
