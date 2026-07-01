package com.example.slagalica.viewmodel.friends;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.FriendsRepository;
import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.model.Friend;
import com.example.slagalica.model.SentGameInvite;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FriendsViewModel extends AndroidViewModel {

    private final FriendsRepository friendsRepository;
    private final GameInviteRepository inviteRepository;
    private final MutableLiveData<List<Friend>> friends = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    private final Handler inviteExpireHandler = new Handler(Looper.getMainLooper());
    private final Map<String, Runnable> scheduledInviteExpirations = new HashMap<>();

    public FriendsViewModel(@NonNull Application application) {
        super(application);
        friendsRepository = new FriendsRepository(application);
        inviteRepository = new GameInviteRepository();
    }

    @NonNull
    public LiveData<List<Friend>> getFriends() {
        return friends;
    }

    @NonNull
    public LiveData<String> getMessage() {
        return message;
    }

    @NonNull
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    public void loadFriends() {
        loading.setValue(true);
        friendsRepository.loadFriends(
                loadedFriends -> {
                    loading.setValue(false);
                    friends.setValue(loadedFriends);
                },
                error -> {
                    loading.setValue(false);
                    message.setValue(mapError(error));
                }
        );
    }

    public void searchAndAddFriend(@NonNull String username) {
        if (username.trim().isEmpty()) {
            message.setValue("Unesite korisnicko ime.");
            return;
        }
        loading.setValue(true);
        friendsRepository.searchByUsername(
                username.trim(),
                (uid, profile) -> friendsRepository.addFriend(
                        uid,
                        () -> {
                            loading.setValue(false);
                            message.setValue(profile.getUsername() + " je dodat/a u prijatelje.");
                            loadFriends();
                        },
                        error -> {
                            loading.setValue(false);
                            message.setValue(mapError(error));
                        }
                ),
                error -> {
                    loading.setValue(false);
                    message.setValue(mapError(error));
                }
        );
    }

    public void addFriendFromQr(@NonNull String qrContent) {
        loading.setValue(true);
        friendsRepository.addFriendFromQr(
                qrContent,
                () -> {
                    loading.setValue(false);
                    message.setValue("Prijatelj je dodat preko QR koda.");
                    loadFriends();
                },
                error -> {
                    loading.setValue(false);
                    message.setValue(mapError(error));
                }
        );
    }

    public void sendInvite(@NonNull Friend friend) {
        if (!friend.canInvite()) {
            if (friend.isInActiveGame()) {
                message.setValue("Prijatelj trenutno igra partiju.");
            } else if (friend.isInvitePending()) {
                message.setValue("Poziv je vec poslat.");
            }
            return;
        }

        loading.setValue(true);
        inviteRepository.sendInvite(
                friend.toInviteUser(),
                sentInvite -> {
                    loading.setValue(false);
                    message.setValue("Poziv je poslat.");
                    scheduleSentInviteExpiry(sentInvite);
                    loadFriends();
                },
                error -> {
                    loading.setValue(false);
                    message.setValue(mapError(error));
                }
        );
    }

    public void cancelInvite(@NonNull Friend friend) {
        inviteRepository.loadPendingSentInvites(invites -> {
            SentGameInvite target = null;
            for (SentGameInvite invite : invites) {
                if (friend.getUid().equals(invite.getToUid())) {
                    target = invite;
                    break;
                }
            }
            if (target == null) {
                message.setValue("Nema aktivnog poziva.");
                loadFriends();
                return;
            }
            final SentGameInvite inviteToCancel = target;
            loading.setValue(true);
            inviteRepository.cancelInvite(
                    inviteToCancel.getInviteId(),
                    () -> {
                        cancelScheduledExpiry(inviteToCancel.getInviteId());
                        loading.setValue(false);
                        message.setValue("Poziv je otkazan.");
                        loadFriends();
                    },
                    error -> {
                        loading.setValue(false);
                        message.setValue(mapError(error));
                    }
            );
        }, error -> message.setValue(mapError(error)));
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        inviteExpireHandler.removeCallbacksAndMessages(null);
        scheduledInviteExpirations.clear();
    }

    private void scheduleSentInviteExpiry(@NonNull SentGameInvite invite) {
        cancelScheduledExpiry(invite.getInviteId());
        Runnable expiryRunnable = () -> {
            scheduledInviteExpirations.remove(invite.getInviteId());
            inviteRepository.expireInviteIfPending(
                    invite.getInviteId(),
                    invite.getNotificationId(),
                    () -> {
                    }
            );
        };
        scheduledInviteExpirations.put(invite.getInviteId(), expiryRunnable);
        inviteExpireHandler.postDelayed(expiryRunnable, GameInviteRepository.INVITE_EXPIRE_MS);
    }

    private void cancelScheduledExpiry(@NonNull String inviteId) {
        Runnable expiryRunnable = scheduledInviteExpirations.remove(inviteId);
        if (expiryRunnable != null) {
            inviteExpireHandler.removeCallbacks(expiryRunnable);
        }
    }

    @NonNull
    private String mapError(@NonNull String error) {
        if ("PERMISSION_DENIED".equals(error)) {
            return "Nemate dozvolu za ovu akciju. Proverite da ste prijavljeni registrovanim nalogom.";
        }
        String lower = error.toLowerCase(Locale.ROOT);
        if (lower.contains("permission") || lower.contains("insufficient")) {
            return "Nemate dozvolu za ovu akciju. Proverite da ste prijavljeni registrovanim nalogom.";
        }
        switch (error) {
            case "USERNAME_NOT_FOUND":
                return "Korisnik nije pronadjen.";
            case "ALREADY_FRIEND":
                return "Korisnik je vec u listi prijatelja.";
            case "CANNOT_ADD_SELF":
                return "Ne mozete dodati sami sebe.";
            case "INVALID_QR":
                return "QR kod nije validan.";
            case "FRIEND_IN_GAME":
                return "Prijatelj trenutno igra partiju.";
            case "NOT_LOGGED_IN":
                return "Morate biti prijavljeni.";
            default:
                return error;
        }
    }
}
