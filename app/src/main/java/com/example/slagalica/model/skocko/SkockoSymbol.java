package com.example.slagalica.model.skocko;

import androidx.annotation.StringRes;

import com.example.slagalica.R;

public enum SkockoSymbol {
    SKOCKO(R.string.skocko_symbol_skocko),
    SQUARE(R.string.skocko_symbol_square),
    CIRCLE(R.string.skocko_symbol_circle),
    HEART(R.string.skocko_symbol_heart),
    TRIANGLE(R.string.skocko_symbol_triangle),
    STAR(R.string.skocko_symbol_star);

    @StringRes
    private final int textResId;

    SkockoSymbol(@StringRes int textResId) {
        this.textResId = textResId;
    }

    @StringRes
    public int getTextResId() {
        return textResId;
    }
}
