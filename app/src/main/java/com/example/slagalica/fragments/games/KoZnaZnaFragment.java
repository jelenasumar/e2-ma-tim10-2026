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

public class KoZnaZnaFragment extends Fragment {

    public KoZnaZnaFragment() {
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_ko_zna_zna, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button confirmBtn = view.findViewById(R.id.kzzConfirmButton);
        Button skipBtn = view.findViewById(R.id.kzzSkipButton);

        confirmBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
        skipBtn.setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );
    }
}
