package com.espressif.cloudapi;

import com.google.gson.JsonObject;

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

public interface ApiGptModel {
    @Multipart
    @POST
    Call<ResponseBody> speechToText(@Url String url, @PartMap Map<String, RequestBody> maps);
//    Call<ResponseBody> speechToText(@Url String url, @HeaderMap Map<String,String> headerParams, @Part() List<MultipartBody.Part> parts);
//    Call<ResponseBody> speechToText(@Url String url, @HeaderMap Map<String,String> headerParams, @Body() List<RequestBody> bodys);

    /**
     * 语音转文字
     *
     * @param file           语音文件
     * @param requestBodyMap 参数
     * @return 文本
     */
    @Multipart
    @POST("v1/audio/transcriptions")
    Call<ResponseBody> speechToTextTranscriptions(@Part MultipartBody.Part file, @PartMap() Map<String, RequestBody> requestBodyMap);
}
