package com.example.slagalica.ui.challenge;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RoomGameKeys;
import com.example.slagalica.viewmodel.challenge.ChallengeViewModel;
import com.google.android.material.button.MaterialButton;

import androidx.lifecycle.ViewModelProvider;

import java.util.List;

public class ChallengeSessionFragment extends Fragment {

    public static final String ARG_CHALLENGE_ID = "challengeId";
    public static final String ARG_GAME_INDEX = "challengeGameIndex";
    public static final String ARG_TOTAL_SCORE = "challengeTotalScore";

    private ChallengeViewModel viewModel;
    private String challengeId = "";
    private int gameIndex = 0;
    private int totalScore = 0;

    private TextView statusView;
    private TextView scoreView;
    private MaterialButton startButton;

    public ChallengeSessionFragment() {
        super(R.layout.fragment_challenge_session);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ChallengeViewModel.class);

        statusView = view.findViewById(R.id.challengeSessionStatus);
        scoreView = view.findViewById(R.id.challengeSessionScore);
        startButton = view.findViewById(R.id.challengeSessionStart);

        view.findViewById(R.id.challengeSessionBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        Bundle args = getArguments();
        if (args != null) {
            challengeId = args.getString(ARG_CHALLENGE_ID, "");
            gameIndex = args.getInt(ARG_GAME_INDEX, 0);
            totalScore = args.getInt(ARG_TOTAL_SCORE, 0);
        }

        startButton.setOnClickListener(v -> openCurrentGame());

        render();
    }

    private void render() {
        List<String> games = RoomGameKeys.DEFAULT_GAME_ORDER;

        scoreView.setText(getString(R.string.challenge_session_score, totalScore));

        if (gameIndex >= games.size()) {
            statusView.setText(getString(R.string.challenge_session_score, totalScore));
            startButton.setText(R.string.challenge_session_finish);
            startButton.setOnClickListener(v -> submitFinalResult());
            return;
        }

        String gameKey = games.get(gameIndex);
        statusView.setText(getString(
                R.string.challenge_session_status,
                gameIndex + 1,
                games.size(),
                labelForGame(gameKey)
        ));
        startButton.setText(R.string.challenge_session_start);
        startButton.setOnClickListener(v -> openCurrentGame());
    }

    private void openCurrentGame() {
        if (challengeId.isEmpty()) {
            Toast.makeText(requireContext(), "Izazov nije izabran.", Toast.LENGTH_SHORT).show();
            return;
        }

        List<String> games = RoomGameKeys.DEFAULT_GAME_ORDER;
        if (gameIndex >= games.size()) {
            submitFinalResult();
            return;
        }

        String gameKey = games.get(gameIndex);
        int destinationId = RoomGameKeys.destinationForGame(gameKey);
        if (destinationId == 0) {
            Toast.makeText(requireContext(), "Igra nije pronadjena.", Toast.LENGTH_SHORT).show();
            return;
        }

        Bundle args = new Bundle();
        args.putString(ARG_CHALLENGE_ID, challengeId);
        args.putInt(ARG_GAME_INDEX, gameIndex);
        args.putInt(ARG_TOTAL_SCORE, totalScore);

        NavHostFragment.findNavController(this).navigate(destinationId, args);
    }

    private void submitFinalResult() {
        viewModel.submitResult(
                challengeId,
                totalScore,
                () -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), "Izazov je zavrsen.", Toast.LENGTH_SHORT).show();
                        NavHostFragment.findNavController(this).navigateUp();
                    }
                },
                error -> {
                    if (isAdded()) {
                        Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                    }
                }
        );
    }

    @NonNull
    private static String labelForGame(@NonNull String gameKey) {
        switch (gameKey) {
            case RoomGameKeys.KO_ZNA_ZNA:
                return "Ko zna zna";
            case RoomGameKeys.SPOJNICE:
                return "Spojnice";
            case RoomGameKeys.ASOCIJACIJE:
                return "Asocijacije";
            case RoomGameKeys.SKOCKO:
                return "Skocko";
            case RoomGameKeys.KORAK_PO_KORAK:
                return "Korak po korak";
            case RoomGameKeys.MOJ_BROJ:
                return "Moj broj";
            default:
                return gameKey;
        }
    }
}