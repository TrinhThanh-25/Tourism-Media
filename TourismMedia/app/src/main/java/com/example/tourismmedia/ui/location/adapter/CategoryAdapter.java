package com.example.tourismmedia.ui.location.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.ui.location.LocationFormatter;

import java.util.ArrayList;
import java.util.List;

/**
 * Single-choice category chips. Position 0 is always "All", which maps to a
 * null category filter on the server.
 */
public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.Holder> {

    public interface OnCategorySelected {
        /** @param category the chosen category, or null for "All" */
        void onCategory(String category);
    }

    private final List<String> values = new ArrayList<>();
    private String allLabel;
    private final OnCategorySelected listener;
    private int selected = 0;

    public CategoryAdapter(OnCategorySelected listener) {
        this.listener = listener;
    }

    /** @param categories distinct category names; the "All" chip is prepended here */
    public void submit(String allLabel, List<String> categories) {
        this.allLabel = allLabel;
        int oldSize = values.size();
        values.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        values.add(null);
        values.addAll(categories);
        if (selected >= values.size()) {
            selected = 0;
        }
        notifyItemRangeInserted(0, values.size());
    }

    public void select(String category) {
        int index = category == null ? 0 : values.indexOf(category);
        int previous = selected;
        selected = Math.max(0, index);
        if (previous != selected) {
            notifyItemChanged(previous);
            notifyItemChanged(selected);
        }
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder((TextView) LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        boolean active = position == selected;
        String value = values.get(position);
        holder.label.setText(position == 0 ? allLabel : LocationFormatter.categoryLabel(value));
        holder.label.setSelected(active);
        holder.label.setTextColor(ContextCompat.getColor(holder.label.getContext(),
                active ? R.color.white : R.color.ink));
        holder.label.setOnClickListener(view -> {
            int previous = selected;
            selected = holder.getBindingAdapterPosition();
            notifyItemChanged(previous);
            notifyItemChanged(selected);
            listener.onCategory(values.get(selected));
        });
    }

    @Override
    public int getItemCount() {
        return values.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView label;

        Holder(TextView view) {
            super(view);
            label = view;
        }
    }
}
