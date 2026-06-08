package com.example.slagalica.ui.games;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.fragment.NavHostFragment;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.example.slagalica.R;
import com.example.slagalica.data.repository.UserProfileRepository;
import com.example.slagalica.model.GameHeaderPlayerState;
import com.example.slagalica.model.GameHeaderState;
import com.example.slagalica.model.UserProfile;

/**
 * A simple {@link Fragment} subclass.
 * Use the {@link MyNumberFragment#newInstance} factory method to
 * create an instance of this fragment.
 */
public class MyNumberFragment extends Fragment {

    // TODO: Rename parameter arguments, choose names that match
    // the fragment initialization parameters, e.g. ARG_ITEM_NUMBER
    private static final String ARG_PARAM1 = "param1";
    private static final String ARG_PARAM2 = "param2";

    // TODO: Rename and change types of parameters
    private String mParam1;
    private String mParam2;

    public MyNumberFragment() {
        // Required empty public constructor
    }

    /**
     * Use this factory method to create a new instance of
     * this fragment using the provided parameters.
     *
     * @param param1 Parameter 1.
     * @param param2 Parameter 2.
     * @return A new instance of fragment MyNumberFragment.
     */
    // TODO: Rename and change types and number of parameters
    public static MyNumberFragment newInstance(String param1, String param2) {
        MyNumberFragment fragment = new MyNumberFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PARAM1, param1);
        args.putString(ARG_PARAM2, param2);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mParam1 = getArguments().getString(ARG_PARAM1);
            mParam2 = getArguments().getString(ARG_PARAM2);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_number, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupGameHeader(view);
        Button submitBtn = view.findViewById(R.id.submitButton);

        submitBtn.setOnClickListener(v -> {
            NavHostFragment.findNavController(this)
                    .navigateUp();
        });
    }

    private void setupGameHeader(@NonNull View view) {
        Fragment fragment = getChildFragmentManager().findFragmentById(R.id.mojBrojGameHeader);
        if (!(fragment instanceof GameHeaderFragment)) {
            return;
        }
        GameHeaderFragment gameHeader = (GameHeaderFragment) fragment;
        UserProfile profile = new UserProfileRepository(requireContext()).loadProfile();
        String username = profile.getUsername();
        if (username.trim().isEmpty()) {
            username = getString(R.string.guest_player);
        }
        gameHeader.setHeaderState(new GameHeaderState(
                getString(R.string.game_header_round_default),
                getString(R.string.game_header_time_default),
                new GameHeaderPlayerState(username, 0, profile.getAvatarUri()),
                new GameHeaderPlayerState(getString(R.string.opponent_player), 0, null)
        ));
    }
}