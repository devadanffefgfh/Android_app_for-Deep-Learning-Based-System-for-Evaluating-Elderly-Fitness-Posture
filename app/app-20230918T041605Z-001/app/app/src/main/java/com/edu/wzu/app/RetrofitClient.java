package com.edu.wzu.app;

import android.util.Log;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    private static Retrofit retrofit = null;

    public static Retrofit getClient() {
        if (retrofit == null) {
            try {
                // 配置 OkHttpClient 并设置超时时间
                OkHttpClient okHttpClient = new OkHttpClient.Builder()
                        .connectTimeout(120, TimeUnit.SECONDS) // 连接超时 30 秒
                        .readTimeout(120, TimeUnit.SECONDS)    // 读取超时 30 秒
                        .writeTimeout(120, TimeUnit.SECONDS)   // 写入超时 30 秒
                        .build();

                // 初始化 Retrofit 实例
                retrofit = new Retrofit.Builder()
                        .baseUrl("http://0.0.0.0:5000") // 替换为你的 API 基础 URL
                        .addConverterFactory(GsonConverterFactory.create())
                        .client(okHttpClient) // 设置自定义的 OkHttpClient
                        .build();
                ApiService apiService = retrofit.create(ApiService.class);
            } catch (Exception e) {
                Log.e("RetrofitClient", "Retrofit 初始化失败: " + e.getMessage());
            }
        }
        return retrofit;
    }
}

