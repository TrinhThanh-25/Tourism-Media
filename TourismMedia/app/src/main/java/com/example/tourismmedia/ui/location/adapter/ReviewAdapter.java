package com.example.tourismmedia.ui.location.adapter;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.model.AppModels.Review;
import com.example.tourismmedia.ui.location.LocationFormatter;

import java.util.ArrayList;
import java.util.List;

/** Reviews of a location. The signed-in user's own review is long-pressable to edit or delete. */
public class ReviewAdapter extends RecyclerView.Adapter<ReviewAdapter.Holder> {

    public interface OnOwnReviewLongClick {
        void onOwnReview(Review review);
    }

    private final List<Review> items = new ArrayList<>();
    private final OnOwnReviewLongClick onOwnReview;
    private long currentUserId;

    public ReviewAdapter(OnOwnReviewLongClick onOwnReview) {
        this.onOwnReview = onOwnReview;
    }

    public void submit(List<Review> next, long currentUserId) {
        this.currentUserId = currentUserId;
        int oldSize = items.size();
        items.clear();
        if (oldSize > 0) notifyItemRangeRemoved(0, oldSize);
        items.addAll(next);
        if (!items.isEmpty()) notifyItemRangeInserted(0, items.size());
    }

    /** The review written by the signed-in user, or null when they have not reviewed yet. */
    public Review ownReview() {
        for (Review review : items) {
            if (review.userId == currentUserId && currentUserId != 0) {
                return review;
            }
        }
        return null;
    }

    public double averageRating() {
        if (items.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Review review : items) {
            total += review.rating;
        }
        return (double) total / items.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_review, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Review review = items.get(position);
        Context context = holder.itemView.getContext();
        String author = review.username == null || review.username.isBlank()
                ? context.getString(R.string.location_reviewer_anonymous)
                : review.username;
        boolean mine = currentUserId != 0 && review.userId == currentUserId;

        holder.avatar.setText(LocationFormatter.initials(author));
        holder.author.setText(mine ? context.getString(R.string.location_reviewer_you, author) : author);
        holder.stars.setText(LocationFormatter.stars(review.rating));
        holder.date.setText(LocationFormatter.date(review.createdAt));
        holder.comment.setText(review.comment == null || review.comment.isBlank()
                ? context.getString(R.string.location_review_no_comment)
                : review.comment);

        holder.itemView.setOnLongClickListener(mine ? view -> {
            onOwnReview.onOwnReview(review);
            return true;
        } : null);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class Holder extends RecyclerView.ViewHolder {
        final TextView avatar;
        final TextView author;
        final TextView stars;
        final TextView date;
        final TextView comment;

        Holder(View view) {
            super(view);
            avatar = view.findViewById(R.id.review_avatar);
            author = view.findViewById(R.id.review_author);
            stars = view.findViewById(R.id.review_stars);
            date = view.findViewById(R.id.review_date);
            comment = view.findViewById(R.id.review_comment);
        }
    }
}
