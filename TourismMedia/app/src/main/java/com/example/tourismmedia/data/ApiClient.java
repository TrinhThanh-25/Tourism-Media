package com.example.tourismmedia.data;

import com.example.tourismmedia.data.api.ApiService;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Single Retrofit entry point. {@code 10.0.2.2} is how the Android emulator reaches
 * the host machine; change {@link #BASE_URL} to the LAN address for a physical device.
 */
public final class ApiClient {

    public static final String BASE_URL = "http://10.0.2.2:3000/";

    private static volatile ApiService service;

    private ApiClient() {
    }

    public static ApiService service() {
        if (service == null) {
            synchronized (ApiClient.class) {
                if (service == null) {
                    service = new Retrofit.Builder()
                            .baseUrl(BASE_URL)
                            .addConverterFactory(GsonConverterFactory.create())
                            .build()
                            .create(ApiService.class);
                }
            }
        }
        return service;
    }
}
