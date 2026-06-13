package com.example.slagalica.model.associations;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class AssociationColumn {

    public static final int CLUE_COUNT = 4;

    private final List<String> clues;
    private final String answer;

    public AssociationColumn(
            @NonNull List<String> clues,
            @NonNull String answer
    ) {
        if (clues.size() != CLUE_COUNT) {
            throw new IllegalArgumentException("Association column must contain exactly four clues.");
        }
        this.clues = Collections.unmodifiableList(new ArrayList<>(clues));
        this.answer = answer;
    }

    @NonNull
    public List<String> getClues() {
        return clues;
    }

    @NonNull
    public String getAnswer() {
        return answer;
    }

    @NonNull
    public String getClue(int index) {
        if (index < 0 || index >= clues.size()) {
            throw new IllegalArgumentException("Clue index is out of range.");
        }
        return clues.get(index);
    }
}
