package com.example.slagalica.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public class NotificationsFragment extends Fragment {

    private static final int CATEGORY_ALL = 0;
    private static final int CATEGORY_CHAT = 1;
    private static final int CATEGORY_RANKING = 2;
    private static final int CATEGORY_REWARD = 3;
    private static final int CATEGORY_OTHER = 4;

    private static final int STATUS_ALL = 0;
    private static final int STATUS_UNREAD = 1;
    private static final int STATUS_READ = 2;

    public NotificationsFragment() {
        super(R.layout.fragment_notifications);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button backBtn = view.findViewById(R.id.backButton);
        Spinner categoryFilterSpinner = view.findViewById(R.id.categoryFilterSpinner);
        Spinner statusFilterSpinner = view.findViewById(R.id.statusFilterSpinner);

        backBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });

        setupSpinner(categoryFilterSpinner, R.array.notification_category_filters, view);
        setupSpinner(statusFilterSpinner, R.array.notification_status_filters, view);
        applyFilters(view);
    }

    private void setupSpinner(@NonNull Spinner spinner, int arrayResource, @NonNull View root) {
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
                requireContext(),
                arrayResource,
                android.R.layout.simple_spinner_item
        );
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                applyFilters(root);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                applyFilters(root);
            }
        });
    }

    private void applyFilters(@NonNull View view) {
        int category = getSelectedCategory(view);
        int status = getSelectedStatus(view);

        setNotificationVisibility(view, R.id.rewardNotification, CATEGORY_REWARD, STATUS_UNREAD, category, status);
        setNotificationVisibility(view, R.id.friendNotification, CATEGORY_OTHER, STATUS_UNREAD, category, status);
        setNotificationVisibility(view, R.id.rankingNotification, CATEGORY_RANKING, STATUS_READ, category, status);
        setNotificationVisibility(view, R.id.chatNotification, CATEGORY_CHAT, STATUS_READ, category, status);
        setNotificationVisibility(view, R.id.leagueNotification, CATEGORY_OTHER, STATUS_UNREAD, category, status);
    }

    private int getSelectedCategory(@NonNull View view) {
        Spinner spinner = view.findViewById(R.id.categoryFilterSpinner);
        switch (spinner.getSelectedItemPosition()) {
            case 1:
                return CATEGORY_CHAT;
            case 2:
                return CATEGORY_RANKING;
            case 3:
                return CATEGORY_REWARD;
            case 4:
                return CATEGORY_OTHER;
            default:
                return CATEGORY_ALL;
        }
    }

    private int getSelectedStatus(@NonNull View view) {
        Spinner spinner = view.findViewById(R.id.statusFilterSpinner);
        switch (spinner.getSelectedItemPosition()) {
            case 1:
                return STATUS_UNREAD;
            case 2:
                return STATUS_READ;
            default:
                return STATUS_ALL;
        }
    }

    private void setNotificationVisibility(
            @NonNull View view,
            int notificationId,
            int notificationCategory,
            int notificationStatus,
            int selectedCategory,
            int selectedStatus
    ) {
        boolean categoryMatches = selectedCategory == CATEGORY_ALL || selectedCategory == notificationCategory;
        boolean statusMatches = selectedStatus == STATUS_ALL || selectedStatus == notificationStatus;
        view.findViewById(notificationId).setVisibility(categoryMatches && statusMatches ? View.VISIBLE : View.GONE);
    }
}
