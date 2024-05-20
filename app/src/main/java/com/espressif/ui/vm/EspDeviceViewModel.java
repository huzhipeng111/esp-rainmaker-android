package com.espressif.ui.vm;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModel;

import com.espressif.cloudapi.ApiResponseListener;
import com.espressif.cloudapi.LargeModelClient;

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
    public Observable<Integer> requestLargeModelBue() {
        return Observable.create(new ObservableOnSubscribe<Integer>() {
            @Override
            public void subscribe(ObservableEmitter<Integer> emitter) throws Exception {
                LargeModelClient.getInstance().requestBue("彩虹", new ApiResponseListener() {
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
}