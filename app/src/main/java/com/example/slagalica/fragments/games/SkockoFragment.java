package com.example.slagalica.fragments.games;

import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;

public class SkockoFragment extends Fragment {

    private final TextView[] currentAttemptCells = new TextView[4];
    private int nextCellIndex = 0;

    public SkockoFragment() {
        super(R.layout.fragment_skocko);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button submitBtn = view.findViewById(R.id.submitButton);

        setupCombinationInput(view);

        submitBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });
    }

    private void setupCombinationInput(@NonNull View view) {
        currentAttemptCells[0] = view.findViewById(R.id.skockoAttemptCell1);
        currentAttemptCells[1] = view.findViewById(R.id.skockoAttemptCell2);
        currentAttemptCells[2] = view.findViewById(R.id.skockoAttemptCell3);
        currentAttemptCells[3] = view.findViewById(R.id.skockoAttemptCell4);

        setSymbolClick(view, R.id.skockoSymbolSkocko);
        setSymbolClick(view, R.id.skockoSymbolSquare);
        setSymbolClick(view, R.id.skockoSymbolCircle);
        setSymbolClick(view, R.id.skockoSymbolHeart);
        setSymbolClick(view, R.id.skockoSymbolTriangle);
        setSymbolClick(view, R.id.skockoSymbolStar);
    }

    private void setSymbolClick(@NonNull View view, int symbolId) {
        TextView symbol = view.findViewById(symbolId);
        symbol.setOnClickListener(v -> {
            if (nextCellIndex < currentAttemptCells.length) {
                TextView cell = currentAttemptCells[nextCellIndex];
                cell.setText(symbol.getText());
                if (symbolId == R.id.skockoSymbolTriangle) {
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 30);
                    cell.setPadding(0, 0, dpToPx(2), dpToPx(4));
                } else {
                    cell.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    cell.setPadding(0, 0, 0, 0);
                }
                nextCellIndex++;
            }
        });
    }

    private int dpToPx(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
