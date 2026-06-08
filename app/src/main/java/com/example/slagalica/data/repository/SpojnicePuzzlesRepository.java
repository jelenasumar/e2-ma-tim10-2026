package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.spojnice.SpojnicePuzzle;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class SpojnicePuzzlesRepository {

    public static final String COLLECTION = "spojnice_puzzles";
    private static final int REQUIRED_PAIRS = 5;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadPuzzles(
            @NonNull Consumer<List<SpojnicePuzzle>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<SpojnicePuzzle> puzzles = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        SpojnicePuzzle puzzle = parsePuzzle(document);
                        if (puzzle != null) {
                            puzzles.add(puzzle);
                        }
                    }
                    onSuccess.accept(puzzles);
                })
                .addOnFailureListener(error ->
                        onError.accept(error.getMessage() != null
                                ? error.getMessage()
                                : "Spojnice nisu ucitane iz baze.")
                );
    }

    @Nullable
    private static SpojnicePuzzle parsePuzzle(@NonNull DocumentSnapshot document) {
        if (Boolean.FALSE.equals(document.getBoolean("active"))) {
            return null;
        }
        String criterion = stringOrEmpty(document.getString("criterion"));
        List<String> leftTerms = stringList(document.get("leftTerms"));
        List<String> rightTerms = stringList(document.get("rightTerms"));
        if (criterion.isEmpty()
                || leftTerms.size() != REQUIRED_PAIRS
                || rightTerms.size() != REQUIRED_PAIRS) {
            return null;
        }
        return new SpojnicePuzzle(criterion, leftTerms, rightTerms);
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

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }
}
