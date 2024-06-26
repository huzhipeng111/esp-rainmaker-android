package com.espressif.cloudapi;

import com.google.gson.JsonObject;

import java.io.File;
import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.HeaderMap;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Url;

public interface ApiLargeModel {
    @POST("api/chatgpt/qmbox/chat/33")
    Call<ResponseBody> requestRgb(@Url String url, @Body JsonObject body);

    @POST("api/chatgpt/qmbox/chat/34")
    Call<ResponseBody> requestHue(@Body JsonObject body);

    @POST("api/chatgpt/qmbox/chat/37")
    Call<ResponseBody> requestCycleHue(@Body JsonObject body);
}
