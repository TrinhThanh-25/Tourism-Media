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

/** Horizontal "Featured places" carousel on Home. */
public class FeaturedLocationAdapter extends RecyclerView.Adapter<FeaturedLocationAdapter.Holder> {

    private final List<Location> items = new ArrayList<>();
    private final LocationAdapter.OnLocationClick onClick;

    public FeaturedLocationAdapter(LocationAdapter.OnLocationClick onClick) {
        this.onClick = onClick;
    }

    public void submit(List<Location> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_featured, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Location location = items.get(position);
        holder.name.setText(location.name);
        holder.meta.setText(LocationFormatter.meta(location));
        holder.rating.setText("★ " + LocationFormatter.rating(location.rating));

        Glide.with(holder.image.getContext())
                .load(location.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.bg_hero)
                .error(R.drawable.bg_hero)
                .into(holder.image);

        holder.itemView.setOnClickListener(view -> onClick.onLocation(location));
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView meta;
        final TextView rating;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.featured_image);
            name = view.findViewById(R.id.featured_name);
            meta = view.findViewById(R.id.featured_meta);
            rating = view.findViewById(R.id.featured_rating);
        }
    }
}
