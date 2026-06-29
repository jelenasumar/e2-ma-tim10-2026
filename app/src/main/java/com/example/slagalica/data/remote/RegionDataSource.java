package com.example.slagalica.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.Transaction;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class RegionDataSource {

    private static final String COLLECTION_REGIONS = "regions";
    private static final String COLLECTION_CONFIG = "config";
    private static final String DOC_CYCLE = "region_cycle";

    private final FirebaseFirestore db;

    public RegionDataSource() {
        this.db = FirebaseFirestore.getInstance();
    }

    public void fetchAllPlayerMarkers(
            @NonNull Consumer<List<Map<String, Object>>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> markers = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        String regionKey = stringOrEmpty(document.getString("regionKey"));
                        if (regionKey.isEmpty()) {
                            continue;
                        }
                        Map<String, Object> marker = new HashMap<>();
                        marker.put("uid", document.getId());
                        marker.put("username", stringOrEmpty(document.getString("username")));
                        marker.put("regionKey", regionKey);
                        marker.put("mapPointX", floatOrZero(document.get("mapPointX")));
                        marker.put("mapPointY", floatOrZero(document.get("mapPointY")));
                        markers.add(marker);
                    }
                    onSuccess.accept(markers);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo učitavanje mape.")));
    }

    public void fetchAllProfilesForCycle(
            @NonNull Consumer<List<Map<String, Object>>> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<Map<String, Object>> profiles = new ArrayList<>();
                    for (QueryDocumentSnapshot document : snapshot) {
                        Map<String, Object> row = new HashMap<>();
                        row.put("uid", document.getId());
                        row.put("username", stringOrEmpty(document.getString("username")));
                        row.put("regionKey", stringOrEmpty(document.getString("regionKey")));
                        row.put("region", stringOrEmpty(document.getString("region")));
                        row.put("monthlyStars", longOrZero(document.get("monthlyStars")));
                        row.put("starsCycleKey", stringOrEmpty(document.getString("starsCycleKey")));
                        row.put("regionRankFrame", stringOrEmpty(document.getString("regionRankFrame")));
                        row.put("lastActiveAt", longOrZero(document.get("lastActiveAt")));
                        profiles.add(row);
                    }
                    onSuccess.accept(profiles);
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo učitavanje rang liste.")));
    }

    public void fetchRegionDocument(
            @NonNull String regionKey,
            @NonNull Consumer<DocumentSnapshot> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION_REGIONS)
                .document(regionKey)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo učitavanje statistike regiona.")));
    }

    public void fetchCycleConfig(
            @NonNull Consumer<DocumentSnapshot> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION_CONFIG)
                .document(DOC_CYCLE)
                .get()
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo učitavanje ciklusa.")));
    }

    public void incrementRegionRegistration(
            @NonNull String regionKey,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection(COLLECTION_REGIONS)
                .document(regionKey)
                .set(defaultRegionDocument(regionKey), SetOptions.merge())
                .addOnSuccessListener(unused ->
                        db.collection(COLLECTION_REGIONS)
                                .document(regionKey)
                                .update("totalRegistered", com.google.firebase.firestore.FieldValue.increment(1))
                                .addOnSuccessListener(done -> onSuccess.run())
                                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo ažuriranje regiona.")))
                )
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo ažuriranje regiona.")));
    }

    public void touchPlayerActivity(
            @NonNull String uid,
            @NonNull String regionKey,
            long timestamp,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("lastActiveAt", timestamp);
        updates.put("regionKey", regionKey);
        db.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo ažuriranje aktivnosti.")));
    }

    public void saveUserRegionFields(
            @NonNull String uid,
            @NonNull Map<String, Object> updates,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspelo čuvanje profila.")));
    }

    public void processCycleRollover(
            @NonNull String newCycleKey,
            @NonNull String previousCycleKey,
            @NonNull List<String> topRegionKeys,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.runTransaction((Transaction.Function<Void>) transaction -> {
            DocumentSnapshot cycleDoc = transaction.get(
                    db.collection(COLLECTION_CONFIG).document(DOC_CYCLE)
            );
            String processed = cycleDoc.exists()
                    ? stringOrEmpty(cycleDoc.getString("currentCycleKey"))
                    : "";
            if (newCycleKey.equals(processed)) {
                return null;
            }

            Map<String, Object> cycleUpdate = new HashMap<>();
            cycleUpdate.put("currentCycleKey", newCycleKey);
            cycleUpdate.put("previousCycleKey", previousCycleKey);
            cycleUpdate.put("previousTopRegions", topRegionKeys);
            transaction.set(db.collection(COLLECTION_CONFIG).document(DOC_CYCLE), cycleUpdate, SetOptions.merge());

            for (int i = 0; i < topRegionKeys.size(); i++) {
                String regionKey = topRegionKeys.get(i);
                DocumentSnapshot regionDoc = transaction.get(
                        db.collection(COLLECTION_REGIONS).document(regionKey)
                );
                Map<String, Object> regionData = regionDoc.exists()
                        ? new HashMap<>(regionDoc.getData() != null ? regionDoc.getData() : new HashMap<>())
                        : defaultRegionDocument(regionKey);
                if (i == 0) {
                    regionData.put("podiumFirst", intOrZero(regionData.get("podiumFirst")) + 1);
                } else if (i == 1) {
                    regionData.put("podiumSecond", intOrZero(regionData.get("podiumSecond")) + 1);
                } else if (i == 2) {
                    regionData.put("podiumThird", intOrZero(regionData.get("podiumThird")) + 1);
                }
                transaction.set(db.collection(COLLECTION_REGIONS).document(regionKey), regionData, SetOptions.merge());
            }
            return null;
        }).addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Neuspela obrada ciklusa.")));
    }

    @NonNull
    private static Map<String, Object> defaultRegionDocument(@NonNull String regionKey) {
        Map<String, Object> map = new HashMap<>();
        map.put("regionKey", regionKey);
        map.put("podiumFirst", 0);
        map.put("podiumSecond", 0);
        map.put("podiumThird", 0);
        map.put("totalRegistered", 0);
        return map;
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
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

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        return error.getMessage() != null ? error.getMessage() : fallback;
    }
}
