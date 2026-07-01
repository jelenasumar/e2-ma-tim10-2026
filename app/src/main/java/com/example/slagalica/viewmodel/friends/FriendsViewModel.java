package com.example.slagalica.viewmodel.friends;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.data.repository.FriendsRepository;
import com.example.slagalica.data.repository.GameInviteRepository;
import com.example.slagalica.model.Friend;
import com.example.slagalica.model.SentGameInvite;

import java.util.ArrayList;
import java.util.List;

public class FriendsViewModel extends AndroidViewModel {

    private final FriendsRepository friendsRepository;
    private final GameInviteRepository inviteRepository;
    private final MutableLiveData<List<Friend>> friends = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> message = new MutableLiveData<>();
    private final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);

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
                () -> {
                    loading.setValue(false);
                    message.setValue("Poziv je poslat.");
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
            loading.setValue(true);
            inviteRepository.cancelInvite(
                    target.getInviteId(),
                    () -> {
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

    @NonNull
    private String mapError(@NonNull String error) {
        if (error.toLowerCase(java.util.Locale.ROOT).contains("permission")
                || error.toLowerCase(java.util.Locale.ROOT).contains("insufficient")) {
            return mapError("PERMISSION_DENIED");
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
            case "PERMISSION_DENIED":
                return "Nemate dozvolu za ovu akciju. Proverite da ste prijavljeni registrovanim nalogom.";
            default:
                return error;
        }
    }
}
