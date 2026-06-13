package com.example.slagalica.model;

import java.util.Arrays;
import java.util.List;

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
    private final List<Float> korakPoKorakStepPercents;
    private final int asocijacijeSolved;
    private final int asocijacijeUnsolved;
    private final float skockoComboPercent;
    private final float spojniceLinkedPercent;
    private final int totalMatches;
    private final float matchesWinPercent;
    private final float matchesLossPercent;
    private final int matchesWon;
    private final int matchesLost;

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
            List<Float> korakPoKorakStepPercents,
            int asocijacijeSolved,
            int asocijacijeUnsolved,
            float skockoComboPercent,
            float spojniceLinkedPercent,
            int totalMatches,
            float matchesWinPercent,
            float matchesLossPercent,
            int matchesWon,
            int matchesLost
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
        this.korakPoKorakStepPercents = korakPoKorakStepPercents;
        this.asocijacijeSolved = asocijacijeSolved;
        this.asocijacijeUnsolved = asocijacijeUnsolved;
        this.skockoComboPercent = skockoComboPercent;
        this.spojniceLinkedPercent = spojniceLinkedPercent;
        this.totalMatches = totalMatches;
        this.matchesWinPercent = matchesWinPercent;
        this.matchesLossPercent = matchesLossPercent;
        this.matchesWon = matchesWon;
        this.matchesLost = matchesLost;
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

    public List<Float> getKorakPoKorakStepPercents() {
        return korakPoKorakStepPercents;
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

    public int getMatchesWon() {
        return matchesWon;
    }

    public int getMatchesLost() {
        return matchesLost;
    }
}
