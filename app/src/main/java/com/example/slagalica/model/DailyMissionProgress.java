package com.example.slagalica.model;

import androidx.annotation.NonNull;

public final class DailyMissionProgress {

    public static final String MISSION_WIN_MATCH = "WIN_MATCH";
    public static final String MISSION_SEND_CHAT = "SEND_CHAT";
    public static final String MISSION_PLAY_FRIENDLY = "PLAY_FRIENDLY";
    public static final String MISSION_WIN_TOURNAMENT = "WIN_TOURNAMENT";

    private final String dateKey;
    private final boolean wonMatch;
    private final boolean sentChatMessage;
    private final boolean playedFriendlyMatch;
    private final boolean wonTournamentMatch;
    private final boolean completionBonusClaimed;

    public DailyMissionProgress(
            @NonNull String dateKey,
            boolean wonMatch,
            boolean sentChatMessage,
            boolean playedFriendlyMatch,
            boolean wonTournamentMatch,
            boolean completionBonusClaimed
    ) {
        this.dateKey = dateKey;
        this.wonMatch = wonMatch;
        this.sentChatMessage = sentChatMessage;
        this.playedFriendlyMatch = playedFriendlyMatch;
        this.wonTournamentMatch = wonTournamentMatch;
        this.completionBonusClaimed = completionBonusClaimed;
    }

    @NonNull
    public static DailyMissionProgress empty(@NonNull String dateKey) {
        return new DailyMissionProgress(dateKey, false, false, false, false, false);
    }

    @NonNull
    public DailyMissionProgress forDate(@NonNull String currentDateKey) {
        if (dateKey.equals(currentDateKey)) {
            return this;
        }
        return empty(currentDateKey);
    }

    @NonNull
    public DailyMissionProgress markCompleted(@NonNull String missionKey) {
        switch (missionKey) {
            case MISSION_WIN_MATCH:
                return new DailyMissionProgress(dateKey, true, sentChatMessage, playedFriendlyMatch, wonTournamentMatch, completionBonusClaimed);
            case MISSION_SEND_CHAT:
                return new DailyMissionProgress(dateKey, wonMatch, true, playedFriendlyMatch, wonTournamentMatch, completionBonusClaimed);
            case MISSION_PLAY_FRIENDLY:
                return new DailyMissionProgress(dateKey, wonMatch, sentChatMessage, true, wonTournamentMatch, completionBonusClaimed);
            case MISSION_WIN_TOURNAMENT:
                return new DailyMissionProgress(dateKey, wonMatch, sentChatMessage, playedFriendlyMatch, true, completionBonusClaimed);
            default:
                return this;
        }
    }

    @NonNull
    public DailyMissionProgress withCompletionBonusClaimed() {
        return new DailyMissionProgress(dateKey, wonMatch, sentChatMessage, playedFriendlyMatch, wonTournamentMatch, true);
    }

    public boolean isCompleted(@NonNull String missionKey) {
        switch (missionKey) {
            case MISSION_WIN_MATCH:
                return wonMatch;
            case MISSION_SEND_CHAT:
                return sentChatMessage;
            case MISSION_PLAY_FRIENDLY:
                return playedFriendlyMatch;
            case MISSION_WIN_TOURNAMENT:
                return wonTournamentMatch;
            default:
                return false;
        }
    }

    public int completedCount() {
        int count = 0;
        if (wonMatch) {
            count++;
        }
        if (sentChatMessage) {
            count++;
        }
        if (playedFriendlyMatch) {
            count++;
        }
        if (wonTournamentMatch) {
            count++;
        }
        return count;
    }

    public boolean isAllCompleted() {
        return completedCount() == 4;
    }

    @NonNull
    public String getDateKey() {
        return dateKey;
    }

    public boolean hasWonMatch() {
        return wonMatch;
    }

    public boolean hasSentChatMessage() {
        return sentChatMessage;
    }

    public boolean hasPlayedFriendlyMatch() {
        return playedFriendlyMatch;
    }

    public boolean hasWonTournamentMatch() {
        return wonTournamentMatch;
    }

    public boolean isCompletionBonusClaimed() {
        return completionBonusClaimed;
    }
}
