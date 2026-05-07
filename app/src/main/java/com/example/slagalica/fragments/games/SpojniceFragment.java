package com.example.slagalica.fragments.games;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public class SpojniceFragment extends Fragment {

    public SpojniceFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_spojnice, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button submitBtn = view.findViewById(R.id.spojniceSubmitButton);
        Button nextRoundBtn = view.findViewById(R.id.spojniceNextRoundButton);

        submitBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        nextRoundBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }
}
