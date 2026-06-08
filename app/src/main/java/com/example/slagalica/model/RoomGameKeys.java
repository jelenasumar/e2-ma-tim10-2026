package com.example.slagalica.model;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.R;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class RoomGameKeys {

    public static final String KO_ZNA_ZNA = "KO_ZNA_ZNA";
    public static final String SKOCKO = "SKOCKO";
    public static final String SPOJNICE = "SPOJNICE";
    public static final String ASOCIJACIJE = "ASOCIJACIJE";

    public static final String STATUS_READY = "READY";
    public static final String STATUS_PLAYING = "PLAYING";
    public static final String STATUS_BREAK = "BREAK";
    public static final String STATUS_FINISHED = "FINISHED";

    public static final long BREAK_DURATION_MS = 10_000L;

    @NonNull
    public static final List<String> DEFAULT_GAME_ORDER = Collections.unmodifiableList(Arrays.asList(
            KO_ZNA_ZNA,
            SKOCKO,
            SPOJNICE,
            ASOCIJACIJE
    ));

    private RoomGameKeys() {
    }

    public static int destinationForGame(@NonNull String gameKey) {
        switch (gameKey) {
            case KO_ZNA_ZNA:
                return R.id.koZnaZnaFragment;
            case SKOCKO:
                return R.id.skockoFragment;
            case SPOJNICE:
                return R.id.spojniceFragment;
            case ASOCIJACIJE:
                return R.id.associationsFragment;
            default:
                return 0;
        }
    }

    @Nullable
    public static String nextGame(@NonNull List<String> gameOrder, int currentIndex) {
        int nextIndex = currentIndex + 1;
        if (nextIndex < 0 || nextIndex >= gameOrder.size()) {
            return null;
        }
        return gameOrder.get(nextIndex);
    }
}
