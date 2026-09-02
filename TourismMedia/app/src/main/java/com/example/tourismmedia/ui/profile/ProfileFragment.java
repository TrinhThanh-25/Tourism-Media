package com.example.tourismmedia.ui.profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.auth.AuthActivity;
import com.example.tourismmedia.data.AppRepository;

public class ProfileFragment extends Fragment {
    private static final String COVER = "https://www.homepaylater.vn/static/88357cc22dcb6460df003e4ff1dec446/9d72c/1_khu_du_lich_binh_quoi_2_mang_den_khong_gian_thu_gian_hoa_minh_vao_thien_nhien_299b8d9efd.jpg";
    private AppRepository repo;
    private TextView name, email, points, avatar, trips, checkins, savedLocations, savedTrips;
    private ImageView avatarImage;

    public ProfileFragment() { super(R.layout.fragment_profile); }

    @Override public void onViewCreated(@NonNull View view, Bundle state) {
        repo = AppRepository.get(requireContext());
        Glide.with(this).load(COVER).centerCrop().placeholder(R.drawable.bg_hero).error(R.drawable.bg_hero)
                .into((ImageView) view.findViewById(R.id.profile_cover));
        name = view.findViewById(R.id.profile_name);
        email = view.findViewById(R.id.profile_email);
        points = view.findViewById(R.id.profile_points);
        avatar = view.findViewById(R.id.profile_avatar);
        avatarImage = view.findViewById(R.id.profile_avatar_image);
        trips = view.findViewById(R.id.profile_trips);
        checkins = view.findViewById(R.id.profile_checkin_count);
        savedLocations = view.findViewById(R.id.profile_saved_locations_count);
        savedTrips = view.findViewById(R.id.profile_saved_trips_count);

        view.findViewById(R.id.edit_profile).setOnClickListener(v -> workspace("edit-profile", 0));
        view.findViewById(R.id.profile_settings).setOnClickListener(v -> workspace("edit-profile", 0));
        view.findViewById(R.id.profile_settings_top).setOnClickListener(v -> workspace("edit-profile", 0));
        view.findViewById(R.id.profile_saved_locations).setOnClickListener(v -> collection("favorite_locations"));
        view.findViewById(R.id.profile_saved_trips).setOnClickListener(v -> collection("favorite_trips"));
        view.findViewById(R.id.profile_checkins).setOnClickListener(v -> collection("checkins"));
        view.findViewById(R.id.profile_vouchers).setOnClickListener(v -> workspace("vouchers", 0));
        view.findViewById(R.id.profile_points_row).setOnClickListener(v -> workspace("points", 0));
        view.findViewById(R.id.logout_button).setOnClickListener(v -> {
            repo.logout((message, error, stale) -> {
                if (!isAdded()) return;
                startActivity(new Intent(requireContext(), AuthActivity.class));
                requireActivity().finish();
            });
        });
        load();
    }

    private void load() {
        repo.profile((profile, error, stale) -> {
            if (profile == null) { toast(error == null ? "Không tải được hồ sơ" : error); return; }
            name.setText(profile.username);
            email.setText("@" + profile.username + " · Thành viên Explorer");
            points.setText(String.format("%,d\nĐiểm", profile.points));
            String username = profile.username == null || profile.username.isBlank() ? "Traveler" : profile.username.trim();
            String[] words = username.split(" ");
            String initials = words[0].substring(0, 1);
            if (words.length > 1) initials += words[words.length - 1].substring(0, 1);
            avatar.setText(initials.toUpperCase());
            boolean hasAvatar = profile.avatar != null && !profile.avatar.isBlank();
            avatarImage.setVisibility(hasAvatar ? View.VISIBLE : View.GONE);
            avatar.setVisibility(hasAvatar ? View.GONE : View.VISIBLE);
            if (hasAvatar) Glide.with(this).load(profile.avatar).centerCrop().error(R.drawable.bg_hero).into(avatarImage);
        });
        repo.myTrips((data, error, stale) -> trips.setText(data.size() + "\nChuyến đi"));
        repo.checkIns((data, error, stale) -> checkins.setText(data.size() + "\nCheck-in"));
        repo.favoriteLocations((data, error, stale) -> savedLocations.setText(data.size() + " ›"));
        repo.favoriteTrips((data, error, stale) -> savedTrips.setText(data.size() + " ›"));
    }

    private void collection(String mode) {
        Bundle args = new Bundle(); args.putString("mode", mode);
        Navigation.findNavController(requireView()).navigate(R.id.accountCollectionFragment, args);
    }

    private void workspace(String mode, long id) {
        Bundle args = new Bundle(); args.putString("mode", mode); args.putLong("id", id);
        Navigation.findNavController(requireView()).navigate(R.id.member3WorkspaceFragment, args);
    }

    private void toast(String message) { Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show(); }
}
