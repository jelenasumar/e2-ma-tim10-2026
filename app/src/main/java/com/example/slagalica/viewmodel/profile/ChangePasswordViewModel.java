package com.example.slagalica.viewmodel.profile;

import android.app.Application;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.utils.SingleLiveEvent;

public class ChangePasswordViewModel extends AndroidViewModel {

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserProfileRepository repository;

    private final MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final SingleLiveEvent<Boolean> passwordChanged = new SingleLiveEvent<>();

    public ChangePasswordViewModel(@NonNull Application application) {
        super(application);
        repository = new UserProfileRepository(application);
    }

    @NonNull
    public LiveData<Boolean> getIsLoading() {
        return isLoading;
    }

    @NonNull
    public LiveData<String> getErrorMessage() {
        return errorMessage;
    }

    @NonNull
    public LiveData<Boolean> getPasswordChanged() {
        return passwordChanged;
    }

    public void changePassword(
            @NonNull String currentPassword,
            @NonNull String newPassword,
            @NonNull String confirmPassword
    ) {
        errorMessage.setValue(null);

        String current = currentPassword.trim();
        String next = newPassword.trim();
        String confirm = confirmPassword.trim();

        if (TextUtils.isEmpty(current)
                || TextUtils.isEmpty(next)
                || TextUtils.isEmpty(confirm)) {
            errorMessage.setValue(getApplication().getString(R.string.error_fill_all_fields));
            return;
        }

        if (next.length() < MIN_PASSWORD_LENGTH) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_too_short));
            return;
        }

        if (!next.equals(confirm)) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_mismatch));
            return;
        }

        if (current.equals(next)) {
            errorMessage.setValue(getApplication().getString(R.string.error_password_same_as_old));
            return;
        }

        isLoading.setValue(true);

        repository.changePassword(
                current,
                next,
                () -> {
                    isLoading.setValue(false);
                    passwordChanged.setValue(true);
                },
                error -> {
                    isLoading.setValue(false);
                    errorMessage.setValue(mapRepositoryError(error));
                }
        );
    }

    @NonNull
    private String mapRepositoryError(@NonNull String errorCode) {
        switch (errorCode) {
            case "WRONG_CURRENT_PASSWORD":
                return getApplication().getString(R.string.error_wrong_current_password);
            case "WEAK_PASSWORD":
                return getApplication().getString(R.string.error_password_too_short);
            case "TOO_MANY_REQUESTS":
                return getApplication().getString(R.string.error_too_many_requests);
            case "NOT_LOGGED_IN":
                return getApplication().getString(R.string.error_not_logged_in);
            case "EMAIL_NOT_AVAILABLE":
                return getApplication().getString(R.string.error_email_not_available);
            case "REQUIRES_RECENT_LOGIN":
                return getApplication().getString(R.string.error_requires_recent_login);
            case "PASSWORD_CHANGE_FAILED":
                return getApplication().getString(R.string.error_password_change_failed);
            default:
                return errorCode;
        }
    }
}
