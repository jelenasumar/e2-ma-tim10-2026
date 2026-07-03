package com.example.slagalica.ui.missions;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.DailyMissionRewardHelper;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.DailyMissionProgress;
import com.example.slagalica.model.UserProfile;
import com.google.firebase.firestore.ListenerRegistration;

public class DailyMissionsFragment extends Fragment {

    private UserProfileRepository profileRepository;
    private ListenerRegistration profileListener;
    private TextView dailyMissionSummary;
    private TextView dailyMissionWinMatch;
    private TextView dailyMissionSendChat;
    private TextView dailyMissionPlayFriendly;
    private TextView dailyMissionWinTournament;
    private TextView dailyMissionBonus;
    private View dailyMissionWinMatchAction;
    private View dailyMissionSendChatAction;
    private View dailyMissionPlayFriendlyAction;
    private View dailyMissionWinTournamentAction;

    public DailyMissionsFragment() {
        super(R.layout.fragment_daily_missions);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        dailyMissionSummary = view.findViewById(R.id.dailyMissionSummary);
        dailyMissionWinMatch = view.findViewById(R.id.dailyMissionWinMatch);
        dailyMissionSendChat = view.findViewById(R.id.dailyMissionSendChat);
        dailyMissionPlayFriendly = view.findViewById(R.id.dailyMissionPlayFriendly);
        dailyMissionWinTournament = view.findViewById(R.id.dailyMissionWinTournament);
        dailyMissionBonus = view.findViewById(R.id.dailyMissionBonus);
        dailyMissionWinMatchAction = view.findViewById(R.id.dailyMissionWinMatchAction);
        dailyMissionSendChatAction = view.findViewById(R.id.dailyMissionSendChatAction);
        dailyMissionPlayFriendlyAction = view.findViewById(R.id.dailyMissionPlayFriendlyAction);
        dailyMissionWinTournamentAction = view.findViewById(R.id.dailyMissionWinTournamentAction);

        view.findViewById(R.id.dailyMissionsBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        dailyMissionWinMatchAction.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_dailyMissions_to_onlineMatchmaking)
        );
        dailyMissionSendChatAction.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_dailyMissions_to_regionChat)
        );
        dailyMissionPlayFriendlyAction.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_dailyMissions_to_inviteFriends)
        );
        dailyMissionWinTournamentAction.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigate(R.id.action_dailyMissions_to_tournament)
        );
        profileRepository = new UserProfileRepository(requireContext());
        renderDailyMissions(profileRepository.loadProfile());
        profileListener = profileRepository.listenCurrentProfile(
                this::renderDailyMissions,
                error -> { }
        );
    }

    @Override
    public void onDestroyView() {
        if (profileListener != null) {
            profileListener.remove();
            profileListener = null;
        }
        super.onDestroyView();
    }

    private void renderDailyMissions(@NonNull UserProfile profile) {
        DailyMissionProgress progress = profile.getDailyMissionProgress()
                .forDate(DailyMissionRewardHelper.todayKey());

        dailyMissionSummary.setText(getString(
                R.string.daily_missions_summary,
                progress.completedCount()
        ));
        dailyMissionWinMatch.setText(missionText(
                progress.hasWonMatch(),
                R.string.daily_mission_win_match
        ));
        dailyMissionSendChat.setText(missionText(
                progress.hasSentChatMessage(),
                R.string.daily_mission_send_chat
        ));
        dailyMissionPlayFriendly.setText(missionText(
                progress.hasPlayedFriendlyMatch(),
                R.string.daily_mission_play_friendly
        ));
        dailyMissionWinTournament.setText(missionText(
                progress.hasWonTournamentMatch(),
                R.string.daily_mission_win_tournament
        ));
        dailyMissionWinMatchAction.setVisibility(progress.hasWonMatch() ? View.GONE : View.VISIBLE);
        dailyMissionSendChatAction.setVisibility(progress.hasSentChatMessage() ? View.GONE : View.VISIBLE);
        dailyMissionPlayFriendlyAction.setVisibility(progress.hasPlayedFriendlyMatch() ? View.GONE : View.VISIBLE);
        dailyMissionWinTournamentAction.setVisibility(progress.hasWonTournamentMatch() ? View.GONE : View.VISIBLE);
        dailyMissionBonus.setText(progress.isCompletionBonusClaimed()
                ? R.string.daily_mission_bonus_done
                : R.string.daily_mission_bonus_pending);
    }

    @NonNull
    private String missionText(boolean completed, int labelResId) {
        return getString(
                completed ? R.string.daily_mission_done : R.string.daily_mission_pending,
                getString(labelResId)
        );
    }
}
