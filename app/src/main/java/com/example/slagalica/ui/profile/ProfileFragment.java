package com.example.slagalica.ui.profile;

import android.graphics.Bitmap;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.utils.QrBitmapEncoder;
import com.example.slagalica.viewmodel.profile.ProfileViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.WriterException;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private ActivityResultLauncher<String> pickImageLauncher;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        viewModel.updateAvatar(uri);
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.profile_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.homeFragment)
        );

        view.findViewById(R.id.profile_change_avatar).setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        MaterialButton logout = view.findViewById(R.id.profile_logout);
        logout.setOnClickListener(v -> viewModel.logout());

        Button changePasswordBtn = view.findViewById(R.id.change_password_btn);
        changePasswordBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.changePassword)
        );

        observeViewModel(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        viewModel.loadProfile();
    }

    private void observeViewModel(@NonNull View root) {
        viewModel.getProfile().observe(getViewLifecycleOwner(), profile -> {
            if (profile != null) {
                bindProfile(root, profile);
            }
        });

        viewModel.getStatsText().observe(getViewLifecycleOwner(), stats -> {
            TextView statsBody = root.findViewById(R.id.profile_stats_body);
            if (stats != null) {
                statsBody.setText(stats);
            }
        });

        viewModel.getMatchSummaryText().observe(getViewLifecycleOwner(), summary -> {
            TextView matchSummary = root.findViewById(R.id.profile_match_summary);
            if (summary != null) {
                matchSummary.setText(summary);
            }
        });

        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), error -> {
            if (error != null && !error.isEmpty()) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            }
        });

        viewModel.getLogoutCompleted().observe(getViewLifecycleOwner(), completed -> {
            if (Boolean.TRUE.equals(completed)) {
                LoginActivity.openFresh(requireActivity());
                requireActivity().finish();
            }
        });

        viewModel.getQrGenerationFailed().observe(getViewLifecycleOwner(), failed -> {
            if (Boolean.TRUE.equals(failed)) {
                Toast.makeText(requireContext(), R.string.profile_qr_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindProfile(@NonNull View root, @NonNull UserProfile profile) {
        TextView username = root.findViewById(R.id.profile_username);
        TextView email = root.findViewById(R.id.profile_email);
        username.setText(profile.getUsername());
        email.setText(profile.getEmail());

        TextView tokens = root.findViewById(R.id.profile_tokens_value);
        TextView stars = root.findViewById(R.id.profile_stars_value);
        tokens.setText(String.format(Locale.getDefault(), "%d", profile.getTokens()));
        stars.setText(String.format(Locale.getDefault(), "%d", profile.getTotalStars()));

        TextView leagueName = root.findViewById(R.id.profile_league_name);
        leagueName.setText(profile.getLeagueName());

        ImageView leagueIcon = root.findViewById(R.id.profile_league_icon);
        leagueIcon.setImageResource(R.drawable.ic_league_badge);
        leagueIcon.setColorFilter(new PorterDuffColorFilter(
                ProfileViewModel.leagueColor(profile.getLeagueTierKey()),
                PorterDuff.Mode.SRC_IN
        ));

        TextView region = root.findViewById(R.id.profile_region_value);
        region.setText(profile.getRegion());

        ImageView avatar = root.findViewById(R.id.profile_avatar);
        AvatarImageLoader.load(avatar, profile.getAvatarUri(), R.drawable.ic_avatar_placeholder);

        ImageView qr = root.findViewById(R.id.profile_qr);
        int qrSizePx = (int) (200f * getResources().getDisplayMetrics().density);
        try {
            Bitmap bmp = QrBitmapEncoder.encode(profile.getInvitePayload(), qrSizePx);
            qr.setImageBitmap(bmp);
        } catch (WriterException e) {
            qr.setImageBitmap(null);
            viewModel.notifyQrGenerationFailed();
        }
    }
}
