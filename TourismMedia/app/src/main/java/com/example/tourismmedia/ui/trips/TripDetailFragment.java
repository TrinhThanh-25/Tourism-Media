package com.example.tourismmedia.ui.trips;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.ui.common.SimpleCardAdapter;
import com.example.tourismmedia.ui.location.LocationDetailFragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TripDetailFragment extends Fragment {
    private long id;
    private Trip trip;
    private AppRepository repository;
    private TextView saveButton;
    private TextView publishButton;
    private View editButton;

    public TripDetailFragment() { super(R.layout.fragment_trip_detail); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        id = getArguments() == null ? 0 : getArguments().getLong("id");
        repository = AppRepository.get(requireContext());
        saveButton = view.findViewById(R.id.trip_detail_save);
        publishButton = view.findViewById(R.id.trip_detail_publish);
        editButton = view.findViewById(R.id.trip_detail_edit);
        view.findViewById(R.id.trip_detail_back).setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        view.findViewById(R.id.trip_detail_itinerary).setOnClickListener(v -> open("itinerary"));
        editButton.setOnClickListener(v -> open("edit"));
        view.findViewById(R.id.trip_detail_reviews).setOnClickListener(v -> open("reviews"));
        saveButton.setOnClickListener(v -> toggleFavorite());
        publishButton.setOnClickListener(v -> togglePublished());

        RecyclerView list = view.findViewById(R.id.trip_detail_locations);
        list.setLayoutManager(new LinearLayoutManager(requireContext()));
        SimpleCardAdapter adapter = new SimpleCardAdapter(item -> {
            Location location = (Location) item.value;
            Navigation.findNavController(view).navigate(R.id.locationDetailFragment,
                    LocationDetailFragment.argsFor(location.id, location.name));
        });
        list.setAdapter(adapter);

        repository.trip(id, (loaded, error, stale) -> {
            if (!isAdded() || getView() == null) return;
            if (loaded == null) { Toast.makeText(requireContext(), error, Toast.LENGTH_LONG).show(); return; }
            trip = loaded;
            ((TextView)view.findViewById(R.id.trip_detail_title)).setText(loaded.title);
            ((TextView)view.findViewById(R.id.trip_detail_rating)).setText("★ " + loaded.rating + " · " + loaded.reviewCount + " đánh giá");
            ((TextView)view.findViewById(R.id.trip_detail_author)).setText(loaded.published == 1
                    ? "Đăng bởi " + safe(loaded.authorUsername) + " · " + date(loaded.publishedAt) : "Chuyến đi riêng tư");
            ((TextView)view.findViewById(R.id.trip_detail_time)).setText(time(loaded.totalTime));
            int count = loaded.locations == null ? 0 : loaded.locations.size();
            ((TextView)view.findViewById(R.id.trip_detail_places)).setText(count + " địa điểm");
            ((TextView)view.findViewById(R.id.trip_detail_price)).setText(String.format(Locale.getDefault(), "%,dđ", loaded.estimatedPrice));
            ((TextView)view.findViewById(R.id.trip_detail_description)).setText(safe(loaded.description));
            ((TextView)view.findViewById(R.id.trip_detail_highlight)).setText("Điểm nổi bật\n" + safe(loaded.highlight));
            updateFavoriteButton();
            boolean owner = loaded.userId == repository.session().userId();
            editButton.setVisibility(owner ? View.VISIBLE : View.GONE);
            publishButton.setVisibility(owner ? View.VISIBLE : View.GONE);
            publishButton.setText(loaded.published == 1 ? "Gỡ cộng đồng" : "Đăng cộng đồng");
            saveButton.setVisibility(!owner && loaded.published == 1 ? View.VISIBLE : View.GONE);
            Glide.with(this).load(loaded.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).error(R.drawable.bg_hero).into((ImageView)view.findViewById(R.id.trip_detail_image));
            List<SimpleCardAdapter.CardItem> cards = new ArrayList<>();
            if (loaded.locations != null) for (Location location : loaded.locations) cards.add(new SimpleCardAdapter.CardItem("⌖", location.name, safe(location.category) + " · " + safe(location.city), "★ " + location.rating, location.imageUrl, location));
            adapter.submit(cards);
        });
    }

    private void toggleFavorite() {
        if (trip == null) return;
        boolean saved = trip.favorite == 1;
        saveButton.setEnabled(false);
        repository.favoriteTrip(id, saved, (message, error, stale) -> {
            if (!isAdded()) return;
            saveButton.setEnabled(true);
            if (error != null) { Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show(); return; }
            trip.favorite = saved ? 0 : 1;
            updateFavoriteButton();
            Toast.makeText(requireContext(), saved ? "Đã bỏ lưu chuyến đi" : "Đã lưu chuyến đi", Toast.LENGTH_SHORT).show();
        });
    }

    private void updateFavoriteButton() { saveButton.setText(trip != null && trip.favorite == 1 ? "♥ Đã lưu" : "♡ Lưu"); }
    private void togglePublished() {
        if (trip == null) return;
        boolean publish = trip.published != 1;
        publishButton.setEnabled(false);
        repository.setTripPublished(id, publish, (updated, error, stale) -> {
            if (!isAdded()) return;
            publishButton.setEnabled(true);
            if (error != null || updated == null) {
                Toast.makeText(requireContext(), error == null ? "Không thể cập nhật trạng thái" : error, Toast.LENGTH_SHORT).show();
                return;
            }
            trip.published = updated.published;
            trip.publishedAt = updated.publishedAt;
            publishButton.setText(trip.published == 1 ? "Gỡ cộng đồng" : "Đăng cộng đồng");
            ((TextView)requireView().findViewById(R.id.trip_detail_author)).setText(trip.published == 1
                    ? "Đăng bởi " + safe(updated.authorUsername) + " · " + date(updated.publishedAt) : "Chuyến đi riêng tư");
            Toast.makeText(requireContext(), publish ? "Đã đăng lên cộng đồng" : "Đã gỡ khỏi cộng đồng", Toast.LENGTH_SHORT).show();
        });
    }
    private void open(String mode) { Bundle args=new Bundle(); args.putString("mode",mode); args.putLong("id",id); Navigation.findNavController(requireView()).navigate(R.id.tripWorkspaceFragment,args); }
    private String safe(String value) { return value == null || value.isBlank() ? "Chưa có thông tin" : value; }
    private String date(String value) { return value == null || value.isBlank() ? "vừa đăng" : value.substring(0, Math.min(10, value.length())); }
    private String time(int value) { return value >= 1440 ? Math.max(1,value/1440)+" ngày" : value+" phút"; }
}
