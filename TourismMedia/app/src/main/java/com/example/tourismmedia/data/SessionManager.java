package com.example.tourismmedia.data;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.tourismmedia.data.model.AppModels.AuthResponse;

/** Persists the signed-in user across launches. */
public class SessionManager {

    private static final String FILE = "tourism_session";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REFRESH = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USERNAME = "username";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences preferences;

    public SessionManager(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public void save(AuthResponse response, String email) {
        preferences.edit()
                .putString(KEY_TOKEN, response.token)
                .putString(KEY_REFRESH, response.refreshToken)
                .putLong(KEY_USER_ID, response.userId)
                .putString(KEY_USERNAME, response.username)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    /** Compatibility helper for flows where the email is not returned by the API. */
    public void save(AuthResponse response) {
        save(response, email());
    }

    public void clear() {
        preferences.edit().clear().apply();
    }

    public synchronized void updateTokens(String accessToken, String refreshToken) {
        // Synchronous persistence is intentional because concurrent requests may use the rotated token immediately.
        //noinspection ApplySharedPref
        preferences.edit()
                .putString(KEY_TOKEN, accessToken)
                .putString(KEY_REFRESH, refreshToken)
                .commit();
    }

    public boolean isLoggedIn() {
        return token() != null;
    }

    public String token() {
        String value = preferences.getString(KEY_TOKEN, null);
        return value == null || value.isBlank() ? null : value;
    }

    /** Ready-to-send header value, or null when nobody is signed in. */
    public String authorization() {
        String value = token();
        return value == null ? null : "Bearer " + value;
    }

    public String refreshToken() {
        String value = preferences.getString(KEY_REFRESH, null);
        return value == null || value.isBlank() ? null : value;
    }

    public long userId() {
        return preferences.getLong(KEY_USER_ID, 0L);
    }

    public String username() {
        return preferences.getString(KEY_USERNAME, "Traveler");
    }

    public String email() {
        return preferences.getString(KEY_EMAIL, null);
    }
}
