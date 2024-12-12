package com.espressif.ui.vm;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.LargeModelClient;
import com.espressif.ui.model.LargeModelHue;

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
    public Observable<LargeModelHue> requestLargeModelBue(String content) {
        return Observable.create(new ObservableOnSubscribe<LargeModelHue>() {
            @Override
            public void subscribe(ObservableEmitter<LargeModelHue> emitter) throws Exception {
                LargeModelClient.Companion.getInstance().requestHue(content, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {
                        LargeModelHue hue = new LargeModelHue();
                        hue.setHue(data.getInt("hue"));
                        hue.setBrightness(data.getInt("brightness"));
                        emitter.onNext(hue);
                        emitter.onComplete();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<LargeModelHue> requestLargeModelRgb(File file) {
        return Observable.create(new ObservableOnSubscribe<LargeModelHue>() {
            @Override
            public void subscribe(ObservableEmitter<LargeModelHue> emitter) throws Exception {
                LargeModelClient.Companion.getInstance().requestRgb(file, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {
                        LargeModelHue hue = new LargeModelHue();
                        hue.setHue(data.getInt("hue"));
                        hue.setBrightness(data.getInt("brightness"));
                        emitter.onNext(hue);
                        emitter.onComplete();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<String> requestLargeModelCycleHue(File file) {
        return Observable.create(new ObservableOnSubscribe<String>() {
            @Override
            public void subscribe(ObservableEmitter<String> emitter) throws Exception {
                LargeModelClient.Companion.getInstance().requestCycleHue(file, new ApiResponseListener() {
                    @Override
                    public void onSuccess(@Nullable Bundle data) {
                        String cycleHue = data.getString("cycleHue");
                        emitter.onNext(cycleHue);
                        emitter.onComplete();
                    }

                    @Override
                    public void onResponseFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
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
                        emitter.onError(exception);
                        emitter.onComplete();
                    }

                    @Override
                    public void onNetworkFailure(@NonNull Exception exception) {
                        emitter.onError(exception);
                        emitter.onComplete();
                    }
                });
            }
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }
}