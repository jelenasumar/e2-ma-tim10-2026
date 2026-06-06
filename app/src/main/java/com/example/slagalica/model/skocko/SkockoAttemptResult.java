package com.example.slagalica.model.skocko;

public final class SkockoAttemptResult {

    private final int exactMatches;
    private final int partialMatches;

    public SkockoAttemptResult(int exactMatches, int partialMatches) {
        this.exactMatches = exactMatches;
        this.partialMatches = partialMatches;
    }

    public int getExactMatches() {
        return exactMatches;
    }

    public int getPartialMatches() {
        return partialMatches;
    }

    public boolean isSolved() {
        return exactMatches == 4;
    }
}
