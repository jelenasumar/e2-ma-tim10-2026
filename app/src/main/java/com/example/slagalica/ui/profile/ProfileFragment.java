package com.example.slagalica.ui.profile;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.ui.auth.LoginActivity;
import com.example.slagalica.utils.AvatarFrameHelper;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.utils.QrBitmapEncoder;
import com.example.slagalica.viewmodel.profile.ProfileViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.zxing.WriterException;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private ProfileViewModel viewModel;
    private UserProfileRepository profileRepository;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.profile_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.homeFragment)
        );
        profileRepository = new UserProfileRepository(requireContext());

        view.findViewById(R.id.profile_change_avatar).setOnClickListener(v -> showAvatarPicker());

        MaterialButton logout = view.findViewById(R.id.profile_logout);
        logout.setOnClickListener(v -> viewModel.logout());

        Button changePasswordBtn = view.findViewById(R.id.change_password_btn);
        changePasswordBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.changePassword)
        );

        observeViewModel(view);
        viewModel.startProfileListener();
    }

    private void showAvatarPicker() {
        String[] labels = {
                "Crveni pas",
                "Ljubicasta macka",
                "Plavi zec",
                "Zeleni medved",
                "Narandzasta lisica",
                "Tirkizna sova"
        };
        String[] values = {
                "preset:avatar_preset_1",
                "preset:avatar_preset_2",
                "preset:avatar_preset_3",
                "preset:avatar_preset_4",
                "preset:avatar_preset_5",
                "preset:avatar_preset_6"
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_change_avatar)
                .setItems(labels, (dialog, which) -> viewModel.updateAvatarPreset(values[which]))
                .show();
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

        boolean registered = profileRepository == null || profileRepository.isRegisteredPlayer();
        int profileRewardsVisibility = registered ? View.VISIBLE : View.GONE;
        root.findViewById(R.id.profile_tokens_row).setVisibility(profileRewardsVisibility);
        root.findViewById(R.id.profile_stars_row).setVisibility(profileRewardsVisibility);
        root.findViewById(R.id.profile_monthly_stars_row).setVisibility(profileRewardsVisibility);
        root.findViewById(R.id.profile_league_row).setVisibility(profileRewardsVisibility);

        TextView tokens = root.findViewById(R.id.profile_tokens_value);
        TextView stars = root.findViewById(R.id.profile_stars_value);
        TextView monthlyStars = root.findViewById(R.id.profile_monthly_stars_value);
        tokens.setText(String.format(Locale.getDefault(), "%d", profile.getTokens()));
        stars.setText(String.format(Locale.getDefault(), "%d", profile.getTotalStars()));
        monthlyStars.setText(String.format(Locale.getDefault(), "%d", profile.getMonthlyStars()));

        TextView leagueName = root.findViewById(R.id.profile_league_name);
        leagueName.setText(profile.getLeagueName());

        ImageView leagueIcon = root.findViewById(R.id.profile_league_icon);
        leagueIcon.setImageResource(ProfileViewModel.leagueIcon(profile.getLeagueTierKey()));
        leagueIcon.clearColorFilter();

        TextView region = root.findViewById(R.id.profile_region_value);
        SerbiaRegion resolvedRegion = SerbiaRegion.resolve(profile.getRegionKey(), profile.getRegion());
        region.setText(resolvedRegion != null
                ? resolvedRegion.getDisplayName(requireContext())
                : profile.getRegion());

        MaterialCardView avatarCard = root.findViewById(R.id.profile_avatar_card);
        ImageView avatarRing = root.findViewById(R.id.profile_avatar_ring);
        String frameKey = AvatarFrameHelper.resolveRankFrame(profile.getRegionKey(), profile.getRegion());
        if (frameKey.isEmpty() && profile.getRegionRankFrame() != null) {
            frameKey = profile.getRegionRankFrame();
        }
        AvatarFrameHelper.applyFrame(avatarRing, avatarCard, frameKey);

        TextView frameLabel = root.findViewById(R.id.profile_avatar_frame_label);
        if (frameKey != null && !frameKey.isEmpty()) {
            frameLabel.setVisibility(View.VISIBLE);
            int labelRes;
            switch (frameKey) {
                case AvatarFrameHelper.FRAME_GOLD:
                    labelRes = R.string.profile_frame_gold;
                    break;
                case AvatarFrameHelper.FRAME_SILVER:
                    labelRes = R.string.profile_frame_silver;
                    break;
                case AvatarFrameHelper.FRAME_BRONZE:
                    labelRes = R.string.profile_frame_bronze;
                    break;
                default:
                    labelRes = 0;
                    break;
            }
            if (labelRes != 0) {
                frameLabel.setText(labelRes);
            } else {
                frameLabel.setVisibility(View.GONE);
            }
        } else {
            frameLabel.setVisibility(View.GONE);
        }

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
