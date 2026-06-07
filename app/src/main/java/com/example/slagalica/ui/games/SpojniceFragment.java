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
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.SpojniceRoomRepository;
import com.example.slagalica.model.spojnice.SpojniceUiState;
import com.example.slagalica.viewmodel.games.SpojniceViewModel;

import java.util.ArrayList;
import java.util.List;

public class SpojniceFragment extends Fragment {

    private static final int ROW_COUNT = 5;

    private SpojniceViewModel viewModel;
    private TextView roundView;
    private TextView timeView;
    private TextView scoreP1View;
    private TextView scoreP2View;
    private TextView criterionView;
    private TextView statusView;
    private Button submitBtn;
    private Button backBtn;
    private final TextView[] leftLabels = new TextView[ROW_COUNT];
    private final Spinner[] rightSpinners = new Spinner[ROW_COUNT];
    private final View[] rowContainers = new View[ROW_COUNT];
    private final List<String>[] cachedRightItems = new List[ROW_COUNT];
    private boolean suppressSpinnerCallbacks;

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
        viewModel = new ViewModelProvider(this).get(SpojniceViewModel.class);

        submitBtn.setOnClickListener(v -> viewModel.submitPair());
        backBtn.setOnClickListener(v -> NavHostFragment.findNavController(this).navigateUp());

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

        String roomId = "";
        Bundle args = getArguments();
        if (args != null) {
            roomId = args.getString("roomId", "");
        }
        if (!roomId.isEmpty()) {
            viewModel.startRoomGame(roomId);
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(getString(R.string.spojnice_waiting_room));
            submitBtn.setEnabled(false);
        }
    }

    private void bindViews(@NonNull View view) {
        roundView = view.findViewById(R.id.spojniceRound);
        timeView = view.findViewById(R.id.spojniceTime);
        scoreP1View = view.findViewById(R.id.spojniceScoreP1);
        scoreP2View = view.findViewById(R.id.spojniceScoreP2);
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
        roundView.setText(getString(R.string.spojnice_round_value, state.getCurrentRound(), state.getTotalRounds()));
        timeView.setText(getString(R.string.spojnice_time_value, state.getSecondsLeft()));
        scoreP1View.setText(getString(R.string.spojnice_score_named, state.getPlayerOneLabel(), state.getPlayerOneScore()));
        scoreP2View.setText(getString(R.string.spojnice_score_named, state.getPlayerTwoLabel(), state.getPlayerTwoScore()));
        criterionView.setText(state.getCriterion());
        statusView.setText(state.getStatusMessage());
        statusView.setVisibility(state.getStatusMessage().isEmpty() ? View.GONE : View.VISIBLE);

        submitBtn.setEnabled(state.isCanSubmit());
        submitBtn.setVisibility(state.isGameOver() ? View.GONE : View.VISIBLE);
        backBtn.setVisibility(state.isGameOver() ? View.VISIBLE : View.GONE);

        List<String> leftTerms = state.getLeftTerms();
        List<String> availableRightTerms = state.getAvailableRightTerms();
        boolean followupPhase = SpojniceRoomRepository.PHASE_FOLLOWUP.equals(state.getPhase());

        suppressSpinnerCallbacks = true;
        try {
            for (int i = 0; i < ROW_COUNT; i++) {
                String leftLabel = i < leftTerms.size() ? leftTerms.get(i) : "";
                bindLeftLabel(i, leftLabel, state, followupPhase);

                boolean rowRightEnabled = state.isRowSelectable(i);
                rightSpinners[i].setEnabled(rowRightEnabled);

                if (state.isRowConnected(i)) {
                    bindRightSpinner(i, List.of("✓ Povezano"), false, 0);
                } else if (rowRightEnabled && !availableRightTerms.isEmpty()) {
                    int selection = (i == state.getSelectedRow())
                            ? state.getSelectedRightIndex()
                            : SpojniceUiState.NO_SELECTION;
                    bindRightSpinner(i, availableRightTerms, true, selection);
                } else if (followupPhase && state.isFollowupPending(i)) {
                    bindRightSpinner(i, List.of(getString(R.string.spojnice_pick_left_first)), false, 0);
                } else {
                    bindRightSpinner(i, List.of("—"), false, 0);
                }

                rowContainers[i].setBackgroundColor(resolveRowColor(state, i, followupPhase));
            }
        } finally {
            suppressSpinnerCallbacks = false;
        }
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

        if (followupPhase && state.isFollowupPending(rowIndex)) {
            label.setText(leftLabel);
            label.setTypeface(Typeface.DEFAULT_BOLD);
            label.setClickable(state.isMyTurn());
            label.setAlpha(1f);
            if (state.isFollowupSelected(rowIndex)) {
                label.setText("▶ " + leftLabel);
            }
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
        if (followupPhase && state.isFollowupSelected(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_followup_selected);
        }
        if (followupPhase && state.isFollowupPending(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_followup_row);
        }
        if (state.isActiveRow(rowIndex)) {
            return ContextCompat.getColor(requireContext(), R.color.spojnice_active_row);
        }
        return ContextCompat.getColor(requireContext(), R.color.spojnice_inactive_row);
    }

    private void bindRightSpinner(
            int rowIndex,
            @NonNull List<String> items,
            boolean enabled,
            int selectionIndex
    ) {
        if (sameItems(cachedRightItems[rowIndex], items)) {
            rightSpinners[rowIndex].setEnabled(enabled);
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
        if (selectionIndex < 0 || itemCount <= 0) {
            return;
        }
        int safeIndex = Math.min(selectionIndex, itemCount - 1);
        if (spinner.getSelectedItemPosition() != safeIndex) {
            spinner.setSelection(safeIndex);
        }
    }

    private static boolean sameItems(@NonNull List<String> left, @NonNull List<String> right) {
        return left.size() == right.size() && left.equals(right);
    }
}
