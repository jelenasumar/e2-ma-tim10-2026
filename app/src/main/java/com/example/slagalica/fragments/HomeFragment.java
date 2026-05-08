package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.activities.LoginActivity;
import com.example.slagalica.activities.MainActivity;
import com.example.slagalica.data.UserProfileRepository;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button logoutBtn = view.findViewById(R.id.logout);
        Button profileBtn = view.findViewById(R.id.profile_button);
        Button koZnaZnaBtn = view.findViewById(R.id.koZnaZna);
        Button mojBrojBtn = view.findViewById(R.id.mojBroj);
        Button spojniceBtn = view.findViewById(R.id.spojnice);
        Button stepByStepBtn = view.findViewById(R.id.korakPoKorak);
        Button associationsBtn = view.findViewById(R.id.asocijacije);
        Button skockoBtn = view.findViewById(R.id.skocko);
        Button notificationsBtn = view.findViewById(R.id.notifikacije);

        if (isGuestMode()) {
            view.findViewById(R.id.accountSectionTitle).setVisibility(View.GONE);
            profileBtn.setVisibility(View.GONE);
            notificationsBtn.setVisibility(View.GONE);
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

        mojBrojBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_mojBroj);
        });

        spojniceBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_spojnice);
        });

        koZnaZnaBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_koZnaZna);
        });

        stepByStepBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_stepByStep);
        });

        associationsBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_associations);
        });
        skockoBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_skocko);
        });
        notificationsBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_notifications);
        });
    }

    private boolean isGuestMode() {
        return requireActivity().getIntent().getBooleanExtra(MainActivity.EXTRA_GUEST_MODE, false);
    }
}
