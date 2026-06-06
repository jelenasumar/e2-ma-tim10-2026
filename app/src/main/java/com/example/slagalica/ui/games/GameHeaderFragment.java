package com.example.slagalica.ui.games;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.UserProfile;

import java.util.Locale;

public class GameHeaderFragment extends Fragment {

    private String roundText;
    private String timeText;
    private String playerOneName;
    private String playerTwoName;
    private int playerOneScore;
    private int playerTwoScore;
    private String playerOneAvatarUri;

    public GameHeaderFragment() {
        super(R.layout.fragment_game_header);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        loadDefaultPlayers();
        render(view);
    }

    public void setGameState(
            @NonNull String roundText,
            @NonNull String timeText,
            int playerOneScore,
            int playerTwoScore
    ) {
        this.roundText = roundText;
        this.timeText = timeText;
        this.playerOneScore = playerOneScore;
        this.playerTwoScore = playerTwoScore;

        View view = getView();
        if (view != null) {
            render(view);
        }
    }

    private void loadDefaultPlayers() {
        UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
        playerOneName = profile.getUsername();
        if (playerOneName == null || playerOneName.trim().isEmpty()) {
            playerOneName = getString(R.string.guest_player);
        }
        playerTwoName = getString(R.string.opponent_player);
        playerOneAvatarUri = profile.getAvatarUri();
    }

    private void render(@NonNull View view) {
        TextView round = view.findViewById(R.id.gameHeaderRound);
        TextView time = view.findViewById(R.id.gameHeaderTime);
        TextView playerOne = view.findViewById(R.id.gameHeaderPlayerOneScore);
        TextView playerTwo = view.findViewById(R.id.gameHeaderPlayerTwoScore);
        ImageView playerOneAvatar = view.findViewById(R.id.gameHeaderPlayerOneAvatar);
        ImageView playerTwoAvatar = view.findViewById(R.id.gameHeaderPlayerTwoAvatar);

        round.setText(roundText != null ? roundText : getString(R.string.game_header_round_default));
        time.setText(timeText != null ? timeText : getString(R.string.game_header_time_default));
        playerOne.setText(formatScore(playerOneName, playerOneScore));
        playerTwo.setText(formatScore(playerTwoName, playerTwoScore));

        bindAvatar(playerOneAvatar, playerOneAvatarUri);
        playerTwoAvatar.setImageResource(R.drawable.ic_avatar_placeholder);
    }

    private void bindAvatar(@NonNull ImageView imageView, @Nullable String avatarUri) {
        if (avatarUri == null || avatarUri.isEmpty()) {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            return;
        }

        try {
            imageView.setImageURI(Uri.parse(avatarUri));
            if (imageView.getDrawable() == null) {
                imageView.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        } catch (Throwable ignored) {
            imageView.setImageResource(R.drawable.ic_avatar_placeholder);
        }
    }

    @NonNull
    private static String formatScore(@NonNull String playerName, int score) {
        return String.format(Locale.getDefault(), "%s: %d", playerName, score);
    }
}
