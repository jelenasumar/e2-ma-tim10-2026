package com.example.slagalica.ui.challenge;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public final class ChallengeGameFlow {

    private ChallengeGameFlow() {
    }

    public static boolean isChallengeGame(@NonNull Bundle args) {
        String challengeId = args.getString(ChallengeSessionFragment.ARG_CHALLENGE_ID, "");
        return challengeId != null && !challengeId.isEmpty();
    }

    public static void onGameFinished(
            @NonNull Fragment fragment,
            @NonNull Bundle previousArgs,
            int gameScore
    ) {
        String challengeId = previousArgs.getString(ChallengeSessionFragment.ARG_CHALLENGE_ID, "");
        int gameIndex = previousArgs.getInt(ChallengeSessionFragment.ARG_GAME_INDEX, 0);
        int totalScore = previousArgs.getInt(ChallengeSessionFragment.ARG_TOTAL_SCORE, 0);

        Bundle nextArgs = new Bundle();
        nextArgs.putString(ChallengeSessionFragment.ARG_CHALLENGE_ID, challengeId);
        nextArgs.putInt(ChallengeSessionFragment.ARG_GAME_INDEX, gameIndex + 1);
        nextArgs.putInt(ChallengeSessionFragment.ARG_TOTAL_SCORE, totalScore + gameScore);

        NavHostFragment.findNavController(fragment)
                .navigate(R.id.challengeSessionFragment, nextArgs);
    }
}