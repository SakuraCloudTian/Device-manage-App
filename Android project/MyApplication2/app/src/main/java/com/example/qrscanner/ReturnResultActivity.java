// Copyright 2024 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.example.qrscanner;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.net.Uri;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.Callback;
import okhttp3.Call;

public class ReturnResultActivity extends AppCompatActivity {

    private TextView tvStatus;
    private TextView tvResultDevice;
    private TextView tvResultUser;
    private EditText locationEditText;
    private Button btnSubmit;
    private OkHttpClient http = new OkHttpClient();
    private static final String BASE_HOST = "192.168.10.8"; // サーバーのIPアドレス
    private static final int    BASE_PORT = 8000;
    @Nullable private String userName = null;
    @Nullable private String deviceName = null;
    @Nullable private String userCode = null;
    @Nullable private String deviceCode = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_return_result);

        tvStatus = findViewById(R.id.tvStatus);
        tvResultDevice = findViewById(R.id.tvResultDevice);
        tvResultUser = findViewById(R.id.tvResultUser);
        locationEditText = findViewById(R.id.locationEditText);
        btnSubmit = findViewById(R.id.submitButton);



        // ReturnActivityから渡されたデバイスIDを取得
        String deviceCode = getIntent().getStringExtra("DEVICE_CODE");
        String userCode= getIntent().getStringExtra("USER_CODE");

        if(deviceCode != null && userCode != null){
            getNames(userCode, deviceCode);
        }

        String location = locationEditText.getText().toString();

        // Handle the submit button click
        btnSubmit.setOnClickListener(v -> {
            if (deviceCode != null && userCode != null) {
                tvStatus.setText("通信中...");
                httpReturnDevice(deviceCode, userCode, location);
            } else {
                tvStatus.setText("エラー: デバイスIDが見つかりません。");
            }
        });
    }

    private void getNames(String userCode, String deviceCode) {
        String userId = userCode;
        String deviceId = deviceCode;

        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/get_name" +
                "?user_id=" + Uri.encode(userId) +
                "&device_id=" + Uri.encode(deviceId);

        Request req = new Request.Builder().url(url).build();
        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> {
                    tvResultDevice.setText(String.format("デバイスID: %s , デバイス名: 取得失敗", deviceId));
                    tvResultUser.setText(String.format("ユーザID: %s , ユーザ名: 取得失敗", userId));
                    Toast.makeText(ReturnResultActivity.this, "名前の取得に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }

            @Override public void onResponse(okhttp3.Call call, Response resp) throws IOException {
                if (!resp.isSuccessful()) {
                    onFailure(call, new IOException("Unexpected code " + resp));
                    return;
                }
                try {
                    String body = resp.body() != null ? resp.body().string() : "{}";
                    JSONObject json = new JSONObject(body);
                    userName = json.optString("user_name");
                    deviceName = json.optString("device_name");

                    runOnUiThread(() -> {
                        tvResultDevice.setText(String.format("デバイスID: %s , デバイス名: %s", deviceId, deviceName));
                        tvResultUser.setText(String.format("ユーザID: %s , ユーザ名：　%s ", userId, userName));
                    });
                } catch (JSONException e) {
                    onFailure(call, new IOException("Failed to parse JSON", e));
                }
            }
        });
    }


    private void httpReturnDevice(String deviceId, String userId, String location) {
        // 返却場所を仮設定
        String loc = location;
        // ユーザー情報を解除
        String currUser = userId;
        String returnDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // URLにパラメータを含めて組み立てる
        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/return" +
                "?id=" + Uri.encode(deviceId) +
                "&curr_user=" + Uri.encode(currUser) +
                "&return_date=" + Uri.encode(returnDate) +
                "&status=true" +
                "&location=" + Uri.encode(loc);


        Request req = new Request.Builder().url(url).get().build();
        http.newCall(req).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                runOnUiThread(() -> tvStatus.setText("通信失敗: " + e.getMessage()));
            }

            @Override
            public void onResponse(Call call, Response resp) throws IOException {
                if (resp.isSuccessful()) {
                    runOnUiThread(() -> {
                        try {
                            String body = resp.body().string();
                            tvStatus.setText("返却完了！");
                        } catch (IOException e) {
                            tvStatus.setText("通信成功、しかし応答エラー: " + e.getMessage());
                        }
                    });
                } else {
                    runOnUiThread(() -> tvStatus.setText("返却失敗: HTTP " + resp.code()));
                }
            }
        });
    }


}