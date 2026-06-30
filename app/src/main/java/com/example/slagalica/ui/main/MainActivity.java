package com.example.slagalica.ui.main;

import android.Manifest;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.content.pm.PackageManager;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.graphics.Insets;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.data.repository.NotificationsRepository;
import com.example.slagalica.model.LeagueChangeEvent;
import com.example.slagalica.model.SystemNotification;
import com.example.slagalica.utils.LeagueChangeNotifier;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_GUEST_MODE = "com.example.slagalica.EXTRA_GUEST_MODE";
    public static final String EXTRA_OPEN_NOTIFICATIONS = "com.example.slagalica.EXTRA_OPEN_NOTIFICATIONS";
    public static final String EXTRA_OPEN_ROOM_ID = "com.example.slagalica.EXTRA_OPEN_ROOM_ID";
    private static final int NOTIFICATION_PERMISSION_REQUEST = 1001;

    private final Set<String> knownNotificationIds = new HashSet<>();
    private GameInviteRepository inviteRepository;
    private NotificationsRepository notificationsRepository;
    private ListenerRegistration notificationsListener;
    private boolean initialNotificationsLoaded;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        requestNotificationPermissionIfNeeded();
        inviteRepository = new GameInviteRepository();
        notificationsRepository = new NotificationsRepository(this);
        listenForSystemNotifications();
        observeLeagueChanges();
        openRequestedDestination(getIntent());
    }

    private void observeLeagueChanges() {
        LeagueChangeNotifier.get().observe(this, this::showLeagueChangeDialog);
    }

    private void showLeagueChangeDialog(@NonNull LeagueChangeEvent event) {
        new AlertDialog.Builder(this)
                .setTitle(R.string.league_dialog_title)
                .setMessage(event.getMessage())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (notificationsListener != null) {
            notificationsListener.remove();
            notificationsListener = null;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        openRequestedDestination(intent);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return;
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            return;
        }
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                NOTIFICATION_PERMISSION_REQUEST
        );
    }

    private void listenForSystemNotifications() {
        notificationsListener = inviteRepository.listenNotifications(
                this::handleNotificationSnapshot,
                error -> {
                }
        );
    }

    private void handleNotificationSnapshot(List<SystemNotification> notifications) {
        if (!initialNotificationsLoaded) {
            for (SystemNotification notification : notifications) {
                knownNotificationIds.add(notification.getId());
            }
            initialNotificationsLoaded = true;
            return;
        }

        for (SystemNotification notification : notifications) {
            if (knownNotificationIds.add(notification.getId())) {
                notificationsRepository.showSystemNotification(notification);
            }
        }
    }

    private void openRequestedDestination(Intent intent) {
        if (intent == null) {
            return;
        }
        openRoomIfRequested(intent);
        openNotificationsIfRequested(intent);
    }

    private void openNotificationsIfRequested(Intent intent) {
        if (intent == null || !intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATIONS, false)) {
            return;
        }

        intent.removeExtra(EXTRA_OPEN_NOTIFICATIONS);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }

        NavController navController = navHostFragment.getNavController();
        if (navController.getCurrentDestination() == null
                || navController.getCurrentDestination().getId() == R.id.notificationsFragment) {
            return;
        }
        navController.navigate(R.id.notificationsFragment);
    }

    private void openRoomIfRequested(Intent intent) {
        String roomId = intent.getStringExtra(EXTRA_OPEN_ROOM_ID);
        if (roomId == null || roomId.isEmpty()) {
            return;
        }

        intent.removeExtra(EXTRA_OPEN_ROOM_ID);
        NavHostFragment navHostFragment = (NavHostFragment) getSupportFragmentManager()
                .findFragmentById(R.id.nav_host_fragment);
        if (navHostFragment == null) {
            return;
        }

        Bundle args = new Bundle();
        args.putString("roomId", roomId);
        navHostFragment.getNavController().navigate(R.id.roomSessionFragment, args);
    }
}
