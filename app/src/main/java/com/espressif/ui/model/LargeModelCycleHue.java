package com.espressif.ui.model;

import com.google.gson.annotations.SerializedName;

/**
 * author: hzp
 * Date: 2024/6/20
 * Time: 21:29
 * 备注：
 */
public class LargeModelCycleHue {

    @SerializedName("Brightness")
    private Integer brightness;
    @SerializedName("Hue")
    private Integer hue;
    @SerializedName("interval")
    private Integer interval;

    public Integer getBrightness() {
        return brightness;
    }

    public void setBrightness(Integer brightness) {
        this.brightness = brightness;
    }

    public Integer getHue() {
        return hue;
    }

    public void setHue(Integer hue) {
        this.hue = hue;
    }

    public Integer getInterval() {
        return interval;
    }

    public void setInterval(Integer interval) {
        this.interval = interval;
    }
}