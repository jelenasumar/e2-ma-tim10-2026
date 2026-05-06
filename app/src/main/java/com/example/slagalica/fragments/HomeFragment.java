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
        Button mojBrojBtn = view.findViewById(R.id.mojBroj);
        Button stepByStepBtn = view.findViewById(R.id.korakPoKorak);

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

        stepByStepBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigate(R.id.action_home_to_stepByStep);
        });
    }
}
