package com.example.slagalica.viewmodel.games;

import android.os.CountDownTimer;

import androidx.annotation.NonNull;

import com.example.slagalica.model.GameHeaderPlayerState;

import java.util.Locale;

public class SkockoViewModel extends GameViewModel {

    private static final int ROUND_NUMBER = 1;
    private static final int TOTAL_ROUNDS = 2;
    private static final long ROUND_DURATION_MILLIS = 30_000L;
    private static final long TIMER_INTERVAL_MILLIS = 1_000L;

    private CountDownTimer roundTimer;
    private int playerOneScore = 0;
    private int playerTwoScore = 0;

    public void startGameHeader(
            @NonNull GameHeaderPlayerState playerOne,
            @NonNull GameHeaderPlayerState playerTwo
    ) {
        initializeHeader(
                formatRoundText(ROUND_NUMBER, TOTAL_ROUNDS),
                formatTimeText(ROUND_DURATION_MILLIS),
                playerOne.withScore(playerOneScore),
                playerTwo.withScore(playerTwoScore)
        );
        startRoundTimer();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        stopRoundTimer();
    }

    private void startRoundTimer() {
        stopRoundTimer();
        roundTimer = new CountDownTimer(ROUND_DURATION_MILLIS, TIMER_INTERVAL_MILLIS) {
            @Override
            public void onTick(long millisUntilFinished) {
                updateTime(formatTimeText(millisUntilFinished));
            }

            @Override
            public void onFinish() {
                updateTime(formatTimeText(0));
            }
        };
        roundTimer.start();
    }

    private void stopRoundTimer() {
        if (roundTimer != null) {
            roundTimer.cancel();
            roundTimer = null;
        }
    }

    @NonNull
    private static String formatRoundText(int roundNumber, int totalRounds) {
        return String.format(Locale.getDefault(), "Runda: %d/%d", roundNumber, totalRounds);
    }

    @NonNull
    private static String formatTimeText(long millis) {
        long totalSeconds = Math.max(0, millis / 1000);
        return String.format(Locale.getDefault(), "Preostalo vreme: 00:%02d", totalSeconds);
    }
}
