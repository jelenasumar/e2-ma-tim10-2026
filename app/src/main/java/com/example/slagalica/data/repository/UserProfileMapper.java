package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class UserProfileMapper {

    private UserProfileMapper() {
    }

    @NonNull
    public static Map<String, Object> toMap(@NonNull UserProfile profile) {
        Map<String, Object> map = new HashMap<>();
        map.put("username", profile.getUsername());
        map.put("usernameLower", profile.getUsername().trim().toLowerCase(Locale.ROOT));
        map.put("email", profile.getEmail());
        map.put("avatarUri", profile.getAvatarUri());
        map.put("tokens", profile.getTokens());
        map.put("totalStars", profile.getTotalStars());
        map.put("leagueName", profile.getLeagueName());
        map.put("leagueTierKey", profile.getLeagueTierKey());
        map.put("region", profile.getRegion());
        map.put("invitePayload", profile.getInvitePayload());
        map.put("statistics", statisticsToMap(profile.getStatistics()));
        return map;
    }

    @NonNull
    public static UserProfile fromDocument(@NonNull DocumentSnapshot document) {
        PlayerStatistics stats = statisticsFromMap(document.get("statistics"));

        return new UserProfile(
                stringOrEmpty(document.getString("username")),
                stringOrEmpty(document.getString("email")),
                stringOrEmpty(document.getString("avatarUri")),
                longOrZero(document.get("tokens")),
                longOrZero(document.get("totalStars")),
                stringOrDefault(document.getString("leagueName"), "Liga bronza"),
                stringOrDefault(document.getString("leagueTierKey"), "bronze"),
                stringOrEmpty(document.getString("region")),
                stringOrEmpty(document.getString("invitePayload")),
                stats
        );
    }

    @NonNull
    private static Map<String, Object> statisticsToMap(@NonNull PlayerStatistics stats) {
        Map<String, Object> map = new HashMap<>();
        map.put("avgScoreKoZnaZna", stats.getAvgScoreKoZnaZna());
        map.put("avgScoreSpojnice", stats.getAvgScoreSpojnice());
        map.put("avgScoreMojBroj", stats.getAvgScoreMojBroj());
        map.put("avgScoreKorakPoKorak", stats.getAvgScoreKorakPoKorak());
        map.put("avgScoreAsocijacije", stats.getAvgScoreAsocijacije());
        map.put("avgScoreSkocko", stats.getAvgScoreSkocko());
        map.put("koZnaZnaHits", stats.getKoZnaZnaHits());
        map.put("koZnaZnaMisses", stats.getKoZnaZnaMisses());
        map.put("mojBrojCorrectPercent", stats.getMojBrojCorrectPercent());
        map.put("korakPoKorakStepPercents", stepPercentsToString(stats.getKorakPoKorakStepPercents()));
        map.put("asocijacijeSolved", stats.getAsocijacijeSolved());
        map.put("asocijacijeUnsolved", stats.getAsocijacijeUnsolved());
        map.put("skockoComboPercent", stats.getSkockoComboPercent());
        map.put("spojniceLinkedPercent", stats.getSpojniceLinkedPercent());
        map.put("totalMatches", stats.getTotalMatches());
        map.put("matchesWinPercent", stats.getMatchesWinPercent());
        map.put("matchesLossPercent", stats.getMatchesLossPercent());
        map.put("matchesWon", stats.getMatchesWon());
        map.put("matchesLost", stats.getMatchesLost());
        return map;
    }

    @NonNull
    private static PlayerStatistics statisticsFromMap(@Nullable Object raw) {
        if (!(raw instanceof Map)) {
            return emptyStatistics();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) raw;

        return new PlayerStatistics(
                floatOrZero(map.get("avgScoreKoZnaZna")),
                floatOrZero(map.get("avgScoreSpojnice")),
                floatOrZero(map.get("avgScoreMojBroj")),
                floatOrZero(map.get("avgScoreKorakPoKorak")),
                floatOrZero(map.get("avgScoreAsocijacije")),
                floatOrZero(map.get("avgScoreSkocko")),
                intOrZero(map.get("koZnaZnaHits")),
                intOrZero(map.get("koZnaZnaMisses")),
                floatOrZero(map.get("mojBrojCorrectPercent")),
                parseStepPercents(stringOrEmpty(map.get("korakPoKorakStepPercents"))),
                intOrZero(map.get("asocijacijeSolved")),
                intOrZero(map.get("asocijacijeUnsolved")),
                floatOrZero(map.get("skockoComboPercent")),
                floatOrZero(map.get("spojniceLinkedPercent")),
                intOrZero(map.get("totalMatches")),
                floatOrZero(map.get("matchesWinPercent")),
                floatOrZero(map.get("matchesLossPercent")),
                intOrZero(map.get("matchesWon")),
                intOrZero(map.get("matchesLost"))
        );
    }

    @NonNull
    static PlayerStatistics emptyStatistics() {
        return new PlayerStatistics(
                0f, 0f, 0f, 0f, 0f, 0f,
                0, 0,
                0f,
                Arrays.asList(0f, 0f, 0f, 0f, 0f, 0f, 0f),
                0, 0,
                0f, 0f,
                0,
                0f, 0f,
                0, 0
        );
    }

    @NonNull
    private static String stepPercentsToString(@NonNull List<Float> steps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.US, "%.1f", steps.get(i)));
        }
        return sb.toString();
    }

    @NonNull
    public static List<Float> parseStepPercents(@NonNull String raw) {
        List<Float> out = new ArrayList<>(Arrays.asList(0f, 0f, 0f, 0f, 0f, 0f, 0f));
        if (raw.isEmpty()) {
            return out;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < Math.min(parts.length, out.size()); i++) {
            try {
                out.set(i, Float.parseFloat(parts[i].trim()));
            } catch (NumberFormatException ignored) {
                out.set(i, 0f);
            }
        }
        return out;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
    }

    @NonNull
    private static String stringOrEmpty(@Nullable Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    @NonNull
    private static String stringOrDefault(@Nullable String value, @NonNull String fallback) {
        return value != null && !value.isEmpty() ? value : fallback;
    }

    private static long longOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0L;
    }

    private static float floatOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).floatValue();
        }
        return 0f;
    }

    private static int intOrZero(@Nullable Object value) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}
