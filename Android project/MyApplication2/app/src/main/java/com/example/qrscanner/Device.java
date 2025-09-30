package com.example.qrscanner;

import com.google.gson.annotations.SerializedName;

public class Device {

    @SerializedName("name")
    private String name;

    @SerializedName("curr_user")
    private String curr_user;

    @SerializedName("his_user")
    private String his_user;

    @SerializedName("status")
    private int status; // 1: 貸出中, それ以外: 利用可能

    @SerializedName("location")
    private String location;

    @SerializedName("out_date")
    private String out_date;

    @SerializedName("return_date")
    private String return_date;

    @SerializedName("id")
    private String id;

    // --- コンストラクタ ---
    public Device() {
    }

    // --- Getter ---
    public String getName() { return name; }
    public String getCurr_user() { return curr_user; }
    public String getHis_user() { return his_user; }
    public int getStatus() { return status; }
    public String getLocation() { return location; }
    public String getOut_date() { return out_date; }
    public String getReturn_date() { return return_date; }
    public String getId() { return id; }
}