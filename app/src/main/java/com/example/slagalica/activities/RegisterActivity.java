package com.example.slagalica.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.slagalica.R;
import com.example.slagalica.data.UserProfileRepository;

public class RegisterActivity extends AppCompatActivity {

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

        registerBtn.setOnClickListener(v -> {
            String email = emailEt.getText() != null ? emailEt.getText().toString() : "";
            String username = usernameEt.getText() != null ? usernameEt.getText().toString() : "";
            String region = regionEt.getText() != null ? regionEt.getText().toString() : "";
            String password = passwordEt.getText() != null ? passwordEt.getText().toString() : "";
            String confirm = confirmEt.getText() != null ? confirmEt.getText().toString() : "";

            if (TextUtils.isEmpty(email) || TextUtils.isEmpty(username)
                    || TextUtils.isEmpty(region) || TextUtils.isEmpty(password)
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
            repo.saveRegisteredAccount(email, username, region, password);

            startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
            finish();
        });

    }
}
