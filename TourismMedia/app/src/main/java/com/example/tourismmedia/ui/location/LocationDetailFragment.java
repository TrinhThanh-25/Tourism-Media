package com.example.tourismmedia.ui.location;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RatingBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.PagerSnapHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.LocationImage;
import com.example.tourismmedia.data.model.AppModels.Review;
import com.example.tourismmedia.ui.common.SystemBars;
import com.example.tourismmedia.ui.location.adapter.LocationImageAdapter;
import com.example.tourismmedia.ui.location.adapter.ReviewAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Location detail: image gallery, practical information, favourite/check-in actions
 * and the review thread including writing, editing and deleting your own review.
 */
public class LocationDetailFragment extends Fragment {

    private static final String ARG_ID = "location_id";
    private static final String ARG_NAME = "location_name";

    private AppRepository repository;
    private LocationImageAdapter galleryAdapter;
    private ReviewAdapter reviewAdapter;

    private View content;
    private ProgressBar progress;
    private TextView errorView;
    private TextView category;
    private TextView title;
    private TextView rating;
    private TextView address;
    private TextView hours;
    private TextView price;
    private TextView description;
    private TextView highlights;
    private TextView galleryCounter;
    private TextView reviewSummary;
    private TextView reviewsEmpty;
    private ImageView favorite;
    private MaterialButton checkIn;
    private MaterialButton addToTrip;

    private long locationId;
    private Location location;

    public LocationDetailFragment() {
        super(R.layout.fragment_location_detail);
    }

    /** Arguments expected by this destination; see TASK.md section 9 for the cross-feature contract. */
    public static Bundle argsFor(long id, String name) {
        Bundle args = new Bundle();
        args.putLong(ARG_ID, id);
        args.putString(ARG_NAME, name);
        return args;
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.get(requireContext());
        locationId = getArguments() == null ? 0L : getArguments().getLong(ARG_ID);

        bindViews(view);
        bindGallery(view);
        bindReviews(view);

        // Show the name we already know while the full record is still loading.
        String presetName = getArguments() == null ? null : getArguments().getString(ARG_NAME);
        title.setText(presetName == null ? "" : presetName);

        view.findViewById(R.id.detail_back)
                .setOnClickListener(v -> Navigation.findNavController(view).navigateUp());

        loadLocation();
        loadReviews();
    }

    private void bindViews(View view) {
        content = view.findViewById(R.id.detail_scroll);
        progress = view.findViewById(R.id.detail_progress);
        errorView = view.findViewById(R.id.detail_error);
        category = view.findViewById(R.id.detail_category);
        title = view.findViewById(R.id.detail_title);
        rating = view.findViewById(R.id.detail_rating);
        address = view.findViewById(R.id.detail_address);
        hours = view.findViewById(R.id.detail_hours);
        price = view.findViewById(R.id.detail_price);
        description = view.findViewById(R.id.detail_description);
        highlights = view.findViewById(R.id.detail_highlights);
        galleryCounter = view.findViewById(R.id.detail_gallery_counter);
        reviewSummary = view.findViewById(R.id.detail_review_summary);
        reviewsEmpty = view.findViewById(R.id.detail_reviews_empty);
        favorite = view.findViewById(R.id.detail_favorite);
        checkIn = view.findViewById(R.id.detail_check_in);
        addToTrip = view.findViewById(R.id.detail_add_to_trip);

        content.setVisibility(View.INVISIBLE);
        SystemBars.marginTop(view.findViewById(R.id.detail_back));
        SystemBars.marginTop(favorite);
        favorite.setOnClickListener(v -> toggleFavorite());
        checkIn.setOnClickListener(v -> checkIn());
        addToTrip.setOnClickListener(v -> addToTrip());
    }

    private void bindGallery(View view) {
        RecyclerView gallery = view.findViewById(R.id.detail_gallery);
        LinearLayoutManager layoutManager =
                new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false);
        gallery.setLayoutManager(layoutManager);
        galleryAdapter = new LocationImageAdapter();
        gallery.setAdapter(galleryAdapter);
        new PagerSnapHelper().attachToRecyclerView(gallery);

        gallery.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                int position = layoutManager.findFirstCompletelyVisibleItemPosition();
                if (position != RecyclerView.NO_POSITION) {
                    updateGalleryCounter(position + 1, galleryAdapter.getItemCount());
                }
            }
        });
    }

    private void bindReviews(View view) {
        RecyclerView reviews = view.findViewById(R.id.detail_reviews);
        reviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        reviewAdapter = new ReviewAdapter(this::showOwnReviewOptions);
        reviews.setAdapter(reviewAdapter);
        view.findViewById(R.id.detail_write_review).setOnClickListener(v -> showReviewDialog());
    }

    // ------------------------------------------------------------------ load

    private void loadLocation() {
        repository.location(locationId, (data, error, sample) -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            progress.setVisibility(View.GONE);
            if (data == null) {
                errorView.setVisibility(View.VISIBLE);
                errorView.setText(error == null ? getString(R.string.location_load_failed) : error);
                return;
            }
            location = data;
            bindLocation(data);
            content.setVisibility(View.VISIBLE);
        });
    }

    private void bindLocation(Location data) {
        category.setText(LocationFormatter.meta(data).isEmpty()
                ? getString(R.string.location_label)
                : LocationFormatter.meta(data));
        title.setText(data.name);
        rating.setText(LocationFormatter.rating(data.rating)
                + " · " + LocationFormatter.reviewCount(requireContext(), data.reviewCount));
        address.setText(LocationFormatter.orUnknown(requireContext(), data.address));
        hours.setText(openingHours(data));
        price.setText(LocationFormatter.price(requireContext(), data.price));
        description.setText(LocationFormatter.orUnknown(requireContext(), data.description));

        boolean hasHighlights = data.highlights != null && !data.highlights.isBlank();
        highlights.setVisibility(hasHighlights ? View.VISIBLE : View.GONE);
        highlights.setText(data.highlights);

        updateFavoriteIcon();
        showGallery(data);
    }

    private String openingHours(Location data) {
        boolean open = data.openingHours != null && !data.openingHours.isBlank();
        boolean close = data.closingHours != null && !data.closingHours.isBlank();
        if (!open && !close) {
            return getString(R.string.location_unknown);
        }
        return (open ? data.openingHours : "?") + " – " + (close ? data.closingHours : "?");
    }

    /**
     * `GET /api/locations/:id` already embeds the gallery; the nested images endpoint is
     * only queried when that list came back empty.
     */
    private void showGallery(Location data) {
        List<LocationImage> images = data.gallery();
        if (!images.isEmpty()) {
            submitGallery(images);
            return;
        }
        repository.locationImages(data.id, (fetched, error, sample) -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            submitGallery(fetched.isEmpty() ? coverOnly(data) : fetched);
        });
    }

    /** Falls back to the card image so the header is never blank. */
    private List<LocationImage> coverOnly(Location data) {
        List<LocationImage> images = new ArrayList<>();
        if (data.imageUrl != null && !data.imageUrl.isBlank()) {
            LocationImage cover = new LocationImage();
            cover.url = data.imageUrl;
            images.add(cover);
        }
        return images;
    }

    private void submitGallery(List<LocationImage> images) {
        galleryAdapter.submit(images);
        updateGalleryCounter(images.isEmpty() ? 0 : 1, images.size());
    }

    private void updateGalleryCounter(int current, int total) {
        galleryCounter.setVisibility(total > 1 ? View.VISIBLE : View.GONE);
        galleryCounter.setText(String.format(Locale.US, "%d/%d", current, total));
    }

    private void loadReviews() {
        repository.locationReviews(locationId, (data, error, sample) -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            reviewAdapter.submit(data, repository.session().userId());
            reviewsEmpty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            reviewSummary.setText(data.isEmpty()
                    ? ""
                    : "★ " + LocationFormatter.rating(reviewAdapter.averageRating())
                    + " · " + LocationFormatter.reviewCount(requireContext(), data.size()));
        });
    }

    // --------------------------------------------------------------- actions

    private void updateFavoriteIcon() {
        boolean saved = location != null && location.isFavorite();
        favorite.setImageResource(saved ? R.drawable.ic_favorite_filled : R.drawable.ic_favorite_border);
        favorite.setContentDescription(getString(saved ? R.string.location_saved : R.string.location_save));
    }

    private void toggleFavorite() {
        if (location == null) {
            return;
        }
        boolean wasFavorite = location.isFavorite();
        repository.favoriteLocation(location.id, wasFavorite, (message, error, sample) -> {
            if (!isAdded()) {
                return;
            }
            if (error != null) {
                toast(error);
                return;
            }
            location.favorite = wasFavorite ? 0 : 1;
            updateFavoriteIcon();
            toast(message == null ? getString(R.string.location_saved) : message.message);
        });
    }

    private void checkIn() {
        if (location == null) {
            return;
        }
        checkIn.setEnabled(false);
        repository.checkIn(location.id, (message, error, sample) -> {
            if (!isAdded()) {
                return;
            }
            checkIn.setEnabled(true);
            toast(error != null
                    ? error
                    : (message == null ? getString(R.string.location_checked_in) : message.message));
        });
    }

    /**
     * Hands the location over to the Trips feature. Only `locationId` and `locationName`
     * cross the boundary, as agreed in TASK.md section 9, so neither feature edits the other's files.
     */
    private void addToTrip() {
        if (location == null) {
            return;
        }
        Bundle args = new Bundle();
        args.putLong(ARG_ID, location.id);
        args.putString(ARG_NAME, location.name);
        Navigation.findNavController(requireView()).navigate(R.id.tripsFragment, args);
    }

    // --------------------------------------------------------------- reviews

    private void showReviewDialog() {
        if (!repository.session().isLoggedIn()) {
            toast(getString(R.string.location_review_need_login));
            return;
        }

        Review existing = reviewAdapter.ownReview();
        View form = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_write_review, null, false);
        RatingBar ratingBar = form.findViewById(R.id.review_rating_bar);
        EditText comment = form.findViewById(R.id.review_comment_input);
        if (existing != null) {
            ratingBar.setRating(existing.rating);
            comment.setText(existing.comment);
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(existing == null ? R.string.location_write_review : R.string.location_section_reviews)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.location_review_send, (dialog, which) -> {
                    int stars = Math.round(ratingBar.getRating());
                    if (stars < 1) {
                        toast(getString(R.string.location_review_need_rating));
                        return;
                    }
                    submitReview(existing, stars, comment.getText().toString().trim());
                })
                .show();
    }

    private void submitReview(Review existing, int stars, String comment) {
        AppRepository.Result<Review> callback = (data, error, sample) -> {
            if (!isAdded()) {
                return;
            }
            if (error != null) {
                toast(error);
                return;
            }
            toast(getString(existing == null
                    ? R.string.location_review_posted
                    : R.string.location_review_updated));
            loadReviews();
            // The server recomputes the average, so pull the location again.
            loadLocation();
        };

        if (existing == null) {
            repository.createLocationReview(locationId, stars, comment, callback);
        } else {
            repository.updateLocationReview(existing.id, stars, comment, callback);
        }
    }

    private void showOwnReviewOptions(Review review) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.location_section_reviews)
                .setItems(new String[]{
                        getString(R.string.location_review_edit),
                        getString(R.string.location_review_delete)
                }, (dialog, which) -> {
                    if (which == 0) {
                        showReviewDialog();
                    } else {
                        deleteReview(review);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void deleteReview(Review review) {
        repository.deleteLocationReview(review.id, (message, error, sample) -> {
            if (!isAdded()) {
                return;
            }
            toast(error != null ? error : getString(R.string.location_review_deleted));
            if (error == null) {
                loadReviews();
                loadLocation();
            }
        });
    }

    private void toast(String message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }
}
