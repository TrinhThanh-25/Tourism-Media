package com.example.tourismmedia.data;

import android.content.Context;

import androidx.annotation.NonNull;

import com.example.tourismmedia.data.api.ApiService;
import com.example.tourismmedia.data.model.AppModels.AuthResponse;
import com.example.tourismmedia.data.model.AppModels.Challenge;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.LocationImage;
import com.example.tourismmedia.data.model.AppModels.Message;
import com.example.tourismmedia.data.model.AppModels.PointTransaction;
import com.example.tourismmedia.data.model.AppModels.PointsBalance;
import com.example.tourismmedia.data.model.AppModels.Profile;
import com.example.tourismmedia.data.model.AppModels.ProfileUpdate;
import com.example.tourismmedia.data.model.AppModels.Review;
import com.example.tourismmedia.data.model.AppModels.Reward;
import com.example.tourismmedia.data.model.AppModels.RewardCatalog;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.data.model.AppModels.TripPage;
import com.example.tourismmedia.data.model.AppModels.TripReview;
import com.example.tourismmedia.data.model.AppModels.Voucher;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * Every network call the app makes. Callbacks run on the main thread (Retrofit's
 * default for Android) and never hand a null list to the UI: a failed list request
 * falls back to {@link SampleData} and reports {@code sample = true}.
 */
public class AppRepository {

    /**
     * @param data   payload, or null when a single-object request failed
     * @param error  human readable message, or null on success
     * @param sample true when the payload came from the offline fallback
     */
    public interface Result<T> {
        void onResult(T data, String error, boolean sample);
    }

    private static final String OFFLINE = "Không kết nối được máy chủ, đang hiển thị dữ liệu mẫu";
    private static final Gson GSON = new Gson();

    private static volatile AppRepository instance;

    private final ApiService api;
    private final SessionManager session;

    private AppRepository(Context context) {
        session = new SessionManager(context);
        api = ApiClient.service(context, session);
    }

    public static AppRepository get(Context context) {
        if (instance == null) {
            synchronized (AppRepository.class) {
                if (instance == null) {
                    instance = new AppRepository(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    public SessionManager session() {
        return session;
    }

    // ------------------------------------------------------------------ auth

    public void register(String username, String email, String password, Result<AuthResponse> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("email", email);
        body.put("password", password);
        authenticate(api.register(body), email, result);
    }

    public void login(String email, String password, Result<AuthResponse> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("password", password);
        authenticate(api.login(body), email, result);
    }

    public void forgotPassword(String email, String newPassword, Result<Message> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("email", email);
        body.put("new_password", newPassword);
        message(api.forgotPassword(body), result);
    }

    public void logout(Result<Message> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("refreshToken", session.refreshToken());
        api.logout(body).enqueue(new Callback<>() {
            @Override public void onResponse(@NonNull Call<Message> call, @NonNull Response<Message> response) {
                session.clear();
                result.onResult(response.body(), response.isSuccessful() ? null : errorOf(response, "Đăng xuất thất bại"), false);
            }
            @Override public void onFailure(@NonNull Call<Message> call, @NonNull Throwable throwable) {
                session.clear();
                result.onResult(null, null, false);
            }
        });
    }

    /**
     * OAuth is not wired up yet, so the provider buttons sign in to a real backend
     * demo account instead of faking a local-only session.
     */
    public void socialAccount(String provider, Result<AuthResponse> result) {
        String handle = provider.toLowerCase(Locale.ROOT);
        String email = handle + ".demo@tourismmedia.app";
        String password = handle + "-demo-2026";
        login(email, password, (data, error, sample) -> {
            if (data != null) {
                result.onResult(data, null, false);
            } else {
                register(handle + "_traveler", email, password, result);
            }
        });
    }

    private void authenticate(Call<AuthResponse> call, String email, Result<AuthResponse> result) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<AuthResponse> call, @NonNull Response<AuthResponse> response) {
                AuthResponse body = response.body();
                if (!response.isSuccessful() || body == null || body.token == null) {
                    result.onResult(null, errorOf(response, "Đăng nhập thất bại"), false);
                    return;
                }
                session.save(body, email);
                result.onResult(body, null, false);
            }

            @Override
            public void onFailure(@NonNull Call<AuthResponse> call, @NonNull Throwable throwable) {
                result.onResult(null, "Không kết nối được máy chủ: " + throwable.getMessage(), false);
            }
        });
    }

    // ------------------------------------------------------------- locations

    public void locations(String query, Result<List<Location>> result) {
        locations(query, null, null, null, null, "rating-desc", result);
    }

    public void locations(String query, String category, String type, Double minPrice, Double maxPrice,
                          String sort, Result<List<Location>> result) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "q", query);
        put(params, "category", category);
        put(params, "type", type);
        if (minPrice != null) {
            params.put("min_price", String.valueOf(minPrice.longValue()));
        }
        if (maxPrice != null) {
            params.put("max_price", String.valueOf(maxPrice.longValue()));
        }
        put(params, "sort_by", sort);
        list(api.locations(session.authorization(), params), result, SampleData::locations);
    }

    public void location(long id, Result<Location> result) {
        single(api.location(session.authorization(), id), result, "Không thể tải địa điểm này");
    }

    public void nearbyLocations(double latitude, double longitude, double radiusKm, int limit,
                                Result<List<Location>> result) {
        list(api.nearbyLocations(session.authorization(), latitude, longitude, radiusKm, limit),
                result, SampleData::locations);
    }

    public void locationImages(long id, Result<List<LocationImage>> result) {
        list(api.locationImages(id), result, () -> SampleData.images(id));
    }

    public void favoriteLocations(Result<List<Location>> result) {
        list(api.favoriteLocations(session.authorization()), result, ArrayList::new);
    }

    public void favoriteLocation(long id, boolean alreadyFavorite, Result<Message> result) {
        String auth = session.authorization();
        message(alreadyFavorite ? api.removeFavoriteLocation(auth, id) : api.addFavoriteLocation(auth, id), result);
    }

    public void checkIn(long locationId, Result<Message> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location_id", locationId);
        message(api.checkIn(session.authorization(), body), result);
    }

    public void checkIns(Result<List<Location>> result) {
        list(api.checkIns(session.authorization()), result, ArrayList::new);
    }

    // ------------------------------------------------------ location reviews

    public void locationReviews(long locationId, Result<List<Review>> result) {
        list(api.locationReviews(locationId), result, SampleData::reviews);
    }

    public void createLocationReview(long locationId, int rating, String comment, Result<Review> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location_id", locationId);
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.createLocationReview(session.authorization(), body), result, "Không thể gửi đánh giá");
    }

    public void updateLocationReview(long reviewId, int rating, String comment, Result<Review> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.updateLocationReview(session.authorization(), reviewId, body), result, "Không thể cập nhật đánh giá");
    }

    public void deleteLocationReview(long reviewId, Result<Message> result) {
        message(api.deleteLocationReview(session.authorization(), reviewId), result);
    }

    // --------------------------------------------------------------- account

    public void profile(Result<Profile> result) {
        single(api.profile(session.authorization()), result, "Không thể tải hồ sơ");
    }

    public void updateProfile(String username, Result<String> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        api.updateProfile(session.authorization(), body).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<ProfileUpdate> call, @NonNull Response<ProfileUpdate> response) {
                ProfileUpdate body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(null, errorOf(response, "Không thể cập nhật hồ sơ"), false);
                } else {
                    result.onResult(body.message == null ? "Đã cập nhật hồ sơ" : body.message, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<ProfileUpdate> call, @NonNull Throwable throwable) {
                result.onResult(null, "Không kết nối được máy chủ", false);
            }
        });
    }

    public void updateProfile(Map<String, String> values, Result<String> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                body.put(entry.getKey(), entry.getValue());
            }
        }
        api.updateProfile(session.authorization(), body).enqueue(new Callback<>() {
            @Override public void onResponse(@NonNull Call<ProfileUpdate> call, @NonNull Response<ProfileUpdate> response) {
                if (response.isSuccessful()) result.onResult("Cập nhật hồ sơ thành công", null, false);
                else result.onResult(null, errorOf(response, "Không thể cập nhật hồ sơ"), false);
            }
            @Override public void onFailure(@NonNull Call<ProfileUpdate> call, @NonNull Throwable throwable) {
                result.onResult(null, "Không kết nối được server", false);
            }
        });
    }

    public void changePassword(String oldPassword, String newPassword, Result<Message> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("old_password", oldPassword);
        body.put("new_password", newPassword);
        message(api.updatePassword(session.authorization(), body), result);
    }

    public void vouchers(Result<List<Voucher>> result) {
        list(api.vouchers(session.authorization()), result, ArrayList::new);
    }

    public void useVoucher(long voucherId, Result<Message> result) {
        message(api.useVoucher(session.authorization(), voucherId), result);
    }

    public void points(Result<PointsBalance> result) {
        single(api.points(session.authorization()), result, "Không tải được số điểm");
    }

    public void pointTransactions(Result<List<PointTransaction>> result) {
        list(api.pointTransactions(session.authorization()), result, ArrayList::new);
    }

    // ----------------------------------------------------------------- trips

    public void trips(Result<List<Trip>> result) {
        trips("", null, null, null, null, "rating-desc", result);
    }

    public void trips(String query, Double minRating, Long maxPrice, String sort, Result<List<Trip>> result) {
        trips(query, minRating, maxPrice, null, null, sort, result);
    }

    public void trips(String query, Double minRating, Long maxPrice, Integer minTime, Integer maxTime,
                      String sort, Result<List<Trip>> result) {
        Map<String, String> params = new LinkedHashMap<>();
        put(params, "q", query);
        if (minRating != null) {
            params.put("min_rating", String.valueOf(minRating));
        }
        if (maxPrice != null) {
            params.put("max_price", String.valueOf(maxPrice));
        }
        if (minTime != null) params.put("min_time", String.valueOf(minTime));
        if (maxTime != null) params.put("max_time", String.valueOf(maxTime));
        put(params, "sort", sort);
        api.trips(session.authorization(), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<TripPage> call, @NonNull Response<TripPage> response) {
                TripPage body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null) {
                    result.onResult(SampleData.trips(), errorOf(response, "Không thể tải chuyến đi"), true);
                } else {
                    result.onResult(body.data, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripPage> call, @NonNull Throwable throwable) {
                result.onResult(SampleData.trips(), OFFLINE, true);
            }
        });
    }

    public void trip(long id, Result<Trip> result) {
        single(api.trip(session.authorization(), id), result, "Không thể tải chuyến đi này");
    }

    public void createTrip(String title, String description, Result<Trip> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("locations", new ArrayList<>());
        single(api.createTrip(session.authorization(), body), result, "Không thể tạo chuyến đi");
    }

    public void saveTrip(Long id, Map<String, Object> body, Result<Trip> result) {
        Call<Trip> call = id == null
                ? api.createTrip(session.authorization(), body)
                : api.updateTrip(session.authorization(), id, body);
        single(call, result, "Không thể lưu chuyến đi");
    }

    public void setTripPublished(long id, boolean publish, Result<Trip> result) {
        single(publish
                ? api.publishTrip(session.authorization(), id)
                : api.unpublishTrip(session.authorization(), id),
                result, publish ? "Không thể đăng chuyến đi" : "Không thể gỡ chuyến đi");
    }

    public void myTrips(Result<List<Trip>> result) {
        list(api.myTrips(session.authorization()), result, ArrayList::new);
    }

    public void favoriteTrips(Result<List<Trip>> result) {
        list(api.favoriteTrips(session.authorization()), result, ArrayList::new);
    }

    public void favoriteTrip(long id, boolean alreadyFavorite, Result<Message> result) {
        String auth = session.authorization();
        message(alreadyFavorite ? api.removeFavoriteTrip(auth, id) : api.addFavoriteTrip(auth, id), result);
    }

    public void favoriteTrip(long id, Result<Message> result) {
        favoriteTrip(id, false, result);
    }

    public void tripReviews(long tripId, Result<List<TripReview>> result) {
        list(api.tripReviews(tripId), result, ArrayList::new);
    }

    public void createTripReview(long tripId, int rating, String comment, Result<TripReview> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", tripId);
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.createTripReview(session.authorization(), body), result, "Không thể gửi đánh giá");
    }

    // ---------------------------------------------------- challenges/rewards

    public void challenges(Result<List<Challenge>> result) {
        list(api.challenges(), result, SampleData::challenges);
    }

    public void myChallenges(Result<List<Challenge>> result) {
        list(api.myChallenges(session.authorization()), result, ArrayList::new);
    }

    public void challenge(long id, Result<Challenge> result) {
        single(api.challengeProgress(session.authorization(), id), result, "Không tải được tiến độ thử thách");
    }

    public void challengeInfo(long id, Result<Challenge> result) {
        single(api.challenge(id), result, "Không thể tải thử thách này");
    }

    public void joinChallenge(long id, Result<Message> result) {
        message(api.joinChallenge(session.authorization(), id), result);
    }

    public void completeChallenge(long id, Result<Message> result) {
        message(api.completeChallenge(session.authorization(), id), result);
    }

    public void rewards(Result<RewardCatalog> result) {
        single(api.rewardCatalog(session.authorization()), result, "Không thể tải phần thưởng");
    }

    public void reward(long id, Result<Reward> result) {
        single(api.reward(id), result, "Không thể tải phần thưởng");
    }

    public void redeem(long rewardId, Result<Message> result) {
        message(api.redeemReward(session.authorization(), rewardId), result);
    }

    // --------------------------------------------------------------- helpers

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    /** List requests always deliver a usable list so callers can iterate without null checks. */
    private <T> void list(Call<List<T>> call, Result<List<T>> result, Supplier<List<T>> fallback) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<T>> call, @NonNull Response<List<T>> response) {
                List<T> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(fallback.get(), errorOf(response, "Không thể tải dữ liệu"), true);
                } else {
                    result.onResult(body, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<T>> call, @NonNull Throwable throwable) {
                result.onResult(fallback.get(), OFFLINE, true);
            }
        });
    }

    private <T> void single(Call<T> call, Result<T> result, String failureMessage) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                T body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(null, errorOf(response, failureMessage), false);
                } else {
                    result.onResult(body, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable throwable) {
                result.onResult(null, failureMessage + " (máy chủ không phản hồi)", false);
            }
        });
    }

    private void message(Call<Message> call, Result<Message> result) {
        single(call, result, "Không thể thực hiện thao tác này");
    }

    /** Reads the `{ "error": "..." }` body the Express handlers return. */
    private static String errorOf(Response<?> response, String fallback) {
        if (response.errorBody() != null) {
            try {
                Message parsed = GSON.fromJson(response.errorBody().string(), Message.class);
                if (parsed != null && parsed.error != null && !parsed.error.isBlank()) {
                    return parsed.error;
                }
            } catch (Exception ignored) {
                // fall through to the generic message below
            }
        }
        return fallback + " (HTTP " + response.code() + ")";
    }
}
