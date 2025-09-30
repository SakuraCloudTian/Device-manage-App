package com.example.qrscanner;

import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.content.Intent;
import android.view.View;
import androidx.cardview.widget.CardView;

public class HomeActivity extends AppCompatActivity {

    // ReportボタンとShowボタンの変数を追加
    private CardView cardReport;
    private CardView cardShow;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.home);

        // 各CardViewのインスタンスを取得
        CardView cardNewDevice = findViewById(R.id.card_new_device);
        CardView cardNewUser = findViewById(R.id.card_new_user);
        CardView cardLend = findViewById(R.id.card_lend);
        CardView cardReturn = findViewById(R.id.card_return);

        // ★★ Report と Show ボタンの初期化 ★★
        cardShow = findViewById(R.id.card_show);
        cardReport = findViewById(R.id.card_report);

        // 各CardViewにクリックリスナーを設定

        // New Device
        cardNewDevice.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NewDeviceActivityへの遷移
                Intent intent = new Intent(HomeActivity.this, NewDeviceActivity.class);
                startActivity(intent);
            }
        });

        // New User
        cardNewUser.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // NewUserActivityへの遷移
                Intent intent = new Intent(HomeActivity.this, NewUserActivity.class);
                startActivity(intent);
            }
        });

        // Lend Device
        cardLend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // LendActivityへの遷移
                Intent intent = new Intent(HomeActivity.this, LendActivity.class);
                startActivity(intent);
            }
        });

        // Return Device
        cardReturn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ReturnActivityへの遷移
                Intent intent = new Intent(HomeActivity.this, ReturnActivity.class);
                startActivity(intent);
            }
        });

        // ★★ Report ボタンのクリック処理を追加 ★★
        cardReport.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // ReportActivityへの遷移
                Intent intent = new Intent(HomeActivity.this, ReportActivity.class);
                startActivity(intent);
            }
        });

        // Show ボタンのクリック処理のプレースホルダー
        cardShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(HomeActivity.this, DeviceListActivity.class);
                startActivity(intent);
            }
        });

        cardShow.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                Intent intent = new Intent(HomeActivity.this, DeviceListActivity.class);
                startActivity(intent);
            }
        });
    }
}


















