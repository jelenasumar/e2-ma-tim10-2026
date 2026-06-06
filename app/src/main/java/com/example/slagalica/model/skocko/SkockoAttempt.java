package com.example.slagalica.model.skocko;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkockoAttempt {

    private final List<SkockoSymbol> symbols;
    private final SkockoAttemptResult result;

    public SkockoAttempt(
            @NonNull List<SkockoSymbol> symbols,
            @NonNull SkockoAttemptResult result
    ) {
        this.symbols = Collections.unmodifiableList(new ArrayList<>(symbols));
        this.result = result;
    }

    @NonNull
    public List<SkockoSymbol> getSymbols() {
        return symbols;
    }

    @NonNull
    public SkockoAttemptResult getResult() {
        return result;
    }
}
