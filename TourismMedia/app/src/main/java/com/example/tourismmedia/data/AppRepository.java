package com.example.tourismmedia.data;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

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
import com.example.tourismmedia.data.model.AppModels.UploadResult;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import java.io.IOException;
import java.io.InputStream;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import okhttp3.MediaType;
import okhttp3.RequestBody;
import okio.BufferedSink;
import okio.Okio;

/**
 * Every network call the app makes. Callbacks run on the main thread (Retrofit's
 * default for Android). Every payload comes from Travel-App-Server; failed list
 * requests return an empty list plus the real error instead of fabricated data.
 */
public class AppRepository {

    /**
     * @param data   payload, or null when a single-object request failed
     * @param error  human readable message, or null on success
     * @param sample retained for callback compatibility; always false because sample data is disabled
     */
    public interface Result<T> {
        void onResult(T data, String error, boolean sample);
    }

    private static final String OFFLINE = "Không kết nối được máy chủ";
    private static final long CACHE_TTL_MS = 2 * 60 * 1000L;
    private static final long[] NETWORK_RETRY_DELAYS_MS = {600L, 1500L};
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Gson GSON = new Gson();

    private static volatile AppRepository instance;

    private final ApiService api;
    private final SessionManager session;
    private final Context context;
    private final Map<String, CacheEntry> memoryCache = new ConcurrentHashMap<>();

    private static final class CacheEntry {
        final Object value;
        final long storedAt;

        CacheEntry(Object value) {
            this.value = value;
            this.storedAt = SystemClock.elapsedRealtime();
        }
    }

    private AppRepository(Context context) {
        this.context = context.getApplicationContext();
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
                clearCache();
                result.onResult(response.body(), response.isSuccessful() ? null : errorOf(response, "Đăng xuất thất bại"), false);
            }
            @Override public void onFailure(@NonNull Call<Message> call, @NonNull Throwable throwable) {
                session.clear();
                clearCache();
                result.onResult(null, null, false);
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
                clearCache();
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
        cachedList(key("locations", params), api.locations(session.authorization(), params), result);
    }

    public void location(long id, Result<Location> result) {
        cachedSingle(key("location", id), api.location(session.authorization(), id), result, "Không thể tải địa điểm này");
    }

    public void nearbyLocations(double latitude, double longitude, double radiusKm, int limit,
                                Result<List<Location>> result) {
        cachedList(key("nearby", latitude, longitude, radiusKm, limit),
                api.nearbyLocations(session.authorization(), latitude, longitude, radiusKm, limit), result);
    }

    public void locationImages(long id, Result<List<LocationImage>> result) {
        cachedList(key("locationImages", id), api.locationImages(id), result);
    }

    public void favoriteLocations(Result<List<Location>> result) {
        cachedList(key("favoriteLocations"), api.favoriteLocations(session.authorization()), result);
    }

    public void favoriteLocation(long id, boolean alreadyFavorite, Result<Message> result) {
        String auth = session.authorization();
        message(alreadyFavorite ? api.removeFavoriteLocation(auth, id) : api.addFavoriteLocation(auth, id), invalidating(result));
    }

    public void checkIn(long locationId, Result<Message> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location_id", locationId);
        message(api.checkIn(session.authorization(), body), invalidating(result));
    }

    public void checkIns(Result<List<Location>> result) {
        cachedList(key("checkIns"), api.checkIns(session.authorization()), result);
    }

    public void recordLocationRead(long locationId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location_id", locationId);
        api.recordLocationRead(session.authorization(), body).enqueue(new Callback<>() {
            @Override public void onResponse(@NonNull Call<Message> call, @NonNull Response<Message> response) {
                if (response.isSuccessful()) invalidateMatching("myChallenges", "challengeProgress");
            }
            @Override public void onFailure(@NonNull Call<Message> call, @NonNull Throwable throwable) { }
        });
    }

    public void uploadImage(Uri uri, Result<String> result) {
        String mime = context.getContentResolver().getType(uri);
        if (!("image/jpeg".equals(mime) || "image/png".equals(mime) || "image/webp".equals(mime))) {
            result.onResult(null, "Chỉ hỗ trợ ảnh JPEG, PNG hoặc WebP", false);
            return;
        }
        MediaType mediaType = MediaType.get(mime);
        RequestBody requestBody = new RequestBody() {
            @Override public MediaType contentType() { return mediaType; }
            @Override public void writeTo(@NonNull BufferedSink sink) throws IOException {
                try (InputStream input = context.getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Không thể đọc ảnh đã chọn");
                    sink.writeAll(Okio.source(input));
                }
            }
        };
        api.uploadImage(session.authorization(), requestBody).enqueue(new Callback<>() {
            @Override public void onResponse(@NonNull Call<UploadResult> call, @NonNull Response<UploadResult> response) {
                UploadResult uploaded = response.body();
                if (!response.isSuccessful() || uploaded == null || uploaded.url == null) {
                    result.onResult(null, errorOf(response, "Không thể tải ảnh lên"), false);
                } else result.onResult(uploaded.url, null, false);
            }
            @Override public void onFailure(@NonNull Call<UploadResult> call, @NonNull Throwable throwable) {
                result.onResult(null, OFFLINE + ": " + throwable.getMessage(), false);
            }
        });
    }

    // ------------------------------------------------------ location reviews

    public void locationReviews(long locationId, Result<List<Review>> result) {
        cachedList(key("locationReviews", locationId), api.locationReviews(locationId), result);
    }

    public void createLocationReview(long locationId, int rating, String comment, Result<Review> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("location_id", locationId);
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.createLocationReview(session.authorization(), body), invalidating(result), "Không thể gửi đánh giá");
    }

    public void updateLocationReview(long reviewId, int rating, String comment, Result<Review> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.updateLocationReview(session.authorization(), reviewId, body), invalidating(result), "Không thể cập nhật đánh giá");
    }

    public void deleteLocationReview(long reviewId, Result<Message> result) {
        message(api.deleteLocationReview(session.authorization(), reviewId), invalidating(result));
    }

    // --------------------------------------------------------------- account

    public void profile(Result<Profile> result) {
        cachedSingle(key("profile"), api.profile(session.authorization()), result, "Không thể tải hồ sơ");
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
                    clearCache();
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
                if (response.isSuccessful()) {
                    clearCache();
                    result.onResult("Cập nhật hồ sơ thành công", null, false);
                }
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
        message(api.updatePassword(session.authorization(), body), invalidating(result));
    }

    public void vouchers(Result<List<Voucher>> result) {
        cachedList(key("vouchers"), api.vouchers(session.authorization()), result);
    }

    public void useVoucher(long voucherId, Result<Message> result) {
        message(api.useVoucher(session.authorization(), voucherId), invalidating(result));
    }

    public void points(Result<PointsBalance> result) {
        cachedSingle(key("points"), api.points(session.authorization()), result, "Không tải được số điểm");
    }

    public void pointTransactions(Result<List<PointTransaction>> result) {
        cachedList(key("pointTransactions"), api.pointTransactions(session.authorization()), result);
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
        String cacheKey = key("trips", params);
        if (deliverCached(cacheKey, result)) return;
        api.trips(session.authorization(), params).enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<TripPage> call, @NonNull Response<TripPage> response) {
                TripPage body = response.body();
                if (!response.isSuccessful() || body == null || body.data == null) {
                    result.onResult(new ArrayList<>(), errorOf(response, "Không thể tải chuyến đi"), false);
                } else {
                    memoryCache.put(cacheKey, new CacheEntry(new ArrayList<>(body.data)));
                    result.onResult(body.data, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<TripPage> call, @NonNull Throwable throwable) {
                result.onResult(new ArrayList<>(), OFFLINE + ": " + throwable.getMessage(), false);
            }
        });
    }

    public void trip(long id, Result<Trip> result) {
        cachedSingle(key("trip", id), api.trip(session.authorization(), id), result, "Không thể tải chuyến đi này");
    }

    public void createTrip(String title, String description, Result<Trip> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        body.put("description", description);
        body.put("locations", new ArrayList<>());
        single(api.createTrip(session.authorization(), body), invalidating(result), "Không thể tạo chuyến đi");
    }

    public void saveTrip(Long id, Map<String, Object> body, Result<Trip> result) {
        Call<Trip> call = id == null
                ? api.createTrip(session.authorization(), body)
                : api.updateTrip(session.authorization(), id, body);
        single(call, invalidating(result), "Không thể lưu chuyến đi");
    }

    public void setTripPublished(long id, boolean publish, Result<Trip> result) {
        single(publish
                ? api.publishTrip(session.authorization(), id)
                : api.unpublishTrip(session.authorization(), id),
                invalidating(result), publish ? "Không thể đăng chuyến đi" : "Không thể gỡ chuyến đi");
    }

    public void myTrips(Result<List<Trip>> result) {
        cachedList(key("myTrips"), api.myTrips(session.authorization()), result);
    }

    public void favoriteTrips(Result<List<Trip>> result) {
        cachedList(key("favoriteTrips"), api.favoriteTrips(session.authorization()), result);
    }

    public void favoriteTrip(long id, boolean alreadyFavorite, Result<Message> result) {
        String auth = session.authorization();
        message(alreadyFavorite ? api.removeFavoriteTrip(auth, id) : api.addFavoriteTrip(auth, id), invalidating(result));
    }

    public void favoriteTrip(long id, Result<Message> result) {
        favoriteTrip(id, false, result);
    }

    public void tripReviews(long tripId, Result<List<TripReview>> result) {
        cachedList(key("tripReviews", tripId), api.tripReviews(session.authorization(), tripId), result);
    }

    public void createTripReview(long tripId, int rating, String comment, Result<TripReview> result) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("trip_id", tripId);
        body.put("rating", rating);
        body.put("comment", comment);
        single(api.createTripReview(session.authorization(), body), invalidating(result), "Không thể gửi đánh giá");
    }

    // ---------------------------------------------------- challenges/rewards

    public void challenges(Result<List<Challenge>> result) {
        cachedList(key("challenges"), api.challenges(), result);
    }

    public void myChallenges(Result<List<Challenge>> result) {
        cachedList(key("myChallenges"), api.myChallenges(session.authorization()), result);
    }

    public void challenge(long id, Result<Challenge> result) {
        cachedSingle(key("challengeProgress", id), api.challengeProgress(session.authorization(), id), result, "Không tải được tiến độ thử thách");
    }

    public void challengeInfo(long id, Result<Challenge> result) {
        cachedSingle(key("challengeInfo", id), api.challenge(id), result, "Không thể tải thử thách này");
    }

    public void joinChallenge(long id, Result<Message> result) {
        message(api.joinChallenge(session.authorization(), id), invalidating(result));
    }

    public void completeChallenge(long id, Result<Message> result) {
        message(api.completeChallenge(session.authorization(), id), invalidating(result));
    }

    public void rewards(Result<RewardCatalog> result) {
        cachedSingle(key("rewards"), api.rewardCatalog(session.authorization()), result, "Không thể tải phần thưởng");
    }

    public void reward(long id, Result<Reward> result) {
        cachedSingle(key("reward", id), api.reward(id), result, "Không thể tải phần thưởng");
    }

    public void redeem(long rewardId, Result<Message> result) {
        message(api.redeemReward(session.authorization(), rewardId), invalidating(result));
    }

    // --------------------------------------------------------------- helpers

    private static void put(Map<String, String> params, String key, String value) {
        if (value != null && !value.isBlank()) {
            params.put(key, value);
        }
    }

    private String key(String resource, Object... parts) {
        StringBuilder value = new StringBuilder("user:")
                .append(session.userId()).append('|').append(resource);
        for (Object part : parts) value.append('|').append(String.valueOf(part));
        return value.toString();
    }

    @SuppressWarnings("unchecked")
    private <T> boolean deliverCached(String cacheKey, Result<T> result) {
        CacheEntry cached = memoryCache.get(cacheKey);
        if (cached == null) return false;
        if (SystemClock.elapsedRealtime() - cached.storedAt > CACHE_TTL_MS) {
            memoryCache.remove(cacheKey, cached);
            return false;
        }
        result.onResult((T) cached.value, null, false);
        return true;
    }

    private void clearCache() {
        memoryCache.clear();
    }

    private void invalidateMatching(String... resources) {
        memoryCache.keySet().removeIf(cacheKey -> {
            for (String resource : resources) {
                if (cacheKey.contains("|" + resource)) return true;
            }
            return false;
        });
    }

    private <T> Result<T> invalidating(Result<T> result) {
        return (data, error, sample) -> {
            if (error == null) clearCache();
            result.onResult(data, error, sample);
        };
    }

    private <T> void cachedList(String cacheKey, Call<List<T>> call, Result<List<T>> result) {
        if (deliverCached(cacheKey, result)) return;
        enqueueCachedList(cacheKey, call, result, 0);
    }

    private <T> void enqueueCachedList(String cacheKey, Call<List<T>> call,
                                       Result<List<T>> result, int attempt) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<T>> call, @NonNull Response<List<T>> response) {
                List<T> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(new ArrayList<>(), errorOf(response, "Không thể tải dữ liệu"), false);
                } else {
                    List<T> snapshot = new ArrayList<>(body);
                    memoryCache.put(cacheKey, new CacheEntry(snapshot));
                    result.onResult(snapshot, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<T>> call, @NonNull Throwable throwable) {
                if (attempt < NETWORK_RETRY_DELAYS_MS.length) {
                    MAIN_HANDLER.postDelayed(
                            () -> enqueueCachedList(cacheKey, call.clone(), result, attempt + 1),
                            NETWORK_RETRY_DELAYS_MS[attempt]);
                    return;
                }
                result.onResult(new ArrayList<>(), OFFLINE + ": " + throwable.getMessage(), false);
            }
        });
    }

    private <T> void cachedSingle(String cacheKey, Call<T> call, Result<T> result, String failureMessage) {
        if (deliverCached(cacheKey, result)) return;
        enqueueCachedSingle(cacheKey, call, result, failureMessage, 0);
    }

    private <T> void enqueueCachedSingle(String cacheKey, Call<T> call, Result<T> result,
                                         String failureMessage, int attempt) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<T> call, @NonNull Response<T> response) {
                T body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(null, errorOf(response, failureMessage), false);
                } else {
                    memoryCache.put(cacheKey, new CacheEntry(body));
                    result.onResult(body, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<T> call, @NonNull Throwable throwable) {
                if (attempt < NETWORK_RETRY_DELAYS_MS.length) {
                    MAIN_HANDLER.postDelayed(
                            () -> enqueueCachedSingle(cacheKey, call.clone(), result, failureMessage, attempt + 1),
                            NETWORK_RETRY_DELAYS_MS[attempt]);
                    return;
                }
                result.onResult(null, failureMessage + " (máy chủ không phản hồi)", false);
            }
        });
    }

    /** List requests return only server data and never hand a null list to callers. */
    private <T> void list(Call<List<T>> call, Result<List<T>> result) {
        call.enqueue(new Callback<>() {
            @Override
            public void onResponse(@NonNull Call<List<T>> call, @NonNull Response<List<T>> response) {
                List<T> body = response.body();
                if (!response.isSuccessful() || body == null) {
                    result.onResult(new ArrayList<>(), errorOf(response, "Không thể tải dữ liệu"), false);
                } else {
                    result.onResult(body, null, false);
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<T>> call, @NonNull Throwable throwable) {
                result.onResult(new ArrayList<>(), OFFLINE + ": " + throwable.getMessage(), false);
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
