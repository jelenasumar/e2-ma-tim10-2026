package com.example.slagalica.model.mynumber;

import androidx.annotation.NonNull;

public final class MyNumberExpressionResult {

    private final boolean valid;
    private final double value;
    private final String errorMessage;

    private MyNumberExpressionResult(
            boolean valid,
            double value,
            @NonNull String errorMessage
    ) {
        this.valid = valid;
        this.value = value;
        this.errorMessage = errorMessage;
    }

    @NonNull
    public static MyNumberExpressionResult valid(double value) {
        return new MyNumberExpressionResult(true, value, "");
    }

    @NonNull
    public static MyNumberExpressionResult invalid(@NonNull String errorMessage) {
        return new MyNumberExpressionResult(false, 0d, errorMessage);
    }

    public boolean isValid() {
        return valid;
    }

    public double getValue() {
        return value;
    }

    @NonNull
    public String getErrorMessage() {
        return errorMessage;
    }

    public int roundedValue() {
        return (int) Math.round(value);
    }

    public boolean isWholeNumber() {
        return Math.abs(value - Math.round(value)) < 0.000001d;
    }
}