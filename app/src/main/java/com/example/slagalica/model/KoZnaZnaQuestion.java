package com.example.slagalica.model;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

public final class KoZnaZnaQuestion {

    private final String text;
    private final List<String> options;
    private final int correctIndex;

    public KoZnaZnaQuestion(
            @NonNull String text,
            @NonNull List<String> options,
            int correctIndex
    ) {
        this.text = text;
        this.options = options;
        this.correctIndex = correctIndex;
    }

    @NonNull
    public String getText() {
        return text;
    }

    @NonNull
    public List<String> getOptions() {
        return options;
    }

    public int getCorrectIndex() {
        return correctIndex;
    }

    public boolean isCorrect(int selectedIndex) {
        return selectedIndex == correctIndex;
    }

    @NonNull
    public static List<KoZnaZnaQuestion> defaultQuestions() {
        return Arrays.asList(
                new KoZnaZnaQuestion(
                        "Koji je glavni grad Srbije?",
                        Arrays.asList("Novi Sad", "Beograd", "Niš", "Kragujevac"),
                        1
                ),
                new KoZnaZnaQuestion(
                        "Koja je najduža reka koja protiče kroz Srbiju?",
                        Arrays.asList("Tisa", "Sava", "Dunav", "Morava"),
                        2
                ),
                new KoZnaZnaQuestion(
                        "Ko je autor romana \"Na Drini ćuprija\"?",
                        Arrays.asList("Miloš Crnjanski", "Ivo Andrić", "Danilo Kiš", "Borislav Pekić"),
                        1
                ),
                new KoZnaZnaQuestion(
                        "Koliko planeta ima Sunčev sistem?",
                        Arrays.asList("7", "8", "9", "10"),
                        1
                ),
                new KoZnaZnaQuestion(
                        "Koji hemijski simbol označava element zlato?",
                        Arrays.asList("Ag", "Fe", "Au", "Cu"),
                        2
                )
        );
    }
}
