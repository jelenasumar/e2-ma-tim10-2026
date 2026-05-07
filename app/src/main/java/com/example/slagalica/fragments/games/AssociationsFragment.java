package com.example.slagalica.fragments.games;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;

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

        submitBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });
    }
}
