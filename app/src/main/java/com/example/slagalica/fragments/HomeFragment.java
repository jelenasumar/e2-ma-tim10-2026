package com.example.slagalica.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.activities.LoginActivity;

public class HomeFragment extends Fragment {

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button logoutBtn = view.findViewById(R.id.logout);
        Button mojBrojBtn = view.findViewById(R.id.mojBroj);
        Button stepByStepBtn = view.findViewById(R.id.korakPoKorak);
        Button associationsBtn = view.findViewById(R.id.asocijacije);
        Button skockoBtn = view.findViewById(R.id.skocko);
        Button notificationsBtn = view.findViewById(R.id.notifikacije);

        logoutBtn.setOnClickListener(v -> {
            startActivity(new Intent(requireActivity(), LoginActivity.class));
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

    private void showNotImplementedMessage() {
        Toast.makeText(requireContext(), R.string.screen_not_implemented, Toast.LENGTH_SHORT).show();
    }
}
