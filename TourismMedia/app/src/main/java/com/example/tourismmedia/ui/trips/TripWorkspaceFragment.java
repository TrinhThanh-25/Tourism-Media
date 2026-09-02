package com.example.tourismmedia.ui.trips;

import android.Manifest;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RatingBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.example.tourismmedia.R;
import com.example.tourismmedia.data.AppRepository;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.data.model.AppModels.TripReview;
import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TripWorkspaceFragment extends Fragment {
    private static final int INK = Color.rgb(23, 51, 43);
    private static final int MUTED = Color.rgb(113, 128, 120);
    private static final int FOREST = Color.rgb(15, 65, 51);

    private LinearLayout content;
    private TextView title;
    private TextView subtitle;
    private MaterialButton primary;
    private MaterialButton secondary;
    private MaterialButton top;
    private AppRepository repo;
    private long id;
    private String mode;
    private long initialLocationId;
    private Trip trip;
    private EditText name;
    private EditText description;
    private EditText price;
    private EditText duration;
    private EditText highlight;
    private final List<Long> selected = new ArrayList<>();
    private Uri selectedCover;
    private ImageView coverPreview;
    private final ActivityResultLauncher<String[]> coverPicker = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri == null) return;
                selectedCover = uri;
                try { requireContext().getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                catch (SecurityException ignored) { }
                if (coverPreview != null) Glide.with(this).load(uri).centerCrop().into(coverPreview);
            });
    private final ActivityResultLauncher<String[]> imagePermission = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                boolean allowed = Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_IMAGES))
                        || Boolean.TRUE.equals(result.get(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED));
                if (allowed) coverPicker.launch(new String[]{"image/*"});
                else Toast.makeText(requireContext(), "Cần quyền chia sẻ ảnh để chọn ảnh chuyến đi", Toast.LENGTH_LONG).show();
            });

    public TripWorkspaceFragment() {
        super(R.layout.fragment_trip_workspace);
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        repo = AppRepository.get(requireContext());
        id = getArguments() == null ? 0 : getArguments().getLong("id");
        mode = getArguments() == null ? "create" : getArguments().getString("mode", "create");
        initialLocationId = getArguments() == null ? 0 : getArguments().getLong("initial_location_id", 0);
        content = view.findViewById(R.id.workspace_content);
        title = view.findViewById(R.id.workspace_title);
        subtitle = view.findViewById(R.id.workspace_subtitle);
        primary = view.findViewById(R.id.workspace_primary);
        secondary = view.findViewById(R.id.workspace_secondary);
        top = view.findViewById(R.id.workspace_top_action);
        view.findViewById(R.id.workspace_back).setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        secondary.setOnClickListener(v -> Navigation.findNavController(view).navigateUp());
        if (id > 0 && !mode.equals("filter") && !mode.equals("reviews") && !mode.equals("write-review")) {
            repo.trip(id, (item, error, stale) -> {
                trip = item;
                render();
            });
        } else {
            render();
        }
    }

    private void render() {
        content.removeAllViews();
        top.setVisibility(View.GONE);
        primary.setVisibility(View.VISIBLE);
        switch (mode) {
            case "filter": filter(); break;
            case "edit": form(true); break;
            case "itinerary": itinerary(); break;
            case "add-location": locations(); break;
            case "reviews": reviews(false); break;
            case "write-review": reviews(true); break;
            default: form(false);
        }
    }

    private void header(String heading, String caption) {
        title.setText(heading);
        subtitle.setText(caption);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(requireContext());
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(INK);
        if (bold) view.setTypeface(null, Typeface.BOLD);
        return view;
    }

    private TextView heading(String value) {
        TextView view = text(value, 22, true);
        view.setPadding(dp(4), dp(14), dp(4), dp(8));
        content.addView(view);
        return view;
    }

    private TextView caption(String value) {
        TextView view = text(value, 14, false);
        view.setTextColor(MUTED);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(dp(4), 0, dp(4), dp(14));
        content.addView(view, params);
        return view;
    }

    private GradientDrawable card() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(18));
        return drawable;
    }

    private GradientDrawable outline(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(dp(radius));
        drawable.setStroke(dp(1), color);
        return drawable;
    }

    private EditText fieldTo(LinearLayout parent, String label, String value, boolean multiline) {
        TextView labelView = text(label, 13, true);
        labelView.setTextColor(MUTED);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-1, -2);
        labelParams.setMargins(0, 0, 0, dp(6));
        parent.addView(labelView, labelParams);

        EditText field = new EditText(requireContext());
        field.setText(value == null ? "" : value);
        field.setHint("Nhập " + label.toLowerCase());
        field.setTextSize(15);
        field.setTextColor(INK);
        field.setHintTextColor(Color.rgb(160, 170, 164));
        field.setBackground(outline(Color.rgb(228, 232, 227), 16));
        field.setPadding(dp(14), multiline ? dp(12) : 0, dp(14), multiline ? dp(12) : 0);
        field.setGravity(multiline ? Gravity.TOP : Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(multiline ? 92 : 52));
        params.setMargins(0, 0, 0, dp(14));
        parent.addView(field, params);
        return field;
    }

    private EditText searchField(String hint) {
        EditText field = new EditText(requireContext());
        field.setHint(hint);
        field.setSingleLine(true);
        field.setTextSize(15);
        field.setTextColor(INK);
        field.setHintTextColor(Color.rgb(160, 170, 164));
        field.setBackground(outline(Color.rgb(228, 232, 227), 16));
        field.setPadding(dp(14), 0, dp(14), 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.setMargins(0, 0, 0, dp(14));
        content.addView(field, params);
        return field;
    }

    private void form(boolean editing) {
        header(editing ? "Chỉnh sửa chuyến đi" : "Tạo chuyến đi",
                editing ? "Cập nhật hành trình của bạn" : "Lên kế hoạch cho hành trình mới");
        heading("Thông tin hành trình");
        caption(editing ? "Điều chỉnh thông tin và lịch trình bên dưới."
                : "Mặc định chuyến đi là riêng tư; bạn có thể đăng cộng đồng bất cứ lúc nào.");

        LinearLayout form = new LinearLayout(requireContext());
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(18), dp(18), dp(18), dp(8));
        form.setBackground(card());
        content.addView(form, new LinearLayout.LayoutParams(-1, -2));

        FrameLayout coverBox = new FrameLayout(requireContext());
        GradientDrawable coverBackground = new GradientDrawable();
        coverBackground.setColor(Color.rgb(247, 250, 248));
        coverBackground.setCornerRadius(dp(18));
        coverBackground.setStroke(dp(1), Color.rgb(173, 191, 181), dp(5), dp(4));
        coverBox.setBackground(coverBackground);
        coverBox.setClipToOutline(true);

        coverPreview = new ImageView(requireContext());
        coverPreview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        coverBox.addView(coverPreview, new FrameLayout.LayoutParams(-1, -1));
        boolean hasCover = trip != null && trip.imageUrl != null && !trip.imageUrl.isBlank();
        if (selectedCover != null) Glide.with(this).load(selectedCover).centerCrop().into(coverPreview);
        else if (hasCover) Glide.with(this).load(trip.imageUrl).centerCrop().into(coverPreview);
        TextView coverHint = text(editing ? "Thay ảnh bìa từ thư viện" : "+ Chọn ảnh bìa từ thư viện", 15, true);
        coverHint.setGravity(Gravity.CENTER);
        coverHint.setTextColor(hasCover ? Color.WHITE : MUTED);
        coverBox.addView(coverHint, new FrameLayout.LayoutParams(-1, -1));
        coverBox.setOnClickListener(v -> requestCoverPermission());
        LinearLayout.LayoutParams coverParams = new LinearLayout.LayoutParams(-1, dp(150));
        coverParams.setMargins(0, 0, 0, dp(16));
        form.addView(coverBox, coverParams);

        name = fieldTo(form, "Tên chuyến đi", trip == null ? "" : trip.title, false);
        description = fieldTo(form, "Mô tả", trip == null ? "" : trip.description, true);

        LinearLayout paired = new LinearLayout(requireContext());
        paired.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = new LinearLayout(requireContext());
        left.setOrientation(LinearLayout.VERTICAL);
        LinearLayout right = new LinearLayout(requireContext());
        right.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams leftParams = new LinearLayout.LayoutParams(0, -2, 1);
        leftParams.setMargins(0, 0, dp(5), 0);
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -2, 1);
        rightParams.setMargins(dp(5), 0, 0, 0);
        paired.addView(left, leftParams);
        paired.addView(right, rightParams);
        form.addView(paired, new LinearLayout.LayoutParams(-1, -2));
        price = fieldTo(left, "Ngân sách", trip == null ? "" : String.valueOf(trip.estimatedPrice), false);
        price.setInputType(InputType.TYPE_CLASS_NUMBER);
        duration = fieldTo(right, "Thời lượng (phút)", trip == null ? "" : String.valueOf(trip.totalTime), false);
        duration.setHint("Số phút");
        duration.setInputType(InputType.TYPE_CLASS_NUMBER);
        highlight = fieldTo(form, "Điểm nổi bật", trip == null ? "" : trip.highlight, false);

        LinearLayout locationCard = new LinearLayout(requireContext());
        locationCard.setOrientation(LinearLayout.VERTICAL);
        locationCard.setPadding(dp(18), dp(16), dp(18), dp(16));
        locationCard.setBackground(card());
        LinearLayout titleRow = new LinearLayout(requireContext());
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView locationTitle = text("Địa điểm", 18, true);
        titleRow.addView(locationTitle, new LinearLayout.LayoutParams(0, -2, 1));
        MaterialButton add = new MaterialButton(requireContext());
        add.setText("+ Thêm");
        add.setTextColor(FOREST);
        add.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
        titleRow.addView(add, new LinearLayout.LayoutParams(-2, dp(44)));
        locationCard.addView(titleRow);
        int placeCount = trip == null || trip.locations == null ? 0 : trip.locations.size();
        TextView locationNote = text(placeCount == 0
                ? "Chưa có địa điểm. Bạn có thể thêm sau khi tạo chuyến đi."
                : placeCount + " địa điểm đang có trong itinerary.", 14, false);
        locationNote.setTextColor(MUTED);
        locationCard.addView(locationNote);
        add.setOnClickListener(v -> {
            if (id > 0) open("add-location");
            else Toast.makeText(requireContext(), "Hãy tạo chuyến đi trước khi thêm địa điểm", Toast.LENGTH_SHORT).show();
        });
        LinearLayout.LayoutParams locationParams = new LinearLayout.LayoutParams(-1, -2);
        locationParams.setMargins(0, dp(14), 0, 0);
        content.addView(locationCard, locationParams);

        if (editing) {
            MaterialButton itineraryButton = new MaterialButton(requireContext());
            itineraryButton.setText("♧  Quản lý itinerary  ›");
            itineraryButton.setOnClickListener(v -> open("itinerary"));
            LinearLayout.LayoutParams itineraryParams = new LinearLayout.LayoutParams(-1, dp(54));
            itineraryParams.setMargins(0, dp(14), 0, 0);
            content.addView(itineraryButton, itineraryParams);
        }

        primary.setText(editing ? "Lưu thay đổi" : "Tạo và đăng cộng đồng");
        secondary.setText(editing ? "Hủy" : "Lưu riêng tư");
        primary.setOnClickListener(v -> save(editing, true));
        secondary.setOnClickListener(v -> {
            if (editing) Navigation.findNavController(requireView()).navigateUp();
            else save(false, false);
        });
    }

    private void save(boolean editing, boolean publishAfterCreate) {
        if (name.getText().toString().trim().isEmpty()) {
            name.setError("Vui lòng nhập tên");
            return;
        }
        Map<String, Object> body = new HashMap<>();
        body.put("title", name.getText().toString().trim());
        body.put("description", description.getText().toString().trim());
        body.put("estimate_price", number(price));
        body.put("total_time", number(duration));
        body.put("key_highlight", highlight.getText().toString().trim());
        if (selectedCover != null) body.put("url_image", selectedCover.toString());
        if (!editing) body.put("locations", new ArrayList<>());
        repo.saveTrip(editing ? id : null, body, (item, error, stale) -> {
            if (!isAdded()) return;
            if (error != null || item == null) {
                Toast.makeText(requireContext(), error == null ? "Không thể lưu chuyến đi" : error, Toast.LENGTH_LONG).show();
                return;
            }
            if (!editing && publishAfterCreate) {
                repo.setTripPublished(item.id, true, (published, publishError, ignored) -> {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), publishError == null
                            ? "Đã tạo và đăng chuyến đi lên cộng đồng" : publishError, Toast.LENGTH_LONG).show();
                    if (publishError == null) Navigation.findNavController(requireView()).navigateUp();
                });
            } else {
                Toast.makeText(requireContext(), editing ? "Đã lưu thay đổi" : "Đã lưu chuyến đi riêng tư", Toast.LENGTH_LONG).show();
                Navigation.findNavController(requireView()).navigateUp();
            }
        });
    }

    private long number(EditText field) {
        try { return Long.parseLong(field.getText().toString()); }
        catch (Exception ignored) { return 0; }
    }

    private void requestCoverPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_IMAGES) == android.content.pm.PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
            coverPicker.launch(new String[]{"image/*"});
        } else {
            imagePermission.launch(new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED});
        }
    }

    private MaterialButton chip(String label, LinearLayout row, boolean selected) {
        MaterialButton button = new MaterialButton(requireContext());
        button.setText(label);
        button.setTextSize(13);
        button.setMinWidth(0);
        button.setInsetLeft(0);
        button.setInsetRight(0);
        button.setInsetTop(0);
        button.setInsetBottom(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setCornerRadius(dp(22));
        setChipSelected(button, selected);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, dp(44));
        params.setMargins(0, 0, dp(8), 0);
        row.addView(button, params);
        return button;
    }

    private void setChipSelected(MaterialButton button, boolean selected) {
        button.setBackgroundTintList(ColorStateList.valueOf(selected ? FOREST : Color.WHITE));
        button.setTextColor(selected ? Color.WHITE : MUTED);
        button.setStrokeWidth(selected ? 0 : dp(1));
        button.setStrokeColor(ColorStateList.valueOf(Color.rgb(224, 229, 225)));
    }

    private LinearLayout chipRow(LinearLayout parent) {
        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void filter() {
        header("Lọc chuyến đi", "Tìm hành trình phù hợp với bạn");
        LinearLayout panel = new LinearLayout(requireContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(14), dp(18), dp(18));
        panel.setBackground(card());
        content.addView(panel, new LinearLayout.LayoutParams(-1, -2));

        int[] sortIndex = {0};
        double[] ratingValue = {4};
        TextView sortTitle = text("Sắp xếp", 18, true);
        sortTitle.setPadding(0, 0, 0, dp(8));
        panel.addView(sortTitle);
        LinearLayout sorts = chipRow(panel);
        MaterialButton high = chip("Đánh giá cao", sorts, true);
        MaterialButton newest = chip("Mới nhất", sorts, false);
        MaterialButton cheap = chip("Giá thấp", sorts, false);
        MaterialButton[] sortButtons = {high, newest, cheap};
        for (int i = 0; i < sortButtons.length; i++) {
            final int index = i;
            sortButtons[i].setOnClickListener(v -> {
                sortIndex[0] = index;
                for (int j = 0; j < sortButtons.length; j++) setChipSelected(sortButtons[j], j == index);
            });
        }

        TextView ratingTitle = text("Mức đánh giá", 18, true);
        ratingTitle.setPadding(0, dp(14), 0, dp(8));
        panel.addView(ratingTitle);
        LinearLayout ratings = chipRow(panel);
        MaterialButton all = chip("Tất cả", ratings, false);
        MaterialButton four = chip("Từ 4 sao", ratings, true);
        MaterialButton fourHalf = chip("Từ 4.5 sao", ratings, false);
        MaterialButton[] ratingButtons = {all, four, fourHalf};
        double[] ratingValues = {0, 4, 4.5};
        for (int i = 0; i < ratingButtons.length; i++) {
            final int index = i;
            ratingButtons[i].setOnClickListener(v -> {
                ratingValue[0] = ratingValues[index];
                for (int j = 0; j < ratingButtons.length; j++) setChipSelected(ratingButtons[j], j == index);
            });
        }

        TextView budgetTitle = text("Ngân sách tối đa", 13, true);
        budgetTitle.setTextColor(MUTED);
        budgetTitle.setPadding(0, dp(14), 0, 0);
        panel.addView(budgetTitle);
        SeekBar budget = new SeekBar(requireContext());
        budget.setMax(10000000);
        budget.setProgress(5000000);
        panel.addView(budget, new LinearLayout.LayoutParams(-1, dp(44)));
        TextView budgetValue = text("5.000.000đ", 13, true);
        budgetValue.setTextColor(MUTED);
        panel.addView(budgetValue);
        budget.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar seekBar, int value, boolean fromUser) {
                budgetValue.setText(String.format("%,dđ", value));
            }
            public void onStartTrackingTouch(SeekBar seekBar) { }
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        TextView durationTitle = text("Thời lượng", 18, true);
        durationTitle.setPadding(0, dp(14), 0, dp(8));
        panel.addView(durationTitle);
        LinearLayout durations = chipRow(panel);
        int[] durationValue = {0};
        MaterialButton durationAll = chip("Tất cả", durations, true);
        MaterialButton durationOne = chip("1 ngày", durations, false);
        MaterialButton durationTwoThree = chip("2–3 ngày", durations, false);
        MaterialButton durationFour = chip("4+ ngày", durations, false);
        MaterialButton[] durationButtons = {durationAll, durationOne, durationTwoThree, durationFour};
        for (int i = 0; i < durationButtons.length; i++) {
            final int index = i;
            durationButtons[i].setOnClickListener(v -> {
                durationValue[0] = index;
                for (int j = 0; j < durationButtons.length; j++) setChipSelected(durationButtons[j], j == index);
            });
        }

        primary.setText("Xem chuyến đi");
        secondary.setText("Xóa lọc");
        primary.setOnClickListener(v -> {
            String[] sortValues = {"rating-desc", "id-desc", "estimate_price-asc"};
            Bundle result = new Bundle();
            result.putString("sort", sortValues[sortIndex[0]]);
            result.putDouble("rating", ratingValue[0]);
            result.putLong("budget", budget.getProgress());
            result.putInt("duration", durationValue[0]);
            returnFilter(result);
        });
        secondary.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putString("sort", "rating-desc");
            result.putInt("duration", 0);
            returnFilter(result);
        });
    }

    private void returnFilter(Bundle result) {
        NavController nav = Navigation.findNavController(requireView());
        if (nav.getPreviousBackStackEntry() != null) {
            nav.getPreviousBackStackEntry().getSavedStateHandle().set("trip_filter", result);
        }
        nav.navigateUp();
    }

    private void itinerary() {
        header("Itinerary", trip == null ? "Đang tải..." : trip.title);
        top.setVisibility(View.VISIBLE);
        top.setOnClickListener(v -> open("add-location"));
        heading((trip == null || trip.locations == null ? 0 : trip.locations.size()) + " địa điểm trong hành trình");
        if (trip == null || trip.locations == null || trip.locations.isEmpty()) {
            empty("Chưa có địa điểm", "Thêm địa điểm để bắt đầu xây dựng lịch trình.");
        } else {
            int indexValue = 1;
            int currentDay = -1;
            LinearLayout dayCard = null;
            for (Location location : trip.locations) {
                int day = location.day == null ? 1 : location.day;
                if (day != currentDay) {
                    dayCard = new LinearLayout(requireContext());
                    dayCard.setOrientation(LinearLayout.VERTICAL);
                    dayCard.setPadding(dp(16), dp(14), dp(16), dp(8));
                    dayCard.setBackground(card());
                    LinearLayout.LayoutParams dayParams = new LinearLayout.LayoutParams(-1, -2);
                    dayParams.setMargins(0, 0, 0, dp(12));
                    content.addView(dayCard, dayParams);
                    TextView dayTitle = text("Ngày " + day, 18, true);
                    dayTitle.setPadding(0, 0, 0, dp(8));
                    dayCard.addView(dayTitle);
                    currentDay = day;
                }
                LinearLayout itemRow = row();
                itemRow.setBackgroundColor(Color.TRANSPARENT);
                TextView time = text(location.time == null || location.time.isBlank()
                        ? String.format("%02d", indexValue) : location.time, 13, true);
                indexValue++;
                time.setTextColor(Color.rgb(47, 107, 80));
                itemRow.addView(time, new LinearLayout.LayoutParams(dp(52), -2));
                ImageView image = new ImageView(requireContext());
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                itemRow.addView(image, new LinearLayout.LayoutParams(dp(64), dp(58)));
                Glide.with(this).load(location.imageUrl).centerCrop().placeholder(R.drawable.bg_hero).into(image);
                LinearLayout labels = new LinearLayout(requireContext());
                labels.setOrientation(LinearLayout.VERTICAL);
                labels.addView(text(location.name, 16, true));
                TextView meta = text((location.category == null ? "Địa điểm" : location.category)
                        + " · " + (location.city == null ? "" : location.city), 13, false);
                meta.setTextColor(MUTED);
                labels.addView(meta);
                LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1);
                labelParams.setMargins(dp(12), 0, 0, 0);
                itemRow.addView(labels, labelParams);
                itemRow.addView(text("☰", 20, false));
                dayCard.addView(itemRow);
            }
        }
        primary.setText("Lưu itinerary");
        secondary.setText("Thêm địa điểm");
        secondary.setOnClickListener(v -> open("add-location"));
        primary.setOnClickListener(v -> saveCurrentItinerary());
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(requireContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(card());
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(params);
        return row;
    }

    private void empty(String heading, String message) {
        TextView view = text("🧭\n\n" + heading + "\n" + message, 17, true);
        view.setGravity(Gravity.CENTER);
        view.setTextColor(MUTED);
        content.addView(view, new LinearLayout.LayoutParams(-1, dp(260)));
    }

    private void locations() {
        header("Thêm địa điểm", "Chọn điểm đến cho itinerary");
        EditText search = searchField("Tìm địa điểm...");
        List<CheckBox> rows = new ArrayList<>();
        List<Location> visibleLocations = new ArrayList<>();
        repo.locations("", (data, error, stale) -> {
            for (Location location : data.subList(0, Math.min(30, data.size()))) {
                CheckBox box = new CheckBox(requireContext());
                box.setText(location.name + "\n" + (location.category == null ? "" : location.category) + " · ★ " + location.rating);
                box.setTextColor(INK);
                box.setPadding(dp(14), dp(8), dp(14), dp(8));
                box.setBackground(card());
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(72));
                params.setMargins(0, 0, 0, dp(9));
                content.addView(box, params);
                rows.add(box);
                visibleLocations.add(location);
                if (location.id == initialLocationId && !selected.contains(location.id)) {
                    selected.add(location.id);
                    box.setChecked(true);
                }
                box.setOnCheckedChangeListener((button, checked) -> {
                    if (checked && !selected.contains(location.id)) selected.add(location.id);
                    else if (!checked) selected.remove(location.id);
                    primary.setText("Thêm " + selected.size() + " địa điểm");
                });
            }
            primary.setText("Thêm " + selected.size() + " địa điểm");
        });
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) {
                String query = value.toString().trim().toLowerCase(Locale.ROOT);
                for (int i = 0; i < rows.size(); i++) {
                    Location location = visibleLocations.get(i);
                    String searchable = ((location.name == null ? "" : location.name) + " "
                            + (location.category == null ? "" : location.category) + " "
                            + (location.city == null ? "" : location.city)).toLowerCase(Locale.ROOT);
                    rows.get(i).setVisibility(searchable.contains(query) ? View.VISIBLE : View.GONE);
                }
            }
            @Override public void afterTextChanged(Editable value) { }
        });
        primary.setText("Thêm 0 địa điểm");
        secondary.setText("Hủy");
        primary.setOnClickListener(v -> addSelectedLocations());
    }

    private void addSelectedLocations() {
        if (selected.isEmpty()) {
            Toast.makeText(requireContext(), "Hãy chọn địa điểm", Toast.LENGTH_SHORT).show();
            return;
        }
        List<Map<String, Object>> items = new ArrayList<>();
        if (trip != null && trip.locations != null) {
            for (int index = 0; index < trip.locations.size(); index++) {
                items.add(locationMap(trip.locations.get(index), index));
            }
        }
        for (Long locationId : selected) {
            boolean exists = false;
            for (Map<String, Object> item : items) {
                if (locationId.equals(((Number) item.get("location_id")).longValue())) exists = true;
            }
            if (!exists) {
                Map<String, Object> item = new HashMap<>();
                item.put("location_id", locationId);
                item.put("order_index", items.size());
                item.put("day", 1);
                items.add(item);
            }
        }
        Map<String, Object> body = new HashMap<>();
        body.put("locations", items);
        repo.saveTrip(id, body, (item, error, stale) -> {
            Toast.makeText(requireContext(), error == null ? "Đã cập nhật itinerary" : error, Toast.LENGTH_LONG).show();
            if (error == null) Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private Map<String, Object> locationMap(Location location, int fallbackOrder) {
        Map<String, Object> item = new HashMap<>();
        item.put("location_id", location.id);
        item.put("order_index", location.orderIndex == null ? fallbackOrder : location.orderIndex);
        if (location.day != null) item.put("day", location.day);
        if (location.time != null && !location.time.isBlank()) item.put("time", location.time);
        return item;
    }

    private void saveCurrentItinerary() {
        if (trip == null || trip.locations == null) return;
        List<Map<String, Object>> items = new ArrayList<>();
        for (int index = 0; index < trip.locations.size(); index++) {
            items.add(locationMap(trip.locations.get(index), index));
        }
        Map<String, Object> body = new HashMap<>();
        body.put("locations", items);
        repo.saveTrip(id, body, (updated, error, stale) -> {
            if (!isAdded()) return;
            Toast.makeText(requireContext(), error == null ? "Itinerary đã được lưu" : error, Toast.LENGTH_SHORT).show();
            if (error == null) Navigation.findNavController(requireView()).navigateUp();
        });
    }

    private void reviews(boolean write) {
        header("Đánh giá chuyến đi", "Chia sẻ từ cộng đồng");
        if (write) {
            reviewForm();
            return;
        }
        MaterialButton add = new MaterialButton(requireContext());
        add.setText("Viết đánh giá");
        add.setOnClickListener(v -> {
            mode = "write-review";
            render();
        });
        content.addView(add, new LinearLayout.LayoutParams(-1, dp(52)));
        repo.tripReviews(id, (data, error, stale) -> {
            heading(data.size() + " đánh giá");
            if (data.isEmpty()) empty("Chưa có đánh giá", "Hãy là người đầu tiên chia sẻ trải nghiệm.");
            for (TripReview review : data) {
                LinearLayout reviewCard = row();
                reviewCard.setOrientation(LinearLayout.VERTICAL);
                reviewCard.addView(text((review.username == null ? "Traveler" : review.username)
                        + "   " + "★".repeat(Math.max(1, review.rating)), 16, true));
                TextView body = text(review.comment == null ? "Không có nội dung" : review.comment, 14, false);
                body.setTextColor(MUTED);
                reviewCard.addView(body);
                content.addView(reviewCard);
            }
        });
        primary.setVisibility(View.GONE);
        secondary.setText("Quay lại");
    }

    private void reviewForm() {
        heading("Đánh giá của bạn");
        RatingBar stars = new RatingBar(requireContext(), null, android.R.attr.ratingBarStyle);
        stars.setNumStars(5);
        stars.setStepSize(1);
        stars.setRating(5);
        content.addView(stars);
        EditText comment = searchField("Chia sẻ trải nghiệm");
        comment.setSingleLine(false);
        comment.setGravity(Gravity.TOP);
        comment.setMinHeight(dp(120));
        primary.setText("Gửi đánh giá");
        primary.setOnClickListener(v -> repo.createTripReview(id, (int) stars.getRating(),
                comment.getText().toString(), (item, error, stale) -> {
                    Toast.makeText(requireContext(), error == null ? "Đã gửi đánh giá" : error, Toast.LENGTH_LONG).show();
                    if (error == null) {
                        mode = "reviews";
                        render();
                    }
                }));
        secondary.setText("Hủy");
    }

    private void open(String next) {
        Bundle args = new Bundle();
        args.putString("mode", next);
        args.putLong("id", id);
        Navigation.findNavController(requireView()).navigate(R.id.tripWorkspaceFragment, args);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
