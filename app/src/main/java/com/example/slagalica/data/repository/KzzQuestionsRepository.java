package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.KoZnaZnaQuestion;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

public final class KzzQuestionsRepository {

    public static final String COLLECTION = "kzz_questions";
    private static final int DEFAULT_MATCH_QUESTION_COUNT = 5;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();

    public void loadQuestions(
            @NonNull Consumer<List<KoZnaZnaQuestion>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<KoZnaZnaQuestion> questions = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        KoZnaZnaQuestion question = parseQuestion(document);
                        if (question != null) {
                            questions.add(question);
                        }
                    }
                    onSuccess.accept(questions);
                })
                .addOnFailureListener(error ->
                        onError.accept(error.getMessage() != null
                                ? error.getMessage()
                                : "Pitanja nisu ucitana iz baze.")
                );
    }

    @NonNull
    public static List<Integer> shuffledIndices(int questionCount) {
        int count = Math.max(1, Math.min(questionCount, DEFAULT_MATCH_QUESTION_COUNT));
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            order.add(i);
        }
        Collections.shuffle(order, new Random());
        return order;
    }

    @Nullable
    private static KoZnaZnaQuestion parseQuestion(@NonNull DocumentSnapshot document) {
        if (Boolean.FALSE.equals(document.getBoolean("active"))) {
            return null;
        }
        String text = stringOrEmpty(document.getString("text"));
        List<String> options = stringList(document.get("options"));
        int correctIndex = intOrZero(document.get("correctIndex"));
        if (text.isEmpty() || options.size() < 4 || correctIndex < 0 || correctIndex >= options.size()) {
            return null;
        }
        return new KoZnaZnaQuestion(text, options.subList(0, 4), correctIndex);
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

    private static int intOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
