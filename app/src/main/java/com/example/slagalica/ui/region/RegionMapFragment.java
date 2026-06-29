package com.example.slagalica.ui.region;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

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

    public RegionMapFragment() {
        super(R.layout.fragment_region_map);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(RegionMapViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mapView = view.findViewById(R.id.region_map_view);
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

        mapView.setRegionClickListener(region -> {
            viewModel.onRegionSelected(region);
            mapView.setSelectedRegion(region);
        });

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
}
