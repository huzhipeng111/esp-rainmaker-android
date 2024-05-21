package com.espressif.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;

import com.espressif.rainmaker.databinding.ViewAudioControllerBinding;
import com.google.android.material.card.MaterialCardView;

import io.reactivex.disposables.CompositeDisposable;

/**
 * author: hzp
 * Date: 2024/5/21
 * Time: 18:14
 * 备注： 录音功能控制控件
 */
public class AudioControllerView extends MaterialCardView implements GestureDetector.OnGestureListener {

    private ViewAudioControllerBinding binding;
    private CompositeDisposable mDisposable;
    private OnAudioEvent onAudioEvent;
    private GestureDetectorCompat gestureDetector;
    private boolean isRecording;
    private final static long minRecordMillis = 1000;
    private long startRecordMillis;

    public AudioControllerView(@NonNull Context context) {
        super(context);
        init();
    }

    public AudioControllerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AudioControllerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }


    private void init() {
        binding = ViewAudioControllerBinding.inflate(LayoutInflater.from(getContext()), this, true);
        mDisposable = new CompositeDisposable();
        gestureDetector = new GestureDetectorCompat(getContext(), this);
    }

    @Override
    protected void onDetachedFromWindow() {
        if (mDisposable != null) {
            mDisposable.dispose();
            mDisposable = null;
        }
        super.onDetachedFromWindow();
    }

    public OnAudioEvent getOnAudioEvent() {
        return onAudioEvent;
    }

    public void setOnAudioEvent(OnAudioEvent onAudioEvent) {
        this.onAudioEvent = onAudioEvent;
    }

    @Override
    public boolean onDown(@NonNull MotionEvent motionEvent) {
        return false;
    }

    @Override
    public void onShowPress(@NonNull MotionEvent motionEvent) {

    }

    @Override
    public boolean onSingleTapUp(@NonNull MotionEvent motionEvent) {
        return false;
    }

    @Override
    public boolean onScroll(@NonNull MotionEvent motionEvent, @NonNull MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent motionEvent) {
        binding.progressIndicator.setVisibility(View.VISIBLE);
        if (onAudioEvent != null) {
            isRecording = true;
            onAudioEvent.onStartRecord();
            startRecordMillis = System.currentTimeMillis();
        }
    }

    @Override
    public boolean onFling(@NonNull MotionEvent motionEvent, @NonNull MotionEvent motionEvent1, float v, float v1) {
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
            binding.progressIndicator.setVisibility(View.GONE);
            if (isRecording) {
                isRecording = false;
                if (System.currentTimeMillis() - startRecordMillis <= minRecordMillis) {
                    //录音取消
                    if (onAudioEvent != null) {
                        onAudioEvent.onRecordCancel();
                    }
                } else {
                    //录音完成
                    if (onAudioEvent != null) {
                        onAudioEvent.onStopRecord();
                    }
                }
            }
        }
        return true;
    }

    public interface OnAudioEvent {
        /**
         * 按下，开始录音
         */
        void onStartRecord();

        /**
         * 按下时间过短，认为录音取消
         */
        void onRecordCancel();

        /**
         * 停止录音
         */
        void onStopRecord();
    }
}