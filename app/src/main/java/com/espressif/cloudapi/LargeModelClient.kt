package com.espressif.cloudapi

import android.graphics.Color
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import com.espressif.AppConstants
import com.espressif.rainmaker.BuildConfig
import com.espressif.ui.model.LargeModelHue
import com.espressif.ui.model.LargeModelRgb
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONException
import org.json.JSONObject
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit


class LargeModelClient private constructor() {
    private var retrofit: Retrofit? = null
    private var gptRetrofit: Retrofit? = null
    private var largeModelApi: ApiLargeModel? = null
    private var gptModelApi: ApiGptModel? = null

    //构造方法私有
    init {
        //手动创建一个OkHttpClient并设置超时时间
        val httpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        httpClientBuilder.connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)

        val loggingInterceptor = HttpLoggingInterceptor()
        loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        httpClientBuilder.addInterceptor(loggingInterceptor)
        httpClientBuilder.callTimeout(1, TimeUnit.MINUTES)
        httpClientBuilder.readTimeout(1, TimeUnit.MINUTES)


        try {
            retrofit = Retrofit.Builder()
                .client(httpClientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(baseUrl)
                .build()

            largeModelApi = retrofit!!.create(ApiLargeModel::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        //手动创建一个OkHttpClient并设置超时时间
        val gptHttpClientBuilder: OkHttpClient.Builder = OkHttpClient.Builder()
        gptHttpClientBuilder.connectTimeout(DEFAULT_TIMEOUT.toLong(), TimeUnit.SECONDS)

        val gptLoggingInterceptor = HttpLoggingInterceptor()
        gptLoggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY)

        // 创建拦截器

        // 创建拦截器
        val headerInterceptor = Interceptor { chain ->
            val originalRequest: Request = chain.request()
            val requestWithHeader: Request = originalRequest.newBuilder()
                .header("Authorization", "Bearer ${BuildConfig.AUDIO_PARSE_KEY}")
                .header("Content-Type", "multipart/form-data")
                .build()
            chain.proceed(requestWithHeader)
        }

        gptHttpClientBuilder.addInterceptor(headerInterceptor)
        gptHttpClientBuilder.addInterceptor(gptLoggingInterceptor)
        gptHttpClientBuilder.callTimeout(1, TimeUnit.MINUTES)
        gptHttpClientBuilder.readTimeout(1, TimeUnit.MINUTES)


        try {
            gptRetrofit = Retrofit.Builder()
                .client(gptHttpClientBuilder.build())
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .baseUrl(BuildConfig.AUDIO_PARSE_URL)
                .build()

            gptModelApi = gptRetrofit!!.create(ApiGptModel::class.java)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    /**
     *
     */
    fun requestRgb(file: File, listener: ApiResponseListener) {
        Log.d(TAG, "requestBue...")
        val bueUrl = baseUrl
        Log.d(TAG, "requestBue URL : $bueUrl")

//        val body = JsonObject()
//        body.addProperty(AppConstants.KEY_PROMPT, prompt)

        //文件
        val fileBody: RequestBody = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("file", file.name, fileBody)
        val format = MultipartBody.Part.createFormData("format", "wav")
        // http post  form-data  file=xxx语音文件， format=wav,或者mp3
        //header deviceId：  lightDemo  productId：rainmaker_app

        largeModelApi!!.requestRgb(multipartBody, format, "lightDemo", "rainmaker_app")
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    Log.d(TAG, "Login, Response code  : " + response.code())

                    try {
                        if (response.isSuccessful) {
                            val jsonResponse = response.body()!!.string()
                            Log.d(TAG, " -- Auth Success : response : $jsonResponse")
                            val jsonObject = JSONObject(jsonResponse)
                            val content = jsonObject.getString("data")
                            val hue = Gson().fromJson(content, LargeModelHue::class.java)
                            val bundle = Bundle()
                            if (hue.hue != null) {
                                bundle.putInt("hue", hue.hue)
                            }
                            bundle.putInt("brightness", hue.brightness)
                            listener.onSuccess(bundle)
                        } else {
                            val jsonErrResponse = response.errorBody()!!.string()
                            processError(jsonErrResponse, listener, "Request error")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onResponseFailure(RuntimeException("请明确说清你想要的场景"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                    listener.onNetworkFailure(RuntimeException("Request error"))
                }
            })
    }

    /**
     *
     */
    fun requestHue(prompt: String?, listener: ApiResponseListener) {
        Log.d(TAG, "requestBue...")
        val bueUrl = baseUrl
        Log.d(TAG, "requestBue URL : $bueUrl")

        val body = JsonObject()
        body.addProperty(AppConstants.KEY_PROMPT, prompt)

        largeModelApi!!.requestHue(body).enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d(TAG, "Login, Response code  : " + response.code())

                try {
                    if (response.isSuccessful) {
                        val jsonResponse = response.body()!!.string()
                        Log.d(TAG, " -- Auth Success : response : $jsonResponse")
                        val jsonObject = JSONObject(jsonResponse)
                        val content = jsonObject.getJSONObject("data").getString("content")
                        val hue = Gson().fromJson(content, LargeModelHue::class.java)
                        val bundle = Bundle()
                        if (hue.hue != null) {
                            bundle.putInt("hue", hue.hue)
                        }
                        bundle.putInt("brightness", hue.brightness)
                        listener.onSuccess(bundle)
                    } else {
                        val jsonErrResponse = response.errorBody()!!.string()
                        processError(jsonErrResponse, listener, "Request error")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    listener.onResponseFailure(RuntimeException("请明确说清你想要的场景"))
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
                listener.onNetworkFailure(RuntimeException("Request error"))
            }
        })
    }

    /**
     *
     */
    fun requestCycleHue(file: File, listener: ApiResponseListener) {
        Log.d(TAG, "requestBue...")
        val bueUrl = baseUrl
        Log.d(TAG, "requestBue URL : $bueUrl")

//        val body = JsonObject()
//        body.addProperty(AppConstants.KEY_PROMPT, prompt)

        //文件
        val fileBody: RequestBody = file.asRequestBody("multipart/form-data".toMediaTypeOrNull())
        val multipartBody = MultipartBody.Part.createFormData("file", file.name, fileBody)
        val format = MultipartBody.Part.createFormData("format", "wav")

        largeModelApi!!.requestCycleHue(multipartBody, format, "lightDemo", "rainmaker_app").enqueue(object : Callback<ResponseBody> {
            override fun onResponse(call: Call<ResponseBody>, response: Response<ResponseBody>) {
                Log.d(TAG, "Login, Response code  : " + response.code())

                try {
                    if (response.isSuccessful) {
                        val jsonResponse = response.body()!!.string()
                        Log.d(TAG, " -- Auth Success : response : $jsonResponse")
                        val jsonObject = JSONObject(jsonResponse)
                        val content = jsonObject.getString("data")
                        val bundle = Bundle()
                        bundle.putString("cycleHue", content)
                        listener.onSuccess(bundle)
                    } else {
                        val jsonErrResponse = response.errorBody()!!.string()
                        processError(jsonErrResponse, listener, "Request error")
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    listener.onResponseFailure(RuntimeException("请明确说清你想要的场景"))
                }
            }

            override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                t.printStackTrace()
                listener.onNetworkFailure(RuntimeException("Failed to login"))
            }
        })
    }

    fun speech2Text(file: File, listener: ApiResponseListener) {
        val key = BuildConfig.AUDIO_PARSE_KEY
        val url = BuildConfig.AUDIO_PARSE_URL + "v1/audio/transcriptions"
        var map: HashMap<String, RequestBody> = HashMap()
        val fileBody: RequestBody = file.asRequestBody("".toMediaTypeOrNull())
        val requestModel: RequestBody = "whisper-1".toRequestBody("".toMediaTypeOrNull())
        map["file"] = fileBody
        map["model"] = requestModel

        val list = mutableListOf<MultipartBody.Part>()
        list.add(MultipartBody.Part.createFormData("file", file.name, fileBody))
        list.add(MultipartBody.Part.createFormData("model", "whisper-1"))
//        val multipartBody: MultipartBody = MultipartBody.Builder() //添加文件
//            .addFormDataPart("file", file.name, requestFile)
//            .addFormDataPart("model", "model", requestModel)
//            .build()


        gptModelApi!!.speechToText(url, map)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    Log.d(TAG, "Login, Response code  : " + response.code())

                    try {
                        if (response.isSuccessful) {
                            val jsonResponse = response.body()!!.string()
                            Log.d(TAG, " -- Auth Success : response : $jsonResponse")
                            val jsonObject = JSONObject(jsonResponse)
                            val content = jsonObject.getString("text")
                            val bundle = Bundle()
                            bundle.putString("content", content)
                            listener.onSuccess(bundle)
                        } else {
                            val jsonErrResponse = response.errorBody()!!.string()
                            processError(jsonErrResponse, listener, "Failed to login")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onResponseFailure(RuntimeException("Failed to login"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                    listener.onNetworkFailure(RuntimeException("Failed to login"))
                }
            })
    }

    fun speech2TextTranscriptions(file: File, listener: ApiResponseListener) {
        //文件
        val fileBody: RequestBody = file.asRequestBody("".toMediaTypeOrNull())
        val multipartBody: MultipartBody.Part =
            MultipartBody.Part.createFormData("file", file.name, fileBody)
        //自定义参数
        val requestBodyMap: MutableMap<String, RequestBody> = HashMap()
        requestBodyMap["model"] =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), "whisper-1")

        gptModelApi!!.speechToTextTranscriptions(multipartBody, requestBodyMap)
            .enqueue(object : Callback<ResponseBody> {
                override fun onResponse(
                    call: Call<ResponseBody>,
                    response: Response<ResponseBody>
                ) {
                    Log.d(TAG, "Login, Response code  : " + response.code())

                    try {
                        if (response.isSuccessful) {
                            val jsonResponse = response.body()!!.string()
                            Log.d(TAG, " -- Auth Success : response : $jsonResponse")
                            val jsonObject = JSONObject(jsonResponse)
                            val content = jsonObject.getString("text")
                            val bundle = Bundle()
                            bundle.putString("content", content)
                            listener.onSuccess(bundle)
                        } else {
                            val jsonErrResponse = response.errorBody()!!.string()
                            processError(jsonErrResponse, listener, "Failed to login")
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        listener.onResponseFailure(RuntimeException("Failed to login"))
                    }
                }

                override fun onFailure(call: Call<ResponseBody>, t: Throwable) {
                    t.printStackTrace()
                    listener.onNetworkFailure(RuntimeException("Failed to login"))
                }
            })
    }

    private fun processError(
        jsonErrResponse: String,
        listener: ApiResponseListener,
        errMsg: String
    ) {
        Log.e(TAG, "Error Response : $jsonErrResponse")
        try {
            if (jsonErrResponse.contains(AppConstants.KEY_FAILURE_RESPONSE)) {
                val jsonObject = JSONObject(jsonErrResponse)
                val err = jsonObject.optString(AppConstants.KEY_DESCRIPTION)

                if (!TextUtils.isEmpty(err)) {
                    listener.onResponseFailure(CloudException(err))
                } else {
                    listener.onResponseFailure(RuntimeException(errMsg))
                }
            } else {
                listener.onResponseFailure(RuntimeException(errMsg))
            }
        } catch (e: JSONException) {
            e.printStackTrace()
            listener.onResponseFailure(RuntimeException(errMsg))
        }
    }


    companion object {
        val TAG: String = LargeModelClient::class.java.name

        const val BASE_PATH: String = ""
        private const val DEFAULT_TIMEOUT = 30
        var baseUrl: String = BuildConfig.LARGE_MODEL_URL

        private var instance: LargeModelClient? = null

        fun getInstance(): LargeModelClient {
            if (instance == null) {
                instance = LargeModelClient()
            }
            return instance!!
        }
    }
}


