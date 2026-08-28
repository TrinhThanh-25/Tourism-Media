package com.example.tourismmedia.data;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import com.example.tourismmedia.auth.AuthActivity;
import com.example.tourismmedia.BuildConfig;
import com.example.tourismmedia.data.api.ApiService;
import com.example.tourismmedia.data.model.AppModels.AuthResponse;
import com.google.gson.Gson;

import java.io.IOException;
import java.util.Collections;

import okhttp3.Authenticator;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.Route;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/** Retrofit client with one-time refresh-token rotation for authenticated calls. */
public final class ApiClient {
    public static final String BASE_URL = BuildConfig.API_BASE_URL;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final Gson GSON = new Gson();
    private static final Object REFRESH_LOCK = new Object();
    private static volatile ApiService service;

    private ApiClient() { }

    public static ApiService service(Context context, SessionManager session) {
        if (service == null) {
            synchronized (ApiClient.class) {
                if (service == null) {
                    Context application = context.getApplicationContext();
                    OkHttpClient client = new OkHttpClient.Builder()
                            .authenticator(new TokenAuthenticator(application, session))
                            .build();
                    service = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .client(client)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService.class);
                }
            }
        }
        return service;
    }

    private static final class TokenAuthenticator implements Authenticator {
        private final Context context;
        private final SessionManager session;
        private final OkHttpClient refreshClient = new OkHttpClient();

        private TokenAuthenticator(Context context, SessionManager session) {
            this.context = context;
            this.session = session;
        }

        @Override public Request authenticate(Route route, Response response) {
            String failedAuthorization = response.request().header("Authorization");
            if (failedAuthorization == null || responseCount(response) > 1) return null;
            synchronized (REFRESH_LOCK) {
                String currentAuthorization = session.authorization();
                if (currentAuthorization != null && !currentAuthorization.equals(failedAuthorization)) {
                    return response.request().newBuilder().header("Authorization", currentAuthorization).build();
                }
                String refreshToken = session.refreshToken();
                if (refreshToken == null) {
                    expireSession();
                    return null;
                }
                try {
                    String payload = GSON.toJson(Collections.singletonMap("refreshToken", refreshToken));
                    Request refreshRequest = new Request.Builder()
                            .url(BASE_URL + "auth/refresh")
                            .post(RequestBody.create(payload, JSON))
                            .build();
                    try (Response refreshResponse = refreshClient.newCall(refreshRequest).execute()) {
                        if (!refreshResponse.isSuccessful() || refreshResponse.body() == null) {
                            expireSession();
                            return null;
                        }
                        AuthResponse tokens = GSON.fromJson(refreshResponse.body().string(), AuthResponse.class);
                        if (tokens == null || tokens.token == null || tokens.refreshToken == null) {
                            expireSession();
                            return null;
                        }
                        session.updateTokens(tokens.token, tokens.refreshToken);
                        return response.request().newBuilder()
                                .header("Authorization", "Bearer " + tokens.token)
                                .build();
                    }
                } catch (IOException | RuntimeException ignored) {
                    expireSession();
                    return null;
                }
            }
        }

        private void expireSession() {
            session.clear();
            new Handler(Looper.getMainLooper()).post(() -> {
                Intent intent = new Intent(context, AuthActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                context.startActivity(intent);
            });
        }

        private int responseCount(Response response) {
            int count = 1;
            while ((response = response.priorResponse()) != null) count++;
            return count;
        }
    }
}
