package com.example.slagalica.ui.region;

import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RegionLeaderboardEntry;
import com.example.slagalica.model.RegionRankingSummary;
import com.example.slagalica.utils.AvatarFrameHelper;
import com.example.slagalica.viewmodel.region.RegionLeaderboardViewModel;
import com.google.android.material.card.MaterialCardView;

import java.util.List;
import java.util.Locale;

public class RegionLeaderboardFragment extends Fragment {

    private RegionLeaderboardViewModel viewModel;
    private LinearLayout regionList;
    private LinearLayout playerList;
    private TextView cycleLabel;

    public RegionLeaderboardFragment() {
        super(R.layout.fragment_region_leaderboard);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RegionLeaderboardViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        regionList = view.findViewById(R.id.region_leaderboard_regions);
        playerList = view.findViewById(R.id.region_leaderboard_players);
        cycleLabel = view.findViewById(R.id.region_leaderboard_cycle);

        view.findViewById(R.id.region_leaderboard_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        viewModel.getRegionRankings().observe(getViewLifecycleOwner(), this::renderRegions);
        viewModel.getPlayerRankings().observe(getViewLifecycleOwner(), this::renderPlayers);
        viewModel.getCycleLabel().observe(getViewLifecycleOwner(), label -> {
            if (label != null) {
                cycleLabel.setText(getString(R.string.region_leaderboard_cycle, label));
            }
        });
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty() && getContext() != null) {
                android.widget.Toast.makeText(getContext(), error, android.widget.Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.load();
    }

    private void renderRegions(@Nullable List<RegionRankingSummary> regions) {
        regionList.removeAllViews();
        if (regions == null || regions.isEmpty()) {
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (RegionRankingSummary summary : regions) {
            View row = inflater.inflate(R.layout.item_region_ranking, regionList, false);
            TextView rank = row.findViewById(R.id.item_region_rank);
            ImageView icon = row.findViewById(R.id.item_region_icon);
            TextView name = row.findViewById(R.id.item_region_name);
            TextView stars = row.findViewById(R.id.item_region_stars);
            MaterialCardView card = row.findViewById(R.id.item_region_card);

            rank.setText(String.format(Locale.getDefault(), "%d.", summary.getRank()));
            icon.setImageResource(summary.getIconRes());
            name.setText(summary.getRegionName());
            stars.setText(String.format(Locale.getDefault(), "%d", summary.getTotalMonthlyStars()));

            if (summary.isCurrentUserRegion()) {
                card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.region_highlight));
                card.setStrokeWidth((int) (3f * getResources().getDisplayMetrics().density));
            }

            regionList.addView(row);
        }
    }

    private void renderPlayers(@Nullable List<RegionLeaderboardEntry> players) {
        playerList.removeAllViews();
        if (players == null || players.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.region_leaderboard_empty);
            playerList.addView(empty);
            return;
        }
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        int position = 1;
        for (RegionLeaderboardEntry entry : players) {
            View row = inflater.inflate(R.layout.item_region_player_ranking, playerList, false);
            TextView rank = row.findViewById(R.id.item_player_rank);
            TextView username = row.findViewById(R.id.item_player_username);
            TextView region = row.findViewById(R.id.item_player_region);
            TextView stars = row.findViewById(R.id.item_player_stars);
            MaterialCardView card = row.findViewById(R.id.item_player_card);
            ImageView frameIcon = row.findViewById(R.id.item_player_frame_icon);

            rank.setText(String.format(Locale.getDefault(), "%d.", position++));
            username.setText(entry.getUsername());
            region.setText(entry.getRegionName());
            stars.setText(String.format(Locale.getDefault(), "%d", entry.getMonthlyStars()));

            if (entry.isCurrentUser()) {
                card.setStrokeColor(ContextCompat.getColor(requireContext(), R.color.region_highlight));
                card.setStrokeWidth((int) (3f * getResources().getDisplayMetrics().density));
            }

            String frame = entry.getRegionRankFrame();
            if (frame == null || frame.isEmpty()) {
                frame = AvatarFrameHelper.resolveRankFrame(entry.getRegionKey(), entry.getRegionName());
            }
            if (frame != null && !frame.isEmpty()) {
                frameIcon.setVisibility(View.VISIBLE);
                frameIcon.setColorFilter(new PorterDuffColorFilter(
                        AvatarFrameHelper.frameColor(frame),
                        PorterDuff.Mode.SRC_IN
                ));
            } else {
                frameIcon.setVisibility(View.GONE);
            }

            playerList.addView(row);
        }
    }
}
