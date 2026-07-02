package com.example.slagalica.ui.region;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.example.slagalica.model.Challenge;
import com.example.slagalica.model.ChallengeParticipant;
import com.example.slagalica.viewmodel.challenge.ChallengeViewModel;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RegionStats;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.utils.RegionCycleTestConfig;
import com.example.slagalica.viewmodel.region.RegionMapViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;

public class RegionMapFragment extends Fragment {

    private RegionMapViewModel viewModel;
    private SerbiaRegionMapView mapView;
    private TextView currentRegionValue;
    private MaterialCardView currentRegionCard;
    private ChallengeViewModel challengeViewModel;
    private LinearLayout challengesContainer;
    private LinearLayout participantsContainer;
    private TextView challengesEmpty;
    private TextView challengeResultsTitle;
    private List<ChallengeParticipant> latestParticipants = new ArrayList<>();

    public RegionMapFragment() {
        super(R.layout.fragment_region_map);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RegionMapViewModel.class);
        challengeViewModel = new ViewModelProvider(this).get(ChallengeViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.region_map_view);
        mapView.onCreate(savedInstanceState);
        currentRegionValue = view.findViewById(R.id.region_map_current_value);
        currentRegionCard = view.findViewById(R.id.region_map_current_card);
        ImageView currentRegionIcon = view.findViewById(R.id.region_map_current_icon);

        view.findViewById(R.id.region_map_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        MaterialButton leaderboardButton = view.findViewById(R.id.region_map_leaderboard);
        leaderboardButton.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_regionMap_to_leaderboard)
        );
        challengesContainer = view.findViewById(R.id.region_map_challenges_container);
        participantsContainer = view.findViewById(R.id.region_map_challenge_participants_container);
        challengesEmpty = view.findViewById(R.id.region_map_challenges_empty);
        challengeResultsTitle = view.findViewById(R.id.region_map_challenge_results_title);

        MaterialButton createChallengeButton = view.findViewById(R.id.region_map_create_challenge);
        createChallengeButton.setOnClickListener(v -> showCreateChallengeDialog());

        challengeViewModel.getChallenges().observe(getViewLifecycleOwner(), this::renderChallenges);
        challengeViewModel.getParticipants().observe(getViewLifecycleOwner(), participants -> {
            latestParticipants = participants != null ? participants : new ArrayList<>();
            renderParticipants(latestParticipants);
        });
        challengeViewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty() && getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        challengeViewModel.getSuccessMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty() && getContext() != null) {
                Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        challengeViewModel.start();

        mapView.setRegionClickListener(region -> viewModel.onRegionSelected(region));

        viewModel.getMarkers().observe(getViewLifecycleOwner(), markers -> {
            if (markers != null) {
                mapView.setMarkers(markers);
            }
        });

        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                bindCurrentRegion(profile, currentRegionIcon);
            }
        });

        viewModel.getSelectedRegionStats().observe(getViewLifecycleOwner(), stats -> {
            if (stats != null) {
                showRegionStatsDialog(stats);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty() && getContext() != null) {
                android.widget.Toast.makeText(getContext(), error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.load();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
        super.onPause();
    }

    @Override
    public void onDestroyView() {
        if (mapView != null) {
            mapView.onDestroy();
        }
        super.onDestroyView();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        if (mapView != null) {
            mapView.onLowMemory();
        }
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (mapView != null) {
            mapView.onSaveInstanceState(outState);
        }
    }

    private void bindCurrentRegion(@NonNull UserProfile profile, @NonNull ImageView iconView) {
        SerbiaRegion region = SerbiaRegion.resolve(profile.getRegionKey(), profile.getRegion());
        if (region == null) {
            currentRegionValue.setText(profile.getRegion());
            return;
        }
        currentRegionValue.setText(region.getDisplayName(requireContext()));
        iconView.setImageResource(region.getIconRes());
        mapView.setSelectedRegion(region);
    }

    private void showRegionStatsDialog(@NonNull RegionStats stats) {
        View content = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_region_stats, null, false);

        ImageView icon = content.findViewById(R.id.region_stats_icon);
        TextView title = content.findViewById(R.id.region_stats_title);
        TextView previousCycle = content.findViewById(R.id.region_stats_previous_cycle);
        TextView first = content.findViewById(R.id.region_stats_first);
        TextView second = content.findViewById(R.id.region_stats_second);
        TextView third = content.findViewById(R.id.region_stats_third);
        TextView active = content.findViewById(R.id.region_stats_active);
        TextView registered = content.findViewById(R.id.region_stats_registered);

        icon.setImageResource(stats.getIconRes());
        title.setText(stats.getRegionName());
        if (stats.getPreviousCycleRank() > 0) {
            previousCycle.setText(getString(
                    R.string.region_stats_previous_cycle_rank,
                    RegionCycleTestConfig.previousCycleLabel(),
                    stats.getPreviousCycleRank()
            ));
        } else {
            previousCycle.setText(getString(
                    R.string.region_stats_previous_cycle_none,
                    RegionCycleTestConfig.previousCycleLabel()
            ));
        }
        first.setText(getString(R.string.region_stats_first_value, stats.getPodiumFirst()));
        second.setText(getString(R.string.region_stats_second_value, stats.getPodiumSecond()));
        third.setText(getString(R.string.region_stats_third_value, stats.getPodiumThird()));
        active.setText(getString(R.string.region_stats_active, stats.getActivePlayers()));
        registered.setText(getString(R.string.region_stats_registered, stats.getTotalRegistered()));

        new AlertDialog.Builder(requireContext())
                .setView(content)
                .setPositiveButton(R.string.back, null)
                .show();
    }
    private void showCreateChallengeDialog() {
        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(8), dp(20), 0);

        EditText starsInput = new EditText(requireContext());
        starsInput.setHint(R.string.challenge_stake_stars);
        starsInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(starsInput);

        EditText tokensInput = new EditText(requireContext());
        tokensInput.setHint(R.string.challenge_stake_tokens);
        tokensInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        content.addView(tokensInput);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.challenge_create_dialog_title)
                .setView(content)
                .setNegativeButton(R.string.back, null)
                .setPositiveButton(R.string.challenge_create, (dialog, which) -> {
                    int stars = parseInt(starsInput.getText().toString());
                    int tokens = parseInt(tokensInput.getText().toString());
                    challengeViewModel.createChallenge(stars, tokens);
                })
                .show();
    }

    private void renderChallenges(@NonNull List<Challenge> challenges) {
        challengesContainer.removeAllViews();
        challengesEmpty.setVisibility(challenges.isEmpty() ? View.VISIBLE : View.GONE);

        for (Challenge challenge : challenges) {
            challengesContainer.addView(createChallengeView(challenge));
        }
    }

    @NonNull
    private View createChallengeView(@NonNull Challenge challenge) {
        MaterialCardView card = new MaterialCardView(requireContext());
        card.setCardElevation(dp(2));
        card.setUseCompatPadding(true);

        LinearLayout content = new LinearLayout(requireContext());
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView title = new TextView(requireContext());
        title.setText(challenge.getCreatorUsername());
        title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        content.addView(title);

        TextView info = new TextView(requireContext());
        info.setText("Ulog: " + challenge.getStakeStars()
                + " zvezda, " + challenge.getStakeTokens()
                + " tokena\nUcesnici: "
                + challenge.getParticipantCount()
                + "/4\nStatus: "
                + challenge.getStatus());
        info.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
        content.addView(info);

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);

        MaterialButton detailsButton = new MaterialButton(requireContext());
        detailsButton.setText(R.string.challenge_results);
        detailsButton.setOnClickListener(v -> challengeViewModel.selectChallenge(challenge.getChallengeId()));

        challengeViewModel.loadMyParticipation(
                challenge.getChallengeId(),
                participant -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (participant.isFinished() || !challenge.isOpen()) {
                        return;
                    }

                    MaterialButton playButton = new MaterialButton(requireContext());
                    playButton.setText(R.string.challenge_play);
                    playButton.setOnClickListener(v -> openChallengeSession(challenge.getChallengeId()));
                    actions.addView(playButton);
                },
                () -> {
                    if (!isAdded()) {
                        return;
                    }

                    if (challenge.isOpen() && challenge.getParticipantCount() < 4) {
                        MaterialButton acceptButton = new MaterialButton(requireContext());
                        acceptButton.setText(R.string.challenge_accept);
                        acceptButton.setOnClickListener(v -> challengeViewModel.joinChallenge(challenge.getChallengeId()));
                        actions.addView(acceptButton);
                    }
                }
        );

        actions.addView(detailsButton);



        content.addView(actions);
        card.addView(content);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(params);

        return card;
    }

    private void renderParticipants(@NonNull List<ChallengeParticipant> participants) {
        participantsContainer.removeAllViews();
        challengeResultsTitle.setVisibility(participants.isEmpty() ? View.GONE : View.VISIBLE);

        for (ChallengeParticipant participant : participants) {
            TextView row = new TextView(requireContext());
            row.setText(participant.getUsername()
                    + " - "
                    + participant.getScore()
                    + " poena"
                    + rewardText(participant));
            row.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            row.setPadding(0, dp(4), 0, dp(4));
            participantsContainer.addView(row);
        }
    }

    @NonNull
    private String rewardText(@NonNull ChallengeParticipant participant) {
        if (participant.getRewardStars() == 0 && participant.getRewardTokens() == 0) {
            return "";
        }
        return " | nagrada: "
                + participant.getRewardStars()
                + " zvezda, "
                + participant.getRewardTokens()
                + " tokena";
    }

    private int parseInt(@NonNull String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void openChallengeSession(@NonNull String challengeId) {
        Bundle args = new Bundle();
        args.putString("challengeId", challengeId);
        args.putInt("challengeGameIndex", 0);
        args.putInt("challengeTotalScore", 0);
        NavHostFragment.findNavController(this)
                .navigate(R.id.action_regionMap_to_challengeSession, args);
    }
}
