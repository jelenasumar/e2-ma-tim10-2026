package com.example.slagalica.activities;

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
import com.example.slagalica.data.UserProfileRepository;

public class LoginActivity extends AppCompatActivity {

    /**
     * Za logout — briše task i otvara čist login (vidi {@link com.example.slagalica.fragments.ProfileFragment}).
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

            if (!repo.hasRegisteredAccount()) {
                Toast.makeText(this, R.string.error_register_first, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!repo.tryLogin(email, password)) {
                Toast.makeText(this, R.string.error_login_failed, Toast.LENGTH_SHORT).show();
                return;
            }

            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

        Button registerBtn = findViewById(R.id.register);

        registerBtn.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            finish();
        });

        findViewById(R.id.continueAsGuest).setOnClickListener(v -> {
            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
            intent.putExtra(MainActivity.EXTRA_GUEST_MODE, true);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            finish();
        });

    }
}
