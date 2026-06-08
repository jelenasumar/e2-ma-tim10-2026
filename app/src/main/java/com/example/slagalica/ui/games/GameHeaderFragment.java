package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.AvatarImageLoader;

import java.util.Locale;

public class GameHeaderFragment extends Fragment {

    private GameHeaderState state;
    private GameHeaderPlayerState defaultPlayerOne;
    private GameHeaderPlayerState defaultPlayerTwo;

    public GameHeaderFragment() {
        super(R.layout.fragment_game_header);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadDefaultPlayers();
        render(view);
    }

    public void setHeaderState(@NonNull GameHeaderState state) {
        this.state = state;

        View view = getView();
        if (view != null) {
            render(view);
        }
    }

    public void setGameState(
            @NonNull String roundText,
            @NonNull String timeText,
            int playerOneScore,
            int playerTwoScore
    ) {
        GameHeaderPlayerState playerOne = getDefaultPlayerOne().withScore(playerOneScore);
        GameHeaderPlayerState playerTwo = getDefaultPlayerTwo().withScore(playerTwoScore);
        setHeaderState(new GameHeaderState(roundText, timeText, playerOne, playerTwo));
    }

    private void loadDefaultPlayers() {
        UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
        String playerOneName = profile.getUsername();
        if (playerOneName.trim().isEmpty()) {
            playerOneName = getString(R.string.guest_player);
        }
        defaultPlayerOne = new GameHeaderPlayerState(playerOneName, 0, profile.getAvatarUri());
        defaultPlayerTwo = new GameHeaderPlayerState(getString(R.string.opponent_player), 0, null);
    }

    private void render(@NonNull View view) {
        GameHeaderState currentState = state != null ? state : createDefaultState();

        TextView round = view.findViewById(R.id.gameHeaderRound);
        TextView time = view.findViewById(R.id.gameHeaderTime);
        TextView playerOne = view.findViewById(R.id.gameHeaderPlayerOneScore);
        TextView playerTwo = view.findViewById(R.id.gameHeaderPlayerTwoScore);
        ImageView playerOneAvatar = view.findViewById(R.id.gameHeaderPlayerOneAvatar);
        ImageView playerTwoAvatar = view.findViewById(R.id.gameHeaderPlayerTwoAvatar);
        LinearLayout playerOneContainer = view.findViewById(R.id.gameHeaderPlayerOneContainer);
        LinearLayout playerTwoContainer = view.findViewById(R.id.gameHeaderPlayerTwoContainer);

        round.setText(currentState.getRoundText());
        time.setText(currentState.getTimeText());
        playerOne.setText(formatScore(currentState.getPlayerOne()));
        playerTwo.setText(formatScore(currentState.getPlayerTwo()));

        bindAvatar(playerOneAvatar, currentState.getPlayerOne().getAvatarUri());
        bindAvatar(playerTwoAvatar, currentState.getPlayerTwo().getAvatarUri());
        bindActivePlayer(playerOneContainer, playerTwoContainer, currentState.getActivePlayerNumber());
    }

    private void bindActivePlayer(
            @NonNull View playerOneContainer,
            @NonNull View playerTwoContainer,
            int activePlayerNumber
    ) {
        playerOneContainer.setBackgroundResource(
                activePlayerNumber == 1 ? R.drawable.game_header_active_player_background : 0
        );
        playerTwoContainer.setBackgroundResource(
                activePlayerNumber == 2 ? R.drawable.game_header_active_player_background : 0
        );
        playerOneContainer.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3));
        playerTwoContainer.setPadding(dpToPx(4), dpToPx(3), dpToPx(4), dpToPx(3));
    }

    @NonNull
    private GameHeaderState createDefaultState() {
        return new GameHeaderState(
                getString(R.string.game_header_round_default),
                getString(R.string.game_header_time_default),
                getDefaultPlayerOne(),
                getDefaultPlayerTwo()
        );
    }

    @NonNull
    private GameHeaderPlayerState getDefaultPlayerOne() {
        if (defaultPlayerOne == null) {
            loadDefaultPlayers();
        }
        return defaultPlayerOne;
    }

    @NonNull
    private GameHeaderPlayerState getDefaultPlayerTwo() {
        if (defaultPlayerTwo == null) {
            loadDefaultPlayers();
        }
        return defaultPlayerTwo;
    }

    private void bindAvatar(@NonNull ImageView imageView, @Nullable String avatarUri) {
        AvatarImageLoader.load(imageView, avatarUri, R.drawable.ic_avatar_placeholder);
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @NonNull
    private static String formatScore(@NonNull GameHeaderPlayerState player) {
        return String.format(Locale.getDefault(), "%s: %d", player.getUsername(), player.getScore());
    }
}
