package com.example.tourismmedia.ui.location.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.ui.location.LocationFormatter;

import java.util.ArrayList;
import java.util.List;

/** Vertical location list used by both Home ("Top rated") and Explore. */
public class LocationAdapter extends RecyclerView.Adapter<LocationAdapter.Holder> {

    public interface OnLocationClick {
        void onLocation(Location location);
    }

    public interface OnFavoriteClick {
        void onFavorite(Location location, int position);
    }

    private final List<Location> items = new ArrayList<>();
    private final OnLocationClick onClick;
    private final OnFavoriteClick onFavorite;

    public LocationAdapter(OnLocationClick onClick, OnFavoriteClick onFavorite) {
        this.onClick = onClick;
        this.onFavorite = onFavorite;
    }

    public void submit(List<Location> next) {
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(next);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    public Location itemAt(int position) {
        return items.get(position);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Location location = items.get(position);
        holder.name.setText(location.name);
        holder.meta.setText(LocationFormatter.meta(location));
        holder.rating.setText(LocationFormatter.rating(location.rating));
        holder.price.setText(LocationFormatter.price(holder.price.getContext(), location.price));
        holder.favorite.setImageResource(location.isFavorite()
                ? R.drawable.ic_favorite_filled
                : R.drawable.ic_favorite_border);

        Glide.with(holder.image.getContext())
                .load(location.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.bg_thumb)
                .error(R.drawable.bg_thumb)
                .into(holder.image);

        holder.itemView.setOnClickListener(view -> onClick.onLocation(location));
        if (onFavorite == null) {
            holder.favorite.setVisibility(View.GONE);
        } else {
            holder.favorite.setVisibility(View.VISIBLE);
            holder.favorite.setOnClickListener(view ->
                    onFavorite.onFavorite(location, holder.getBindingAdapterPosition()));
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final ImageView favorite;
        final TextView name;
        final TextView meta;
        final TextView rating;
        final TextView price;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.location_image);
            favorite = view.findViewById(R.id.location_favorite);
            name = view.findViewById(R.id.location_name);
            meta = view.findViewById(R.id.location_meta);
            rating = view.findViewById(R.id.location_rating);
            price = view.findViewById(R.id.location_price);
        }
    }
}
