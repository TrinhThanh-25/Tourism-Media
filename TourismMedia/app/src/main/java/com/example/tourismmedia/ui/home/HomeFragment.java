package com.example.tourismmedia.ui.home;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.ui.location.LocationDetailFragment;
import com.example.tourismmedia.ui.location.LocationFormatter;
import com.example.tourismmedia.ui.location.adapter.CategoryAdapter;
import com.example.tourismmedia.ui.location.adapter.FeaturedLocationAdapter;
import com.example.tourismmedia.ui.location.adapter.LocationAdapter;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Landing screen: greeting, a hero picked from the highest rated location,
 * category shortcuts into Explore, a featured carousel and a top-rated list.
 */
public class HomeFragment extends Fragment {

    private static final int FEATURED_COUNT = 6;
    private static final int TOP_RATED_COUNT = 5;

    private AppRepository repository;
    private FeaturedLocationAdapter featuredAdapter;
    private LocationAdapter topRatedAdapter;
    private CategoryAdapter categoryAdapter;
    private ImageView heroImage;
    private TextView heroTitle;
    private TextView heroSubtitle;
    private TextView status;
    private TextView topRatedTitle;
    private RecyclerView topRatedList;
    private Location heroLocation;

    public HomeFragment() {
        super(R.layout.fragment_home);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        repository = AppRepository.get(requireContext());

        heroImage = view.findViewById(R.id.home_hero_image);
        heroTitle = view.findViewById(R.id.home_hero_title);
        heroSubtitle = view.findViewById(R.id.home_hero_subtitle);
        status = view.findViewById(R.id.home_status);

        bindHeader(view);
        bindLists(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        // Favourites and ratings can change on the detail screen, so reload on every entry.
        load();
    }

    private void bindHeader(View view) {
        String username = repository.session().username();
        ((TextView) view.findViewById(R.id.home_greeting)).setText(greeting());
        ((TextView) view.findViewById(R.id.home_username))
                .setText(username == null || username.isBlank() ? "Tourism Media" : username);

        TextView avatar = view.findViewById(R.id.home_avatar);
        ImageView avatarImage = view.findViewById(R.id.home_avatar_image);
        avatar.setText(LocationFormatter.initials(username));
        avatar.setOnClickListener(v -> navigate(R.id.profileFragment, null));
        avatarImage.setOnClickListener(v -> navigate(R.id.profileFragment, null));
        repository.profile((profile, error) -> {
            if (!isAdded() || getView() == null || profile == null) return;
            boolean hasAvatar = profile.avatar != null && !profile.avatar.isBlank();
            avatar.setVisibility(hasAvatar ? View.GONE : View.VISIBLE);
            avatarImage.setVisibility(hasAvatar ? View.VISIBLE : View.GONE);
            if (hasAvatar) Glide.with(this).load(profile.avatar).centerCrop().error(R.drawable.bg_hero).into(avatarImage);
        });

        view.findViewById(R.id.home_search).setOnClickListener(v -> openExplore(null));
        view.findViewById(R.id.home_explore).setOnClickListener(v -> openExplore(null));
        view.findViewById(R.id.home_see_all).setOnClickListener(v -> openExplore(null));
        view.findViewById(R.id.home_hero).setOnClickListener(v -> {
            if (heroLocation != null) {
                openDetail(heroLocation);
            }
        });
    }

    private void bindLists(View view) {
        RecyclerView categories = view.findViewById(R.id.home_categories);
        categories.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        categoryAdapter = new CategoryAdapter(this::openExplore);
        categories.setAdapter(categoryAdapter);

        RecyclerView featured = view.findViewById(R.id.home_featured);
        featured.setLayoutManager(new LinearLayoutManager(requireContext(), RecyclerView.HORIZONTAL, false));
        featuredAdapter = new FeaturedLocationAdapter(this::openDetail);
        featured.setAdapter(featuredAdapter);

        topRatedTitle = view.findViewById(R.id.home_top_rated_title);
        topRatedList = view.findViewById(R.id.home_top_rated);
        topRatedList.setLayoutManager(new LinearLayoutManager(requireContext()));
        topRatedAdapter = new LocationAdapter(this::openDetail, this::toggleFavorite);
        topRatedList.setAdapter(topRatedAdapter);
    }

    private void load() {
        repository.locations("", (data, error) -> {
            if (!isAdded() || getView() == null) {
                return;
            }
            bindHero(data);
            categoryAdapter.submit(getString(R.string.location_all_categories), categoriesOf(data));
            int featuredEnd = Math.min(FEATURED_COUNT, data.size());
            featuredAdapter.submit(data.subList(0, featuredEnd));

            // Anything the carousel did not show, capped so the landing page stays short.
            List<Location> rest = data.subList(featuredEnd,
                    Math.min(featuredEnd + TOP_RATED_COUNT, data.size()));
            topRatedAdapter.submit(rest);
            int restVisibility = rest.isEmpty() ? View.GONE : View.VISIBLE;
            topRatedTitle.setVisibility(restVisibility);
            topRatedList.setVisibility(restVisibility);
            status.setVisibility(error == null ? View.GONE : View.VISIBLE);
            status.setText(error);
        });
    }

    private void bindHero(List<Location> data) {
        if (data.isEmpty()) {
            return;
        }
        heroLocation = data.get(0);
        heroTitle.setText(heroLocation.name);
        String subtitle = LocationFormatter.meta(heroLocation);
        if (heroLocation.reviewCount > 0) {
            subtitle += "  ·  ★ " + LocationFormatter.rating(heroLocation.rating);
        }
        heroSubtitle.setText(subtitle);
        Glide.with(this)
                .load(heroLocation.imageUrl)
                .centerCrop()
                .placeholder(R.drawable.bg_hero)
                .error(R.drawable.bg_hero)
                .into(heroImage);
    }

    private static List<String> categoriesOf(List<Location> data) {
        LinkedHashSet<String> categories = new LinkedHashSet<>();
        for (Location location : data) {
            if (location.category != null && !location.category.isBlank()) {
                categories.add(location.category);
            }
        }
        return new ArrayList<>(categories);
    }

    private void toggleFavorite(Location location, int position) {
        boolean wasFavorite = location.isFavorite();
        repository.favoriteLocation(location.id, wasFavorite, (message, error) -> {
            if (!isAdded()) {
                return;
            }
            if (error != null) {
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show();
                return;
            }
            location.favorite = wasFavorite ? 0 : 1;
            topRatedAdapter.notifyItemChanged(position);
        });
    }

    private void openDetail(Location location) {
        navigate(R.id.locationDetailFragment, LocationDetailFragment.argsFor(location.id, location.name));
    }

    private void openExplore(String category) {
        Bundle args = null;
        if (category != null) {
            args = new Bundle();
            args.putString("category", category);
        }
        navigate(R.id.exploreFragment, args);
    }

    private void navigate(int destination, Bundle args) {
        NavController controller = Navigation.findNavController(requireView());
        controller.navigate(destination, args);
    }

    @StringRes
    private static int greeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hour < 12) {
            return R.string.home_greeting_morning;
        }
        return hour < 18 ? R.string.home_greeting_afternoon : R.string.home_greeting_evening;
    }
}
