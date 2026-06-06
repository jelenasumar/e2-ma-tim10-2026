package com.example.slagalica.model;

import androidx.annotation.Nullable;

public final class KoZnaZnaScoring {

    public static final int CORRECT_POINTS = 10;
    public static final int WRONG_POINTS = -5;
    public static final int MIN_SCORE = -25;
    public static final int MAX_SCORE = 50;
    public static final int ANSWER_PENDING = -999;
    public static final int ANSWER_SKIP = -1;

    private KoZnaZnaScoring() {
    }

    public static final class PlayerAnswer {
        private final boolean answered;
        @Nullable
        private final Boolean correct;
        private final long answerMillis;

        public PlayerAnswer(boolean answered, @Nullable Boolean correct, long answerMillis) {
            this.answered = answered;
            this.correct = correct;
            this.answerMillis = answerMillis;
        }

        public boolean isAnswered() {
            return answered;
        }

        @Nullable
        public Boolean getCorrect() {
            return correct;
        }

        public long getAnswerMillis() {
            return answerMillis;
        }
    }

    public static int[] resolveQuestion(int hostScore, int guestScore, PlayerAnswer host, PlayerAnswer guest) {
        int newHost = hostScore;
        int newGuest = guestScore;

        boolean hostValid = host.isAnswered() && host.getCorrect() != null;
        boolean guestValid = guest.isAnswered() && guest.getCorrect() != null;

        if (!hostValid && !guestValid) {
            return new int[]{newHost, newGuest};
        }

        if (hostValid && guestValid) {
            if (Boolean.TRUE.equals(host.getCorrect()) && Boolean.TRUE.equals(guest.getCorrect())) {
                if (host.getAnswerMillis() <= guest.getAnswerMillis()) {
                    newHost = clamp(newHost + CORRECT_POINTS);
                } else {
                    newGuest = clamp(newGuest + CORRECT_POINTS);
                }
                return new int[]{newHost, newGuest};
            }
            if (Boolean.TRUE.equals(host.getCorrect())) {
                newHost = clamp(newHost + CORRECT_POINTS);
            } else {
                newHost = clamp(newHost + WRONG_POINTS);
            }
            if (Boolean.TRUE.equals(guest.getCorrect())) {
                newGuest = clamp(newGuest + CORRECT_POINTS);
            } else {
                newGuest = clamp(newGuest + WRONG_POINTS);
            }
            return new int[]{newHost, newGuest};
        }

        if (hostValid) {
            newHost = clamp(newHost + (Boolean.TRUE.equals(host.getCorrect()) ? CORRECT_POINTS : WRONG_POINTS));
        }
        if (guestValid) {
            newGuest = clamp(newGuest + (Boolean.TRUE.equals(guest.getCorrect()) ? CORRECT_POINTS : WRONG_POINTS));
        }
        return new int[]{newHost, newGuest};
    }

    public static int clamp(int score) {
        return Math.max(MIN_SCORE, Math.min(MAX_SCORE, score));
    }

    @Nullable
    public static Boolean correctness(int answerIndex, int correctIndex) {
        if (answerIndex == ANSWER_SKIP || answerIndex == ANSWER_PENDING) {
            return null;
        }
        return answerIndex == correctIndex;
    }
}
