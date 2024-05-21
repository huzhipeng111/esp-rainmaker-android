package com.espressif;

import android.app.Service;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;

import androidx.annotation.Nullable;

import java.io.File;

import io.reactivex.Notification;
import io.reactivex.Observable;
import io.reactivex.ObservableEmitter;
import io.reactivex.ObservableOnSubscribe;
import io.reactivex.Scheduler;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.internal.schedulers.SingleScheduler;
import omrecorder.AudioChunk;
import omrecorder.AudioRecordConfig;
import omrecorder.OmRecorder;
import omrecorder.PullTransport;
import omrecorder.PullableSource;
import omrecorder.Recorder;

/**
 * author: hzp
 * Date: 2024/5/16
 * Time: 18:56
 * 备注：录音服务
 */
public class SoundRecordService extends Service {
    Recorder recorder;
    private Scheduler scheduler;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new IBinder();
    }

    @Override
    public void onCreate() {
        super.onCreate();
        scheduler = new SingleScheduler();
    }

    public class IBinder extends Binder {
        public SoundRecordService getService() {
            return SoundRecordService.this;
        }
    }

    private void initRecord() {
        recorder = OmRecorder.wav(new PullTransport.Default(mic(), new PullTransport.OnAudioChunkPulledListener() {
            @Override
            public void onAudioChunkPulled(AudioChunk audioChunk) {
                animateVoice((float) (audioChunk.maxAmplitude() / 200.0));
            }
        }), file());
    }

    public Observable<?> startRecord() {
        return Observable.create(new ObservableOnSubscribe<Object>() {
            @Override
            public void subscribe(ObservableEmitter<Object> emitter) throws Exception {
                initRecord();
                recorder.startRecording();
                emitter.onNext(Notification.createOnNext(Notification.createOnComplete()));
                emitter.onComplete();
            }
        }).subscribeOn(scheduler).observeOn(AndroidSchedulers.mainThread());
    }

    public Observable<File> stopRecord() {
        return Observable.create(new ObservableOnSubscribe<File>() {
            @Override
            public void subscribe(ObservableEmitter<File> emitter) throws Exception {
                recorder.stopRecording();
                emitter.onNext(file());
                emitter.onComplete();
            }
        }).subscribeOn(scheduler).observeOn(AndroidSchedulers.mainThread());
    }


    private File file() {
        return new File(getCacheDir(), "demo.wav");
//        return new File(Environment.getExternalStorageDirectory(), "demo.wav");
    }

    /**
     * 正在录音中的动画
     *
     * @param maxPeak
     */
    private void animateVoice(final float maxPeak) {

    }

    private PullableSource mic() {
        return new PullableSource.Default(new AudioRecordConfig.Default(
                MediaRecorder.AudioSource.MIC, AudioFormat.ENCODING_PCM_16BIT,
                AudioFormat.CHANNEL_IN_MONO, 44100
        ));
    }
}