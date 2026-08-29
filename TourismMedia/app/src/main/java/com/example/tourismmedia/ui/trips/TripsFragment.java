package com.example.tourismmedia.ui.trips;

import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TripsFragment extends Fragment {
    private static final String TAB_MINE = "mine";
    private static final String TAB_SAVED = "saved";
    private static final String TAB_COMMUNITY = "community";

    private AppRepository repo;
    private TripCardAdapter adapter;
    private MaterialButton mineButton;
    private MaterialButton savedButton;
    private MaterialButton communityButton;
    private MaterialButton sortButton;
    private EditText searchInput;
    private RecyclerView list;
    private TextView emptyView;
    private String activeTab = TAB_MINE;
    private String sort = "published_at-desc";
    private String query = "";
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private final Runnable searchTask = this::load;
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
        sortButton = view.findViewById(R.id.trips_sort);
        searchInput = view.findViewById(R.id.trips_search_input);

        mineButton.setOnClickListener(v -> selectTab(TAB_MINE));
        savedButton.setOnClickListener(v -> selectTab(TAB_SAVED));
        communityButton.setOnClickListener(v -> selectTab(TAB_COMMUNITY));
        sortButton.setOnClickListener(v -> chooseSort());
        view.findViewById(R.id.add_trip).setOnClickListener(v -> workspace("create", 0));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) {
                query = value.toString().trim();
                searchHandler.removeCallbacks(searchTask);
                searchHandler.postDelayed(searchTask, 300);
            }
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
        AppRepository.Result<List<Trip>> result = (data, error, stale) -> {
            if (!viewActive()) return;
            List<Trip> visibleTrips = present(data);
            adapter.setOwnerMode(TAB_MINE.equals(activeTab));
            adapter.submit(visibleTrips);
            boolean empty = visibleTrips.isEmpty();
            list.setVisibility(empty ? View.GONE : View.VISIBLE);
            emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (empty) {
                emptyView.setText(!query.isEmpty()
                        ? "🔎\n\nKhông tìm thấy chuyến đi phù hợp với ‘" + query + "’."
                        : error != null && error.contains("401")
                        ? "🔐\n\nPhiên đăng nhập đã hết hạn\nĐăng nhập lại để xem chuyến đi cá nhân."
                        : "🧭\n\nChưa có chuyến đi\nTạo một hành trình mới hoặc khám phá cộng đồng.");
            }
            if (error != null) Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
            if (TAB_MINE.equals(activeTab) && pendingLocationId > 0) offerLocationToTrip(data);
        };
        if (TAB_SAVED.equals(activeTab)) {
            repo.favoriteTrips(result);
        } else if (TAB_COMMUNITY.equals(activeTab)) {
            repo.trips(query, null, null, sort, result);
        } else {
            repo.myTrips(result);
        }
    }

    private List<Trip> present(List<Trip> source) {
        List<Trip> result = new ArrayList<>();
        if (source != null) {
            String needle = query.toLowerCase(Locale.ROOT);
            for (Trip trip : source) {
                String searchable = (safe(trip.title) + " " + safe(trip.description) + " " + safe(trip.highlight))
                        .toLowerCase(Locale.ROOT);
                if (needle.isEmpty() || searchable.contains(needle)) result.add(trip);
            }
        }
        if ("title-asc".equals(sort)) {
            result.sort(Comparator.comparing(trip -> safe(trip.title), String.CASE_INSENSITIVE_ORDER));
        } else if ("rating-desc".equals(sort)) {
            result.sort((first, second) -> {
                int reviewed = Integer.compare(second.reviewCount > 0 ? 1 : 0, first.reviewCount > 0 ? 1 : 0);
                return reviewed != 0 ? reviewed : Double.compare(second.rating, first.rating);
            });
        } else {
            result.sort(Comparator.comparingLong((Trip trip) -> trip.id).reversed());
        }
        return result;
    }

    private void chooseSort() {
        String[] labels = {"Mới nhất", "Tên A–Z", "Đánh giá cao"};
        String[] values = {"published_at-desc", "title-asc", "rating-desc"};
        int checked = "title-asc".equals(sort) ? 1 : "rating-desc".equals(sort) ? 2 : 0;
        new AlertDialog.Builder(requireContext())
                .setTitle("Sắp xếp chuyến đi")
                .setSingleChoiceItems(labels, checked, (dialog, selected) -> {
                    sort = values[selected];
                    sortButton.setText(labels[selected]);
                    dialog.dismiss();
                    load();
                })
                .setNegativeButton("Hủy", null)
                .show();
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

    @Override public void onDestroyView() {
        searchHandler.removeCallbacks(searchTask);
        super.onDestroyView();
    }

    private boolean viewActive() { return isAdded() && getView() != null; }
    private String safe(String value) { return value == null ? "" : value; }
}
