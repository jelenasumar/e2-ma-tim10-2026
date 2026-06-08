package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.model.skocko.SkockoAttempt;
import com.example.slagalica.model.skocko.SkockoGameState;
import com.example.slagalica.model.skocko.SkockoSymbol;
import com.example.slagalica.ui.room.RoomGameFlow;
import com.example.slagalica.viewmodel.games.SkockoViewModel;

import java.util.List;

public class SkockoFragment extends Fragment {

    private static final int MAX_ATTEMPTS = 6;
    private static final int COMBINATION_SIZE = 4;

    private final TextView[][] attemptCells = new TextView[MAX_ATTEMPTS][COMBINATION_SIZE];
    private final TextView[] resultCells = new TextView[MAX_ATTEMPTS];
    private final TextView[] opponentAttemptCells = new TextView[COMBINATION_SIZE];
    private final TextView[] finalCombinationCells = new TextView[COMBINATION_SIZE];
    private Button submitButton;
    private SkockoViewModel viewModel;
    private String roomId = "";
    private boolean gameOverHandled;
    private boolean statsRecorded;

    public SkockoFragment() {
        super(R.layout.fragment_skocko);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(SkockoViewModel.class);
        submitButton = view.findViewById(R.id.submitButton);

        bindAttemptViews(view);
        bindOpponentAttemptViews(view);
        bindFinalCombinationViews(view);
        setupGameHeader();
        setupCombinationInput(view);
        observeGameState();

        submitButton.setOnClickListener(v -> viewModel.submitAttempt());
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            roomId = args.getString("roomId", "");
        }
        if (!roomId.isEmpty()) {
            viewModel.startRoomGame(roomId);
        } else {
            viewModel.startGame(createPlayerOneState(), createPlayerTwoState());
        }
    }

    private void setupGameHeader() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.skockoGameHeader);
        if (fragment instanceof GameHeaderFragment) {
            GameHeaderFragment gameHeader = (GameHeaderFragment) fragment;
            viewModel.getHeaderState().observe(getViewLifecycleOwner(), gameHeader::setHeaderState);
        }
    }

    private void observeGameState() {
        viewModel.getGameState().observe(getViewLifecycleOwner(), this::renderGameState);
    }

    @NonNull
    private GameHeaderPlayerState createPlayerOneState() {
        UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
        String username = profile.getUsername();
        if (username.trim().isEmpty()) {
            username = getString(R.string.guest_player);
        }
        return new GameHeaderPlayerState(username, 0, profile.getAvatarUri());
    }

    @NonNull
    private GameHeaderPlayerState createPlayerTwoState() {
        return new GameHeaderPlayerState(getString(R.string.opponent_player), 0, null);
    }

    private void setupCombinationInput(@NonNull View view) {
        setSymbolClick(view, R.id.skockoSymbolSkocko, SkockoSymbol.SKOCKO);
        setSymbolClick(view, R.id.skockoSymbolSquare, SkockoSymbol.SQUARE);
        setSymbolClick(view, R.id.skockoSymbolCircle, SkockoSymbol.CIRCLE);
        setSymbolClick(view, R.id.skockoSymbolHeart, SkockoSymbol.HEART);
        setSymbolClick(view, R.id.skockoSymbolTriangle, SkockoSymbol.TRIANGLE);
        setSymbolClick(view, R.id.skockoSymbolStar, SkockoSymbol.STAR);
    }

    private void setSymbolClick(
            @NonNull View view,
            int symbolId,
            @NonNull SkockoSymbol symbol
    ) {
        view.findViewById(symbolId).setOnClickListener(v -> viewModel.selectSymbol(symbol));
    }

    private void renderGameState(@NonNull SkockoGameState state) {
        clearAttemptRows();
        clearOpponentAttemptRow();
        renderSubmittedAttempts(state.getAttempts());
        if (!state.getBonusInput().isEmpty()) {
            renderBonusInput(state.getBonusInput());
        } else {
            renderCurrentInput(state.getAttempts().size(), state.getCurrentInput());
        }
        renderFinalCombination(state);
        submitButton.setEnabled(canSubmit(state));

        recordStatsIfNeeded(state);

        if (state.isGameOver() && !roomId.isEmpty() && !gameOverHandled) {
            gameOverHandled = true;
            RoomGameFlow.onGameFinished(this, roomId);
        }
    }

    private void recordStatsIfNeeded(@NonNull SkockoGameState state) {
        if (statsRecorded || !state.isGameOver()) {
            return;
        }
        statsRecorded = true;
        new UserProfileRepository(requireContext()).recordSkockoGame(
                viewModel.getCurrentUserScore(),
                viewModel.getStatsComboPercent()
        );
    }

    private void renderSubmittedAttempts(@NonNull List<SkockoAttempt> attempts) {
        for (int row = 0; row < attempts.size() && row < MAX_ATTEMPTS; row++) {
            SkockoAttempt attempt = attempts.get(row);
            for (int col = 0; col < attempt.getSymbols().size(); col++) {
                setCellSymbol(attemptCells[row][col], attempt.getSymbols().get(col));
            }
            setAttemptResult(resultCells[row], attempt);
        }
    }

    private void renderCurrentInput(int row, @NonNull List<SkockoSymbol> currentInput) {
        if (row >= MAX_ATTEMPTS) {
            return;
        }

        for (int col = 0; col < currentInput.size(); col++) {
            setCellSymbol(attemptCells[row][col], currentInput.get(col));
        }
    }

    private void renderBonusInput(@NonNull List<SkockoSymbol> bonusInput) {
        for (int col = 0; col < bonusInput.size(); col++) {
            setCellSymbol(opponentAttemptCells[col], bonusInput.get(col));
        }
    }

    private void renderFinalCombination(@NonNull SkockoGameState state) {
        boolean shouldReveal = state.isRoundOver() || state.isGameOver();
        for (int i = 0; i < COMBINATION_SIZE; i++) {
            if (shouldReveal && i < state.getSecretCombination().size()) {
                setCellSymbol(finalCombinationCells[i], state.getSecretCombination().get(i));
            } else {
                clearCell(finalCombinationCells[i]);
            }
        }
    }

    private boolean canSubmit(@NonNull SkockoGameState state) {
        if (state.isRoundOver() || state.isGameOver()) {
            return false;
        }

        if (state.isBonusPhase()) {
            return state.getBonusInput().size() == COMBINATION_SIZE;
        }

        return state.getCurrentInput().size() == COMBINATION_SIZE;
    }

    private void clearAttemptRows() {
        for (int row = 0; row < MAX_ATTEMPTS; row++) {
            for (int col = 0; col < COMBINATION_SIZE; col++) {
                clearCell(attemptCells[row][col]);
            }
            resultCells[row].setText(R.string.skocko_result_placeholder);
            resultCells[row].setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        }
    }

    private void clearOpponentAttemptRow() {
        for (TextView cell : opponentAttemptCells) {
            clearCell(cell);
        }
    }

    private void setCellSymbol(@NonNull TextView cell, @NonNull SkockoSymbol symbol) {
        cell.setText(symbol.getTextResId());
        if (symbol == SkockoSymbol.TRIANGLE) {
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
            cell.setPadding(0, 0, dpToPx(2), dpToPx(4));
        } else {
            cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
            cell.setPadding(0, 0, 0, 0);
        }
    }

    private void clearCell(@NonNull TextView cell) {
        cell.setText("");
        cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        cell.setPadding(0, 0, 0, 0);
    }

    private void setAttemptResult(
            @NonNull TextView resultCell,
            @NonNull SkockoAttempt attempt
    ) {
        resultCell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        int exactMatches = attempt.getResult().getExactMatches();
        int partialMatches = attempt.getResult().getPartialMatches();
        StringBuilder circles = new StringBuilder();
        for (int i = 0; i < COMBINATION_SIZE; i++) {
            if (i > 0) {
                circles.append(' ');
            }
            circles.append('●');
        }

        SpannableString result = new SpannableString(circles.toString());
        int red = ContextCompat.getColor(requireContext(), android.R.color.holo_red_dark);
        int yellow = ContextCompat.getColor(requireContext(), android.R.color.holo_orange_light);
        int empty = ContextCompat.getColor(requireContext(), android.R.color.darker_gray);

        for (int i = 0; i < COMBINATION_SIZE; i++) {
            int color;
            if (i < exactMatches) {
                color = red;
            } else if (i < exactMatches + partialMatches) {
                color = yellow;
            } else {
                color = empty;
            }
            result.setSpan(
                    new ForegroundColorSpan(color),
                    i * 2,
                    i * 2 + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            );
        }

        resultCell.setText(result);
    }

    private void bindAttemptViews(@NonNull View view) {
        int[][] cellIds = {
                {R.id.skockoAttempt1Cell1, R.id.skockoAttempt1Cell2, R.id.skockoAttempt1Cell3, R.id.skockoAttempt1Cell4},
                {R.id.skockoAttempt2Cell1, R.id.skockoAttempt2Cell2, R.id.skockoAttempt2Cell3, R.id.skockoAttempt2Cell4},
                {R.id.skockoAttempt3Cell1, R.id.skockoAttempt3Cell2, R.id.skockoAttempt3Cell3, R.id.skockoAttempt3Cell4},
                {R.id.skockoAttempt4Cell1, R.id.skockoAttempt4Cell2, R.id.skockoAttempt4Cell3, R.id.skockoAttempt4Cell4},
                {R.id.skockoAttempt5Cell1, R.id.skockoAttempt5Cell2, R.id.skockoAttempt5Cell3, R.id.skockoAttempt5Cell4},
                {R.id.skockoAttempt6Cell1, R.id.skockoAttempt6Cell2, R.id.skockoAttempt6Cell3, R.id.skockoAttempt6Cell4}
        };
        int[] resultIds = {
                R.id.skockoAttempt1Result,
                R.id.skockoAttempt2Result,
                R.id.skockoAttempt3Result,
                R.id.skockoAttempt4Result,
                R.id.skockoAttempt5Result,
                R.id.skockoAttempt6Result
        };

        for (int row = 0; row < MAX_ATTEMPTS; row++) {
            for (int col = 0; col < COMBINATION_SIZE; col++) {
                attemptCells[row][col] = view.findViewById(cellIds[row][col]);
                int attemptRow = row;
                int symbolIndex = col;
                attemptCells[row][col].setOnClickListener(v ->
                        viewModel.removeInputAt(attemptRow, symbolIndex)
                );
            }
            resultCells[row] = view.findViewById(resultIds[row]);
        }
    }

    private void bindOpponentAttemptViews(@NonNull View view) {
        opponentAttemptCells[0] = view.findViewById(R.id.skockoOpponentCell1);
        opponentAttemptCells[1] = view.findViewById(R.id.skockoOpponentCell2);
        opponentAttemptCells[2] = view.findViewById(R.id.skockoOpponentCell3);
        opponentAttemptCells[3] = view.findViewById(R.id.skockoOpponentCell4);

        for (int i = 0; i < opponentAttemptCells.length; i++) {
            int symbolIndex = i;
            opponentAttemptCells[i].setOnClickListener(v ->
                    viewModel.removeBonusInputAt(symbolIndex)
            );
        }
    }

    private void bindFinalCombinationViews(@NonNull View view) {
        finalCombinationCells[0] = view.findViewById(R.id.skockoFinalCell1);
        finalCombinationCells[1] = view.findViewById(R.id.skockoFinalCell2);
        finalCombinationCells[2] = view.findViewById(R.id.skockoFinalCell3);
        finalCombinationCells[3] = view.findViewById(R.id.skockoFinalCell4);
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
