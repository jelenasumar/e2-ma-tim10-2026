package com.example.slagalica.model.skocko;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkockoGameState {

    private final int currentRound;
    private final int activePlayerNumber;
    private final List<SkockoAttempt> attempts;
    private final List<SkockoSymbol> currentInput;
    private final List<SkockoSymbol> bonusInput;
    private final List<SkockoSymbol> secretCombination;
    private final boolean bonusPhase;
    private final boolean roundOver;
    private final boolean gameOver;

    public SkockoGameState(
            int currentRound,
            int activePlayerNumber,
            @NonNull List<SkockoAttempt> attempts,
            @NonNull List<SkockoSymbol> currentInput,
            @NonNull List<SkockoSymbol> bonusInput,
            @NonNull List<SkockoSymbol> secretCombination,
            boolean bonusPhase,
            boolean roundOver,
            boolean gameOver
    ) {
        this.currentRound = currentRound;
        this.activePlayerNumber = activePlayerNumber;
        this.attempts = Collections.unmodifiableList(new ArrayList<>(attempts));
        this.currentInput = Collections.unmodifiableList(new ArrayList<>(currentInput));
        this.bonusInput = Collections.unmodifiableList(new ArrayList<>(bonusInput));
        this.secretCombination = Collections.unmodifiableList(new ArrayList<>(secretCombination));
        this.bonusPhase = bonusPhase;
        this.roundOver = roundOver;
        this.gameOver = gameOver;
    }

    public int getCurrentRound() {
        return currentRound;
    }

    public int getActivePlayerNumber() {
        return activePlayerNumber;
    }

    @NonNull
    public List<SkockoAttempt> getAttempts() {
        return attempts;
    }

    @NonNull
    public List<SkockoSymbol> getCurrentInput() {
        return currentInput;
    }

    @NonNull
    public List<SkockoSymbol> getBonusInput() {
        return bonusInput;
    }

    @NonNull
    public List<SkockoSymbol> getSecretCombination() {
        return secretCombination;
    }

    public boolean isBonusPhase() {
        return bonusPhase;
    }

    public boolean isRoundOver() {
        return roundOver;
    }

    public boolean isGameOver() {
        return gameOver;
    }
}
