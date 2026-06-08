package com.example.slagalica.data.local;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.slagalica.data.repository.UserProfileMapper;
import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;

import java.util.Locale;
import java.util.UUID;

public final class UserPreferences {

    private static final String PREFS = "slagalica_user_profile";

    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_TOKENS = "tokens";
    private static final String KEY_STARS = "stars_total";
    private static final String KEY_LEAGUE_NAME = "league_name";
    private static final String KEY_LEAGUE_TIER = "league_tier";
    private static final String KEY_REGION = "region";
    private static final String KEY_INVITE_CODE = "invite_code";
    private static final String KEY_INVITE_PAYLOAD = "invite_payload";

    private static final String KEY_AVG_KZZ = "avg_score_ko_zna_zna";
    private static final String KEY_AVG_SPOJNICE = "avg_score_spojnice";
    private static final String KEY_AVG_MOJ_BROJ = "avg_score_moj_broj";
    private static final String KEY_AVG_KPK = "avg_score_kpk";
    private static final String KEY_AVG_ASOC = "avg_score_asocijacije";
    private static final String KEY_AVG_SKOCKO = "avg_score_skocko";

    private static final String KEY_KZZ_HITS = "kzz_hits";
    private static final String KEY_KZZ_MISSES = "kzz_misses";
    private static final String KEY_MOJ_BROJ_PCT = "moj_broj_correct_pct";
    private static final String KEY_KPK_STEPS_PCT = "kpk_step_pcts";
    private static final String KEY_ASOC_SOLVED = "asoc_solved";
    private static final String KEY_ASOC_UNSOLVED = "asoc_unsolved";
    private static final String KEY_SKOCKO_COMBO_PCT = "skocko_combo_pct";
    private static final String KEY_ASOC_GAMES = "asoc_games_count";
    private static final String KEY_SKOCKO_GAMES = "skocko_games_count";
    private static final String KEY_SPOJNICE_LINKED_PCT = "spojnice_linked_pct";
    private static final String KEY_SPOJNICE_GAMES = "spojnice_games_count";
    private static final String KEY_TOTAL_MATCHES = "total_matches";
    private static final String KEY_WIN_PCT = "matches_win_pct";
    private static final String KEY_LOSS_PCT = "matches_loss_pct";

    private final SharedPreferences prefs;

    public UserPreferences(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureSeedDefaults();
    }

    @NonNull
    public UserProfile loadProfile() {
        PlayerStatistics stats = new PlayerStatistics(
                prefs.getFloat(KEY_AVG_KZZ, 0f),
                prefs.getFloat(KEY_AVG_SPOJNICE, 0f),
                prefs.getFloat(KEY_AVG_MOJ_BROJ, 0f),
                prefs.getFloat(KEY_AVG_KPK, 0f),
                prefs.getFloat(KEY_AVG_ASOC, 0f),
                prefs.getFloat(KEY_AVG_SKOCKO, 0f),
                prefs.getInt(KEY_KZZ_HITS, 0),
                prefs.getInt(KEY_KZZ_MISSES, 0),
                prefs.getFloat(KEY_MOJ_BROJ_PCT, 0f),
                UserProfileMapper.parseStepPercents(prefs.getString(KEY_KPK_STEPS_PCT, "")),
                prefs.getInt(KEY_ASOC_SOLVED, 0),
                prefs.getInt(KEY_ASOC_UNSOLVED, 0),
                prefs.getFloat(KEY_SKOCKO_COMBO_PCT, 0f),
                prefs.getFloat(KEY_SPOJNICE_LINKED_PCT, 0f),
                prefs.getInt(KEY_TOTAL_MATCHES, 0),
                prefs.getFloat(KEY_WIN_PCT, 0f),
                prefs.getFloat(KEY_LOSS_PCT, 0f)
        );

        String invitePayload = prefs.getString(KEY_INVITE_PAYLOAD, "");
        if (invitePayload == null || invitePayload.isEmpty()) {
            invitePayload = buildInvitePayload();
        }

        return new UserProfile(
                prefs.getString(KEY_USERNAME, ""),
                prefs.getString(KEY_EMAIL, ""),
                prefs.getString(KEY_AVATAR_URI, ""),
                prefs.getLong(KEY_TOKENS, 0L),
                prefs.getLong(KEY_STARS, 0L),
                prefs.getString(KEY_LEAGUE_NAME, "Liga bronza"),
                prefs.getString(KEY_LEAGUE_TIER, "bronze"),
                prefs.getString(KEY_REGION, ""),
                invitePayload,
                stats
        );
    }

    public int getSpojniceGamesPlayed() {
        return prefs.getInt(KEY_SPOJNICE_GAMES, 0);
    }

    public void setSpojniceGamesPlayed(int count) {
        prefs.edit().putInt(KEY_SPOJNICE_GAMES, count).apply();
    }

    public int getAsocijacijeGamesPlayed() {
        return prefs.getInt(KEY_ASOC_GAMES, 0);
    }

    public void setAsocijacijeGamesPlayed(int count) {
        prefs.edit().putInt(KEY_ASOC_GAMES, count).apply();
    }

    public int getSkockoGamesPlayed() {
        return prefs.getInt(KEY_SKOCKO_GAMES, 0);
    }

    public void setSkockoGamesPlayed(int count) {
        prefs.edit().putInt(KEY_SKOCKO_GAMES, count).apply();
    }

    public void saveProfile(@NonNull UserProfile profile) {
        PlayerStatistics stats = profile.getStatistics();
        String stepPercents = stepPercentsToStorage(stats.getKorakPoKorakStepPercents());

        prefs.edit()
                .putString(KEY_USERNAME, profile.getUsername())
                .putString(KEY_EMAIL, profile.getEmail())
                .putString(KEY_AVATAR_URI, profile.getAvatarUri())
                .putLong(KEY_TOKENS, profile.getTokens())
                .putLong(KEY_STARS, profile.getTotalStars())
                .putString(KEY_LEAGUE_NAME, profile.getLeagueName())
                .putString(KEY_LEAGUE_TIER, profile.getLeagueTierKey())
                .putString(KEY_REGION, profile.getRegion())
                .putString(KEY_INVITE_PAYLOAD, profile.getInvitePayload())
                .putFloat(KEY_AVG_KZZ, stats.getAvgScoreKoZnaZna())
                .putFloat(KEY_AVG_SPOJNICE, stats.getAvgScoreSpojnice())
                .putFloat(KEY_AVG_MOJ_BROJ, stats.getAvgScoreMojBroj())
                .putFloat(KEY_AVG_KPK, stats.getAvgScoreKorakPoKorak())
                .putFloat(KEY_AVG_ASOC, stats.getAvgScoreAsocijacije())
                .putFloat(KEY_AVG_SKOCKO, stats.getAvgScoreSkocko())
                .putInt(KEY_KZZ_HITS, stats.getKoZnaZnaHits())
                .putInt(KEY_KZZ_MISSES, stats.getKoZnaZnaMisses())
                .putFloat(KEY_MOJ_BROJ_PCT, stats.getMojBrojCorrectPercent())
                .putString(KEY_KPK_STEPS_PCT, stepPercents)
                .putInt(KEY_ASOC_SOLVED, stats.getAsocijacijeSolved())
                .putInt(KEY_ASOC_UNSOLVED, stats.getAsocijacijeUnsolved())
                .putFloat(KEY_SKOCKO_COMBO_PCT, stats.getSkockoComboPercent())
                .putFloat(KEY_SPOJNICE_LINKED_PCT, stats.getSpojniceLinkedPercent())
                .putInt(KEY_TOTAL_MATCHES, stats.getTotalMatches())
                .putFloat(KEY_WIN_PCT, stats.getMatchesWinPercent())
                .putFloat(KEY_LOSS_PCT, stats.getMatchesLossPercent())
                .apply();

        syncInviteCodeFromPayload(profile.getInvitePayload());
    }

    public void saveAvatarUri(@NonNull String uriString) {
        prefs.edit().putString(KEY_AVATAR_URI, uriString).apply();
    }

    public void clearSessionFields() {
        prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_REGION)
                .remove(KEY_AVATAR_URI)
                .remove(KEY_INVITE_PAYLOAD)
                .apply();
        ensureSeedDefaults();
    }

    public void ensureSeedDefaults() {
        SharedPreferences.Editor ed = prefs.edit();
        if (!prefs.contains(KEY_LEAGUE_NAME)) {
            ed.putString(KEY_LEAGUE_NAME, "Liga bronza");
        }
        if (!prefs.contains(KEY_LEAGUE_TIER)) {
            ed.putString(KEY_LEAGUE_TIER, "bronze");
        }
        if (!prefs.contains(KEY_INVITE_CODE)) {
            ed.putString(KEY_INVITE_CODE, UUID.randomUUID().toString());
        }
        if (!prefs.contains(KEY_KPK_STEPS_PCT)) {
            ed.putString(KEY_KPK_STEPS_PCT, "0,0,0,0,0");
        }
        ed.apply();
    }

    @NonNull
    private String buildInvitePayload() {
        String code = prefs.getString(KEY_INVITE_CODE, "");
        if (code == null || code.isEmpty()) {
            code = UUID.randomUUID().toString();
            prefs.edit().putString(KEY_INVITE_CODE, code).apply();
        }
        String user = prefs.getString(KEY_USERNAME, "guest");
        return String.format(Locale.US, "slagalica://invite?user=%s&code=%s", user, code);
    }

    private void syncInviteCodeFromPayload(@NonNull String payload) {
        int codeIndex = payload.indexOf("code=");
        if (codeIndex >= 0) {
            String code = payload.substring(codeIndex + 5);
            int amp = code.indexOf('&');
            if (amp >= 0) {
                code = code.substring(0, amp);
            }
            if (!code.isEmpty()) {
                prefs.edit().putString(KEY_INVITE_CODE, code).apply();
            }
        }
    }

    @NonNull
    private static String stepPercentsToStorage(@NonNull java.util.List<Float> steps) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(steps.get(i));
        }
        return sb.toString();
    }
}
