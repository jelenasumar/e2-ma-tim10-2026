package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.associations.AssociationColumn;
import com.example.slagalica.model.associations.AssociationPuzzle;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class AssociationPuzzlesRepository {

    private static final String COLLECTION = "association_puzzles";
    private static final String COLUMNS = "columns";
    private static final String FINAL_ANSWER = "finalAnswer";
    private static final String CLUES = "clues";
    private static final String ANSWER = "answer";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadPuzzles(
            @NonNull Consumer<List<AssociationPuzzle>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<AssociationPuzzle> puzzles = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        AssociationPuzzle puzzle = parsePuzzle(document);
                        if (puzzle != null) {
                            puzzles.add(puzzle);
                        }
                    }
                    onSuccess.accept(puzzles);
                })
                .addOnFailureListener(error ->
                        onError.accept(error.getMessage() != null
                                ? error.getMessage()
                                : "Asocijacije nisu ucitane iz baze.")
                );
    }

    @Nullable
    private static AssociationPuzzle parsePuzzle(@NonNull DocumentSnapshot document) {
        Object rawColumns = document.get(COLUMNS);
        List<AssociationColumn> columns = parseColumns(rawColumns);
        String finalAnswer = stringOrEmpty(document.getString(FINAL_ANSWER));
        if (columns.size() != AssociationPuzzle.COLUMN_COUNT || finalAnswer.isEmpty()) {
            return null;
        }
        return new AssociationPuzzle(columns, finalAnswer);
    }

    @NonNull
    private static List<AssociationColumn> parseColumns(@Nullable Object raw) {
        List<AssociationColumn> columns = new ArrayList<>();
        if (raw instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) raw;
            addColumn(columns, map.get("A"));
            addColumn(columns, map.get("B"));
            addColumn(columns, map.get("C"));
            addColumn(columns, map.get("D"));
        } else if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                addColumn(columns, value);
            }
        }
        return columns;
    }

    private static void addColumn(
            @NonNull List<AssociationColumn> columns,
            @Nullable Object rawColumn
    ) {
        if (!(rawColumn instanceof Map)) {
            return;
        }
        Map<?, ?> map = (Map<?, ?>) rawColumn;
        List<String> clues = stringList(map.get(CLUES));
        String answer = stringOrEmpty(asString(map.get(ANSWER)));
        if (clues.size() == AssociationColumn.CLUE_COUNT && !answer.isEmpty()) {
            columns.add(new AssociationColumn(clues, answer));
        }
    }

    @NonNull
    private static List<String> stringList(@Nullable Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List) {
            for (Object value : (List<?>) raw) {
                if (value != null) {
                    values.add(String.valueOf(value));
                }
            }
        }
        return values;
    }

    @Nullable
    private static String asString(@Nullable Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }
}
