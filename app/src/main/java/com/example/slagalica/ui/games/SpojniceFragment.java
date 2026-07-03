package com.example.slagalica.ui.games;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.SpojniceRoomRepository;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.model.spojnice.SpojniceUiState;
import com.example.slagalica.ui.room.RoomGameFlow;
import com.example.slagalica.viewmodel.games.SpojniceViewModel;
import com.example.slagalica.ui.challenge.ChallengeGameFlow;

import java.util.ArrayList;
import java.util.List;

public class SpojniceFragment extends Fragment {

    private static final int ROW_COUNT = 5;

    private SpojniceViewModel viewModel;
    private TextView criterionView;
    private String roomId = "";
    private TextView statusView;
    private Button submitBtn;
    private Button backBtn;
    private final TextView[] leftLabels = new TextView[ROW_COUNT];
    private final Spinner[] rightSpinners = new Spinner[ROW_COUNT];
    private final View[] rowContainers = new View[ROW_COUNT];
    private final List<String>[] cachedRightItems = new List[ROW_COUNT];
    private boolean suppressSpinnerCallbacks;
    private String lastRenderedPhase = "";
    private String lastRenderedBoardKey = "";
    private int lastRenderedRound = 0;
    private boolean gameOverHandled;
    private Bundle challengeArgs;
    private boolean challengeMode;

    public SpojniceFragment() {
        for (int i = 0; i < ROW_COUNT; i++) {
            cachedRightItems[i] = new ArrayList<>();
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        setupGameHeader();
        viewModel = new ViewModelProvider(this).get(SpojniceViewModel.class);

        submitBtn.setOnClickListener(v -> viewModel.submitPair());
        backBtn.setOnClickListener(v -> RoomGameFlow.confirmAbandonOrNavigateUp(this, roomId));

        for (int i = 0; i < ROW_COUNT; i++) {
            int rowIndex = i;
            leftLabels[i].setOnClickListener(v -> viewModel.selectFollowupRow(rowIndex));
            rightSpinners[i].setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(AdapterView<?> parent, View itemView, int position, long id) {
                    if (suppressSpinnerCallbacks) {
                        return;
                    }
                    viewModel.selectRightOption(rowIndex, position);
                }

                @Override
                public void onNothingSelected(AdapterView<?> parent) {
                }
            });
        }

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });

        Bundle args = getArguments();
        if (args != null) {
            roomId = args.getString("roomId", "");
            if (ChallengeGameFlow.isChallengeGame(args)) {
                challengeArgs = new Bundle(args);
                challengeMode = true;
            }
        }
        if (!roomId.isEmpty()) {
            viewModel.startRoomGame(roomId);
            RoomGameFlow.registerRoomBackHandler(this, roomId);
        } else if (challengeMode) {
            viewModel.startChallengeGame();
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(getString(R.string.spojnice_waiting_room));
            submitBtn.setEnabled(false);
        }
    }

    private void bindViews(@NonNull View view) {
        criterionView = view.findViewById(R.id.spojniceCriterion);
        statusView = view.findViewById(R.id.spojniceStatus);
        submitBtn = view.findViewById(R.id.spojniceSubmitButton);
        backBtn = view.findViewById(R.id.spojniceNextRoundButton);

        leftLabels[0] = view.findViewById(R.id.spojniceLeft1);
        leftLabels[1] = view.findViewById(R.id.spojniceLeft2);
        leftLabels[2] = view.findViewById(R.id.spojniceLeft3);
        leftLabels[3] = view.findViewById(R.id.spojniceLeft4);
        leftLabels[4] = view.findViewById(R.id.spojniceLeft5);

        rightSpinners[0] = view.findViewById(R.id.spojniceRight1);
        rightSpinners[1] = view.findViewById(R.id.spojniceRight2);
        rightSpinners[2] = view.findViewById(R.id.spojniceRight3);
        rightSpinners[3] = view.findViewById(R.id.spojniceRight4);
        rightSpinners[4] = view.findViewById(R.id.spojniceRight5);

        rowContainers[0] = (View) leftLabels[0].getParent();
        rowContainers[1] = (View) leftLabels[1].getParent();
        rowContainers[2] = (View) leftLabels[2].getParent();
        rowContainers[3] = (View) leftLabels[3].getParent();
        rowContainers[4] = (View) leftLabels[4].getParent();
    }

    private void renderState(@NonNull SpojniceUiState state) {
        if (!state.getPhase().equals(lastRenderedPhase)) {
            clearRightSpinnerCache();
            lastRenderedPhase = state.getPhase();
        }
        if (state.getCurrentRound() != lastRenderedRound) {
            clearRightSpinnerCache();
            lastRenderedBoardKey = "";
            lastRenderedRound = state.getCurrentRound();
        }

        updateGameHeader(state);
        criterionView.setText(state.getCriterion());
        statusView.setText(state.getStatusMessage());
        statusView.setVisibility(state.getStatusMessage().isEmpty() ? View.GONE : View.VISIBLE);

        submitBtn.setEnabled(state.isCanSubmit());
        submitBtn.setVisibility(state.isGameOver() ? View.GONE : View.VISIBLE);
        if (state.isGameOver()) {
            backBtn.setVisibility(View.GONE);

            if (!gameOverHandled) {
                gameOverHandled = true;

                if (challengeMode && challengeArgs != null) {
                    ChallengeGameFlow.onGameFinished(
                            this,
                            challengeArgs,
                            viewModel.getCurrentUserGameScore()
                    );
                } else if (!roomId.isEmpty()) {
                    RoomGameFlow.onGameFinished(
                            this,
                            roomId,
                            state.getPlayerOneScore(),
                            state.getPlayerTwoScore()
                    );
                } else {
                    backBtn.setVisibility(View.VISIBLE);
                }
            }
        } else {
            backBtn.setVisibility(View.GONE);
        }

        List<String> leftTerms = state.getLeftTerms();
        List<String> spinnerOptions = buildSpinnerOptions(state);
        boolean followupPhase = SpojniceRoomRepository.PHASE_FOLLOWUP.equals(state.getPhase());
        String boardKey = boardRenderKey(state, leftTerms, spinnerOptions, followupPhase);
        if (boardKey.equals(lastRenderedBoardKey)) {
            return;
        }
        lastRenderedBoardKey = boardKey;

        suppressSpinnerCallbacks = true;
        try {
            for (int i = 0; i < ROW_COUNT; i++) {
                String leftLabel = i < leftTerms.size() ? leftTerms.get(i) : "";
                bindLeftLabel(i, leftLabel, state, followupPhase);

                if (state.isRowConnected(i)) {
                    configureRightSpinner(i, List.of("✓ Povezano"), false, 0);
                } else if (state.isFollowupRowLocked(i)) {
                    configureRightSpinner(i, List.of(getString(R.string.spojnice_locked_row)), false, 0);
                } else if (state.isFollowupRightSpinnerEnabled(i)) {
                    int selection = state.getSelectedRightIndex();
                    configureRightSpinner(i, spinnerOptions, true, selection);
                } else if (state.isRowSelectable(i)) {
                    int selection = state.getSelectedRightIndex();
                    configureRightSpinner(i, spinnerOptions, true, selection);
                } else if (followupPhase && state.isFollowupLeftSelectable(i)) {
                    configureRightSpinner(i, List.of(getString(R.string.spojnice_pick_left_first)), false, 0);
                } else {
                    configureRightSpinner(i, List.of("—"), false, 0);
                }

                rowContainers[i].setBackgroundColor(resolveRowColor(state, i, followupPhase));
            }
        } finally {
            suppressSpinnerCallbacks = false;
        }
    }

    @NonNull
    private String boardRenderKey(
            @NonNull SpojniceUiState state,
            @NonNull List<String> leftTerms,
            @NonNull List<String> spinnerOptions,
            boolean followupPhase
    ) {
        return state.getPhase()
                + "|" + followupPhase
                + "|" + state.isMyTurn()
                + "|" + state.isInputsEnabled()
                + "|" + state.getCurrentLeftIndex()
                + "|" + state.getSelectedRow()
                + "|" + state.getSelectedRightIndex()
                + "|" + leftTerms
                + "|" + spinnerOptions
                + "|" + state.getConnectedLeftIndices()
                + "|" + state.getUsedRightIndices()
                + "|" + state.getFollowupLockedLeftIndices();
    }

    @NonNull
    private List<String> buildSpinnerOptions(@NonNull SpojniceUiState state) {
        List<String> options = new ArrayList<>();
        options.add(getString(R.string.spojnice_choose_answer));
        options.addAll(state.getAvailableRightTerms());
        return options;
    }

    private void bindLeftLabel(
            int rowIndex,
            @NonNull String leftLabel,
            @NonNull SpojniceUiState state,
            boolean followupPhase
    ) {
        TextView label = leftLabels[rowIndex];
        if (state.isRowConnected(rowIndex)) {
            label.setText("✓ " + leftLabel);
            label.setTypeface(Typeface.DEFAULT);
            label.setClickable(false);
            label.setAlpha(1f);
            return;
        }

        if (followupPhase && state.isFollowupRowLocked(rowIndex)) {
            label.setText("✗ " + leftLabel);
            label.setTypeface(Typeface.DEFAULT);
            label.setClickable(false);
            label.setAlpha(0.55f);
            return;
        }

        if (followupPhase && state.isFollowupLeftSelectable(rowIndex)) {
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setClickable(true);
            label.setAlpha(1f);
            label.setText(state.isFollowupSelected(rowIndex) ? "▶ " + leftLabel : leftLabel);
            return;
        }

        if (state.isActiveRow(rowIndex)) {
            label.setText("▶ " + leftLabel);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setClickable(false);
            label.setAlpha(1f);
            return;
        }

        label.setText(leftLabel);
        label.setTypeface(Typeface.DEFAULT);
        label.setClickable(false);
        label.setAlpha(0.85f);
    }

    private int resolveRowColor(@NonNull SpojniceUiState state, int rowIndex, boolean followupPhase) {
        if (state.isRowConnected(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_connected_row);
        }
        if (followupPhase && state.isFollowupRowLocked(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_locked_row);
        }
        if (followupPhase && state.isFollowupSelected(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_followup_selected);
        }
        if (state.isActiveRow(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_active_row);
        }
        return ContextCompat.getColor(requireContext(), R.color.spojnice_inactive_row);
    }

    private void configureRightSpinner(
            int rowIndex,
            @NonNull List<String> items,
            boolean enabled,
            int selectionIndex
    ) {
        bindRightSpinner(rowIndex, items, enabled, selectionIndex);
        Spinner spinner = rightSpinners[rowIndex];
        spinner.setEnabled(enabled);
        spinner.setClickable(enabled);
        spinner.setAlpha(enabled ? 1f : 0.75f);
    }

    private void bindRightSpinner(
            int rowIndex,
            @NonNull List<String> items,
            boolean enabled,
            int selectionIndex
    ) {
        if (sameItems(cachedRightItems[rowIndex], items)) {
            applySelection(rightSpinners[rowIndex], selectionIndex, items.size());
            return;
        }
        cachedRightItems[rowIndex] = new ArrayList<>(items);
        setSpinnerAdapter(rightSpinners[rowIndex], items, enabled, selectionIndex);
    }

    private void setSpinnerAdapter(
            @NonNull Spinner spinner,
            @NonNull List<String> items,
            boolean enabled,
            int selectionIndex
    ) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new ArrayList<>(items)
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(enabled);
        applySelection(spinner, selectionIndex, items.size());
    }

    private void applySelection(@NonNull Spinner spinner, int selectionIndex, int itemCount) {
        if (itemCount <= 0) {
            return;
        }
        int safeIndex = selectionIndex < 0 ? 0 : Math.min(selectionIndex, itemCount - 1);
        if (spinner.getSelectedItemPosition() != safeIndex) {
            suppressSpinnerCallbacks = true;
            spinner.setSelection(safeIndex, false);
            spinner.post(() -> suppressSpinnerCallbacks = false);
        }
    }

    private void clearRightSpinnerCache() {
        for (int i = 0; i < ROW_COUNT; i++) {
            cachedRightItems[i] = new ArrayList<>();
        }
    }

    private static boolean sameItems(@NonNull List<String> left, @NonNull List<String> right) {
        return left.size() == right.size() && left.equals(right);
    }

    private void setupGameHeader() {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.spojniceGameHeader);
        if (fragment instanceof GameHeaderFragment) {
            GameHeaderFragment gameHeader = (GameHeaderFragment) fragment;
            UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
            String username = profile.getUsername();
            if (username.trim().isEmpty()) {
                username = getString(R.string.guest_player);
            }
            gameHeader.setHeaderState(new GameHeaderState(
                    getString(R.string.game_header_round_default),
                    getString(R.string.game_header_time_default),
                    new GameHeaderPlayerState(username, 0, profile.getAvatarUri()),
                    new GameHeaderPlayerState(getString(R.string.opponent_player), 0, null)
            ));
        }
    }

    private void updateGameHeader(@NonNull SpojniceUiState state) {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.spojniceGameHeader);
        if (!(fragment instanceof GameHeaderFragment)) {
            return;
        }
        GameHeaderFragment gameHeader = (GameHeaderFragment) fragment;
        gameHeader.setHeaderState(new GameHeaderState(
                getString(R.string.spojnice_round_value, state.getCurrentRound(), state.getTotalRounds()),
                getString(R.string.spojnice_time_value, state.getSecondsLeft()),
                new GameHeaderPlayerState(
                        state.getPlayerOneLabel(),
                        state.getPlayerOneScore(),
                        state.getPlayerOneAvatarUri()
                ),
                new GameHeaderPlayerState(
                        state.getPlayerTwoLabel(),
                        state.getPlayerTwoScore(),
                        state.getPlayerTwoAvatarUri()
                ),
                state.getActivePlayerNumber()
        ));
    }
}
