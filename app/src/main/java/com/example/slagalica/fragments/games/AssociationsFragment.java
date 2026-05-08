package com.example.slagalica.fragments.games;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public class AssociationsFragment extends Fragment {

    public AssociationsFragment() {
        super(R.layout.fragment_associations);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button submitBtn = view.findViewById(R.id.submitButton);
        Button nextBtn = view.findViewById(R.id.nextButton);

        setupAssociationFields(view);

        submitBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });

        nextBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });
    }

    private void setupAssociationFields(@NonNull View view) {
        setFieldTextOnClick(view, R.id.associationA1, "More");
        setFieldTextOnClick(view, R.id.associationA2, "Plaza");
        setFieldTextOnClick(view, R.id.associationA3, "Talas");
        setFieldTextOnClick(view, R.id.associationA4, "Pesak");

        setFieldTextOnClick(view, R.id.associationB1, "Sunce");
        setFieldTextOnClick(view, R.id.associationB2, "Toplota");
        setFieldTextOnClick(view, R.id.associationB3, "Jul");
        setFieldTextOnClick(view, R.id.associationB4, "Odmor");

        setFieldTextOnClick(view, R.id.associationC1, "Kofer");
        setFieldTextOnClick(view, R.id.associationC2, "Put");
        setFieldTextOnClick(view, R.id.associationC3, "Hotel");
        setFieldTextOnClick(view, R.id.associationC4, "Mapa");

        setFieldTextOnClick(view, R.id.associationD1, "Sladoled");
        setFieldTextOnClick(view, R.id.associationD2, "Suncobran");
        setFieldTextOnClick(view, R.id.associationD3, "Kupaci");
        setFieldTextOnClick(view, R.id.associationD4, "Leto");
    }

    private void setFieldTextOnClick(@NonNull View view, int fieldId, @NonNull String value) {
        TextView field = view.findViewById(fieldId);
        field.setOnClickListener(v -> field.setText(value));
    }
}
