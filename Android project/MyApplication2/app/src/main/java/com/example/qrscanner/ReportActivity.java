package com.example.qrscanner;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import android.content.Intent;
import android.os.Bundle;

public class ReportActivity extends AppCompatActivity {

    private CardView cardNewDevice;
    private CardView cardDeviceDetail;
    private CardView cardDeviceFailure;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 新しいレイアウトファイル名を指定
        setContentView(R.layout.activity_report);

        // レイアウトのCardViewを紐付け
        cardNewDevice = findViewById(R.id.card_new_device);
        cardDeviceDetail = findViewById(R.id.card_device_detail);
        cardDeviceFailure = findViewById(R.id.card_device_failure);

        // 「新しいデバイス申請」のカードをクリックしたときの処理
        cardNewDevice.setOnClickListener(v -> {
            Intent intent = new Intent(ReportActivity.this, Report_NewDeviceActivity.class);
            startActivity(intent);
        });


        //
         cardDeviceFailure.setOnClickListener(v -> {
             Intent intent = new Intent(ReportActivity.this, Report_DeviceReportActivity.class);
             startActivity(intent);
        });

        cardDeviceDetail.setOnClickListener(v -> {
            Intent intent = new Intent(ReportActivity.this, ReportListActivity.class);
            startActivity(intent);
        });
    }
}
