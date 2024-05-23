package com.espressif.ui.vm;

import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.LargeModelClient;

import java.io.File;

import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.schedulers.Schedulers;

/**
 * author: hzp
 * Date: 2024/5/20
 * Time: 21:01
 * 备注：
 */
public class EspDeviceViewModel extends ViewModel {
    public Observable<Integer> requestLargeModelBue(String content) {
        return Observable.create(new ObservableOnSubscribe<Integer>() {
            @Override
            public void subscribe(ObservableEmitter<Integer> emitter) throws Exception {
                LargeModelClient.Companion.getInstance().requestBue(content, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {
                        emitter.onNext(data.getInt("rgb"));
                        emitter.onComplete();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {

                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {

                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<String> requestSpeech2Text(File file) {
        return Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                LargeModelClient.Companion.getInstance().speech2TextTranscriptions(file, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {
                        emitter.onNext(data.getString("content"));
                        emitter.onComplete();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {

                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {

                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }
}