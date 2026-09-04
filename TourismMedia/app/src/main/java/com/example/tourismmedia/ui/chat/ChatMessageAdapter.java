package com.example.tourismmedia.ui.chat;

import android.graphics.Color;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.model.AppModels.ChatTurn;

import java.util.List;

final class ChatMessageAdapter extends RecyclerView.Adapter<ChatMessageAdapter.Holder> {
    private final List<ChatTurn> messages;

    ChatMessageAdapter(List<ChatTurn> messages) {
        this.messages = messages;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_message, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        ChatTurn message = messages.get(position);
        boolean user = "user".equals(message.role);
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) holder.text.getLayoutParams();
        params.gravity = user ? Gravity.END : Gravity.START;
        holder.text.setLayoutParams(params);
        holder.text.setText(user ? message.content : ChatMarkdown.render(message.content));
        holder.text.setTextColor(user ? Color.WHITE
                : ContextCompat.getColor(holder.itemView.getContext(), R.color.ink));
        holder.text.setBackgroundResource(user
                ? R.drawable.bg_chat_bubble_user
                : R.drawable.bg_chat_bubble_assistant);
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView text;

        Holder(View itemView) {
            super(itemView);
            text = itemView.findViewById(R.id.chat_message_text);
        }
    }
}
