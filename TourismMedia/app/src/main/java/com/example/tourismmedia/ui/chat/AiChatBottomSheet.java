package com.example.tourismmedia.ui.chat;

import android.app.Activity;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.ChatTurn;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

/** Messenger-style, activity-wide travel assistant backed by the authenticated API. */
public final class AiChatBottomSheet {
    private static final int MAX_HISTORY = 12;

    private final Activity activity;
    private final AppRepository repository;
    private final List<ChatTurn> messages = new ArrayList<>();
    private BottomSheetDialog dialog;
    private boolean waiting;
    private ChatMessageAdapter visibleAdapter;
    private RecyclerView visibleList;
    private EditText visibleInput;
    private ImageButton visibleSend;

    public AiChatBottomSheet(@NonNull Activity activity) {
        this.activity = activity;
        repository = AppRepository.get(activity);
        messages.add(new ChatTurn("assistant", activity.getString(R.string.chat_welcome)));
    }

    public void show() {
        if (dialog != null && dialog.isShowing()) return;

        dialog = new BottomSheetDialog(activity);
        FrameLayout inflationParent = new FrameLayout(activity);
        View content = LayoutInflater.from(activity)
                .inflate(R.layout.dialog_ai_chat, inflationParent, false);
        dialog.setContentView(content);

        RecyclerView list = content.findViewById(R.id.chat_messages);
        LinearLayoutManager layoutManager = new LinearLayoutManager(activity);
        layoutManager.setStackFromEnd(true);
        list.setLayoutManager(layoutManager);
        ChatMessageAdapter adapter = new ChatMessageAdapter(messages);
        list.setAdapter(adapter);

        EditText input = content.findViewById(R.id.chat_input);
        ImageButton send = content.findViewById(R.id.chat_send);
        visibleAdapter = adapter;
        visibleList = list;
        visibleInput = input;
        visibleSend = send;
        input.setEnabled(!waiting);
        send.setEnabled(!waiting);
        content.findViewById(R.id.chat_close).setOnClickListener(view -> dialog.dismiss());
        send.setOnClickListener(view -> send(input, send, list, adapter));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId != EditorInfo.IME_ACTION_SEND) return false;
            send(input, send, list, adapter);
            return true;
        });

        bindSuggestion(content, R.id.chat_suggestion_weekend,
                activity.getString(R.string.chat_suggestion_weekend), input, send, list, adapter);
        bindSuggestion(content, R.id.chat_suggestion_budget,
                activity.getString(R.string.chat_suggestion_budget), input, send, list, adapter);
        bindSuggestion(content, R.id.chat_suggestion_nha_trang,
                activity.getString(R.string.chat_suggestion_nha_trang), input, send, list, adapter);

        dialog.setOnShowListener(ignored -> {
            FrameLayout sheet = dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
            if (sheet == null) return;
            sheet.setBackgroundColor(Color.TRANSPARENT);
            int height = (int) (activity.getResources().getDisplayMetrics().heightPixels * 0.78f);
            sheet.getLayoutParams().height = height;
            BottomSheetBehavior<FrameLayout> behavior = BottomSheetBehavior.from(sheet);
            behavior.setSkipCollapsed(true);
            behavior.setState(BottomSheetBehavior.STATE_EXPANDED);
            list.scrollToPosition(Math.max(0, messages.size() - 1));
        });
        dialog.show();
    }

    private void bindSuggestion(View root, int id, String prompt, EditText input,
                                ImageButton send, RecyclerView list, ChatMessageAdapter adapter) {
        root.findViewById(id).setOnClickListener(view -> {
            if (waiting) return;
            input.setText(prompt);
            send(input, send, list, adapter);
        });
    }

    private void send(EditText input, ImageButton send, RecyclerView list,
                      ChatMessageAdapter adapter) {
        String prompt = input.getText().toString().trim();
        if (prompt.isEmpty() || waiting) return;

        List<ChatTurn> history = historySnapshot();
        input.setText("");
        waiting = true;
        input.setEnabled(false);
        send.setEnabled(false);
        messages.add(new ChatTurn("user", prompt));
        messages.add(new ChatTurn("assistant", activity.getString(R.string.chat_typing)));
        adapter.notifyItemRangeInserted(messages.size() - 2, 2);
        list.smoothScrollToPosition(messages.size() - 1);

        repository.askTravelAssistant(history, prompt, (response, error) -> {
            if (activity.isFinishing() || activity.isDestroyed()) return;
            String answer = error == null && response != null && response.message != null
                    ? response.message
                    : "Xin lỗi, " + (error == null ? "trợ lý chưa thể trả lời lúc này." : error);
            int answerIndex = messages.size() - 1;
            messages.set(answerIndex, new ChatTurn("assistant", answer));
            waiting = false;
            if (visibleInput != null) visibleInput.setEnabled(true);
            if (visibleSend != null) visibleSend.setEnabled(true);
            if (visibleAdapter != null) visibleAdapter.notifyItemChanged(answerIndex);
            if (visibleList != null) visibleList.smoothScrollToPosition(messages.size() - 1);
            if (visibleInput != null) visibleInput.requestFocus();
        });
    }

    private List<ChatTurn> historySnapshot() {
        int from = Math.max(0, messages.size() - MAX_HISTORY);
        List<ChatTurn> history = new ArrayList<>();
        for (int index = from; index < messages.size(); index++) {
            ChatTurn item = messages.get(index);
            history.add(new ChatTurn(item.role, item.content));
        }
        return history;
    }

    public void dismiss() {
        if (dialog != null) dialog.dismiss();
    }
}
