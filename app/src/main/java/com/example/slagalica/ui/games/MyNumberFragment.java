package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.model.mynumber.MyNumberUiState;
import com.example.slagalica.ui.room.RoomGameFlow;
import com.example.slagalica.viewmodel.games.MyNumberViewModel;

import java.util.List;
import java.util.Locale;

public class MyNumberFragment extends Fragment {

    private static final int NUMBER_COUNT = 6;

    private MyNumberViewModel viewModel;

    private GameHeaderFragment gameHeader;
    private GameHeaderPlayerState playerOneHeaderState;
    private GameHeaderPlayerState playerTwoHeaderState;

    private TextView statusView;
    private TextView targetNumberView;
    private Button stopTargetButton;
    private Button stopNumbersButton;
    private EditText expressionInput;
    private Button submitButton;

    private final TextView[] numberViews = new TextView[NUMBER_COUNT];

    private String roomId = "";
    private boolean gameOverHandled;

    public MyNumberFragment() {
        // Required empty public constructor.
    }

    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_my_number, container, false);
    }

    @Override
    public void onViewCreated(
            @NonNull View view,
            @Nullable Bundle savedInstanceState
    ) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(MyNumberViewModel.class);

        bindViews(view);
        setupGameHeader();

        stopTargetButton.setOnClickListener(v -> viewModel.stopTarget());
        stopNumbersButton.setOnClickListener(v -> viewModel.stopNumbers());

        submitButton.setOnClickListener(v -> submitExpression());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        Bundle args = getArguments();
        roomId = args != null ? args.getString("roomId", "") : "";

        if (!roomId.isEmpty()) {
            viewModel.startRoomGame(roomId);
        } else {
            showUnavailableState();
        }
    }

    private void bindViews(@NonNull View view) {
        statusView = view.findViewById(R.id.timerText);
        targetNumberView = view.findViewById(R.id.targetNumber);
        stopTargetButton = view.findViewById(R.id.stopTargetButton);
        stopNumbersButton = view.findViewById(R.id.stopNumbersButton);
        expressionInput = view.findViewById(R.id.expressionInput);
        submitButton = view.findViewById(R.id.submitButton);

        numberViews[0] = view.findViewById(R.id.number1);
        numberViews[1] = view.findViewById(R.id.number2);
        numberViews[2] = view.findViewById(R.id.number3);
        numberViews[3] = view.findViewById(R.id.number4);
        numberViews[4] = view.findViewById(R.id.number5);
        numberViews[5] = view.findViewById(R.id.number6);
    }

    private void setupGameHeader() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.mojBrojGameHeader);
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

    private void renderState(@NonNull MyNumberUiState state) {
        updateGameHeader(state);
        handleOnlineGameOver(state);

        targetNumberView.setText(state.isTargetRevealed()
                ? String.valueOf(state.getTargetNumber())
                : "???");

        renderNumbers(state.getNumbers(), state.isNumbersRevealed());

        statusView.setText(state.getStatusMessage());

        stopTargetButton.setEnabled(state.isCanStopTarget());
        stopNumbersButton.setEnabled(state.isCanStopNumbers());

        expressionInput.setEnabled(state.isCanSubmitExpression());
        submitButton.setEnabled(state.isCanSubmitExpression());

        if (state.isGameOver()) {
            submitButton.setText(R.string.back);
            submitButton.setEnabled(true);
            submitButton.setOnClickListener(v ->
                    NavHostFragment.findNavController(this).navigateUp()
            );
        } else {
            submitButton.setText(R.string.submit);
            submitButton.setOnClickListener(v -> submitExpression());
        }
    }

    private void updateGameHeader(@NonNull MyNumberUiState state) {
        if (gameHeader == null || playerOneHeaderState == null || playerTwoHeaderState == null) {
            return;
        }

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
                state.isGameOver() ? 0 : state.getActivePlayerNumber()
        ));
    }

    private void renderNumbers(
            @NonNull List<Integer> numbers,
            boolean numbersRevealed
    ) {
        for (int i = 0; i < NUMBER_COUNT; i++) {
            if (numbersRevealed && i < numbers.size()) {
                numberViews[i].setText(String.valueOf(numbers.get(i)));
                numberViews[i].setAlpha(1f);
            } else {
                numberViews[i].setText("-");
                numberViews[i].setAlpha(0.45f);
            }
        }
    }

    private void submitExpression() {
        String expression = expressionInput.getText() != null
                ? expressionInput.getText().toString()
                : "";

        viewModel.submitExpression(expression);
        expressionInput.setText("");
    }

    private void handleOnlineGameOver(@NonNull MyNumberUiState state) {
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

    private void showUnavailableState() {
        targetNumberView.setText("???");
        renderNumbers(java.util.Collections.emptyList(), false);

        statusView.setText("Moj broj se pokrece iz online partije.");

        stopTargetButton.setEnabled(false);
        stopNumbersButton.setEnabled(false);
        expressionInput.setEnabled(false);

        submitButton.setEnabled(true);
        submitButton.setText(R.string.back);
        submitButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }
}