package com.example.slagalica.ui.friends;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.Friend;
import com.example.slagalica.model.LeagueTier;
import com.example.slagalica.utils.AvatarImageLoader;
import com.example.slagalica.viewmodel.friends.FriendsViewModel;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.List;

public class FriendsFragment extends Fragment {

    private FriendsViewModel viewModel;
    private LinearLayout friendsContainer;
    private ProgressBar progressBar;
    private TextInputEditText searchInput;

    private final ActivityResultLauncher<ScanOptions> qrLauncher = registerForActivityResult(
            new ScanContract(),
            result -> {
                if (result.getContents() != null) {
                    viewModel.addFriendFromQr(result.getContents());
                }
            }
    );

    public FriendsFragment() {
        super(R.layout.fragment_friends);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(requireActivity()).get(FriendsViewModel.class);
        friendsContainer = view.findViewById(R.id.friends_container);
        progressBar = view.findViewById(R.id.friends_progress);
        searchInput = view.findViewById(R.id.friends_search_input);

        view.findViewById(R.id.friends_back_button).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        view.findViewById(R.id.friends_add_button).setOnClickListener(v -> {
            String username = searchInput != null && searchInput.getText() != null
                    ? searchInput.getText().toString()
                    : "";
            viewModel.searchAndAddFriend(username);
        });

        view.findViewById(R.id.friends_scan_qr_button).setOnClickListener(v -> startQrScan());

        if (searchInput != null) {
            searchInput.setOnEditorActionListener((textView, actionId, event) -> {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    viewModel.searchAndAddFriend(textView.getText().toString());
                    return true;
                }
                return false;
            });
        }

        viewModel.getFriends().observe(getViewLifecycleOwner(), this::renderFriends);
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE)
        );

        viewModel.loadFriends();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadFriends();
        }
    }

    private void startQrScan() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 2001);
            return;
        }
        launchScanner();
    }

    private void launchScanner() {
        ScanOptions options = new ScanOptions();
        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE);
        options.setPrompt(getString(R.string.friends_scan_prompt));
        options.setBeepEnabled(false);
        options.setBarcodeImageEnabled(false);
        options.setOrientationLocked(false);
        qrLauncher.launch(options);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            launchScanner();
        } else if (requestCode == 2001) {
            Toast.makeText(requireContext(), R.string.friends_camera_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void renderFriends(@NonNull List<Friend> friends) {
        friendsContainer.removeAllViews();
        if (friends.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText(R.string.friends_empty);
            friendsContainer.addView(empty);
            return;
        }

        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Friend friend : friends) {
            View item = inflater.inflate(R.layout.item_friend, friendsContainer, false);
            bindFriendItem(item, friend);
            friendsContainer.addView(item);
        }
    }

    private void bindFriendItem(@NonNull View item, @NonNull Friend friend) {
        ImageView avatar = item.findViewById(R.id.friend_avatar);
        TextView username = item.findViewById(R.id.friend_username);
        TextView monthlyRank = item.findViewById(R.id.friend_monthly_rank);
        ImageView leagueIcon = item.findViewById(R.id.friend_league_icon);
        TextView totalStars = item.findViewById(R.id.friend_total_stars);
        TextView leagueName = item.findViewById(R.id.friend_league_name);
        TextView status = item.findViewById(R.id.friend_status);
        MaterialButton actionButton = item.findViewById(R.id.friend_action_button);

        AvatarImageLoader.load(avatar, friend.getAvatarUri(), R.drawable.ic_avatar_placeholder);
        username.setText(friend.getUsername());
        monthlyRank.setText(getString(R.string.friends_monthly_rank_value, friend.getMonthlyRank()));
        totalStars.setText(getString(R.string.friends_total_stars_value, friend.getTotalStars()));

        LeagueTier tier = LeagueTier.fromKey(friend.getLeagueTierKey());
        leagueIcon.setImageResource(tier.getIconRes());
        leagueName.setText(friend.getLeagueName());

        if (friend.isInActiveGame()) {
            status.setText(R.string.friends_status_in_game);
            actionButton.setText(R.string.friends_invite_unavailable);
            actionButton.setEnabled(false);
        } else if (friend.isInvitePending()) {
            status.setText(R.string.friends_status_invite_pending);
            actionButton.setText(R.string.friends_cancel_invite);
            actionButton.setEnabled(true);
            actionButton.setOnClickListener(v -> viewModel.cancelInvite(friend));
        } else {
            status.setText(R.string.friends_status_available);
            actionButton.setText(R.string.friends_invite_button);
            actionButton.setEnabled(true);
            actionButton.setOnClickListener(v -> viewModel.sendInvite(friend));
        }
    }
}
