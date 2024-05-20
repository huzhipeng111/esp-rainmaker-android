package com.espressif.cloudapi;

import com.google.gson.JsonObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.POST;
import retrofit2.http.Url;

public interface ApiLargeModel {
    @POST
    Call<ResponseBody> requestBue(@Url String url, @Body JsonObject body);
}
