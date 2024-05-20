package com.espressif.cloudapi;

import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import com.espressif.AppConstants;
import com.espressif.rainmaker.BuildConfig;
import com.espressif.ui.model.LargeModelHue;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

public class LargeModelClient {
    public static final String TAG = LargeModelClient.class.getName();

    public static final String BASE_PATH = "";
    private static final int DEFAULT_TIMEOUT = 30;
    public static String BASE_URL = BuildConfig.LARGE_MODEL_URL;
    private Retrofit retrofit;
    private ApiLargeModel largeModelApi;

    //构造方法私有
    private LargeModelClient() {
        //手动创建一个OkHttpClient并设置超时时间
        OkHttpClient.Builder httpClientBuilder = new OkHttpClient.Builder();
        httpClientBuilder.connectTimeout(DEFAULT_TIMEOUT, TimeUnit.SECONDS);

        HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

        httpClientBuilder.addInterceptor(loggingInterceptor);


        try {
            retrofit = new Retrofit.Builder()
                    .client(httpClientBuilder.build())
                    .addConverterFactory(GsonConverterFactory.create())
                    .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                    .baseUrl(getBaseUrl())
                    .build();

            largeModelApi = retrofit.create(ApiLargeModel.class);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    //获取单例
    public static LargeModelClient getInstance() {
        return SingletonHolder.INSTANCE;
    }

    public static String getBaseUrl() {
        return BASE_URL;
    }


    /**
     * 获取统一升级信息
     */
    public void requestBue(String prompt, final ApiResponseListener listener) {
        Log.d(TAG, "requestBue...");
        String bueUrl = BASE_URL;
        Log.d(TAG, "requestBue URL : " + bueUrl);

        JsonObject body = new JsonObject();
        body.addProperty(AppConstants.KEY_PROMPT, prompt);

        largeModelApi.requestBue(bueUrl, body).enqueue(new Callback<ResponseBody>() {

            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {

                Log.d(TAG, "Login, Response code  : " + response.code());

                try {
                    if (response.isSuccessful()) {
                        String jsonResponse = response.body().string();
                        Log.d(TAG, " -- Auth Success : response : " + jsonResponse);
                        JSONObject jsonObject = new JSONObject(jsonResponse);
                        String content = jsonObject.getJSONObject("data").getString("content");
                        LargeModelHue hue = new Gson().fromJson(content, LargeModelHue.class);
                        Color color = Color.valueOf(hue.getR(), hue.getG(), hue.getB());
                        Bundle bundle = new Bundle();
                        bundle.putInt("rgb", color.toArgb());
                        listener.onSuccess(bundle);
                    } else {
                        String jsonErrResponse = response.errorBody().string();
                        processError(jsonErrResponse, listener, "Failed to login");
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    listener.onResponseFailure(new RuntimeException("Failed to login"));
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                t.printStackTrace();
                listener.onNetworkFailure(new RuntimeException("Failed to login"));
            }
        });
    }

    //在访问HttpMethods时创建单例
    private static class SingletonHolder {
        private static final LargeModelClient INSTANCE = new LargeModelClient();
    }

    private void processError(String jsonErrResponse, ApiResponseListener listener, String errMsg) {

        Log.e(TAG, "Error Response : " + jsonErrResponse);
        try {
            if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {

                JSONObject jsonObject = new JSONObject(jsonErrResponse);
                String err = jsonObject.optString(AppConstants.KEY_DESCRIPTION);

                if (!TextUtils.isEmpty(err)) {
                    listener.onResponseFailure(new CloudException(err));
                } else {
                    listener.onResponseFailure(new RuntimeException(errMsg));
                }
            } else {
                listener.onResponseFailure(new RuntimeException(errMsg));
            }
        } catch (JSONException e) {
            e.printStackTrace();
            listener.onResponseFailure(new RuntimeException(errMsg));
        }
    }


}


