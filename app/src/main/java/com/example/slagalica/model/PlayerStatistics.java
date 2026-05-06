package com.example.slagalica.model;

import java.util.Arrays;

/**
 * Aggregated player statistics persisted locally until backend/game modules write real values.
 */
public final class PlayerStatistics {

    private final float avgScoreKoZnaZna;
    private final float avgScoreSpojnice;
    private final float avgScoreMojBroj;
    private final float avgScoreKorakPoKorak;
    private final float avgScoreAsocijacije;
    private final float avgScoreSkocko;
    private final int koZnaZnaHits;
    private final int koZnaZnaMisses;
    private final float mojBrojCorrectPercent;
    private final float[] korakPoKorakStepPercents;
    private final int asocijacijeSolved;
    private final int asocijacijeUnsolved;
    private final float skockoComboPercent;
    private final float spojniceLinkedPercent;
    private final int totalMatches;
    private final float matchesWinPercent;
    private final float matchesLossPercent;

    public PlayerStatistics(
            float avgScoreKoZnaZna,
            float avgScoreSpojnice,
            float avgScoreMojBroj,
            float avgScoreKorakPoKorak,
            float avgScoreAsocijacije,
            float avgScoreSkocko,
            int koZnaZnaHits,
            int koZnaZnaMisses,
            float mojBrojCorrectPercent,
            float[] korakPoKorakStepPercents,
            int asocijacijeSolved,
            int asocijacijeUnsolved,
            float skockoComboPercent,
            float spojniceLinkedPercent,
            int totalMatches,
            float matchesWinPercent,
            float matchesLossPercent
    ) {
        this.avgScoreKoZnaZna = avgScoreKoZnaZna;
        this.avgScoreSpojnice = avgScoreSpojnice;
        this.avgScoreMojBroj = avgScoreMojBroj;
        this.avgScoreKorakPoKorak = avgScoreKorakPoKorak;
        this.avgScoreAsocijacije = avgScoreAsocijacije;
        this.avgScoreSkocko = avgScoreSkocko;
        this.koZnaZnaHits = koZnaZnaHits;
        this.koZnaZnaMisses = koZnaZnaMisses;
        this.mojBrojCorrectPercent = mojBrojCorrectPercent;
        this.korakPoKorakStepPercents = Arrays.copyOf(
                korakPoKorakStepPercents,
                korakPoKorakStepPercents.length
        );
        this.asocijacijeSolved = asocijacijeSolved;
        this.asocijacijeUnsolved = asocijacijeUnsolved;
        this.skockoComboPercent = skockoComboPercent;
        this.spojniceLinkedPercent = spojniceLinkedPercent;
        this.totalMatches = totalMatches;
        this.matchesWinPercent = matchesWinPercent;
        this.matchesLossPercent = matchesLossPercent;
    }

    public float getAvgScoreKoZnaZna() {
        return avgScoreKoZnaZna;
    }

    public float getAvgScoreSpojnice() {
        return avgScoreSpojnice;
    }

    public float getAvgScoreMojBroj() {
        return avgScoreMojBroj;
    }

    public float getAvgScoreKorakPoKorak() {
        return avgScoreKorakPoKorak;
    }

    public float getAvgScoreAsocijacije() {
        return avgScoreAsocijacije;
    }

    public float getAvgScoreSkocko() {
        return avgScoreSkocko;
    }

    public int getKoZnaZnaHits() {
        return koZnaZnaHits;
    }

    public int getKoZnaZnaMisses() {
        return koZnaZnaMisses;
    }

    public float getMojBrojCorrectPercent() {
        return mojBrojCorrectPercent;
    }

    public float[] getKorakPoKorakStepPercents() {
        return Arrays.copyOf(korakPoKorakStepPercents, korakPoKorakStepPercents.length);
    }

    public int getAsocijacijeSolved() {
        return asocijacijeSolved;
    }

    public int getAsocijacijeUnsolved() {
        return asocijacijeUnsolved;
    }

    public float getSkockoComboPercent() {
        return skockoComboPercent;
    }

    public float getSpojniceLinkedPercent() {
        return spojniceLinkedPercent;
    }

    public int getTotalMatches() {
        return totalMatches;
    }

    public float getMatchesWinPercent() {
        return matchesWinPercent;
    }

    public float getMatchesLossPercent() {
        return matchesLossPercent;
    }
}
