package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.korakpokorak.KorakPoKorakUiState;
import com.example.slagalica.viewmodel.games.StepByStepViewModel;

import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.ui.room.RoomGameFlow;

import java.util.Locale;

public class StepByStepFragment extends Fragment {

    private static final int STEP_COUNT = 7;

    private static final int[] STEP_POINTS = {20, 18, 16, 14, 12, 10, 8};

    private StepByStepViewModel viewModel;

    private final TextView[] sentenceViews = new TextView[STEP_COUNT];
    private final TextView[] timerViews = new TextView[STEP_COUNT];

    private EditText answerInput;
    private Button submitButton;

    private TextView statusView;

    private GameHeaderFragment gameHeader;
    private GameHeaderPlayerState playerOneHeaderState;
    private GameHeaderPlayerState playerTwoHeaderState;

    private boolean statsRecorded;

    private String roomId = "";
    private boolean gameOverHandled;

    public StepByStepFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            ViewGroup container,
            Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_step_by_step, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(StepByStepViewModel.class);

        bindViews(view);

        setupGameHeader();

        submitButton.setOnClickListener(v -> {
            String answer = answerInput.getText() != null
                    ? answerInput.getText().toString()
                    : "";

            viewModel.submitAnswer(answer);
            answerInput.setText("");
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        Bundle args = getArguments();
        roomId = args != null ? args.getString("roomId", "") : "";

        if (!roomId.isEmpty()) {
            viewModel.startRoomGame(roomId);
        } else {
            viewModel.startGame();
        }
    }

    private void bindViews(@NonNull View view) {
        sentenceViews[0] = view.findViewById(R.id.sentence1);
        sentenceViews[1] = view.findViewById(R.id.sentence2);
        sentenceViews[2] = view.findViewById(R.id.sentence3);
        sentenceViews[3] = view.findViewById(R.id.sentence4);
        sentenceViews[4] = view.findViewById(R.id.sentence5);
        sentenceViews[5] = view.findViewById(R.id.sentence6);
        sentenceViews[6] = view.findViewById(R.id.sentence7);

        timerViews[0] = view.findViewById(R.id.timer1);
        timerViews[1] = view.findViewById(R.id.timer2);
        timerViews[2] = view.findViewById(R.id.timer3);
        timerViews[3] = view.findViewById(R.id.timer4);
        timerViews[4] = view.findViewById(R.id.timer5);
        timerViews[5] = view.findViewById(R.id.timer6);
        timerViews[6] = view.findViewById(R.id.timer7);

        answerInput = view.findViewById(R.id.answerInput);
        submitButton = view.findViewById(R.id.submitButton);
        statusView = view.findViewById(R.id.stepByStepStatus);
    }

    private void setupGameHeader() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.stepByStepGameHeader);
        if (!(fragment instanceof GameHeaderFragment)) {
            return;
        }

        gameHeader = (GameHeaderFragment) fragment;

        UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
        String username = profile.getUsername();
        if (username == null || username.trim().isEmpty()) {
            username = getString(R.string.guest_player);
        }

        playerOneHeaderState = new GameHeaderPlayerState(username, 0, profile.getAvatarUri());
        playerTwoHeaderState = new GameHeaderPlayerState(getString(R.string.opponent_player), 0, null);

        gameHeader.setHeaderState(new GameHeaderState(
                getString(R.string.game_header_round_default),
                getString(R.string.game_header_time_default),
                playerOneHeaderState,
                playerTwoHeaderState,
                1
        ));
    }

    private void updateGameHeader(@NonNull KorakPoKorakUiState state) {
        if (gameHeader == null || playerOneHeaderState == null || playerTwoHeaderState == null) {
            return;
        }

        int highlightedPlayer = state.isBonusPhase()
                ? state.getAnsweringPlayerNumber()
                : state.getActivePlayerNumber();

        gameHeader.setHeaderState(new GameHeaderState(
                String.format(
                        Locale.getDefault(),
                        "Runda: %d/%d",
                        state.getCurrentRound(),
                        state.getTotalRounds()
                ),
                String.format(
                        Locale.getDefault(),
                        "Preostalo vreme: 00:%02d",
                        state.getSecondsLeft()
                ),
                playerOneHeaderState.withScore(state.getPlayerOneScore()),
                playerTwoHeaderState.withScore(state.getPlayerTwoScore()),
                state.isGameOver() ? 0 : highlightedPlayer
        ));
    }


    private void renderState(@NonNull KorakPoKorakUiState state) {

        updateGameHeader(state);
        recordStatsIfNeeded(state);
        handleOnlineGameOver(state);

        renderSteps(state);
        renderTimers(state);

        if (state.getStatusMessage().isEmpty()) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(state.getStatusMessage());
        }

        answerInput.setEnabled(state.isCanSubmit());

        if (state.isBonusPhase()) {
            answerInput.setHint("Bonus odgovor igrača " + state.getAnsweringPlayerNumber());
        } else {
            answerInput.setHint("Odgovor igrača " + state.getActivePlayerNumber());
        }

        if (state.isGameOver()) {
            submitButton.setText("Nazad");
            submitButton.setEnabled(true);
            submitButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp()
            );
        } else {
            submitButton.setText(R.string.submit);
            submitButton.setEnabled(state.isCanSubmit());
            submitButton.setOnClickListener(v -> {
                String answer = answerInput.getText() != null
                        ? answerInput.getText().toString()
                        : "";

                viewModel.submitAnswer(answer);
                answerInput.setText("");
            });
        }

    }

    private void handleOnlineGameOver(@NonNull KorakPoKorakUiState state) {
        if (roomId.isEmpty() || gameOverHandled || !state.isGameOver()) {
            return;
        }

        gameOverHandled = true;

        RoomGameFlow.onGameFinished(
                this,
                roomId,
                state.getPlayerOneScore(),
                state.getPlayerTwoScore()
        );
    }

    private void recordStatsIfNeeded(@NonNull KorakPoKorakUiState state) {
        if (statsRecorded || !state.isGameOver()) {
            return;
        }

        statsRecorded = true;

        new UserProfileRepository(requireContext()).recordKorakPoKorakGame(
                viewModel.getCurrentUserGameScore(),
                viewModel.getCurrentUserOwnRoundSolvedStepIndex()
        );
    }

    private void renderSteps(@NonNull KorakPoKorakUiState state) {
        for (int i = 0; i < STEP_COUNT; i++) {
            if (i < state.getVisibleSteps().size()) {
                sentenceViews[i].setText((i + 1) + ". " + state.getVisibleSteps().get(i));
                sentenceViews[i].setAlpha(1f);
            } else {
                sentenceViews[i].setText((i + 1) + ".");
                sentenceViews[i].setAlpha(0.45f);
            }
        }
    }

    private void renderTimers(@NonNull KorakPoKorakUiState state) {
        for (int i = 0; i < STEP_COUNT; i++) {
            timerViews[i].setVisibility(View.VISIBLE);

            if (i == state.getCurrentStepIndex()
                    && !state.isRoundOver()
                    && !state.isGameOver()
                    && !state.isBonusPhase()) {
                timerViews[i].setText(String.valueOf(state.getSecondsLeft()));
                timerViews[i].setAlpha(1f);
            } else {
                timerViews[i].setText(String.valueOf(STEP_POINTS[i]));
                timerViews[i].setAlpha(0.35f);
            }
        }

        if (state.isBonusPhase()) {
            timerViews[state.getCurrentStepIndex()].setText(String.valueOf(state.getSecondsLeft()));
            timerViews[state.getCurrentStepIndex()].setAlpha(1f);
        }
    }
}