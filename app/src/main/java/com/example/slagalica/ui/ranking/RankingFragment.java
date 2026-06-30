package com.example.slagalica.ui.ranking;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RankingEntry;
import com.example.slagalica.viewmodel.ranking.RankingViewModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RankingFragment extends Fragment {

    private RankingViewModel viewModel;
    private LinearLayout listContainer;
    private TextView cycleLabel;
    private Button weeklyButton;
    private Button monthlyButton;
    private boolean weeklySelected = true;
    private List<RankingEntry> weeklyEntries = new ArrayList<>();
    private List<RankingEntry> monthlyEntries = new ArrayList<>();
    private String weeklyCycle = "";
    private String monthlyCycle = "";

    public RankingFragment() {
        super(R.layout.fragment_ranking);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RankingViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        listContainer = view.findViewById(R.id.ranking_list);
        cycleLabel = view.findViewById(R.id.ranking_cycle);
        weeklyButton = view.findViewById(R.id.ranking_weekly_button);
        monthlyButton = view.findViewById(R.id.ranking_monthly_button);

        view.findViewById(R.id.ranking_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        view.findViewById(R.id.ranking_refresh).setOnClickListener(v -> viewModel.manualRefresh());
        weeklyButton.setOnClickListener(v -> {
            weeklySelected = true;
            renderSelected();
        });
        monthlyButton.setOnClickListener(v -> {
            weeklySelected = false;
            renderSelected();
        });

        viewModel.getWeeklyEntries().observe(getViewLifecycleOwner(), entries -> {
            weeklyEntries = entries != null ? entries : new ArrayList<>();
            if (weeklySelected) {
                renderSelected();
            }
        });
        viewModel.getMonthlyEntries().observe(getViewLifecycleOwner(), entries -> {
            monthlyEntries = entries != null ? entries : new ArrayList<>();
            if (!weeklySelected) {
                renderSelected();
            }
        });
        viewModel.getWeeklyCycleLabel().observe(getViewLifecycleOwner(), label -> {
            weeklyCycle = label != null ? label : "";
            if (weeklySelected) {
                renderSelected();
            }
        });
        viewModel.getMonthlyCycleLabel().observe(getViewLifecycleOwner(), label -> {
            monthlyCycle = label != null ? label : "";
            if (!weeklySelected) {
                renderSelected();
            }
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getInfoMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                if (message.contains("token") || message.contains("zvezd")) {
                    showRewardDialog(message);
                } else {
                    Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
                }
            }
        });

        viewModel.start();
    }

    private void renderSelected() {
        weeklyButton.setEnabled(!weeklySelected);
        monthlyButton.setEnabled(weeklySelected);
        String label = weeklySelected ? weeklyCycle : monthlyCycle;
        cycleLabel.setText(getString(R.string.ranking_cycle, label));
        renderEntries(weeklySelected ? weeklyEntries : monthlyEntries);
    }

    private void renderEntries(@NonNull List<RankingEntry> entries) {
        listContainer.removeAllViews();
        if (entries.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.ranking_empty);
            empty.setTextSize(15);
            listContainer.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RankingEntry entry : entries) {
            View row = inflater.inflate(R.layout.item_ranking_entry, listContainer, false);
            TextView rank = row.findViewById(R.id.item_ranking_rank);
            TextView username = row.findViewById(R.id.item_ranking_username);
            TextView matches = row.findViewById(R.id.item_ranking_matches);
            TextView stars = row.findViewById(R.id.item_ranking_stars);

            rank.setText(String.format(Locale.getDefault(), "%d.", entry.getRank()));
            username.setText(entry.getUsername());
            matches.setText(getString(R.string.ranking_matches, entry.getMatchesPlayed()));
            stars.setText(getString(R.string.ranking_stars, entry.getStars()));

            listContainer.addView(row);
        }
    }

    private void showRewardDialog(@NonNull String message) {
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180);
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ranking_reward, null, false);
        TextView messageView = content.findViewById(R.id.reward_message);
        ImageView rewardIcon = content.findViewById(R.id.reward_icon);
        RewardConfettiView confetti = content.findViewById(R.id.reward_confetti);
        messageView.setText(message);
        rewardIcon.setImageResource(RewardIconHelper.iconForRank(RewardIconHelper.rankFromMessage(message)));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.ranking_reward_dialog_title)
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dismissed -> tone.release())
                .show();
        confetti.start();
        animateRewardIcon(rewardIcon);
    }

    private void animateRewardIcon(@NonNull View icon) {
        ObjectAnimator jump = ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -34f, 0f, -16f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.7f, 1.18f, 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.7f, 1.18f, 1f, 1.08f, 1f);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(icon, View.ROTATION, -10f, 10f, -6f, 6f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(jump, scaleX, scaleY, rotation);
        set.setDuration(950L);
        set.start();
    }
}
