package com.example.slagalica.model.spojnice;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class SpojnicePuzzle {

    private final String criterion;
    private final List<String> leftTerms;
    private final List<String> rightTerms;
    private final List<Integer> correctRightIndices;

    public SpojnicePuzzle(
            @NonNull String criterion,
            @NonNull List<String> leftTerms,
            @NonNull List<String> rightTerms
    ) {
        this.criterion = criterion;
        this.leftTerms = new ArrayList<>(leftTerms);
        this.rightTerms = new ArrayList<>(rightTerms);
        this.correctRightIndices = buildCorrectIndices(leftTerms, rightTerms);
    }

    @NonNull
    public String getCriterion() {
        return criterion;
    }

    @NonNull
    public List<String> getLeftTerms() {
        return new ArrayList<>(leftTerms);
    }

    @NonNull
    public List<String> getRightTerms() {
        return new ArrayList<>(rightTerms);
    }

    @NonNull
    public List<Integer> getCorrectRightIndices() {
        return new ArrayList<>(correctRightIndices);
    }

    public boolean isCorrect(int leftIndex, int selectedRightIndex) {
        if (leftIndex < 0 || leftIndex >= correctRightIndices.size()) {
            return false;
        }
        return correctRightIndices.get(leftIndex) == selectedRightIndex;
    }

    @NonNull
    public ShuffledRound shuffled(@NonNull Random random) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < rightTerms.size(); i++) {
            order.add(i);
        }
        Collections.shuffle(order, random);

        List<String> shuffledRight = new ArrayList<>();
        List<Integer> answers = new ArrayList<>();
        for (int leftIndex = 0; leftIndex < leftTerms.size(); leftIndex++) {
            int originalRightIndex = correctRightIndices.get(leftIndex);
            int shuffledIndex = order.indexOf(originalRightIndex);
            answers.add(shuffledIndex);
        }
        for (int index : order) {
            shuffledRight.add(rightTerms.get(index));
        }
        return new ShuffledRound(leftTerms, shuffledRight, answers);
    }

    @NonNull
    private static List<Integer> buildCorrectIndices(
            @NonNull List<String> left,
            @NonNull List<String> right
    ) {
        List<Integer> indices = new ArrayList<>();
        for (int i = 0; i < left.size(); i++) {
            indices.add(i);
        }
        return indices;
    }

    @NonNull
    public static List<SpojnicePuzzle> defaultPuzzles() {
        List<SpojnicePuzzle> puzzles = new ArrayList<>();
        puzzles.add(new SpojnicePuzzle(
                "Poveži izvođače sa nazivima njihovih pesama",
                List.of("Bajaga", "Đorđe Balašević", "Riblja Čorba", "Ekatarina Velika", "Zdravko Čolić"),
                List.of("Marlena", "Ne volim januar", "Kad hodaš", "Par godina za nas", "Moja tiho more")
        ));
        puzzles.add(new SpojnicePuzzle(
                "Poveži glavne gradove sa državama",
                List.of("Beograd", "Pariz", "Berlin", "Rim", "Madrid"),
                List.of("Srbija", "Francuska", "Nemačka", "Italija", "Španija")
        ));
        return puzzles;
    }

    public static final class ShuffledRound {
        private final List<String> leftTerms;
        private final List<String> rightTerms;
        private final List<Integer> answers;

        public ShuffledRound(
                @NonNull List<String> leftTerms,
                @NonNull List<String> rightTerms,
                @NonNull List<Integer> answers
        ) {
            this.leftTerms = new ArrayList<>(leftTerms);
            this.rightTerms = new ArrayList<>(rightTerms);
            this.answers = new ArrayList<>(answers);
        }

        @NonNull
        public List<String> getLeftTerms() {
            return leftTerms;
        }

        @NonNull
        public List<String> getRightTerms() {
            return rightTerms;
        }

        @NonNull
        public List<Integer> getAnswers() {
            return answers;
        }
    }
}
