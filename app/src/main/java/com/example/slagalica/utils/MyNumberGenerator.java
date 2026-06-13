package com.example.slagalica.utils;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class MyNumberGenerator {

    private static final int MIN_TARGET = 20;
    private static final int MAX_TARGET = 999;

    private static final int SMALL_NUMBER_COUNT = 4;

    private static final int[] MEDIUM_NUMBERS = {10, 15, 20};
    private static final int[] LARGE_NUMBERS = {25, 50, 75, 100};

    private static final Random RANDOM = new Random();

    private MyNumberGenerator() {
    }

    public static int generateTargetNumber() {
        return MIN_TARGET + RANDOM.nextInt(MAX_TARGET - MIN_TARGET + 1);
    }

    @NonNull
    public static List<Integer> generateNumbers() {
        List<Integer> numbers = new ArrayList<>();

        for (int i = 0; i < SMALL_NUMBER_COUNT; i++) {
            numbers.add(generateSmallNumber());
        }

        numbers.add(randomFrom(MEDIUM_NUMBERS));
        numbers.add(randomFrom(LARGE_NUMBERS));

        Collections.shuffle(numbers, RANDOM);
        return numbers;
    }

    private static int generateSmallNumber() {
        return 1 + RANDOM.nextInt(9);
    }

    private static int randomFrom(@NonNull int[] values) {
        return values[RANDOM.nextInt(values.length)];
    }
}