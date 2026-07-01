package com.example.slagalica.ui.chat;

import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.fragment.NavHostFragment;

import com.example.slagalica.R;
import com.example.slagalica.model.RegionChatMessage;
import com.example.slagalica.model.SerbiaRegion;
import com.example.slagalica.viewmodel.chat.RegionChatViewModel;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class RegionChatFragment extends Fragment {

    private RegionChatViewModel viewModel;
    private LinearLayout messagesContainer;
    private ScrollView scrollView;
    private EditText input;
    private TextView title;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy. HH:mm", Locale.getDefault());

    public RegionChatFragment() {
        super(R.layout.fragment_region_chat);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewModel = new ViewModelProvider(this).get(RegionChatViewModel.class);
        messagesContainer = view.findViewById(R.id.regionChatMessages);
        scrollView = view.findViewById(R.id.regionChatScroll);
        input = view.findViewById(R.id.regionChatInput);
        title = view.findViewById(R.id.regionChatTitle);

        view.findViewById(R.id.regionChatBack).setOnClickListener(v ->
                NavHostFragment.findNavController(this).navigateUp()
        );

        view.findViewById(R.id.regionChatSend).setOnClickListener(v -> sendMessage());

        viewModel.getMessages().observe(getViewLifecycleOwner(), this::renderMessages);
        viewModel.getErrorMessage().observe(getViewLifecycleOwner(), message -> {
            if (message != null && !message.isEmpty()) {
                Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
        viewModel.getRegionKey().observe(getViewLifecycleOwner(), this::bindRegionTitle);

        viewModel.start();
    }

    private void sendMessage() {
        String text = input.getText().toString();
        if (text.trim().isEmpty()) {
            return;
        }
        input.setText("");
        viewModel.sendMessage(text);
    }

    private void renderMessages(@NonNull List<RegionChatMessage> messages) {
        messagesContainer.removeAllViews();

        String myUid = viewModel.getCurrentUid();
        for (RegionChatMessage message : messages) {
            messagesContainer.addView(createMessageView(message, message.isMine(myUid)));
        }

        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }

    @NonNull
    private View createMessageView(@NonNull RegionChatMessage message, boolean mine) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(mine ? Gravity.END : Gravity.START);

        TextView bubble = new TextView(requireContext());
        bubble.setText(buildMessageText(message));
        bubble.setTextSize(14);
        bubble.setTextColor(Color.BLACK);
        bubble.setPadding(dp(12), dp(8), dp(12), dp(8));
        bubble.setBackground(bubbleBackground(mine));

        LinearLayout.LayoutParams bubbleParams = new LinearLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.72f),
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        row.addView(bubble, bubbleParams);

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        rowParams.setMargins(0, dp(4), 0, dp(8));
        row.setLayoutParams(rowParams);

        return row;
    }

    @NonNull
    private String buildMessageText(@NonNull RegionChatMessage message) {
        String date = message.getCreatedAtMillis() > 0
                ? dateFormat.format(new Date(message.getCreatedAtMillis()))
                : "";
        return message.getSenderUsername() + "\n" + date + "\n\n" + message.getText();
    }

    @NonNull
    private GradientDrawable bubbleBackground(boolean mine) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(mine ? 0xFFDFF5E1 : 0xFFEDEDED);
        drawable.setCornerRadius(dp(10));
        drawable.setStroke(1, mine ? 0xFF7CBF80 : 0xFFCCCCCC);
        return drawable;
    }

    private void bindRegionTitle(@NonNull String regionKey) {
        SerbiaRegion region = SerbiaRegion.fromKey(regionKey);
        if (region == null) {
            title.setText(R.string.region_chat_title);
            return;
        }
        title.setText(getString(R.string.region_chat_title_with_region, region.getDisplayName(requireContext())));
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}