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
import retrofit2.http.Header;
import retrofit2.http.HeaderMap;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;
import retrofit2.http.PartMap;
import retrofit2.http.Url;

public interface ApiLargeModel {
    //header deviceId：  lightDemo  productId：rainmaker_app
    @Multipart
    @POST("json_call/33")
    Call<ResponseBody> requestRgb(@Part MultipartBody.Part file, @Part MultipartBody.Part format, @Header("deviceId") String deviceId, @Header("productId") String productId);

    @POST("api/chatgpt/qmbox/chat/34")
    Call<ResponseBody> requestHue(@Body JsonObject body);

    /**
     * 获取循环灯带
     * @return
     */
    @Multipart
    @POST("json_call/37")
    Call<ResponseBody> requestCycleHue(@Part MultipartBody.Part file, @Part MultipartBody.Part format, @Header("deviceId") String deviceId, @Header("productId") String productId);
}
