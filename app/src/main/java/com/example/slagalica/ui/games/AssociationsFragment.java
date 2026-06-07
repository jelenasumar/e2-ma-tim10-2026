package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.model.associations.AssociationColumn;
import com.example.slagalica.model.associations.AssociationPuzzle;
import com.example.slagalica.model.associations.AssociationsGameState;
import com.example.slagalica.viewmodel.games.AssociationsViewModel;

public class AssociationsFragment extends Fragment {

    private final TextView[][] clueViews = new TextView[AssociationPuzzle.COLUMN_COUNT][AssociationColumn.CLUE_COUNT];
    private final EditText[] columnAnswerInputs = new EditText[AssociationPuzzle.COLUMN_COUNT];
    private final Button[] columnSubmitButtons = new Button[AssociationPuzzle.COLUMN_COUNT];
    private EditText finalAnswerInput;
    private Button finalSubmitButton;
    private Button finishTurnButton;
    private AssociationsViewModel viewModel;
    private int renderedRound = -1;

    public AssociationsFragment() {
        super(R.layout.fragment_associations);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(AssociationsViewModel.class);
        finalAnswerInput = view.findViewById(R.id.finalAnswerInput);
        finalSubmitButton = view.findViewById(R.id.submitButton);
        finishTurnButton = view.findViewById(R.id.nextButton);

        bindAssociationViews(view);
        setupGameHeader();
        setupActions();
        observeGameState();

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.startGame(createPlayerOneState(), createPlayerTwoState());
    }

    private void setupGameHeader() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.associationsGameHeader);
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

    private void bindAssociationViews(@NonNull View view) {
        int[][] clueIds = {
                {R.id.associationA1, R.id.associationA2, R.id.associationA3, R.id.associationA4},
                {R.id.associationB1, R.id.associationB2, R.id.associationB3, R.id.associationB4},
                {R.id.associationC1, R.id.associationC2, R.id.associationC3, R.id.associationC4},
                {R.id.associationD1, R.id.associationD2, R.id.associationD3, R.id.associationD4}
        };
        int[] inputIds = {
                R.id.associationAAnswerInput,
                R.id.associationBAnswerInput,
                R.id.associationCAnswerInput,
                R.id.associationDAnswerInput
        };
        int[] buttonIds = {
                R.id.associationASubmitButton,
                R.id.associationBSubmitButton,
                R.id.associationCSubmitButton,
                R.id.associationDSubmitButton
        };

        for (int column = 0; column < AssociationPuzzle.COLUMN_COUNT; column++) {
            for (int clue = 0; clue < AssociationColumn.CLUE_COUNT; clue++) {
                clueViews[column][clue] = view.findViewById(clueIds[column][clue]);
                int columnIndex = column;
                int clueIndex = clue;
                clueViews[column][clue].setOnClickListener(v ->
                        viewModel.openField(columnIndex, clueIndex)
                );
            }
            columnAnswerInputs[column] = view.findViewById(inputIds[column]);
            columnSubmitButtons[column] = view.findViewById(buttonIds[column]);
        }
    }

    private void setupActions() {
        for (int column = 0; column < AssociationPuzzle.COLUMN_COUNT; column++) {
            int columnIndex = column;
            columnSubmitButtons[column].setOnClickListener(v -> {
                String guess = columnAnswerInputs[columnIndex].getText().toString();
                columnAnswerInputs[columnIndex].setText("");
                viewModel.submitColumnGuess(
                        columnIndex,
                        guess
                );
            });
        }

        finalSubmitButton.setOnClickListener(v -> {
            String guess = finalAnswerInput.getText().toString();
            finalAnswerInput.setText("");
            viewModel.submitFinalGuess(guess);
        });
        finishTurnButton.setText(R.string.association_finish_turn);
        finishTurnButton.setOnClickListener(v -> viewModel.finishTurn());
    }

    private void renderGameState(@NonNull AssociationsGameState state) {
        if (state.getCurrentRound() != renderedRound) {
            renderedRound = state.getCurrentRound();
            clearAnswerInputs();
        }

        AssociationPuzzle puzzle = state.getPuzzle();
        boolean revealAll = state.isRoundOver() || state.isGameOver() || state.isFinalAnswerSolved();
        for (int column = 0; column < AssociationPuzzle.COLUMN_COUNT; column++) {
            renderColumn(state, puzzle, column, revealAll);
        }

        if (revealAll) {
            finalAnswerInput.setText(puzzle.getFinalAnswer());
        }
        finalAnswerInput.setEnabled(!revealAll);
        finalSubmitButton.setEnabled(!revealAll && state.isFieldOpenedThisTurn());
        finishTurnButton.setEnabled(!revealAll);
    }

    private void renderColumn(
            @NonNull AssociationsGameState state,
            @NonNull AssociationPuzzle puzzle,
            int column,
            boolean revealAll
    ) {
        boolean columnSolved = state.isColumnSolved(column);
        AssociationColumn associationColumn = puzzle.getColumn(column);
        for (int clue = 0; clue < AssociationColumn.CLUE_COUNT; clue++) {
            TextView clueView = clueViews[column][clue];
            if (revealAll || columnSolved || state.isFieldRevealed(column, clue)) {
                clueView.setText(associationColumn.getClue(clue));
            } else {
                clueView.setText(fieldLabel(column, clue));
            }
            clueView.setEnabled(!revealAll
                    && !columnSolved
                    && !state.isFieldOpenedThisTurn()
                    && !state.isFieldRevealed(column, clue));
        }

        if (revealAll || columnSolved) {
            columnAnswerInputs[column].setText(associationColumn.getAnswer());
        }
        columnAnswerInputs[column].setEnabled(!revealAll && !columnSolved);
        columnSubmitButtons[column].setEnabled(!revealAll
                && !columnSolved
                && state.isFieldOpenedThisTurn());
    }

    private void clearAnswerInputs() {
        for (EditText input : columnAnswerInputs) {
            if (input != null) {
                input.setText("");
            }
        }
        if (finalAnswerInput != null) {
            finalAnswerInput.setText("");
        }
    }

    @NonNull
    private static String fieldLabel(int columnIndex, int clueIndex) {
        char columnLetter = (char) ('A' + columnIndex);
        return columnLetter + String.valueOf(clueIndex + 1);
    }
}
