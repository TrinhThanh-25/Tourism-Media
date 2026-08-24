package com.example.tourismmedia.data.api;

import com.example.tourismmedia.data.model.AppModels.AuthResponse;
import com.example.tourismmedia.data.model.AppModels.Challenge;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.LocationImage;
import com.example.tourismmedia.data.model.AppModels.Message;
import com.example.tourismmedia.data.model.AppModels.Profile;
import com.example.tourismmedia.data.model.AppModels.ProfileUpdate;
import com.example.tourismmedia.data.model.AppModels.PointTransaction;
import com.example.tourismmedia.data.model.AppModels.PointsBalance;
import com.example.tourismmedia.data.model.AppModels.Review;
import com.example.tourismmedia.data.model.AppModels.Reward;
import com.example.tourismmedia.data.model.AppModels.RewardCatalog;
import com.example.tourismmedia.data.model.AppModels.Trip;
import com.example.tourismmedia.data.model.AppModels.TripPage;
import com.example.tourismmedia.data.model.AppModels.TripReview;
import com.example.tourismmedia.data.model.AppModels.Voucher;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.PATCH;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;
import retrofit2.http.QueryMap;

/**
 * Travel-App-Server endpoints. The access token travels as an explicit
 * {@code Authorization} header; Retrofit drops the header when the value is null,
 * which is exactly what the optionalJWT routes expect for anonymous callers.
 */
public interface ApiService {

    // ---------------------------------------------------------------- auth
    @POST("auth/register")
    Call<AuthResponse> register(@Body Map<String, Object> body);

    @POST("auth/login")
    Call<AuthResponse> login(@Body Map<String, Object> body);

    @POST("auth/logout")
    Call<Message> logout(@Body Map<String, Object> body);

    @POST("auth/refresh")
    Call<AuthResponse> refresh(@Body Map<String, Object> body);

    @POST("auth/forgot-password")
    Call<Message> forgotPassword(@Body Map<String, Object> body);

    // ----------------------------------------------------------- locations
    @GET("api/locations")
    Call<List<Location>> locations(@Header("Authorization") String auth,
                                   @QueryMap Map<String, String> query);

    @GET("api/locations/{id}")
    Call<Location> location(@Header("Authorization") String auth, @Path("id") long id);

    @GET("api/locations/nearby")
    Call<List<Location>> nearbyLocations(@Header("Authorization") String auth,
                                         @Query("lat") double lat,
                                         @Query("lon") double lon,
                                         @Query("radius") double radius,
                                         @Query("limit") int limit);

    @GET("api/locations/{id}/images")
    Call<List<LocationImage>> locationImages(@Path("id") long id);

    @GET("api/locations/me/favorites")
    Call<List<Location>> favoriteLocations(@Header("Authorization") String auth);

    @POST("api/locations/{id}/favorite")
    Call<Message> addFavoriteLocation(@Header("Authorization") String auth, @Path("id") long id);

    @DELETE("api/locations/{id}/favorite")
    Call<Message> removeFavoriteLocation(@Header("Authorization") String auth, @Path("id") long id);

    // ------------------------------------------------------ location reviews
    @GET("api/reviews/location/{id}")
    Call<List<Review>> locationReviews(@Path("id") long locationId);

    @POST("api/reviews")
    Call<Review> createLocationReview(@Header("Authorization") String auth,
                                      @Body Map<String, Object> body);

    @PUT("api/reviews/{id}")
    Call<Review> updateLocationReview(@Header("Authorization") String auth,
                                      @Path("id") long reviewId,
                                      @Body Map<String, Object> body);

    @DELETE("api/reviews/{id}")
    Call<Message> deleteLocationReview(@Header("Authorization") String auth, @Path("id") long reviewId);

    // -------------------------------------------------------------- account
    @GET("api/me")
    Call<Profile> profile(@Header("Authorization") String auth);

    @PATCH("api/me")
    Call<ProfileUpdate> updateProfile(@Header("Authorization") String auth,
                                      @Body Map<String, Object> body);

    @POST("api/me/password")
    Call<Message> updatePassword(@Header("Authorization") String auth,
                                 @Body Map<String, Object> body);

    @GET("api/me/locations")
    Call<List<Location>> checkIns(@Header("Authorization") String auth);

    @POST("api/me/locations")
    Call<Message> checkIn(@Header("Authorization") String auth, @Body Map<String, Object> body);

    @GET("api/me/rewards")
    Call<RewardCatalog> rewardCatalog(@Header("Authorization") String auth);

    @POST("api/me/rewards/{id}/redeem")
    Call<Message> redeemReward(@Header("Authorization") String auth, @Path("id") long rewardId);

    @GET("api/me/vouchers")
    Call<List<Voucher>> vouchers(@Header("Authorization") String auth);

    @POST("api/me/vouchers/{id}/use")
    Call<Message> useVoucher(@Header("Authorization") String auth, @Path("id") long voucherId);

    @GET("api/me/points")
    Call<PointsBalance> points(@Header("Authorization") String auth);

    @GET("api/me/point-transactions")
    Call<List<PointTransaction>> pointTransactions(@Header("Authorization") String auth);

    // ---------------------------------------------------------------- trips
    @GET("api/trips")
    Call<TripPage> trips(@Header("Authorization") String auth, @QueryMap Map<String, String> query);

    @GET("api/trips/{id}")
    Call<Trip> trip(@Header("Authorization") String auth, @Path("id") long id);

    @POST("api/trips")
    Call<Trip> createTrip(@Header("Authorization") String auth, @Body Map<String, Object> body);

    @PUT("api/trips/{id}")
    Call<Trip> updateTrip(@Header("Authorization") String auth, @Path("id") long id,
                          @Body Map<String, Object> body);

    @GET("api/trips/me")
    Call<List<Trip>> myTrips(@Header("Authorization") String auth);

    @GET("api/trips/me/favorites")
    Call<List<Trip>> favoriteTrips(@Header("Authorization") String auth);

    @POST("api/trips/{id}/favorite")
    Call<Message> addFavoriteTrip(@Header("Authorization") String auth, @Path("id") long id);

    @DELETE("api/trips/{id}/favorite")
    Call<Message> removeFavoriteTrip(@Header("Authorization") String auth, @Path("id") long id);

    @POST("api/trips/{id}/publish")
    Call<Trip> publishTrip(@Header("Authorization") String auth, @Path("id") long id);

    @POST("api/trips/{id}/unpublish")
    Call<Trip> unpublishTrip(@Header("Authorization") String auth, @Path("id") long id);

    // ----------------------------------------------------- challenges/rewards
    @GET("api/challenges")
    Call<List<Challenge>> challenges();

    @GET("api/challenges/{id}")
    Call<Challenge> challenge(@Path("id") long id);

    @POST("api/challenges/{id}/join")
    Call<Message> joinChallenge(@Header("Authorization") String auth, @Path("id") long id);

    @GET("api/challenges/{id}/progress")
    Call<Challenge> challengeProgress(@Header("Authorization") String auth, @Path("id") long id);

    @GET("api/me/challenges")
    Call<List<Challenge>> myChallenges(@Header("Authorization") String auth);

    @POST("api/challenges/{id}/complete")
    Call<Message> completeChallenge(@Header("Authorization") String auth, @Path("id") long id);

    @GET("api/rewards/{id}")
    Call<Reward> reward(@Path("id") long id);

    // ---------------------------------------------------------- trip reviews
    @GET("api/trip-reviews/trip/{id}")
    Call<List<TripReview>> tripReviews(@Header("Authorization") String auth, @Path("id") long tripId);

    @POST("api/trip-reviews")
    Call<TripReview> createTripReview(@Header("Authorization") String auth,
                                      @Body Map<String, Object> body);
}
