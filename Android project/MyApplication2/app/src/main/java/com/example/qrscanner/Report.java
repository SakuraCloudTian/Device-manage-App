package com.example.qrscanner;

import com.google.gson.annotations.SerializedName;

public class Report {

    @SerializedName("id")
    private String id;

    @SerializedName("name")
    private String name;

    @SerializedName("report")
    private String report;

    @SerializedName("time")
    private String time;

    // --- コンストラクタ ---
    public Report() {
    }

    // --- Getter ---
    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getReport() {
        return report;
    }

    public String getTime() {
        return time;
    }
}