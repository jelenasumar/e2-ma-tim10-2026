package com.example.slagalica.model.associations;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssociationPuzzle {

    public static final int COLUMN_COUNT = 4;

    private final List<AssociationColumn> columns;
    private final String finalAnswer;

    public AssociationPuzzle(
            @NonNull List<AssociationColumn> columns,
            @NonNull String finalAnswer
    ) {
        if (columns.size() != COLUMN_COUNT) {
            throw new IllegalArgumentException("Association puzzle must contain exactly four columns.");
        }
        this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
        this.finalAnswer = finalAnswer;
    }

    @NonNull
    public List<AssociationColumn> getColumns() {
        return columns;
    }

    @NonNull
    public AssociationColumn getColumn(int index) {
        if (index < 0 || index >= columns.size()) {
            throw new IllegalArgumentException("Column index is out of range.");
        }
        return columns.get(index);
    }

    @NonNull
    public String getFinalAnswer() {
        return finalAnswer;
    }

    @NonNull
    public static List<AssociationPuzzle> defaultPuzzles() {
        List<AssociationPuzzle> puzzles = new ArrayList<>();
        puzzles.add(new AssociationPuzzle(
                List.of(
                        column(List.of("More", "Plaza", "Talas", "Pesak"), "Obala"),
                        column(List.of("Sunce", "Toplota", "Jul", "Odmor"), "Leto"),
                        column(List.of("Kofer", "Put", "Hotel", "Mapa"), "Putovanje"),
                        column(List.of("Sladoled", "Suncobran", "Kupaci", "Krema"), "Plaza")
                ),
                "Letovanje"
        ));
        puzzles.add(new AssociationPuzzle(
                List.of(
                        column(List.of("Gol", "Teren", "Sudija", "Lopta"), "Fudbal"),
                        column(List.of("Kos", "Parket", "Petorka", "Obruc"), "Kosarka"),
                        column(List.of("Mreza", "Set", "Servis", "Reket"), "Tenis"),
                        column(List.of("Trka", "Staza", "Medalja", "Rekord"), "Atletika")
                ),
                "Sport"
        ));
        puzzles.add(new AssociationPuzzle(
                List.of(
                        column(List.of("Kafa", "Solja", "Sever", "Gorak"), "Espreso"),
                        column(List.of("Mleko", "Pena", "Belo", "Barista"), "Kapucino"),
                        column(List.of("Led", "Casa", "Leto", "Hladno"), "Frape"),
                        column(List.of("Zrno", "Mlin", "Miris", "Przenje"), "Kafa")
                ),
                "Kafic"
        ));
        puzzles.add(new AssociationPuzzle(
                List.of(
                        column(List.of("Knjiga", "Tabla", "Cas", "Dnevnik"), "Skola"),
                        column(List.of("Profesor", "Ucenik", "Ocena", "Znanje"), "Nastava"),
                        column(List.of("Klupa", "Zvono", "Odmor", "Ucionica"), "Razred"),
                        column(List.of("Ispit", "Diploma", "Indeks", "Predavanje"), "Fakultet")
                ),
                "Obrazovanje"
        ));
        puzzles.add(new AssociationPuzzle(
                List.of(
                        column(List.of("Kralj", "Dama", "Top", "Lovac"), "Sah"),
                        column(List.of("Kockica", "Figura", "Polje", "Tabla"), "Drustvena igra"),
                        column(List.of("As", "Boja", "Spil", "Dzul"), "Karte"),
                        column(List.of("Runda", "Bod", "Protivnik", "Pobeda"), "Takmicenje")
                ),
                "Igra"
        ));
        return Collections.unmodifiableList(puzzles);
    }

    @NonNull
    private static AssociationColumn column(
            @NonNull List<String> clues,
            @NonNull String answer
    ) {
        return new AssociationColumn(clues, answer);
    }
}
