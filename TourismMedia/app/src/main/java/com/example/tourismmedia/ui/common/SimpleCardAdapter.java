package com.example.tourismmedia.ui.common;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;

import java.util.ArrayList;
import java.util.List;

public class SimpleCardAdapter extends RecyclerView.Adapter<SimpleCardAdapter.Holder> {
    public static class CardItem {
        public final String icon, title, subtitle, meta, imageUrl;
        public final Object value;
        public final boolean favoriteVisible;
        public final boolean favorite;

        public CardItem(String icon, String title, String subtitle, String meta, Object value) {
            this(icon, title, subtitle, meta, null, value, false, false);
        }
        public CardItem(String icon, String title, String subtitle, String meta, String image, Object value) {
            this(icon, title, subtitle, meta, image, value, false, false);
        }
        public CardItem(String icon, String title, String subtitle, String meta, String image, Object value,
                        boolean favoriteVisible, boolean favorite) {
            this.icon = icon; this.title = title; this.subtitle = subtitle; this.meta = meta;
            this.imageUrl = image; this.value = value; this.favoriteVisible = favoriteVisible; this.favorite = favorite;
        }
    }

    public interface Listener { void onClick(CardItem item); }
    private final List<CardItem> items = new ArrayList<>();
    private final Listener listener;
    public SimpleCardAdapter(Listener listener) { this.listener = listener; }
    public void submit(List<CardItem> next) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(next);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_travel_card, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        CardItem item = items.get(position);
        holder.icon.setText(item.icon); holder.title.setText(item.title); holder.subtitle.setText(item.subtitle); holder.meta.setText(item.meta);
        holder.meta.setVisibility(item.meta == null || item.meta.isBlank() ? View.GONE : View.VISIBLE);
        holder.favorite.setVisibility(item.favoriteVisible ? View.VISIBLE : View.GONE);
        holder.favorite.setText(item.favorite ? "♥" : "♡");
        if (item.imageUrl != null && !item.imageUrl.isBlank()) {
            holder.image.setVisibility(View.VISIBLE); holder.icon.setVisibility(View.GONE);
            Glide.with(holder.image.getContext()).load(item.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).error(R.drawable.bg_hero).into(holder.image);
        } else {
            Glide.with(holder.image.getContext()).clear(holder.image); holder.image.setVisibility(View.GONE); holder.icon.setVisibility(View.VISIBLE);
        }
        holder.itemView.setOnClickListener(v -> listener.onClick(item));
    }

    @Override public int getItemCount() { return items.size(); }
    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image; final TextView icon, title, subtitle, meta, favorite;
        Holder(View view) { super(view); image=view.findViewById(R.id.card_image); icon=view.findViewById(R.id.card_icon); title=view.findViewById(R.id.card_title); subtitle=view.findViewById(R.id.card_subtitle); meta=view.findViewById(R.id.card_meta); favorite=view.findViewById(R.id.card_favorite); }
    }
}
