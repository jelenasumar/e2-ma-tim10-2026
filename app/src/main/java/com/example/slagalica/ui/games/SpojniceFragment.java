package com.example.slagalica.ui.games;

import android.graphics.Color;
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
    private final Spinner[] leftSpinners = new Spinner[ROW_COUNT];
    private final Spinner[] rightSpinners = new Spinner[ROW_COUNT];
    private final View[] rowContainers = new View[ROW_COUNT];
    private boolean suppressSpinnerCallbacks;

    public SpojniceFragment() {
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

        leftSpinners[0] = view.findViewById(R.id.spojniceLeft1);
        leftSpinners[1] = view.findViewById(R.id.spojniceLeft2);
        leftSpinners[2] = view.findViewById(R.id.spojniceLeft3);
        leftSpinners[3] = view.findViewById(R.id.spojniceLeft4);
        leftSpinners[4] = view.findViewById(R.id.spojniceLeft5);

        rightSpinners[0] = view.findViewById(R.id.spojniceRight1);
        rightSpinners[1] = view.findViewById(R.id.spojniceRight2);
        rightSpinners[2] = view.findViewById(R.id.spojniceRight3);
        rightSpinners[3] = view.findViewById(R.id.spojniceRight4);
        rightSpinners[4] = view.findViewById(R.id.spojniceRight5);

        View root = (View) leftSpinners[0].getParent();
        rowContainers[0] = root;
        rowContainers[1] = (View) leftSpinners[1].getParent();
        rowContainers[2] = (View) leftSpinners[2].getParent();
        rowContainers[3] = (View) leftSpinners[3].getParent();
        rowContainers[4] = (View) leftSpinners[4].getParent();
    }

    private void renderState(@NonNull SpojniceUiState state) {
        roundView.setText(getString(R.string.spojnice_round_value, state.getCurrentRound(), state.getTotalRounds()));
        timeView.setText(getString(R.string.spojnice_time_value, state.getSecondsLeft()));
        scoreP1View.setText(getString(R.string.spojnice_score_named, state.getPlayerOneLabel(), state.getPlayerOneScore()));
        scoreP2View.setText(getString(R.string.spojnice_score_named, state.getPlayerTwoLabel(), state.getPlayerTwoScore()));
        criterionView.setText(state.getCriterion());
        statusView.setText(state.getStatusMessage());
        statusView.setVisibility(state.getStatusMessage().isEmpty() ? View.GONE : View.VISIBLE);

        submitBtn.setEnabled(state.isInputsEnabled());
        submitBtn.setVisibility(state.isGameOver() ? View.GONE : View.VISIBLE);
        backBtn.setVisibility(state.isGameOver() ? View.VISIBLE : View.GONE);

        List<String> leftTerms = state.getLeftTerms();
        List<String> rightTerms = state.getRightTerms();
        suppressSpinnerCallbacks = true;
        for (int i = 0; i < ROW_COUNT; i++) {
            String leftLabel = i < leftTerms.size() ? leftTerms.get(i) : "";
            setSpinnerItems(leftSpinners[i], List.of(leftLabel), false);

            boolean rowEnabled = state.isRowSelectable(i);
            rightSpinners[i].setEnabled(rowEnabled);
            leftSpinners[i].setEnabled(false);

            if (rowEnabled && !rightTerms.isEmpty()) {
                setSpinnerItems(rightSpinners[i], rightTerms, true);
            } else if (state.isRowConnected(i)) {
                setSpinnerItems(rightSpinners[i], List.of("✓ Povezano"), false);
            } else {
                setSpinnerItems(rightSpinners[i], List.of("—"), false);
            }

            int highlightColor = Color.TRANSPARENT;
            if (state.isRowConnected(i)) {
                highlightColor = Color.parseColor("#E8F5E9");
            } else if (rowEnabled) {
                highlightColor = ContextCompat.getColor(requireContext(), R.color.spojnice_active_row);
            }
            rowContainers[i].setBackgroundColor(highlightColor);
        }
        suppressSpinnerCallbacks = false;
    }

    private void setSpinnerItems(@NonNull Spinner spinner, @NonNull List<String> items, boolean enabled) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_spinner_item,
                new ArrayList<>(items)
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setEnabled(enabled);
    }
}
