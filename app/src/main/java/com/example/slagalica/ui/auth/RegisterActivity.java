package com.example.slagalica.ui.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.SerbiaRegion;

public class RegisterActivity extends AppCompatActivity {

    private SerbiaRegion selectedRegion;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_register);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });


        Button registerBtn = findViewById(R.id.register);
        EditText emailEt = findViewById(R.id.etEmail);
        EditText usernameEt = findViewById(R.id.username);
        EditText regionEt = findViewById(R.id.region);
        EditText passwordEt = findViewById(R.id.password);
        EditText confirmEt = findViewById(R.id.confirmPassword);

        regionEt.setOnClickListener(v -> showRegionPicker(regionEt));

        registerBtn.setOnClickListener(v -> {
            String email = emailEt.getText() != null ? emailEt.getText().toString() : "";
            String username = usernameEt.getText() != null ? usernameEt.getText().toString() : "";
            String password = passwordEt.getText() != null ? passwordEt.getText().toString() : "";
            String confirm = confirmEt.getText() != null ? confirmEt.getText().toString() : "";

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(username)
                    || selectedRegion == null || TextUtils.isEmpty(password)
                    || TextUtils.isEmpty(confirm)) {
                Toast.makeText(this, R.string.error_fill_all_fields, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!email.contains("@")) {
                Toast.makeText(this, R.string.error_invalid_email, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, R.string.error_password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            UserProfileRepository repo = new UserProfileRepository(this);

            repo.register(
                    email,
                    username,
                    selectedRegion.getKey(),
                    password,
                    () -> {
                        Toast.makeText(
                                RegisterActivity.this,
                                "Registracija uspešna. Proverite email za potvrdu naloga.",
                                Toast.LENGTH_SHORT
                        ).show();

                        startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                        finish();
                    },
                    errorMessage -> {
                        Toast.makeText(
                                RegisterActivity.this,
                                errorMessage != null
                                        ? errorMessage
                                        : "Greška pri registraciji",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
            );

        });

    }

    private void showRegionPicker(@NonNull EditText regionEt) {
        String[] labels = new String[SerbiaRegion.all().size()];
        for (int i = 0; i < SerbiaRegion.all().size(); i++) {
            labels[i] = SerbiaRegion.all().get(i).getDisplayName(this);
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.region_pick_title)
                .setItems(labels, (dialog, which) -> {
                    selectedRegion = SerbiaRegion.all().get(which);
                    regionEt.setText(selectedRegion.getDisplayName(this));
                })
                .show();
    }
}
