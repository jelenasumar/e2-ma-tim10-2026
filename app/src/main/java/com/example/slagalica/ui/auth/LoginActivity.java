package com.example.slagalica.ui.auth;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.R;
import com.example.slagalica.ui.main.MainActivity;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.ui.profile.ProfileFragment;

public class LoginActivity extends AppCompatActivity {

    /**
     * Za logout — briše task i otvara čist login (vidi {@link ProfileFragment}).
     */
    public static void openFresh(@NonNull Context context) {
        Intent intent = new Intent(context, LoginActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        Button loginBtn = findViewById(R.id.login);
        EditText emailEt = findViewById(R.id.username);
        EditText passwordEt = findViewById(R.id.password);
        UserProfileRepository repo = new UserProfileRepository(this);

        loginBtn.setOnClickListener(v -> {
            String email = emailEt.getText() != null ? emailEt.getText().toString() : "";
            String password = passwordEt.getText() != null ? passwordEt.getText().toString() : "";

            if(email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Unesite email ili korisničko ime i lozinku.", Toast.LENGTH_SHORT).show();
                return;
            }

            repo.login(
                    email,
                    password,
                    () -> {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    },
                    errorMessage -> {
                        String message;

                        if ("EMAIL_NOT_VERIFIED".equals(errorMessage)) {
                            message = "Morate potvrditi email pre logovanja.";
                        } else if ("USERNAME_NOT_FOUND".equals(errorMessage)) {
                            message = "Korisničko ime ne postoji.";
                        } else {
                            message = errorMessage != null
                                    ? errorMessage
                                    : getString(R.string.error_login_failed);
                        }

                        Toast.makeText(
                                LoginActivity.this,
                                message,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );
        });

        Button registerBtn = findViewById(R.id.register);

        registerBtn.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        findViewById(R.id.continueAsGuest).setOnClickListener(v -> {
            repo.ensureGuestAuthenticated(
                    () -> {
                        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                        intent.putExtra(MainActivity.EXTRA_GUEST_MODE, true);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        finish();
                    },
                    error -> Toast.makeText(
                            LoginActivity.this,
                            error != null ? error : getString(R.string.error_guest_sign_in),
                            Toast.LENGTH_SHORT
                    ).show()
            );
        });

    }
}
