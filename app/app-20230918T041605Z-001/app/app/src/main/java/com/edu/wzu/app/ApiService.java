package com.edu.wzu.app;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.Query;

public interface ApiService {
    @Multipart
    @POST("/uploads")
    Call<UploadResponse> uploadVideo(
            @Part MultipartBody.Part file
    );
    @Multipart
    @POST("/realtimeuploads")
    Call<ServerResponse> realtimeVideo(
            @Part MultipartBody.Part file
    );
    @GET("/prediction-result")
    Call<PredictionResponse> getPredictionResult(@Query("videoId") String videoId);


    @POST("/recommend")
    Call<RecommendResponse> getVideoRecommendation(@retrofit2.http.Body RecommendRequest body);


}