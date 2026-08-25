package com.example.tourismmedia.ui.trips;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.google.android.material.button.MaterialButton;

public class TripsFragment extends Fragment {
    private static final String TAB_MINE = "mine";
    private static final String TAB_SAVED = "saved";
    private static final String TAB_COMMUNITY = "community";

    private AppRepository repo;
    private TripCardAdapter adapter;
    private MaterialButton mineButton;
    private MaterialButton savedButton;
    private MaterialButton communityButton;
    private MaterialButton filterButton;
    private RecyclerView list;
    private TextView emptyView;
    private String activeTab = TAB_MINE;
    private String sort = "rating-desc";
    private Double minRating;
    private Long maxPrice;
    private Integer minTime;
    private Integer maxTime;
    private long pendingLocationId;
    private String pendingLocationName;

    public TripsFragment() {
        super(R.layout.fragment_trips);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        repo = AppRepository.get(requireContext());
        if (getArguments() != null) {
            pendingLocationId = getArguments().getLong("location_id", 0);
            pendingLocationName = getArguments().getString("location_name", "địa điểm này");
        }
        list = view.findViewById(R.id.trips_list);
        emptyView = view.findViewById(R.id.trips_empty);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new TripCardAdapter(this::details, this::toggleFavorite, this::togglePublished);
        list.setAdapter(adapter);

        mineButton = view.findViewById(R.id.trips_tab_mine);
        savedButton = view.findViewById(R.id.trips_tab_saved);
        communityButton = view.findViewById(R.id.trips_tab_community);
        filterButton = view.findViewById(R.id.trips_search_toggle);

        mineButton.setOnClickListener(v -> selectTab(TAB_MINE));
        savedButton.setOnClickListener(v -> selectTab(TAB_SAVED));
        communityButton.setOnClickListener(v -> selectTab(TAB_COMMUNITY));
        filterButton.setOnClickListener(v -> workspace("filter", 0));
        view.findViewById(R.id.add_trip).setOnClickListener(v -> workspace("create", 0));

        Navigation.findNavController(view).getCurrentBackStackEntry().getSavedStateHandle()
                .<Bundle>getLiveData("trip_filter")
                .observe(getViewLifecycleOwner(), result -> {
                    sort = result.getString("sort", "rating-desc");
                    double rating = result.getDouble("rating", 0);
                    minRating = rating == 0 ? null : rating;
                    long budget = result.getLong("budget", 0);
                    maxPrice = budget == 0 ? null : budget;
                    int durationMode = result.getInt("duration", 0);
                    minTime = durationMode == 3 ? 4 * 1440 : null;
                    maxTime = durationMode == 1 ? 1440 : durationMode == 2 ? 3 * 1440 : null;
                    if (durationMode == 2) minTime = 2 * 1440;
                    filterButton.setText(minRating == null && maxPrice == null && minTime == null && maxTime == null
                            && "rating-desc".equals(sort) ? "☷" : "●");
                    selectTab(TAB_COMMUNITY);
                });

        updateTabs();
        load();
    }

    private void selectTab(String tab) {
        activeTab = tab;
        updateTabs();
        load();
    }

    private void updateTabs() {
        styleTab(mineButton, TAB_MINE.equals(activeTab));
        styleTab(savedButton, TAB_SAVED.equals(activeTab));
        styleTab(communityButton, TAB_COMMUNITY.equals(activeTab));
    }

    private void styleTab(MaterialButton button, boolean selected) {
        int background = ContextCompat.getColor(requireContext(), selected ? R.color.forest : R.color.white);
        int foreground = ContextCompat.getColor(requireContext(), selected ? R.color.white : R.color.muted);
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
    }

    private void load() {
        AppRepository.Result<java.util.List<Trip>> result = (data, error, stale) -> {
            if (!viewActive()) return;
            adapter.setOwnerMode(TAB_MINE.equals(activeTab));
            adapter.submit(data);
            boolean empty = data == null || data.isEmpty();
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                emptyView.setText(error != null && error.contains("401")
                        ? "🔐\n\nPhiên đăng nhập đã hết hạn\nĐăng nhập lại để xem chuyến đi cá nhân."
                        : "🧭\n\nChưa có chuyến đi\nTạo một hành trình mới hoặc khám phá cộng đồng.");
            }
            if (error != null) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            if (TAB_MINE.equals(activeTab) && pendingLocationId > 0) offerLocationToTrip(data);
        };
        if (TAB_SAVED.equals(activeTab)) {
            repo.favoriteTrips(result);
        } else if (TAB_COMMUNITY.equals(activeTab)) {
            repo.trips("", minRating, maxPrice, minTime, maxTime, sort, result);
        } else {
            repo.myTrips(result);
        }
    }

    private void details(Trip trip) {
        Bundle args = new Bundle();
        args.putLong("id", trip.id);
        Navigation.findNavController(requireView()).navigate(R.id.tripDetailFragment, args);
    }

    private void toggleFavorite(Trip trip, int position) {
        boolean saved = trip.favorite == 1;
        repo.favoriteTrip(trip.id, saved, (message, error, stale) -> {
            if (!viewActive()) return;
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                return;
            }
            trip.favorite = saved ? 0 : 1;
            adapter.notifyItemChanged(position);
            if (TAB_SAVED.equals(activeTab) && saved) load();
        });
    }

    private void togglePublished(Trip trip, int position) {
        boolean publish = trip.published != 1;
        repo.setTripPublished(trip.id, publish, (updated, error, stale) -> {
            if (!viewActive()) return;
            if (error != null || updated == null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                return;
            }
            trip.published = updated.published;
            trip.publishedAt = updated.publishedAt;
            trip.authorUsername = updated.authorUsername;
            adapter.notifyItemChanged(position);
            Toast.makeText(requireContext(), publish
                    ? "Đã đăng chuyến đi lên cộng đồng" : "Đã gỡ chuyến đi khỏi cộng đồng", Toast.LENGTH_SHORT).show();
        });
    }

    private void offerLocationToTrip(java.util.List<Trip> trips) {
        final long locationId = pendingLocationId;
        pendingLocationId = 0;
        if (trips == null || trips.isEmpty()) {
            Toast.makeText(requireContext(), "Hãy tạo chuyến đi trước khi thêm " + pendingLocationName, Toast.LENGTH_LONG).show();
            workspace("create", 0);
            return;
        }
        String[] labels = new String[trips.size()];
        for (int i = 0; i < trips.size(); i++) labels[i] = trips.get(i).title;
        new AlertDialog.Builder(requireContext())
                .setTitle("Thêm “" + pendingLocationName + "” vào chuyến đi")
                .setItems(labels, (dialog, which) -> workspace("add-location", trips.get(which).id, locationId))
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void workspace(String mode, long id) {
        workspace(mode, id, 0);
    }

    private void workspace(String mode, long id, long locationId) {
        Bundle args = new Bundle();
        args.putString("mode", mode);
        args.putLong("id", id);
        if (locationId > 0) args.putLong("initial_location_id", locationId);
        Navigation.findNavController(requireView()).navigate(R.id.tripWorkspaceFragment, args);
    }

    private boolean viewActive() { return isAdded() && getView() != null; }
}
