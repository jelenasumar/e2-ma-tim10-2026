package com.example.slagalica.data.repository;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.remote.KoZnaZnaMatchDataSource;
import com.example.slagalica.model.KoZnaZnaMatch;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.List;
import java.util.function.Consumer;

public final class KoZnaZnaMatchRepository {

    private final KoZnaZnaMatchDataSource remote;

    public KoZnaZnaMatchRepository() {
        this.remote = new KoZnaZnaMatchDataSource();
    }

    @Nullable
    public String getCurrentUid() {
        return remote.getCurrentUid();
    }

    public void createLobby(
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.createLobby(hostUid, hostUsername, onSuccess, onError);
    }

    public void joinLobby(
            @NonNull String lobbyCode,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.joinLobby(lobbyCode, guestUid, guestUsername, onSuccess, onError);
    }

    @NonNull
    public ListenerRegistration listenLobby(
            @NonNull String lobbyCode,
            @NonNull Consumer<DocumentSnapshot> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return remote.listenLobby(lobbyCode, onChanged, onError);
    }

    public void createMatchFromLobby(
            @NonNull String lobbyCode,
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull List<Integer> questionOrder,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.createMatchFromLobby(
                lobbyCode,
                hostUid,
                hostUsername,
                guestUid,
                guestUsername,
                questionOrder,
                onSuccess,
                onError
        );
    }

    public void createMatchFromRoom(
            @NonNull String roomId,
            @NonNull String hostUid,
            @NonNull String hostUsername,
            @NonNull String guestUid,
            @NonNull String guestUsername,
            @NonNull List<Integer> questionOrder,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.createMatchFromRoom(
                roomId,
                hostUid,
                hostUsername,
                guestUid,
                guestUsername,
                questionOrder,
                onSuccess,
                onError
        );
    }

    @NonNull
    public ListenerRegistration listenMatch(
            @NonNull String matchId,
            @NonNull Consumer<KoZnaZnaMatch> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return remote.listenMatch(matchId, onChanged, onError);
    }

    public void submitAnswer(
            @NonNull String matchId,
            @NonNull String playerUid,
            @NonNull String hostUid,
            int answerIndex,
            long answeredAtMs,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.submitAnswer(matchId, playerUid, hostUid, answerIndex, answeredAtMs, onSuccess, onError);
    }

    public void updatePlayerAvatar(
            @NonNull String matchId,
            @NonNull String playerUid,
            @NonNull String hostUid,
            @NonNull String avatarUri,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.updatePlayerAvatar(matchId, playerUid, hostUid, avatarUri, onSuccess, onError);
    }

    public void tryResolveQuestion(
            @NonNull String matchId,
            int correctIndex,
            @NonNull Consumer<KoZnaZnaMatch> onResolved,
            @NonNull Consumer<String> onError
    ) {
        remote.tryResolveQuestion(matchId, correctIndex, onResolved, onError);
    }

    public void advanceQuestion(
            @NonNull String matchId,
            @NonNull Consumer<KoZnaZnaMatch> onAdvanced,
            @NonNull Consumer<String> onError
    ) {
        remote.advanceQuestion(matchId, onAdvanced, onError);
    }

    public void updateStatusMessage(
            @NonNull String matchId,
            @NonNull String message,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        remote.updateStatusMessage(matchId, message, onSuccess, onError);
    }

    public void leaveLobby(@NonNull String lobbyCode) {
        remote.leaveLobby(lobbyCode);
    }
}
