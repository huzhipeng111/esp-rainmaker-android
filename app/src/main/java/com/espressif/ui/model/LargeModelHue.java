package com.espressif.ui.model;

import com.google.gson.annotations.SerializedName;

/**
 * author: hzp
 * Date: 2024/5/20
 * Time: 21:33
 * 备注：
 */
public class LargeModelHue {

    private Integer Brightness;
    private Integer Hue;

    public Integer getBrightness() {
        return Brightness;
    }

    public void setBrightness(Integer brightness) {
        Brightness = brightness;
    }

    public Integer getHue() {
        return Hue;
    }

    public void setHue(Integer hue) {
        Hue = hue;
    }
}