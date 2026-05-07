package com.example.slagalica.fragments;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.activities.LoginActivity;
import com.example.slagalica.data.UserProfileRepository;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;
import com.example.slagalica.util.QrBitmapEncoder;
import com.google.android.material.button.MaterialButton;
import com.google.zxing.WriterException;

import java.util.Locale;

public class ProfileFragment extends Fragment {

    private UserProfileRepository repository;
    private ActivityResultLauncher<String> pickImageLauncher;

    public ProfileFragment() {
        super(R.layout.fragment_profile);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        repository = new UserProfileRepository(requireContext());
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        repository.saveAvatarUri(uri.toString());
                        View v = getView();
                        if (v != null) {
                            bindProfile(v);
                        }
                    }
                }
        );
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.profile_back).setOnClickListener(v ->
                NavHostFragment.findNavController(this).popBackStack()
        );

        view.findViewById(R.id.profile_change_avatar).setOnClickListener(v ->
                pickImageLauncher.launch("image/*")
        );

        MaterialButton logout = view.findViewById(R.id.profile_logout);
        logout.setOnClickListener(v -> {
            repository.clearSession();
            LoginActivity.openFresh(requireActivity());
            requireActivity().finish();
        });

        bindProfile(view);
    }

    private void bindProfile(@NonNull View root) {
        UserProfile profile = repository.loadProfile();

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
                leagueColor(profile.getLeagueTierKey()),
                PorterDuff.Mode.SRC_IN
        ));

        TextView region = root.findViewById(R.id.profile_region_value);
        region.setText(profile.getRegion());

        ImageView avatar = root.findViewById(R.id.profile_avatar);
        String avatarUri = profile.getAvatarUri();
        if (avatarUri != null && !avatarUri.isEmpty()) {
            try {
                avatar.setImageURI(Uri.parse(avatarUri));
                if (avatar.getDrawable() == null) {
                    avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            } catch (Throwable ignored) {
                avatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        } else {
            avatar.setImageResource(R.drawable.ic_avatar_placeholder);
        }

        ImageView qr = root.findViewById(R.id.profile_qr);
        int qrSizePx = (int) (200f * getResources().getDisplayMetrics().density);
        try {
            Bitmap bmp = QrBitmapEncoder.encode(profile.getInvitePayload(), qrSizePx);
            qr.setImageBitmap(bmp);
        } catch (WriterException e) {
            qr.setImageBitmap(null);
            Toast.makeText(requireContext(), R.string.profile_qr_error, Toast.LENGTH_SHORT).show();
        }

        TextView statsBody = root.findViewById(R.id.profile_stats_body);
        statsBody.setText(buildStatsText(profile));
    }

    private static int leagueColor(@NonNull String tier) {
        switch (tier.toLowerCase(Locale.US)) {
            case "silver":
                return Color.rgb(192, 192, 192);
            case "gold":
                return Color.rgb(255, 215, 0);
            case "bronze":
            default:
                return Color.rgb(205, 127, 50);
        }
    }

    @NonNull
    private String buildStatsText(@NonNull UserProfile profile) {
        PlayerStatistics s = profile.getStatistics();
        StringBuilder sb = new StringBuilder();

        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_ko_zna_zna), s.getAvgScoreKoZnaZna())).append('\n');
        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_spojnice), s.getAvgScoreSpojnice())).append('\n');
        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_moj_broj), s.getAvgScoreMojBroj())).append('\n');
        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_korak_po_korak), s.getAvgScoreKorakPoKorak())).append('\n');
        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_asocijacije), s.getAvgScoreAsocijacije())).append('\n');
        sb.append(getString(R.string.profile_stat_avg_points, getString(R.string.game_skocko), s.getAvgScoreSkocko())).append("\n\n");

        sb.append(getString(R.string.profile_stat_kzz_ratio, s.getKoZnaZnaHits(), s.getKoZnaZnaMisses())).append("\n\n");
        sb.append(getString(R.string.profile_stat_moj_broj, s.getMojBrojCorrectPercent())).append("\n\n");

        sb.append(getString(R.string.profile_stat_kpk_header)).append('\n');
        float[] steps = s.getKorakPoKorakStepPercents();
        for (int i = 0; i < steps.length; i++) {
            sb.append(getString(R.string.profile_stat_kpk_step, i + 1, steps[i])).append('\n');
        }
        sb.append('\n');

        sb.append(getString(R.string.profile_stat_asoc, s.getAsocijacijeSolved(), s.getAsocijacijeUnsolved())).append("\n\n");
        sb.append(getString(R.string.profile_stat_skocko, s.getSkockoComboPercent())).append("\n\n");
        sb.append(getString(R.string.profile_stat_spojnice, s.getSpojniceLinkedPercent())).append("\n\n");

        sb.append(getString(R.string.profile_stat_matches_total, s.getTotalMatches())).append("\n\n");
        sb.append(getString(R.string.profile_stat_win_loss, s.getMatchesWinPercent(), s.getMatchesLossPercent()));

        return sb.toString();
    }
}
