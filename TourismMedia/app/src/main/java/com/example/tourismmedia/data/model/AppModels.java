package com.example.tourismmedia.data.model;

import com.google.gson.annotations.SerializedName;

import java.util.ArrayList;
import java.util.List;

/** Data transfer objects shared by every feature. Field names mirror the Express API payloads. */
public final class AppModels {

    private AppModels() {
    }

    public static class Location {
        public long id;
        public String name;
        public String category;
        public String type;
        public String city;
        public String address;
        public String description;
        public double price;
        public double rating;
        public Double latitude;
        public Double longitude;

        @SerializedName("review_count")
        public int reviewCount;

        @SerializedName("image_url")
        public String imageUrl;

        @SerializedName("opening_hours")
        public String openingHours;

        @SerializedName("closing_hours")
        public String closingHours;

        @SerializedName("key_highlights")
        public String highlights;

        @SerializedName("is_favorite")
        public int favorite;

        @SerializedName("checked_in_at")
        public String checkedInAt;

        @SerializedName("distance_km")
        public Double distanceKm;

        public List<LocationImage> images;

        public boolean isFavorite() {
            return favorite == 1;
        }

        public List<LocationImage> gallery() {
            return images == null ? new ArrayList<>() : images;
        }
    }

    public static class LocationImage {
        public long id;
        public String url;
        public String caption;

        @SerializedName("sort_order")
        public int sortOrder;
    }

    public static class Review {
        public long id;
        public int rating;
        public String comment;
        public String username;

        @SerializedName("user_id")
        public long userId;

        @SerializedName("created_at")
        public String createdAt;
    }

    public static class Trip {
        public long id;
        public String title;
        public String description;
        public double rating;

        @SerializedName("review_count")
        public int reviewCount;

        @SerializedName("estimate_price")
        public long estimatedPrice;

        @SerializedName("total_time")
        public int totalTime;

        @SerializedName("url_image")
        public String imageUrl;

        @SerializedName("key_highlight")
        public String highlight;

        @SerializedName("is_favorite")
        public int favorite;
    }

    /** `GET /api/trips` answers with a paged envelope instead of a bare array. */
    public static class TripPage {
        public List<Trip> data;
        public int count;

        @SerializedName("nextOffset")
        public Integer nextOffset;
    }

    public static class Challenge {
        public long id;
        public String name;
        public String description;
        public int progress;
        public int target;

        @SerializedName("reward_point")
        public int rewardPoint;

        @SerializedName("required_checkins")
        public int requiredCheckins;

        @SerializedName("challenge_type")
        public String challengeType;

        @SerializedName("start_date")
        public String startDate;

        @SerializedName("end_date")
        public String endDate;
    }

    public static class Reward {
        public long id;
        public String name;
        public String description;
        public int cost;
        public int percent;
    }

    public static class RewardCatalog {
        public int points;
        public List<Reward> rewards;
    }

    public static class Voucher {
        public String name;
        public String description;
        public int percent;
        public String code;
        public String status;

        @SerializedName("voucher_id")
        public long voucherId;

        @SerializedName("reward_id")
        public long rewardId;
    }

    public static class Profile {
        public long id;
        public String username;
        public String email;
        public String role;
        public String avatar;

        @SerializedName("total_point")
        public int points;
    }

    public static class ProfileUpdate {
        public String message;
        public Profile user;
    }

    public static class AuthResponse {
        public String token;

        @SerializedName("refreshToken")
        public String refreshToken;

        @SerializedName("userId")
        public long userId;

        public String username;
        public String role;
        public int points;
    }

    public static class Message {
        public String message;
        public String error;
    }
}
