package com.example.slagalica.ui.notifications;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.os.Bundle;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.NotificationStatus;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.ui.ranking.RewardConfettiView;
import com.example.slagalica.ui.ranking.RewardIconHelper;
import com.example.slagalica.viewmodel.notifications.NotificationsViewModel;

import java.util.List;

public class NotificationsFragment extends Fragment {

    private NotificationsViewModel viewModel;
    private LinearLayout notificationsContainer;
    private boolean navigatedToRoom = false;

    public NotificationsFragment() {
        super(R.layout.fragment_notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(NotificationsViewModel.class);
        notificationsContainer = view.findViewById(R.id.notificationsContainer);

        Button backBtn = view.findViewById(R.id.backButton);
        Spinner categoryFilterSpinner = view.findViewById(R.id.categoryFilterSpinner);
        Spinner statusFilterSpinner = view.findViewById(R.id.statusFilterSpinner);

        backBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });

        setupCategorySpinner(categoryFilterSpinner);
        setupStatusSpinner(statusFilterSpinner);

        viewModel.getVisibleNotifications().observe(getViewLifecycleOwner(), this::renderNotifications);
        viewModel.getMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getRoomNavigation().observe(getViewLifecycleOwner(), this::navigateToRoom);
        viewModel.getNotificationPageTitle().observe(getViewLifecycleOwner(), this::navigateToNotificationPage);
    }

    private void setupCategorySpinner(@NonNull Spinner spinner) {
        setupSpinner(spinner, R.array.notification_category_filters);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewModel.setCategoryFilter(categoryFromPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                viewModel.setCategoryFilter(NotificationCategory.ALL);
            }
        });
    }

    private void setupStatusSpinner(@NonNull Spinner spinner) {
        setupSpinner(spinner, R.array.notification_status_filters);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewModel.setStatusFilter(statusFromPosition(position));
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                viewModel.setStatusFilter(NotificationStatus.ALL);
            }
        });
    }

    private void setupSpinner(@NonNull Spinner spinner, int arrayResource) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                arrayResource,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    @NonNull
    private NotificationCategory categoryFromPosition(int position) {
        switch (position) {
            case 1:
                return NotificationCategory.CHAT;
            case 2:
                return NotificationCategory.RANKING;
            case 3:
                return NotificationCategory.REWARD;
            case 4:
                return NotificationCategory.OTHER;
            default:
                return NotificationCategory.ALL;
        }
    }

    @NonNull
    private NotificationStatus statusFromPosition(int position) {
        switch (position) {
            case 1:
                return NotificationStatus.UNREAD;
            case 2:
                return NotificationStatus.READ;
            default:
                return NotificationStatus.ALL;
        }
    }

    private void renderNotifications(@NonNull List<SystemNotification> notifications) {
        notificationsContainer.removeAllViews();

        if (notifications.isEmpty()) {
            TextView emptyView = new TextView(requireContext());
            emptyView.setText(R.string.no_notifications);
            emptyView.setTextSize(15);
            emptyView.setPadding(0, dpToPx(16), 0, 0);
            notificationsContainer.addView(emptyView);
            return;
        }

        for (SystemNotification notification : notifications) {
            notificationsContainer.addView(createNotificationCard(notification));
        }
    }

    @NonNull
    private View createNotificationCard(@NonNull SystemNotification notification) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dpToPx(14), dpToPx(14), dpToPx(14), dpToPx(14));
        card.setBackgroundResource(notification.isRead()
                ? android.R.drawable.edit_text
                : R.drawable.notification_unread_background);
        card.setOnClickListener(v -> handleNotificationClick(notification));
        card.setClickable(true);

        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardParams);

        LinearLayout header = new LinearLayout(requireContext());
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView type = new TextView(requireContext());
        type.setText(notification.getCategoryLabel());
        type.setTextSize(12);
        type.setTypeface(type.getTypeface(), android.graphics.Typeface.BOLD);
        type.setOnClickListener(v -> handleNotificationClick(notification));
        header.addView(type, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        Button readButton = new Button(requireContext());
        readButton.setText(notification.isRead() ? R.string.mark_as_unread : R.string.mark_as_read);
        readButton.setTextSize(11);
        readButton.setMinHeight(0);
        readButton.setMinWidth(0);
        readButton.setPadding(dpToPx(8), 0, dpToPx(8), 0);
        readButton.setOnClickListener(v -> {
            if (notification.isRead()) {
                viewModel.markAsUnread(notification.getId());
            } else {
                viewModel.markAsRead(notification.getId());
            }
        });
        header.addView(readButton, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dpToPx(32)
        ));

        card.addView(header);
        TextView title = createText(notification.getTitle(), 17, true, 4);
        title.setOnClickListener(v -> handleNotificationClick(notification));
        card.addView(title);
        TextView message = createText(notification.getMessage(), 14, false, 6);
        message.setOnClickListener(v -> handleNotificationClick(notification));
        card.addView(message);
        if (shouldShowActionResult(notification)) {
            TextView result = createText(notification.getActionResult(), 13, true, 8);
            result.setOnClickListener(v -> handleNotificationClick(notification));
            card.addView(result);
        }
        addActionButton(card, notification);
        TextView dateStatus = createText(buildDateStatus(notification), 12, false, 8);
        dateStatus.setOnClickListener(v -> handleNotificationClick(notification));
        card.addView(dateStatus);

        return card;
    }

    private void handleNotificationClick(@NonNull SystemNotification notification) {
        if (isRewardNotification(notification)) {
            if (!notification.isRead()) {
                viewModel.markAsRead(notification.getId());
            }
            showRewardDialog(notification);
            return;
        }
        viewModel.openNotification(notification);
    }

    private boolean isRewardNotification(@NonNull SystemNotification notification) {
        return notification.getCategory() == NotificationCategory.REWARD;
    }

    private void showRewardDialog(@NonNull SystemNotification notification) {
        ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_NOTIFICATION, 80);
        tone.startTone(ToneGenerator.TONE_PROP_ACK, 180);
        View content = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_ranking_reward, null, false);
        TextView messageView = content.findViewById(R.id.reward_message);
        ImageView rewardIcon = content.findViewById(R.id.reward_icon);
        RewardConfettiView confetti = content.findViewById(R.id.reward_confetti);
        messageView.setText(notification.getMessage());
        int rank = notification.getRank() > 0
                ? notification.getRank()
                : RewardIconHelper.rankFromMessage(notification.getMessage());
        rewardIcon.setImageResource(RewardIconHelper.iconForRank(rank));
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setTitle(notification.getTitle())
                .setView(content)
                .setPositiveButton(android.R.string.ok, null)
                .setOnDismissListener(dismissed -> tone.release())
                .show();
        confetti.start();
        animateRewardIcon(rewardIcon);
    }

    private void animateRewardIcon(@NonNull View icon) {
        ObjectAnimator jump = ObjectAnimator.ofFloat(icon, View.TRANSLATION_Y, 0f, -34f, 0f, -16f, 0f);
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(icon, View.SCALE_X, 0.7f, 1.18f, 1f, 1.08f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(icon, View.SCALE_Y, 0.7f, 1.18f, 1f, 1.08f, 1f);
        ObjectAnimator rotation = ObjectAnimator.ofFloat(icon, View.ROTATION, -10f, 10f, -6f, 6f, 0f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(jump, scaleX, scaleY, rotation);
        set.setDuration(950L);
        set.start();
    }

    @NonNull
    private TextView createText(@NonNull String text, int textSize, boolean bold, int topMarginDp) {
        TextView textView = new TextView(requireContext());
        textView.setText(text);
        textView.setTextSize(textSize);
        if (bold) {
            textView.setTypeface(textView.getTypeface(), android.graphics.Typeface.BOLD);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(topMarginDp), 0, 0);
        textView.setLayoutParams(params);
        return textView;
    }

    private void addActionButton(
            @NonNull LinearLayout card,
            @NonNull SystemNotification notification
    ) {
        if (!shouldShowActionButton(notification)) {
            return;
        }

        LinearLayout actions = new LinearLayout(requireContext());
        actions.setOrientation(LinearLayout.HORIZONTAL);

        String actionLabel = notification.getActionLabel() != null
                ? notification.getActionLabel()
                : getString(R.string.accept_invite);
        Button actionButton = createSmallActionButton(actionLabel);
        actionButton.setOnClickListener(v -> {
            viewModel.reactToNotification(notification);
        });
        actions.addView(actionButton);

        if (notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            Button declineButton = createSmallActionButton(getString(R.string.decline_invite));
            declineButton.setOnClickListener(v -> viewModel.declineInvite(notification));
            LinearLayout.LayoutParams declineParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            declineParams.setMargins(dpToPx(8), 0, 0, 0);
            actions.addView(declineButton, declineParams);
        }

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(8), 0, 0);
        card.addView(actions, params);
    }

    private boolean shouldShowActionButton(@NonNull SystemNotification notification) {
        if (!notification.hasPendingAction()) {
            return false;
        }
        if (notification.getAction() == NotificationAction.ACCEPT_INVITE) {
            return true;
        }
        return notification.getAction() == NotificationAction.OPEN_ROOM && !notification.isRead();
    }

    @NonNull
    private Button createSmallActionButton(@NonNull String text) {
        Button button = new Button(requireContext());
        button.setText(text);
        button.setTextSize(12);
        button.setMinHeight(0);
        button.setMinWidth(0);
        button.setPadding(dpToPx(10), 0, dpToPx(10), 0);
        return button;
    }

    @NonNull
    private String buildDateStatus(@NonNull SystemNotification notification) {
        String status = notification.isRead() ? "Procitano" : "Neprocitano";
        return notification.getDateLabel() + " - " + status;
    }

    private boolean shouldShowActionResult(@NonNull SystemNotification notification) {
        if (!notification.isActionHandled() || notification.getActionResult().isEmpty()) {
            return false;
        }
        return notification.getAction() == NotificationAction.ACCEPT_INVITE
                || notification.getAction() == NotificationAction.OPEN_ROOM;
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void navigateToRoom(@NonNull String roomId) {
        if (roomId.isEmpty() || navigatedToRoom) {
            return;
        }
        navigatedToRoom = true;
        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        NavHostFragment.findNavController(this).navigate(R.id.roomSessionFragment, args);
    }

    private void navigateToNotificationPage(@NonNull String title) {
        if (title.isEmpty()) {
            return;
        }
        if (getString(R.string.notification_destination_league).equals(title)
                || getString(R.string.notification_destination_ranking).equals(title)
                || "Liga".equals(title)
                || "Rang lista".equals(title)) {
            NavHostFragment.findNavController(this).navigate(R.id.rankingFragment);
            return;
        }
        if (getString(R.string.notification_destination_chat).equals(title)
                || "Čet".equals(title)
                || "Cet".equals(title)
                || "ÄŒet".equals(title)) {
            NavHostFragment.findNavController(this).navigate(R.id.regionChatFragment);
            return;
        }
        Bundle args = new Bundle();
        args.putString(NotificationDestinationFragment.ARG_TITLE, title);
        NavHostFragment.findNavController(this).navigate(R.id.notificationDestinationFragment, args);
    }
}
