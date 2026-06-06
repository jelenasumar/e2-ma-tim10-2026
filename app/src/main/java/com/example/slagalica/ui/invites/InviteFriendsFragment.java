package com.example.slagalica.ui.invites;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.InviteUser;
import com.example.slagalica.viewmodel.invites.InviteFriendsViewModel;

import java.util.List;

public class InviteFriendsFragment extends Fragment {

    private InviteFriendsViewModel viewModel;
    private LinearLayout usersContainer;
    private ProgressBar progressBar;

    public InviteFriendsFragment() {
        super(R.layout.fragment_invite_friends);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(InviteFriendsViewModel.class);
        usersContainer = view.findViewById(R.id.inviteUsersContainer);
        progressBar = view.findViewById(R.id.inviteProgress);

        view.findViewById(R.id.inviteBackButton).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        viewModel.getUsers().observe(getViewLifecycleOwner(), this::renderUsers);
        viewModel.getMessage().observe(getViewLifecycleOwner(), message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        );
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading ->
                progressBar.setVisibility(Boolean.TRUE.equals(loading) ? View.VISIBLE : View.GONE)
        );

        viewModel.loadUsers();
    }

    private void renderUsers(@NonNull List<InviteUser> users) {
        usersContainer.removeAllViews();

        if (users.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(R.string.invite_no_users);
            emptyView.setTextSize(15);
            usersContainer.addView(emptyView);
            return;
        }

        for (InviteUser user : users) {
            usersContainer.addView(createUserRow(user));
        }
    }

    @NonNull
    private View createUserRow(@NonNull InviteUser user) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setPadding(0, dpToPx(8), 0, dpToPx(8));

        ImageView avatar = new ImageView(requireContext());
        avatar.setImageResource(R.drawable.ic_avatar_placeholder);
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        if (!user.getAvatarUri().isEmpty()) {
            try {
                avatar.setImageURI(Uri.parse(user.getAvatarUri()));
                if (avatar.getDrawable() == null) {
                    avatar.setImageResource(R.drawable.ic_avatar_placeholder);
                }
            } catch (Throwable ignored) {
                avatar.setImageResource(R.drawable.ic_avatar_placeholder);
            }
        }
        row.addView(avatar, new LinearLayout.LayoutParams(dpToPx(40), dpToPx(40)));

        TextView name = new TextView(requireContext());
        name.setText(user.getUsername());
        name.setTextSize(16);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
        nameParams.setMargins(dpToPx(12), 0, dpToPx(8), 0);
        row.addView(name, nameParams);

        Button inviteButton = new Button(requireContext());
        if (viewModel.isInvitePending(user.getUid())) {
            inviteButton.setText(R.string.invite_pending);
            inviteButton.setEnabled(false);
        } else {
            inviteButton.setText(R.string.invite_send);
            inviteButton.setOnClickListener(v -> viewModel.sendInvite(user));
        }
        row.addView(inviteButton);

        return row;
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
