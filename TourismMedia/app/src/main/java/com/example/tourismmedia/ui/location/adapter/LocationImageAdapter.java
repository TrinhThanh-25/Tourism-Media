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
import com.example.tourismmedia.data.model.AppModels.LocationImage;

import java.util.ArrayList;
import java.util.List;

/**
 * Full-bleed gallery pages for the location detail header. Each page is stretched to
 * the RecyclerView width so a PagerSnapHelper can snap one image at a time.
 */
public class LocationImageAdapter extends RecyclerView.Adapter<LocationImageAdapter.Holder> {

    private final List<LocationImage> items = new ArrayList<>();

    public void submit(List<LocationImage> next) {
        items.clear();
        items.addAll(next);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_location_image, parent, false);
        int width = parent.getMeasuredWidth();
        if (width > 0) {
            view.getLayoutParams().width = width;
            view.findViewById(R.id.gallery_image).getLayoutParams().width = width;
        }
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        LocationImage image = items.get(position);
        Glide.with(holder.image.getContext())
                .load(image.url)
                .centerCrop()
                .placeholder(R.drawable.bg_hero)
                .error(R.drawable.bg_hero)
                .into(holder.image);

        boolean hasCaption = image.caption != null && !image.caption.isBlank();
        holder.caption.setVisibility(hasCaption ? View.VISIBLE : View.GONE);
        holder.caption.setText(image.caption);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView caption;

        Holder(View view) {
            super(view);
            image = view.findViewById(R.id.gallery_image);
            caption = view.findViewById(R.id.gallery_caption);
        }
    }
}
