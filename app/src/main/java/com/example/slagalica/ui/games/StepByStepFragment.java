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

public class StepByStepFragment extends Fragment {

    private static final int STEP_COUNT = 7;

    private StepByStepViewModel viewModel;

    private final TextView[] sentenceViews = new TextView[STEP_COUNT];
    private final TextView[] timerViews = new TextView[STEP_COUNT];

    private EditText answerInput;
    private Button submitButton;

    private TextView statusView;

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

        submitButton.setOnClickListener(v -> {
            String answer = answerInput.getText() != null
                    ? answerInput.getText().toString()
                    : "";

            viewModel.submitAnswer(answer);
            answerInput.setText("");
        });

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);

        viewModel.startGame();
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

    private void renderState(@NonNull KorakPoKorakUiState state) {
        renderSteps(state);
        renderTimers(state);

        if (state.getStatusMessage().isEmpty()) {
            statusView.setVisibility(View.GONE);
        } else {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(state.getStatusMessage());
        }

        answerInput.setEnabled(state.isCanSubmit());
        submitButton.setEnabled(state.isCanSubmit());

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
        }

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
                timerViews[i].setAlpha(0.35f);
            }
        }

        if (state.isBonusPhase()) {
            timerViews[state.getCurrentStepIndex()].setText(String.valueOf(state.getSecondsLeft()));
            timerViews[state.getCurrentStepIndex()].setAlpha(1f);
        }
    }
}