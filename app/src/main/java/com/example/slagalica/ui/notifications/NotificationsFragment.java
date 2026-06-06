package com.example.slagalica.ui.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.NotificationAction;
import com.example.slagalica.model.NotificationCategory;
import com.example.slagalica.model.NotificationStatus;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.viewmodel.notifications.NotificationsViewModel;

import java.util.List;

public class NotificationsFragment extends Fragment {

    private NotificationsViewModel viewModel;
    private LinearLayout notificationsContainer;

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
        viewModel.getMessage().observe(getViewLifecycleOwner(), message ->
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
        );
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
        header.addView(type, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));

        if (!notification.isRead()) {
            Button markReadButton = new Button(requireContext());
            markReadButton.setText(R.string.mark_as_read);
            markReadButton.setTextSize(11);
            markReadButton.setMinHeight(0);
            markReadButton.setMinWidth(0);
            markReadButton.setPadding(dpToPx(8), 0, dpToPx(8), 0);
            markReadButton.setOnClickListener(v -> viewModel.markAsRead(notification.getId()));
            header.addView(markReadButton, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    dpToPx(32)
            ));
        }

        card.addView(header);
        card.addView(createText(notification.getTitle(), 17, true, 4));
        card.addView(createText(notification.getMessage(), 14, false, 6));
        addActionButton(card, notification);
        card.addView(createText(buildDateStatus(notification), 12, false, 8));

        return card;
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
        if (notification.getAction() == NotificationAction.NONE || notification.getActionLabel() == null) {
            return;
        }

        Button actionButton = new Button(requireContext());
        actionButton.setText(notification.getActionLabel());
        actionButton.setOnClickListener(v -> {
            viewModel.reactToNotification(notification);
            handleAction(notification.getAction());
        });

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, dpToPx(8), 0, 0);
        card.addView(actionButton, params);
    }

    @NonNull
    private String buildDateStatus(@NonNull SystemNotification notification) {
        String status = notification.isRead() ? "Procitano" : "Neprocitano";
        return notification.getDateLabel() + " - " + status;
    }

    private void handleAction(@NonNull NotificationAction action) {
        switch (action) {
            case ACCEPT_INVITE:
                Toast.makeText(requireContext(), R.string.notification_action_invite, Toast.LENGTH_SHORT).show();
                break;
            case OPEN_CHAT:
                Toast.makeText(requireContext(), R.string.notification_action_chat, Toast.LENGTH_SHORT).show();
                break;
            case OPEN_LEAGUE:
                Toast.makeText(requireContext(), R.string.notification_action_league, Toast.LENGTH_SHORT).show();
                break;
            case NONE:
            default:
                break;
        }
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
