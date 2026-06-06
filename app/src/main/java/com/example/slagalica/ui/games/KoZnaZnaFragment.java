package com.example.slagalica.ui.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.KoZnaZnaUiState;
import com.example.slagalica.viewmodel.games.KoZnaZnaViewModel;

import java.util.List;

public class KoZnaZnaFragment extends Fragment {

    private KoZnaZnaViewModel viewModel;

    private TextView roundTimeView;
    private TextView questionCounterView;
    private TextView questionTimeView;
    private TextView playerOneScoreView;
    private TextView playerTwoScoreView;
    private TextView questionView;
    private TextView statusView;
    private RadioGroup answersGroup;
    private RadioButton answerA;
    private RadioButton answerB;
    private RadioButton answerC;
    private RadioButton answerD;
    private Button confirmBtn;
    private Button skipBtn;
    private boolean finishUiApplied;

    public KoZnaZnaFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        bindViews(view);
        viewModel = new ViewModelProvider(this).get(KoZnaZnaViewModel.class);

        answersGroup.setOnCheckedChangeListener((group, checkedId) -> {
            int index = indexForCheckedId(checkedId);
            if (index >= 0) {
                viewModel.selectAnswer(index);
            }
        });

        confirmBtn.setOnClickListener(v -> {
            if (finishUiApplied) {
                NavHostFragment.findNavController(this).navigateUp();
            } else {
                viewModel.submitAnswer();
            }
        });
        skipBtn.setOnClickListener(v -> viewModel.skipQuestion());

        viewModel.getUiState().observe(getViewLifecycleOwner(), this::renderState);
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty() && statusView != null) {
                statusView.setVisibility(View.VISIBLE);
                statusView.setText(error);
            }
        });

        Bundle args = getArguments();
        String matchId = args != null ? args.getString("matchId", "") : "";
        String myUid = args != null ? args.getString("myUid", "") : "";
        if (matchId.isEmpty() || myUid.isEmpty()) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(getString(R.string.kzz_missing_match_args));
            return;
        }
        viewModel.startOnlineMatch(matchId, myUid);
    }

    @Override
    public void onDestroyView() {
        viewModel.finishAndExit();
        super.onDestroyView();
    }

    private void bindViews(@NonNull View view) {
        roundTimeView = view.findViewById(R.id.kzzRoundTime);
        questionCounterView = view.findViewById(R.id.kzzQuestionCounter);
        questionTimeView = view.findViewById(R.id.kzzQuestionTime);
        playerOneScoreView = view.findViewById(R.id.kzzPlayerOneScore);
        playerTwoScoreView = view.findViewById(R.id.kzzPlayerTwoScore);
        questionView = view.findViewById(R.id.kzzQuestion);
        statusView = view.findViewById(R.id.kzzStatus);
        answersGroup = view.findViewById(R.id.kzzAnswersGroup);
        answerA = view.findViewById(R.id.kzzAnswerA);
        answerB = view.findViewById(R.id.kzzAnswerB);
        answerC = view.findViewById(R.id.kzzAnswerC);
        answerD = view.findViewById(R.id.kzzAnswerD);
        confirmBtn = view.findViewById(R.id.kzzConfirmButton);
        skipBtn = view.findViewById(R.id.kzzSkipButton);
    }

    private void renderState(@NonNull KoZnaZnaUiState state) {
        roundTimeView.setText(getString(R.string.kzz_round_time_value, state.getRoundSecondsLeft()));
        questionCounterView.setText(getString(
                R.string.kzz_question_counter_value,
                state.getQuestionNumber(),
                state.getTotalQuestions()
        ));
        questionTimeView.setText(getString(R.string.kzz_question_time_value, state.getQuestionSecondsLeft()));
        playerOneScoreView.setText(getString(
                R.string.kzz_player_score_named,
                state.getPlayerOneLabel(),
                state.getPlayerOneScore()
        ));
        playerTwoScoreView.setText(getString(
                R.string.kzz_player_score_named,
                state.getPlayerTwoLabel(),
                state.getPlayerTwoScore()
        ));
        questionView.setText(state.getQuestionText());

        List<String> options = state.getOptions();
        if (options.size() >= 4) {
            answerA.setText(options.get(0));
            answerB.setText(options.get(1));
            answerC.setText(options.get(2));
            answerD.setText(options.get(3));
        }

        if (state.getSelectedAnswerIndex() >= 0) {
            int checkedId = checkedIdForIndex(state.getSelectedAnswerIndex());
            if (answersGroup.getCheckedRadioButtonId() != checkedId) {
                answersGroup.check(checkedId);
            }
        } else if (!state.isAnswersEnabled()) {
            answersGroup.clearCheck();
        }

        boolean enabled = state.isAnswersEnabled();
        answerA.setEnabled(enabled);
        answerB.setEnabled(enabled);
        answerC.setEnabled(enabled);
        answerD.setEnabled(enabled);
        confirmBtn.setEnabled(enabled || state.isGameFinished());
        skipBtn.setEnabled(enabled);
        skipBtn.setVisibility(state.isGameFinished() ? View.GONE : View.VISIBLE);

        if (!state.getStatusMessage().isEmpty()) {
            statusView.setVisibility(View.VISIBLE);
            statusView.setText(state.getStatusMessage());
        } else if (!state.isWaitingForOpponent()) {
            statusView.setVisibility(View.GONE);
        }

        if (state.isGameFinished() && !finishUiApplied) {
            finishUiApplied = true;
            confirmBtn.setText(R.string.kzz_back_home);
        }
    }

    private int indexForCheckedId(int checkedId) {
        if (checkedId == R.id.kzzAnswerA) {
            return 0;
        }
        if (checkedId == R.id.kzzAnswerB) {
            return 1;
        }
        if (checkedId == R.id.kzzAnswerC) {
            return 2;
        }
        if (checkedId == R.id.kzzAnswerD) {
            return 3;
        }
        return KoZnaZnaUiState.NO_SELECTION;
    }

    private int checkedIdForIndex(int index) {
        switch (index) {
            case 0:
                return R.id.kzzAnswerA;
            case 1:
                return R.id.kzzAnswerB;
            case 2:
                return R.id.kzzAnswerC;
            case 3:
            default:
                return R.id.kzzAnswerD;
        }
    }
}
