package com.example.slagalica.ui.notifications;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public class NotificationDestinationFragment extends Fragment {

    public static final String ARG_TITLE = "title";

    public NotificationDestinationFragment() {
        super(R.layout.fragment_notification_destination);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String title = getString(R.string.notification_destination_default_title);
        Bundle args = getArguments();
        if (args != null) {
            title = args.getString(ARG_TITLE, title);
        }

        TextView titleView = view.findViewById(R.id.notificationDestinationTitle);
        titleView.setText(title);
        view.findViewById(R.id.notificationDestinationBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }
}
