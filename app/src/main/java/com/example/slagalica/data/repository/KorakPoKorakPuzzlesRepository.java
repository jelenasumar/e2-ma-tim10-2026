package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.korakpokorak.KorakPoKorakPuzzle;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class KorakPoKorakPuzzlesRepository {

    public static final String COLLECTION = "korak_po_korak_puzzles";

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadPuzzles(
            @NonNull Consumer<List<KorakPoKorakPuzzle>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KorakPoKorakPuzzle> puzzles = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        KorakPoKorakPuzzle puzzle = parsePuzzle(document);
                        if (puzzle != null) {
                            puzzles.add(puzzle);
                        }
                    }
                    onSuccess.accept(puzzles);
                })
                .addOnFailureListener(error ->
                        onError.accept(error.getMessage() != null
                                ? error.getMessage()
                                : "Korak po korak zagonetke nisu ucitane iz baze.")
                );
    }

    @Nullable
    private static KorakPoKorakPuzzle parsePuzzle(@NonNull DocumentSnapshot document) {
        if (Boolean.FALSE.equals(document.getBoolean("active"))) {
            return null;
        }
        String answer = stringOrEmpty(document.getString("answer"));
        List<String> steps = stringList(document.get("steps"));
        if (answer.isEmpty() || steps.size() != KorakPoKorakPuzzle.STEP_COUNT) {
            return null;
        }
        return new KorakPoKorakPuzzle(answer, steps);
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
