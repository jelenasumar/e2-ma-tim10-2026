package com.example.slagalica.data.remote;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.example.slagalica.data.repository.UserProfileMapper;
import com.example.slagalica.model.UserProfile;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageMetadata;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

public final class FireBaseUserDataSource {

    private static final String STORAGE_BUCKET = "gs://slagalica-5e6d9.firebasestorage.app";

    private final FirebaseAuth auth;
    private final FirebaseFirestore db;
    private final FirebaseStorage storage;

    public FireBaseUserDataSource() {
        this.auth = FirebaseAuth.getInstance();
        this.db = FirebaseFirestore.getInstance();
        this.storage = FirebaseStorage.getInstance(STORAGE_BUCKET);
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    public boolean isAnonymousUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && user.isAnonymous();
    }

    public boolean isRegisteredUser() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null && !user.isAnonymous();
    }

    public void signInAnonymously(@NonNull Runnable onSuccess, @NonNull Consumer<String> onError) {
        if (isLoggedIn()) {
            onSuccess.run();
            return;
        }
        auth.signInAnonymously()
                .addOnSuccessListener(result -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Guest sign-in failed."));
    }

    @Nullable
    public String getCurrentUid() {
        FirebaseUser user = auth.getCurrentUser();
        return user != null ? user.getUid() : null;
    }

    public void createUserWithEmailAndPassword(
            @NonNull String email,
            @NonNull String password,
            @NonNull Consumer<FirebaseUser> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        onError.accept("Registration failed.");
                        return;
                    }
                    onSuccess.accept(user);
                })
                .addOnFailureListener(e -> onError.accept(e.getMessage() != null ? e.getMessage() : "Registration failed."));
    }

    public void sendEmailVerification(
            @NonNull FirebaseUser user,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        user.sendEmailVerification()
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Email verification failed."
                ));
    }

    public void requireVerifiedEmail(
            @NonNull FirebaseUser user,
            @NonNull Runnable onVerified,
            @NonNull Consumer<String> onError
    ) {
        user.reload()
                .addOnSuccessListener(unused -> {
                    FirebaseUser refreshedUser = auth.getCurrentUser();

                    if (refreshedUser == null) {
                        onError.accept("NOT_LOGGED_IN");
                        return;
                    }

                    if (!refreshedUser.isEmailVerified()) {
                        auth.signOut();
                        onError.accept("EMAIL_NOT_VERIFIED");
                        return;
                    }

                    onVerified.run();
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Email verification check failed."
                ));
    }

    public void signInWithEmailAndPassword(
            @NonNull String email,
            @NonNull String password,
            @NonNull Consumer<FirebaseUser> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser user = result.getUser();
                    if (user == null) {
                        onError.accept("Login unsuccessful");
                        return;
                    }
                    onSuccess.accept(user);
                })
                .addOnFailureListener(e -> onError.accept(e.getMessage() != null ? e.getMessage() : "Login unsuccessful"));
    }

    public void saveUserProfile(
            @NonNull String uid,
            @NonNull UserProfile profile,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .document(uid)
                .set(UserProfileMapper.toMap(profile))
                .addOnSuccessListener(unused -> onSuccess.run())
                .addOnFailureListener(e -> onError.accept(e.getMessage() != null ? e.getMessage() : "Save failed."));
    }

    public void fetchUserProfile(
            @NonNull String uid,
            @NonNull Consumer<UserProfile> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .document(uid)
                .get()
                .addOnSuccessListener(document -> {
                    if (!document.exists()) {
                        onError.accept("Profile does not exist.");
                        return;
                    }
                    onSuccess.accept(UserProfileMapper.fromDocument(document));
                })
                .addOnFailureListener(e -> onError.accept(e.getMessage() != null ? e.getMessage() : "Load failed."));
    }

    public void findEmailByUsername(
            @NonNull String username,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        String trimmedUsername = username.trim();
        String usernameLower = trimmedUsername.toLowerCase(Locale.ROOT);

        db.collection("users")
                .whereEqualTo("usernameLower", usernameLower)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (!querySnapshot.isEmpty()) {
                        String email = querySnapshot.getDocuments().get(0).getString("email");

                        if (email == null || email.trim().isEmpty()) {
                            onError.accept("EMAIL_NOT_AVAILABLE");
                            return;
                        }

                        onSuccess.accept(email.trim().toLowerCase(Locale.ROOT));
                        return;
                    }

                    findEmailByExactUsername(trimmedUsername, onSuccess, onError);
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Username lookup failed."
                ));
    }

    private void findEmailByExactUsername(
            @NonNull String username,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        db.collection("users")
                .whereEqualTo("username", username)
                .limit(1)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    if (querySnapshot.isEmpty()) {
                        onError.accept("USERNAME_NOT_FOUND");
                        return;
                    }

                    String email = querySnapshot.getDocuments().get(0).getString("email");

                    if (email == null || email.trim().isEmpty()) {
                        onError.accept("EMAIL_NOT_AVAILABLE");
                        return;
                    }

                    onSuccess.accept(email.trim().toLowerCase(Locale.ROOT));
                })
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Username lookup failed."
                ));
    }

    @NonNull
    public ListenerRegistration listenUserProfile(
            @NonNull String uid,
            @NonNull Consumer<UserProfile> onChanged,
            @NonNull Consumer<String> onError
    ) {
        return db.collection("users")
                .document(uid)
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        onError.accept(error.getMessage() != null ? error.getMessage() : "Load failed.");
                        return;
                    }
                    if (snapshot != null && snapshot.exists()) {
                        onChanged.accept(UserProfileMapper.fromDocument(snapshot));
                    }
                });
    }

    public void uploadAvatar(
            @NonNull String uid,
            @NonNull byte[] imageBytes,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        StorageReference storageRef = storage.getReference().child("avatars").child(uid + ".jpg");
        StorageMetadata metadata = new StorageMetadata.Builder()
                .setContentType("image/jpeg")
                .build();

        storageRef.putBytes(imageBytes, metadata)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl()
                        .addOnSuccessListener(downloadUri -> saveAvatarUriToFirestore(
                                uid,
                                downloadUri.toString(),
                                onSuccess,
                                onError
                        ))
                        .addOnFailureListener(e -> onError.accept(mapStorageError(e))))
                .addOnFailureListener(e -> onError.accept(mapStorageError(e)));
    }

    public void saveAvatarUriToFirestore(
            @NonNull String uid,
            @NonNull String avatarUri,
            @NonNull Consumer<String> onSuccess,
            @NonNull Consumer<String> onError
    ) {
        Map<String, Object> updates = new HashMap<>();
        updates.put("avatarUri", avatarUri);
        db.collection("users")
                .document(uid)
                .set(updates, SetOptions.merge())
                .addOnSuccessListener(unused -> onSuccess.accept(avatarUri))
                .addOnFailureListener(e -> onError.accept(
                        e.getMessage() != null ? e.getMessage() : "Avatar save failed."));
    }

    @NonNull
    private static String mapStorageError(@NonNull Exception error) {
        String message = error.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("does not exist") || lower.contains("object not found")) {
                return "STORAGE_NOT_AVAILABLE";
            }
            if (lower.contains("permission") || lower.contains("unauthorized")) {
                return "STORAGE_PERMISSION_DENIED";
            }
        }
        return message != null ? message : "Avatar upload failed.";
    }

    public void signOut() {
        auth.signOut();
    }

    public void changePassword(
            @NonNull String currentPassword,
            @NonNull String newPassword,
            @NonNull Runnable onSuccess,
            @NonNull Consumer<String> onError
    ) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            onError.accept("NOT_LOGGED_IN");
            return;
        }

        String email = user.getEmail();
        if (email == null || email.isEmpty()) {
            onError.accept("EMAIL_NOT_AVAILABLE");
            return;
        }

        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        AuthCredential credential = EmailAuthProvider.getCredential(normalizedEmail, currentPassword);

        user.reauthenticate(credential)
                .addOnSuccessListener(unused -> {
                    FirebaseUser refreshedUser = auth.getCurrentUser();
                    if (refreshedUser == null) {
                        onError.accept("NOT_LOGGED_IN");
                        return;
                    }
                    refreshedUser.updatePassword(newPassword)
                            .addOnSuccessListener(done -> refreshedUser.reload()
                                    .addOnSuccessListener(reloaded -> onSuccess.run())
                                    .addOnFailureListener(reloadError -> onSuccess.run()))
                            .addOnFailureListener(e -> onError.accept(mapPasswordError(e)));
                })
                .addOnFailureListener(e -> onError.accept(mapPasswordError(e)));
    }

    @NonNull
    private static String mapPasswordError(@NonNull Exception error) {
        if (error instanceof FirebaseAuthInvalidCredentialsException) {
            return "WRONG_CURRENT_PASSWORD";
        }
        if (error instanceof FirebaseAuthWeakPasswordException) {
            return "WEAK_PASSWORD";
        }
        if (error instanceof FirebaseAuthException) {
            String code = ((FirebaseAuthException) error).getErrorCode();
            if (code != null) {
                switch (code) {
                    case "ERROR_WRONG_PASSWORD":
                    case "ERROR_INVALID_LOGIN_CREDENTIALS":
                    case "ERROR_USER_MISMATCH":
                    case "ERROR_INVALID_CREDENTIAL":
                        return "WRONG_CURRENT_PASSWORD";
                    case "ERROR_WEAK_PASSWORD":
                        return "WEAK_PASSWORD";
                    case "ERROR_TOO_MANY_REQUESTS":
                        return "TOO_MANY_REQUESTS";
                    case "ERROR_REQUIRES_RECENT_LOGIN":
                        return "REQUIRES_RECENT_LOGIN";
                    default:
                        break;
                }
            }
        }
        String message = error.getMessage();
        if (message != null) {
            String lower = message.toLowerCase(Locale.ROOT);
            if (lower.contains("wrong password") || lower.contains("invalid credential")) {
                return "WRONG_CURRENT_PASSWORD";
            }
            if (lower.contains("too many")) {
                return "TOO_MANY_REQUESTS";
            }
        }
        return message != null ? message : "PASSWORD_CHANGE_FAILED";
    }
}
