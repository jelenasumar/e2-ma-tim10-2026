package com.example.slagalica.model.korakpokorak;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class KorakPoKorakPuzzle {

    public static final int STEP_COUNT = 7;

    private final String answer;
    private final List<String> steps;

    public KorakPoKorakPuzzle(@NonNull String answer, @NonNull List<String> steps) {
        if (steps.size() != STEP_COUNT) {
            throw new IllegalArgumentException("Korak po korak puzzle must contain exactly seven steps.");
        }
        this.answer = answer;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
    }

    @NonNull
    public String getAnswer() {
        return answer;
    }

    @NonNull
    public List<String> getSteps() {
        return steps;
    }

    @NonNull
    public String getStep(int index) {
        return steps.get(index);
    }

    @NonNull
    public static List<KorakPoKorakPuzzle> defaultPuzzles() {
        return Arrays.asList(
                new KorakPoKorakPuzzle(
                        "Beograd",
                        Arrays.asList(
                                "Glavni grad jedne balkanske države",
                                "Leži na ušću dve reke",
                                "Prepoznatljiv po Kalemegdanu",
                                "Sedište Narodne skupštine Srbije",
                                "Ima mostove preko Save i Dunava",
                                "Bivša prestonica Jugoslavije",
                                "Naziv počinje slovom B"
                        )
                ),
                new KorakPoKorakPuzzle(
                        "Sunce",
                        Arrays.asList(
                                "Nalazi se u centru Sunčevog sistema",
                                "Izvor svetlosti i toplote za Zemlju",
                                "Zvezda spektralnog tipa G",
                                "Oko njega planete vrše revoluciju",
                                "Sastoji se pretežno od vodonika",
                                "Njegova energija nastaje fuzijom",
                                "Vidljiv nam je danju sa neba"
                        )
                )
        );
    }
}
