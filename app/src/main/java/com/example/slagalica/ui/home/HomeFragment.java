package com.example.slagalica.ui.home;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.data.repository.UserProfileRepository;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button logoutBtn = view.findViewById(R.id.logout);
        Button profileBtn = view.findViewById(R.id.profile_button);
        Button notificationsBtn = view.findViewById(R.id.notifikacije);
        Button dailyMissionsBtn = view.findViewById(R.id.dailyMissions);
        Button startOnlineMatchBtn = view.findViewById(R.id.startOnlineMatch);
        Button startTournamentBtn = view.findViewById(R.id.startTournament);
        Button inviteFriendsBtn = view.findViewById(R.id.inviteFriends);
        Button regionMapBtn = view.findViewById(R.id.regionMap);
        Button rankingBtn = view.findViewById(R.id.ranking);
        Button regionChatBtn = view.findViewById(R.id.regionChat);

        if (isGuestMode()) {
            view.findViewById(R.id.accountSectionTitle).setVisibility(View.GONE);
            profileBtn.setVisibility(View.GONE);
            notificationsBtn.setVisibility(View.GONE);
            dailyMissionsBtn.setVisibility(View.GONE);
            startOnlineMatchBtn.setVisibility(View.GONE);
            startTournamentBtn.setVisibility(View.GONE);
            inviteFriendsBtn.setVisibility(View.GONE);
            regionMapBtn.setVisibility(View.GONE);
            rankingBtn.setVisibility(View.GONE);
            regionChatBtn.setVisibility(View.GONE);
        }

        profileBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_profile);
        });

        logoutBtn.setOnClickListener(v -> {
            new UserProfileRepository(requireContext()).clearSession();
            LoginActivity.openFresh(requireActivity());
            requireActivity().finish();
        });

        notificationsBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_notifications);
        });
        dailyMissionsBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_dailyMissions);
        });
        startOnlineMatchBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_onlineMatchmaking);
        });
        startTournamentBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_tournament);
        });
        inviteFriendsBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_inviteFriends);
        });
        regionMapBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_regionMap);
        });
        regionChatBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_regionChat);
        });
        rankingBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_ranking);
        });
    }

    private boolean isGuestMode() {
        return requireActivity().getIntent().getBooleanExtra(MainActivity.EXTRA_GUEST_MODE, false);
    }
}
