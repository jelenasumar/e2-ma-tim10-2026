package com.example.slagalica.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import com.example.slagalica.model.PlayerStatistics;
import com.example.slagalica.model.UserProfile;

import java.util.Locale;
import java.util.UUID;

/**
 * Local persistence for profile and statistics. Games or a future Java backend client can update
 * values through the same keys.
 */
public final class UserProfileRepository {

    private static final String PREFS = "slagalica_user_profile";

    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";

    /** Registrovani nalog (ostaje posle logout-a). */
    private static final String KEY_ACC_EMAIL = "acc_email";
    private static final String KEY_ACC_USERNAME = "acc_username";
    private static final String KEY_ACC_REGION = "acc_region";
    private static final String KEY_ACC_PASSWORD = "acc_password";

    private static final String KEY_AVATAR_URI = "avatar_uri";
    private static final String KEY_TOKENS = "tokens";
    private static final String KEY_STARS = "stars_total";
    private static final String KEY_LEAGUE_NAME = "league_name";
    private static final String KEY_LEAGUE_TIER = "league_tier";
    private static final String KEY_REGION = "region";
    private static final String KEY_INVITE_CODE = "invite_code";

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
    private static final String KEY_SPOJNICE_LINKED_PCT = "spojnice_linked_pct";
    private static final String KEY_TOTAL_MATCHES = "total_matches";
    private static final String KEY_WIN_PCT = "matches_win_pct";
    private static final String KEY_LOSS_PCT = "matches_loss_pct";

    private final SharedPreferences prefs;

    public UserProfileRepository(@NonNull Context context) {
        this.prefs = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        ensureSeedDefaults();
        if (!hasRegisteredAccount()) {
            prefs.edit()
                    .remove(KEY_USERNAME)
                    .remove(KEY_EMAIL)
                    .remove(KEY_REGION)
                    .apply();
        }
    }

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
                parseStepPercents(prefs.getString(KEY_KPK_STEPS_PCT, "")),
                prefs.getInt(KEY_ASOC_SOLVED, 0),
                prefs.getInt(KEY_ASOC_UNSOLVED, 0),
                prefs.getFloat(KEY_SKOCKO_COMBO_PCT, 0f),
                prefs.getFloat(KEY_SPOJNICE_LINKED_PCT, 0f),
                prefs.getInt(KEY_TOTAL_MATCHES, 0),
                prefs.getFloat(KEY_WIN_PCT, 0f),
                prefs.getFloat(KEY_LOSS_PCT, 0f)
        );

        return new UserProfile(
                prefs.getString(KEY_USERNAME, ""),
                prefs.getString(KEY_EMAIL, ""),
                prefs.getString(KEY_AVATAR_URI, ""),
                prefs.getLong(KEY_TOKENS, 0L),
                prefs.getLong(KEY_STARS, 0L),
                prefs.getString(KEY_LEAGUE_NAME, ""),
                prefs.getString(KEY_LEAGUE_TIER, "bronze"),
                prefs.getString(KEY_REGION, ""),
                buildInvitePayload(),
                stats
        );
    }

    public void saveAvatarUri(@NonNull String uriString) {
        prefs.edit().putString(KEY_AVATAR_URI, uriString).apply();
    }

    public boolean hasRegisteredAccount() {
        String e = prefs.getString(KEY_ACC_EMAIL, "");
        return e != null && !e.isEmpty();
    }

    /**
     * Čuva korisnika iz registracije i odmah puni podatke za prikaz na profilu.
     * Lozinka je lokalno u SharedPreferences (samo za razvoj bez backend-a).
     */
    public void saveRegisteredAccount(
            @NonNull String email,
            @NonNull String username,
            @NonNull String region,
            @NonNull String password
    ) {
        String em = normalizeEmail(email);
        String un = username.trim();
        String reg = region.trim();
        prefs.edit()
                .putString(KEY_ACC_EMAIL, em)
                .putString(KEY_ACC_USERNAME, un)
                .putString(KEY_ACC_REGION, reg)
                .putString(KEY_ACC_PASSWORD, password)
                .putString(KEY_USERNAME, un)
                .putString(KEY_EMAIL, em)
                .putString(KEY_REGION, reg)
                .apply();
        ensureInviteCodeExists();
        ensureSeedDefaults();
    }

    /**
     * Provera prijave; ako je uspešna, podaci za profil se ponovo upisuju iz naloga.
     */
    public boolean tryLogin(@NonNull String email, @NonNull String password) {
        String savedEmail = prefs.getString(KEY_ACC_EMAIL, "");
        String savedPass = prefs.getString(KEY_ACC_PASSWORD, "");
        if (savedEmail == null || savedEmail.isEmpty()) {
            return false;
        }
        if (!savedEmail.equals(normalizeEmail(email))) {
            return false;
        }
        if (!savedPass.equals(password)) {
            return false;
        }
        prefs.edit()
                .putString(KEY_USERNAME, prefs.getString(KEY_ACC_USERNAME, ""))
                .putString(KEY_EMAIL, savedEmail)
                .putString(KEY_REGION, prefs.getString(KEY_ACC_REGION, ""))
                .apply();
        return true;
    }

    /**
     * Logout: briše prikaz sesije na uređaju; registrovani nalog ostaje za sledeću prijavu.
     */
    public void clearSession() {
        prefs.edit()
                .remove(KEY_USERNAME)
                .remove(KEY_EMAIL)
                .remove(KEY_REGION)
                .remove(KEY_AVATAR_URI)
                .apply();
        ensureSeedDefaults();
    }

    private void ensureInviteCodeExists() {
        if (!prefs.contains(KEY_INVITE_CODE)) {
            prefs.edit().putString(KEY_INVITE_CODE, UUID.randomUUID().toString()).apply();
        }
    }

    private void ensureSeedDefaults() {
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
            ed.putString(KEY_KPK_STEPS_PCT, defaultStepPercents());
        }
        ed.apply();
    }

    @NonNull
    private static String normalizeEmail(@NonNull String email) {
        return email.trim().toLowerCase(Locale.ROOT);
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

    private static float[] parseStepPercents(@NonNull String raw) {
        float[] out = new float[]{0f, 0f, 0f, 0f, 0f};
        if (raw == null || raw.isEmpty()) {
            return out;
        }
        String[] parts = raw.split(",");
        for (int i = 0; i < Math.min(parts.length, out.length); i++) {
            try {
                out[i] = Float.parseFloat(parts[i].trim());
            } catch (NumberFormatException ignored) {
                out[i] = 0f;
            }
        }
        return out;
    }

    @NonNull
    private static String defaultStepPercents() {
        return "0,0,0,0,0";
    }
}
