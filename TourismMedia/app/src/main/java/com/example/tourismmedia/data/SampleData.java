package com.example.tourismmedia.data;

import com.example.tourismmedia.data.model.AppModels.Challenge;
import com.example.tourismmedia.data.model.AppModels.Location;
import com.example.tourismmedia.data.model.AppModels.LocationImage;
import com.example.tourismmedia.data.model.AppModels.Review;
import com.example.tourismmedia.data.model.AppModels.Reward;
import com.example.tourismmedia.data.model.AppModels.Trip;

import java.util.ArrayList;
import java.util.List;

/**
 * Offline fallback so the screens stay demonstrable when Travel-App-Server is not running.
 * Callbacks flag this data with {@code sample = true} so the UI can say so.
 */
public final class SampleData {

    private SampleData() {
    }

    public static List<Location> locations() {
        List<Location> data = new ArrayList<>();
        data.add(location(1, "Independence Palace", "attraction", "history", "Ho Chi Minh City",
                "135 Nam Ky Khoi Nghia, District 1", 65000, 4.7, 1280,
                "https://media-cdn-v2.laodong.vn/Storage/NewsPortal/2023/3/16/1158477/IMG_8725-2.jpg",
                "A landmark of modern Vietnamese history, open to visitors every day."));
        data.add(location(2, "Nguyen Hue Walking Street", "entertainment", "street", "Ho Chi Minh City",
                "Nguyen Hue, District 1", 0, 4.5, 940,
                "https://cdn.xanhsm.com/2025/01/8ab32a72-ban-cung-quan-1-5.jpg",
                "A lively pedestrian boulevard with street music, cafes and weekend events."));
        data.add(location(3, "Ben Thanh Market", "shopping", "market", "Ho Chi Minh City",
                "Le Loi, District 1", 0, 4.2, 2100,
                "https://homesaigon.info/f/634546cf1fbb14c2a8abc986dba3da6e-DIM2_30102022_113_cb8306.jpg",
                "The city's iconic market for local specialities, souvenirs and street food."));
        data.add(location(4, "Binh Quoi Tourist Village", "attraction", "nature", "Ho Chi Minh City",
                "1147 Binh Quoi, Binh Thanh District", 250000, 4.4, 610,
                "https://www.homepaylater.vn/static/88357cc22dcb6460df003e4ff1dec446/9d72c/1_khu_du_lich_binh_quoi_2_mang_den_khong_gian_thu_gian_hoa_minh_vao_thien_nhien_299b8d9efd.jpg",
                "A riverside garden retreat, good for a picnic or the weekend buffet."));
        return data;
    }

    public static Location location(long id) {
        for (Location item : locations()) {
            if (item.id == id) {
                return item;
            }
        }
        return locations().get(0);
    }

    public static List<LocationImage> images(long locationId) {
        List<LocationImage> data = new ArrayList<>();
        int order = 0;
        for (Location item : locations()) {
            LocationImage image = new LocationImage();
            image.id = ++order;
            image.url = item.imageUrl;
            image.caption = item.name;
            image.sortOrder = order;
            data.add(image);
        }
        return data;
    }

    public static List<Review> reviews() {
        List<Review> data = new ArrayList<>();
        data.add(review(1, "an_nguyen", 5, "Beautiful grounds and a very helpful guide. Well worth the trip."));
        data.add(review(2, "minh_tran", 4, "Busy at weekends but still worth it, go early in the morning."));
        data.add(review(3, "hoang_le", 4, "Fair ticket price, though the car park is a bit of a walk."));
        return data;
    }

    public static List<Trip> trips() {
        List<Trip> data = new ArrayList<>();
        data.add(trip(1, "A weekend in Saigon", "Landmarks, food and the walking street after dark.", 4.6, 1200000, 960));
        data.add(trip(2, "A green day at Binh Quoi", "Riverside picnic and a garden buffet.", 4.3, 450000, 480));
        return data;
    }

    public static List<Challenge> challenges() {
        List<Challenge> data = new ArrayList<>();
        data.add(challenge(1, "District 1 explorer", "Check in at 3 places in District 1.", 150, 1, 3));
        data.add(challenge(2, "Storyteller", "Write 5 reviews for places you have visited.", 200, 2, 5));
        return data;
    }

    public static List<Reward> rewards() {
        List<Reward> data = new ArrayList<>();
        data.add(reward(1, "10% off admission", "Valid on entry tickets at partner landmarks.", 300, 10));
        data.add(reward(2, "20% off the garden buffet", "Redeemable at Binh Quoi Tourist Village.", 800, 20));
        return data;
    }

    private static Location location(long id, String name, String category, String type, String city,
                                     String address, double price, double rating, int reviewCount,
                                     String imageUrl, String description) {
        Location item = new Location();
        item.id = id;
        item.name = name;
        item.category = category;
        item.type = type;
        item.city = city;
        item.address = address;
        item.price = price;
        item.rating = rating;
        item.reviewCount = reviewCount;
        item.imageUrl = imageUrl;
        item.description = description;
        item.openingHours = "08:00";
        item.closingHours = "22:00";
        item.highlights = "Offline sample data";
        return item;
    }

    private static Review review(long id, String username, int rating, String comment) {
        Review item = new Review();
        item.id = id;
        item.username = username;
        item.rating = rating;
        item.comment = comment;
        item.createdAt = "2026-08-27T10:30:00.000Z";
        return item;
    }

    private static Trip trip(long id, String title, String description, double rating,
                             long estimatedPrice, int totalTime) {
        Trip item = new Trip();
        item.id = id;
        item.title = title;
        item.description = description;
        item.rating = rating;
        item.estimatedPrice = estimatedPrice;
        item.totalTime = totalTime;
        item.imageUrl = locations().get((int) (id % 4)).imageUrl;
        return item;
    }

    private static Challenge challenge(long id, String name, String description, int rewardPoint,
                                       int progress, int target) {
        Challenge item = new Challenge();
        item.id = id;
        item.name = name;
        item.description = description;
        item.rewardPoint = rewardPoint;
        item.progress = progress;
        item.target = target;
        item.requiredCheckins = target;
        item.challengeType = "checkin";
        return item;
    }

    private static Reward reward(long id, String name, String description, int cost, int percent) {
        Reward item = new Reward();
        item.id = id;
        item.name = name;
        item.description = description;
        item.cost = cost;
        item.percent = percent;
        return item;
    }
}
