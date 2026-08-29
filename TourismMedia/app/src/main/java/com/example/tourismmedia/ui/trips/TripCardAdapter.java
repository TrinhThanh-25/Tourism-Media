package com.example.tourismmedia.ui.trips;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.ui.location.LocationFormatter;

import java.util.ArrayList;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public class TripCardAdapter extends RecyclerView.Adapter<TripCardAdapter.Holder> {
    public interface Listener { void click(Trip trip); }
    public interface FavoriteListener { void toggle(Trip trip, int position); }
    public interface PublishListener { void toggle(Trip trip, int position); }

    private final List<Trip> items = new ArrayList<>();
    private final Listener listener;
    private final FavoriteListener favoriteListener;
    private final PublishListener publishListener;
    private boolean ownerMode;

    public TripCardAdapter(Listener listener) { this(listener, null, null); }
    public TripCardAdapter(Listener listener, FavoriteListener favoriteListener) {
        this(listener, favoriteListener, null);
    }
    public TripCardAdapter(Listener listener, FavoriteListener favoriteListener, PublishListener publishListener) {
        this.listener = listener;
        this.favoriteListener = favoriteListener;
        this.publishListener = publishListener;
    }

    public void setOwnerMode(boolean ownerMode) { this.ownerMode = ownerMode; }

    public void submit(List<Trip> next) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(next);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_trip_card, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Trip trip = items.get(position);
        holder.title.setText(trip.title);
        holder.overlay.setText((trip.highlight == null || trip.highlight.isBlank() ? "Hành trình nổi bật" : trip.highlight) + " · " + time(trip.totalTime));
        holder.meta.setText("Dự kiến " + String.format(Locale.getDefault(), "%,dđ", trip.estimatedPrice) + " · " + time(trip.totalTime));
        holder.rating.setText("★ " + LocationFormatter.rating(trip.rating) + " (" + trip.reviewCount + ")");
        holder.rating.setVisibility(trip.reviewCount > 0 ? View.VISIBLE : View.GONE);
        holder.status.setText(trip.published == 1 ? "Đã xuất bản" : "Riêng tư");
        holder.author.setText("Đăng bởi " + safe(trip.authorUsername, "Traveler") + " · " + relativeTime(trip.publishedAt));
        holder.author.setVisibility(!ownerMode && trip.published == 1 ? View.VISIBLE : View.GONE);
        holder.status.setVisibility(ownerMode ? View.VISIBLE : View.GONE);
        holder.publish.setText(trip.published == 1 ? "Gỡ khỏi cộng đồng" : "Đăng cộng đồng");
        holder.publish.setVisibility(ownerMode && publishListener != null ? View.VISIBLE : View.GONE);
        holder.favorite.setText(trip.favorite == 1 ? "♥ Đã lưu" : "♡ Lưu");
        Glide.with(holder.image).load(trip.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).error(R.drawable.bg_hero).into(holder.image);
        holder.itemView.setOnClickListener(v -> listener.click(trip));
        holder.favorite.setVisibility(ownerMode || favoriteListener == null || trip.published != 1 ? View.GONE : View.VISIBLE);
        holder.favorite.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (favoriteListener != null && adapterPosition != RecyclerView.NO_POSITION) favoriteListener.toggle(trip, adapterPosition);
        });
        holder.publish.setOnClickListener(v -> {
            int adapterPosition = holder.getBindingAdapterPosition();
            if (publishListener != null && adapterPosition != RecyclerView.NO_POSITION) publishListener.toggle(trip, adapterPosition);
        });
    }

    private static String time(int value) {
        if (value <= 0) return "Linh hoạt";
        if (value >= 1440) return Math.max(1, value / 1440) + " ngày";
        return value + " phút";
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static String relativeTime(String value) {
        if (value == null || value.isBlank()) return "vừa đăng";
        try {
            long minutes = Math.max(0, Duration.between(Instant.parse(value), Instant.now()).toMinutes());
            if (minutes < 1) return "vừa đăng";
            if (minutes < 60) return minutes + " phút trước";
            long hours = minutes / 60;
            if (hours < 24) return hours + " giờ trước";
            long days = hours / 24;
            return days < 30 ? days + " ngày trước" : value.substring(0, Math.min(10, value.length()));
        } catch (Exception ignored) { return value; }
    }

    @Override public int getItemCount() { return items.size(); }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView overlay, title, meta, author, rating, status, publish, favorite;
        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.trip_card_image);
            overlay = view.findViewById(R.id.trip_card_overlay);
            title = view.findViewById(R.id.trip_card_title);
            meta = view.findViewById(R.id.trip_card_meta);
            author = view.findViewById(R.id.trip_card_author);
            rating = view.findViewById(R.id.trip_card_rating);
            status = view.findViewById(R.id.trip_card_status);
            publish = view.findViewById(R.id.trip_card_publish);
            favorite = view.findViewById(R.id.trip_card_favorite);
        }
    }
}
