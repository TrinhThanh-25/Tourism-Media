package com.example.tourismmedia.ui.explore;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.ui.common.SystemBars;
import com.example.tourismmedia.ui.location.LocationDetailFragment;
import com.example.tourismmedia.ui.location.adapter.CategoryAdapter;
import com.example.tourismmedia.ui.location.adapter.LocationAdapter;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Location search with server-side filtering. Typing is debounced so each keystroke
 * does not fire its own request, and category/price/sort choices all map onto the
 * query parameters `GET /api/locations` accepts.
 */
public class ExploreFragment extends Fragment {

    /** Labels shown in the sort dialog, parallel to {@link #SORT_VALUES}. */
    private static final int[] SORT_LABELS = {
            R.string.explore_sort_rating, R.string.explore_sort_reviews,
            R.string.explore_sort_price_asc, R.string.explore_sort_price_desc,
            R.string.explore_sort_name_asc, R.string.explore_sort_name_desc
    };
    private static final String[] SORT_VALUES = {
            "rating-desc", "review_count-desc", "price-asc", "price-desc", "name-asc", "name-desc"
    };
    private static final long SEARCH_DEBOUNCE_MS = 350L;

    private final Handler debounce = new Handler(Looper.getMainLooper());
    private final Runnable searchTask = this::load;
    private final LinkedHashSet<String> knownCategories = new LinkedHashSet<>();
    private final LinkedHashSet<String> knownTypes = new LinkedHashSet<>();

    private AppRepository repository;
    private LocationAdapter adapter;
    private CategoryAdapter categoryAdapter;
    private TextView status;
    private TextView empty;
    private ProgressBar progress;
    private MaterialButton filterButton;
    private MaterialButton sortButton;

    private String query = "";
    private String category;
    private String type;
    private String sort = SORT_VALUES[0];
    private Double minPrice;
    private Double maxPrice;

    public ExploreFragment() {
        super(R.layout.fragment_explore);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.get(requireContext());
        SystemBars.padTop(view.findViewById(R.id.explore_root));

        status = view.findViewById(R.id.explore_status);
        empty = view.findViewById(R.id.explore_empty);
        progress = view.findViewById(R.id.explore_progress);
        filterButton = view.findViewById(R.id.explore_filter);
        sortButton = view.findViewById(R.id.explore_sort);
        sortButton.setText(SORT_LABELS[0]);

        RecyclerView list = view.findViewById(R.id.explore_list);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new LocationAdapter(this::openDetail, this::toggleFavorite);
        list.setAdapter(adapter);

        RecyclerView categories = view.findViewById(R.id.explore_categories);
        categories.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(chosen -> {
            category = chosen;
            load();
        });
        categories.setAdapter(categoryAdapter);

        EditText search = view.findViewById(R.id.explore_search);
        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                query = s.toString();
                debounce.removeCallbacks(searchTask);
                debounce.postDelayed(searchTask, SEARCH_DEBOUNCE_MS);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        filterButton.setOnClickListener(v -> showFilterDialog());
        sortButton.setOnClickListener(v -> showSortDialog());
        view.findViewById(R.id.explore_clear).setOnClickListener(v -> clearFilters());

        // Home can deep-link into a category chip.
        Bundle args = getArguments();
        if (args != null && args.getString("category") != null) {
            category = args.getString("category");
        }
        load();
    }

    @Override
    public void onDestroyView() {
        debounce.removeCallbacks(searchTask);
        super.onDestroyView();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        repository.locations(query, category, type, minPrice, maxPrice, sort, (data, error, sample) -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            progress.setVisibility(View.GONE);
            rememberFacets(data);
            adapter.submit(data);
            empty.setVisibility(data.isEmpty() ? View.VISIBLE : View.GONE);
            status.setText(error != null
                    ? error
                    : getString(R.string.explore_result_count, data.size()));
        });
    }

    /**
     * The API has no facet endpoint, so the chip and spinner options are collected
     * from whatever results have already been seen.
     */
    private void rememberFacets(List<Location> data) {
        int before = knownCategories.size();
        for (Location location : data) {
            if (location.category != null && !location.category.isBlank()) {
                knownCategories.add(location.category);
            }
            if (location.type != null && !location.type.isBlank()) {
                knownTypes.add(location.type);
            }
        }
        if (knownCategories.size() != before) {
            categoryAdapter.submit(getString(R.string.location_all_categories), new ArrayList<>(knownCategories));
        }
        categoryAdapter.select(category);
    }

    private void clearFilters() {
        category = null;
        type = null;
        minPrice = null;
        maxPrice = null;
        filterButton.setText(R.string.explore_filter);
        categoryAdapter.select(null);
        load();
    }

    private void showFilterDialog() {
        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(48, 12, 48, 0);

        Spinner categorySpinner = spinner(form, withAll(knownCategories), category);
        Spinner typeSpinner = spinner(form, withAll(knownTypes), type);

        EditText min = priceField(R.string.explore_min_price, minPrice);
        EditText max = priceField(R.string.explore_max_price, maxPrice);
        form.addView(min);
        form.addView(max);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.explore_dialog_filter)
                .setView(form)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.explore_apply, (dialog, which) -> {
                    category = selectedValue(categorySpinner);
                    type = selectedValue(typeSpinner);
                    minPrice = number(min);
                    maxPrice = number(max);
                    boolean filtering = category != null || type != null || minPrice != null || maxPrice != null;
                    filterButton.setText(filtering ? R.string.explore_filter_active : R.string.explore_filter);
                    categoryAdapter.select(category);
                    load();
                })
                .show();
    }

    private void showSortDialog() {
        String[] labels = new String[SORT_LABELS.length];
        for (int i = 0; i < SORT_LABELS.length; i++) {
            labels[i] = getString(SORT_LABELS[i]);
        }
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.explore_dialog_sort)
                .setSingleChoiceItems(labels, indexOf(SORT_VALUES, sort), (dialog, which) -> {
                    sort = SORT_VALUES[which];
                    sortButton.setText(SORT_LABELS[which]);
                    dialog.dismiss();
                    load();
                })
                .show();
    }

    private EditText priceField(int hintRes, Double value) {
        EditText field = new EditText(requireContext());
        field.setHint(hintRes);
        field.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        if (value != null) {
            field.setText(String.valueOf(value.longValue()));
        }
        return field;
    }

    private Spinner spinner(LinearLayout parent, List<String> values, String selected) {
        Spinner view = new Spinner(requireContext());
        view.setAdapter(new ArrayAdapter<>(requireContext(),
                android.R.layout.simple_spinner_dropdown_item, values));
        view.setSelection(Math.max(0, values.indexOf(selected)));
        parent.addView(view);
        return view;
    }

    private List<String> withAll(LinkedHashSet<String> source) {
        List<String> values = new ArrayList<>();
        values.add(getString(R.string.location_all_categories));
        values.addAll(source);
        return values;
    }

    private String selectedValue(Spinner spinner) {
        Object selected = spinner.getSelectedItem();
        if (selected == null) {
            return null;
        }
        String value = selected.toString();
        return getString(R.string.location_all_categories).equals(value) ? null : value;
    }

    private static Double number(EditText field) {
        String text = field.getText().toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Double.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static int indexOf(String[] values, String needle) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(needle)) {
                return i;
            }
        }
        return 0;
    }

    private void toggleFavorite(Location location, int position) {
        boolean wasFavorite = location.isFavorite();
        repository.favoriteLocation(location.id, wasFavorite, (message, error, sample) -> {
            if (!isAdded()) {
                return;
            }
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                return;
            }
            location.favorite = wasFavorite ? 0 : 1;
            adapter.notifyItemChanged(position);
        });
    }

    private void openDetail(Location location) {
        Navigation.findNavController(requireView())
                .navigate(R.id.locationDetailFragment,
                        LocationDetailFragment.argsFor(location.id, location.name));
    }
}
