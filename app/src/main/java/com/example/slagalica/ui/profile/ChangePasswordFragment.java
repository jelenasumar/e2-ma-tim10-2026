package com.example.slagalica.ui.profile;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.viewmodel.profile.ChangePasswordViewModel;

public class ChangePasswordFragment extends Fragment {

    private ChangePasswordViewModel viewModel;

    public ChangePasswordFragment() {
        super(R.layout.fragment_change_password);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ChangePasswordViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText currentPassword = view.findViewById(R.id.currentPassword);
        EditText newPassword = view.findViewById(R.id.newPassword);
        EditText confirmPassword = view.findViewById(R.id.confirmPassword);
        Button changePasswordBtn = view.findViewById(R.id.changePasswordButton);

        changePasswordBtn.setOnClickListener(v -> viewModel.changePassword(
                textOf(currentPassword),
                textOf(newPassword),
                textOf(confirmPassword)
        ));

        viewModel.getIsLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean inProgress = Boolean.TRUE.equals(loading);
            changePasswordBtn.setEnabled(!inProgress);
            currentPassword.setEnabled(!inProgress);
            newPassword.setEnabled(!inProgress);
            confirmPassword.setEnabled(!inProgress);
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        viewModel.getPasswordChanged().observe(getViewLifecycleOwner(), changed -> {
            if (Boolean.TRUE.equals(changed)) {
                Toast.makeText(requireContext(), R.string.change_password_success, Toast.LENGTH_SHORT).show();
                NavHostFragment.findNavController(this).navigateUp();
            }
        });
    }

    @NonNull
    private static String textOf(@NonNull EditText editText) {
        return editText.getText() != null ? editText.getText().toString() : "";
    }
}
