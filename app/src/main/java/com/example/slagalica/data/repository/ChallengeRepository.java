package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.model.Challenge;
import com.example.slagalica.model.ChallengeParticipant;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class ChallengeRepository {

    private static final String USERS = "users";
    private static final String REGION_CHALLENGES = "region_challenges";
    private static final String PARTICIPANTS = "participants";

    private static final int MAX_STAKE_STARS = 10;
    private static final int MAX_STAKE_TOKENS = 2;
    private static final int MAX_PARTICIPANTS = 4;

    private final FirebaseFirestore db = FirebaseFirestore.getInstance();
    private final FirebaseAuth auth = FirebaseAuth.getInstance();

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    @Nullable
    public ListenerRegistration listenRegionChallenges(
            @NonNull String regionKey,
            @NonNull Consumer<List<Challenge>> onChanged,
            @NonNull Consumer<String> onError
    ) {
        if (regionKey.isEmpty()) {
            onError.accept("Region nije izabran.");
            return null;
        }

        return db.collection(REGION_CHALLENGES)
                .whereEqualTo("regionKey", regionKey)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Izazovi nisu ucitani."));
                        return;
                    }

                    List<Challenge> challenges = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            challenges.add(Challenge.fromDocument(document));
                        }
                    }
                    onChanged.accept(challenges);
                });
    }

    @Nullable
    public ListenerRegistration listenParticipants(
            @NonNull String challengeId,
            @NonNull Consumer<List<ChallengeParticipant>> onChanged,
            @NonNull Consumer<String> onError
    ) {
        if (challengeId.isEmpty()) {
            onError.accept("Izazov nije izabran.");
            return null;
        }

        return db.collection(REGION_CHALLENGES)
                .document(challengeId)
                .collection(PARTICIPANTS)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(messageOrDefault(error, "Rezultati izazova nisu ucitani."));
                        return;
                    }

                    List<ChallengeParticipant> participants = new ArrayList<>();
                    if (snapshot != null) {
                        for (DocumentSnapshot document : snapshot.getDocuments()) {
                            participants.add(ChallengeParticipant.fromDocument(document));
                        }
                    }
                    participants.sort((a, b) -> Integer.compare(b.getScore(), a.getScore()));
                    onChanged.accept(participants);
                });
    }

    public void createChallenge(
            int stakeStars,
            int stakeTokens,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za izazov.");
            return;
        }
        if (stakeStars < 0 || stakeStars > MAX_STAKE_STARS) {
            onError.accept("Maksimalan ulog je 10 zvezda.");
            return;
        }
        if (stakeTokens < 0 || stakeTokens > MAX_STAKE_TOKENS) {
            onError.accept("Maksimalan ulog je 2 tokena.");
            return;
        }

        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference challengeRef = db.collection(REGION_CHALLENGES).document();
        DocumentReference participantRef = challengeRef.collection(PARTICIPANTS).document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot userDocument = transaction.get(userRef);
                    if (!userDocument.exists()) {
                        throw new IllegalStateException("Profil nije pronadjen.");
                    }

                    String regionKey = stringOrEmpty(userDocument.getString("regionKey"));
                    String username = stringOrDefault(userDocument.getString("username"), "Igrac");
                    long totalStars = longOrZero(userDocument.get("totalStars"));
                    long tokens = longOrZero(userDocument.get("tokens"));

                    if (regionKey.isEmpty()) {
                        throw new IllegalStateException("Region nije izabran.");
                    }
                    if (totalStars < stakeStars || tokens < stakeTokens) {
                        throw new IllegalStateException("Nemas dovoljno zvezda ili tokena.");
                    }

                    Map<String, Object> challenge = new HashMap<>();
                    challenge.put("regionKey", regionKey);
                    challenge.put("creatorUid", uid);
                    challenge.put("creatorUsername", username);
                    challenge.put("stakeStars", stakeStars);
                    challenge.put("stakeTokens", stakeTokens);
                    challenge.put("participantCount", 1);
                    challenge.put("status", Challenge.STATUS_OPEN);
                    challenge.put("winnerUid", "");
                    challenge.put("runnerUpUid", "");
                    challenge.put("createdAt", FieldValue.serverTimestamp());
                    challenge.put("updatedAt", FieldValue.serverTimestamp());

                    Map<String, Object> participant = new HashMap<>();
                    participant.put("uid", uid);
                    participant.put("username", username);
                    participant.put("score", 0);
                    participant.put("status", ChallengeParticipant.STATUS_JOINED);
                    participant.put("rewardStars", 0L);
                    participant.put("rewardTokens", 0L);
                    participant.put("joinedAt", FieldValue.serverTimestamp());

                    transaction.update(userRef, "totalStars", totalStars - stakeStars);
                    transaction.update(userRef, "tokens", tokens - stakeTokens);
                    transaction.set(challengeRef, challenge);
                    transaction.set(participantRef, participant);

                    return challengeRef.getId();
                })
                .addOnSuccessListener(onSuccess::accept)
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Izazov nije kreiran.")));
    }

    public void joinChallenge(
            @NonNull String challengeId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za izazov.");
            return;
        }

        DocumentReference userRef = db.collection(USERS).document(uid);
        DocumentReference challengeRef = db.collection(REGION_CHALLENGES).document(challengeId);
        DocumentReference participantRef = challengeRef.collection(PARTICIPANTS).document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot userDocument = transaction.get(userRef);
                    DocumentSnapshot challengeDocument = transaction.get(challengeRef);
                    DocumentSnapshot participantDocument = transaction.get(participantRef);

                    if (!userDocument.exists()) {
                        throw new IllegalStateException("Profil nije pronadjen.");
                    }
                    if (!challengeDocument.exists()) {
                        throw new IllegalStateException("Izazov nije pronadjen.");
                    }
                    if (participantDocument.exists()) {
                        throw new IllegalStateException("Vec ucestvujes u ovom izazovu.");
                    }

                    Challenge challenge = Challenge.fromDocument(challengeDocument);
                    String myRegionKey = stringOrEmpty(userDocument.getString("regionKey"));
                    long totalStars = longOrZero(userDocument.get("totalStars"));
                    long tokens = longOrZero(userDocument.get("tokens"));

                    if (!challenge.isOpen()) {
                        throw new IllegalStateException("Izazov je zavrsen.");
                    }
                    if (!challenge.getRegionKey().equals(myRegionKey)) {
                        throw new IllegalStateException("Mozes prihvatiti samo izazov svog regiona.");
                    }
                    if (challenge.getParticipantCount() >= MAX_PARTICIPANTS) {
                        throw new IllegalStateException("Izazov je popunjen.");
                    }
                    if (totalStars < challenge.getStakeStars() || tokens < challenge.getStakeTokens()) {
                        throw new IllegalStateException("Nemas dovoljno zvezda ili tokena.");
                    }

                    String username = stringOrDefault(userDocument.getString("username"), "Igrac");

                    Map<String, Object> participant = new HashMap<>();
                    participant.put("uid", uid);
                    participant.put("username", username);
                    participant.put("score", 0);
                    participant.put("status", ChallengeParticipant.STATUS_JOINED);
                    participant.put("rewardStars", 0L);
                    participant.put("rewardTokens", 0L);
                    participant.put("joinedAt", FieldValue.serverTimestamp());

                    transaction.update(userRef, "totalStars", totalStars - challenge.getStakeStars());
                    transaction.update(userRef, "tokens", tokens - challenge.getStakeTokens());
                    transaction.update(challengeRef, "participantCount", challenge.getParticipantCount() + 1);
                    transaction.update(challengeRef, "updatedAt", FieldValue.serverTimestamp());
                    transaction.set(participantRef, participant);

                    return null;
                })
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Izazov nije prihvacen.")));
    }

    public void submitResult(
            @NonNull String challengeId,
            int score,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onError.accept("Moras biti prijavljen/a za izazov.");
            return;
        }

        DocumentReference challengeRef = db.collection(REGION_CHALLENGES).document(challengeId);
        DocumentReference participantRef = challengeRef.collection(PARTICIPANTS).document(uid);

        db.runTransaction(transaction -> {
                    DocumentSnapshot challengeDocument = transaction.get(challengeRef);
                    DocumentSnapshot participantDocument = transaction.get(participantRef);

                    if (!challengeDocument.exists()) {
                        throw new IllegalStateException("Izazov nije pronadjen.");
                    }
                    if (!participantDocument.exists()) {
                        throw new IllegalStateException("Ne ucestvujes u ovom izazovu.");
                    }

                    ChallengeParticipant participant = ChallengeParticipant.fromDocument(participantDocument);
                    if (participant.isFinished()) {
                        throw new IllegalStateException("Vec si zavrsio/la ovaj izazov.");
                    }

                    Challenge challenge = Challenge.fromDocument(challengeDocument);
                    if (!challenge.isOpen()) {
                        throw new IllegalStateException("Izazov je vec zavrsen.");
                    }

                    transaction.update(participantRef, "score", score);
                    transaction.update(participantRef, "status", ChallengeParticipant.STATUS_FINISHED);
                    transaction.update(participantRef, "finishedAt", FieldValue.serverTimestamp());
                    transaction.update(challengeRef, "updatedAt", FieldValue.serverTimestamp());

                    return null;
                })
                .addOnSuccessListener(unused -> tryFinalizeChallenge(challengeId, onSuccess, onError))
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Rezultat nije sacuvan.")));
    }

    private void tryFinalizeChallenge(
            @NonNull String challengeId,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        DocumentReference challengeRef = db.collection(REGION_CHALLENGES).document(challengeId);

        challengeRef.collection(PARTICIPANTS)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<String> participantUids = new ArrayList<>();
                    for (DocumentSnapshot document : snapshot.getDocuments()) {
                        participantUids.add(document.getId());
                    }

                    db.runTransaction(transaction -> {
                                DocumentSnapshot challengeDocument = transaction.get(challengeRef);
                                if (!challengeDocument.exists()) {
                                    throw new IllegalStateException("Izazov nije pronadjen.");
                                }

                                Challenge challenge = Challenge.fromDocument(challengeDocument);
                                if (!challenge.isOpen()) {
                                    return null;
                                }
                                if (participantUids.size() < 2) {
                                    return null;
                                }

                                List<ChallengeParticipant> participants = new ArrayList<>();
                                for (String participantUid : participantUids) {
                                    DocumentReference participantRef = challengeRef
                                            .collection(PARTICIPANTS)
                                            .document(participantUid);
                                    DocumentSnapshot participantDocument = transaction.get(participantRef);
                                    if (participantDocument.exists()) {
                                        participants.add(ChallengeParticipant.fromDocument(participantDocument));
                                    }
                                }

                                if (participants.size() != challenge.getParticipantCount()) {
                                    return null;
                                }
                                for (ChallengeParticipant participant : participants) {
                                    if (!participant.isFinished()) {
                                        return null;
                                    }
                                }

                                participants.sort(Comparator.comparingInt(ChallengeParticipant::getScore).reversed());

                                ChallengeParticipant winner = participants.get(0);
                                ChallengeParticipant runnerUp = participants.size() > 1 ? participants.get(1) : null;

                                long totalStars = (long) challenge.getStakeStars() * participants.size();
                                long totalTokens = (long) challenge.getStakeTokens() * participants.size();
                                long winnerStars = (totalStars * 75L) / 100L;
                                long winnerTokens = (totalTokens * 75L) / 100L;

                                rewardParticipant(
                                        transaction,
                                        challengeRef,
                                        winner,
                                        winnerStars,
                                        winnerTokens
                                );

                                if (runnerUp != null) {
                                    rewardParticipant(
                                            transaction,
                                            challengeRef,
                                            runnerUp,
                                            challenge.getStakeStars(),
                                            challenge.getStakeTokens()
                                    );
                                }

                                transaction.update(challengeRef, "status", Challenge.STATUS_FINISHED);
                                transaction.update(challengeRef, "winnerUid", winner.getUid());
                                transaction.update(challengeRef, "runnerUpUid", runnerUp != null ? runnerUp.getUid() : "");
                                transaction.update(challengeRef, "finishedAt", FieldValue.serverTimestamp());
                                transaction.update(challengeRef, "updatedAt", FieldValue.serverTimestamp());

                                return null;
                            })
                            .addOnSuccessListener(unused -> onSuccess.run())
                            .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Izazov nije zavrsen.")));
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ucesnici izazova nisu ucitani.")));
    }

    public void loadMyParticipation(
            @NonNull String challengeId,
            @NonNull Consumer<ChallengeParticipant> onSuccess,
            @NonNull Runnable onMissing,
            @NonNull Consumer<String> onError
    ) {
        String uid = getCurrentUid();
        if (uid == null) {
            onMissing.run();
            return;
        }

        db.collection(REGION_CHALLENGES)
                .document(challengeId)
                .collection(PARTICIPANTS)
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (document.exists()) {
                        onSuccess.accept(ChallengeParticipant.fromDocument(document));
                    } else {
                        onMissing.run();
                    }
                })
                .addOnFailureListener(e -> onError.accept(messageOrDefault(e, "Ucesce nije ucitano.")));
    }

    private void rewardParticipant(
            @NonNull com.google.firebase.firestore.Transaction transaction,
            @NonNull DocumentReference challengeRef,
            @NonNull ChallengeParticipant participant,
            long stars,
            long tokens
    ) {
        DocumentReference userRef = db.collection(USERS).document(participant.getUid());
        DocumentReference participantRef = challengeRef.collection(PARTICIPANTS).document(participant.getUid());

        transaction.update(userRef, "totalStars", FieldValue.increment(stars));
        transaction.update(userRef, "tokens", FieldValue.increment(tokens));
        transaction.update(participantRef, "rewardStars", stars);
        transaction.update(participantRef, "rewardTokens", tokens);
    }

    @NonNull
    private static String stringOrEmpty(@Nullable String value) {
        return value != null ? value : "";
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

    @NonNull
    private static String messageOrDefault(@NonNull Exception error, @NonNull String fallback) {
        String message = error.getMessage();
        return message != null && !message.isEmpty() ? message : fallback;
    }
}