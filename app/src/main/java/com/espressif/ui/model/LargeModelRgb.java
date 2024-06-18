package com.espressif.ui.model;

import com.google.gson.annotations.SerializedName;

/**
 * author: hzp
 * Date: 2024/5/20
 * Time: 21:33
 * 备注：
 */
public class LargeModelRgb {

    @SerializedName("r")
    private Integer r;
    @SerializedName("g")
    private Integer g;
    @SerializedName("b")
    private Integer b;

    public Integer getR() {
        return r;
    }

    public void setR(Integer r) {
        this.r = r;
    }

    public Integer getG() {
        return g;
    }

    public void setG(Integer g) {
        this.g = g;
    }

    public Integer getB() {
        return b;
    }

    public void setB(Integer b) {
        this.b = b;
    }
}