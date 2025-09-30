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

import android.app.DatePickerDialog;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * 貸し出し情報入力ページ：スキャン結果を受け取り、手動で情報を入力して送信する
 */
public class LendResultActivity extends AppCompatActivity {

    private EditText etReturnDate;
    private Button btnSubmit;
    private TextView tvStatus;
    private TextView tvScannedCodesUser;
    private TextView tvScannedCodesDevice;

    @Nullable private String userCode = null;
    @Nullable private String deviceCode = null;
    @Nullable private String returnDate = null;
    @Nullable private String userName = null;
    @Nullable private String deviceName = null;
    private final OkHttpClient http = new OkHttpClient();

    private static final String BASE_HOST = "192.168.10.8"; // Your server IP
    private static final int BASE_PORT = 8000;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lend_result);


        tvScannedCodesDevice = findViewById(R.id.tvScannedCodesDevice);
        tvScannedCodesUser = findViewById(R.id.tvScannedCodesUser);
        etReturnDate = findViewById(R.id.etReturnDate);
        btnSubmit = findViewById(R.id.btnSubmit);
        tvStatus = findViewById(R.id.tvStatus);

        // Get the codes passed from LendActivity
        if (getIntent().getExtras() != null) {
            userCode = getIntent().getStringExtra("USER_CODE");
            deviceCode = getIntent().getStringExtra("DEVICE_CODE");
            getNames(userCode,deviceCode);
        }


        // Show DatePickerDialog when the EditText is clicked
        etReturnDate.setOnClickListener(v -> showDatePickerDialog());

        // Handle the submit button click
        btnSubmit.setOnClickListener(v -> {
            if (returnDate == null) {
                Toast.makeText(this, "返却日を選択してください。", Toast.LENGTH_SHORT).show();
                return;
            }

            httpLendDevice(deviceCode, userCode, returnDate);
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
                    tvScannedCodesDevice.setText(String.format("デバイスID: %s , デバイス名: 取得失敗", deviceId));
                    tvScannedCodesUser.setText(String.format("ユーザID: %s , ユーザ名: 取得失敗", userId));
                    Toast.makeText(LendResultActivity.this, "名前の取得に失敗しました", Toast.LENGTH_SHORT).show();
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
                        tvScannedCodesDevice.setText(String.format("デバイスID: %s , デバイス名: %s", deviceId, deviceName));
                        tvScannedCodesUser.setText(String.format("ユーザID: %s , ユーザ名：　%s ", userId, userName));
                    });
                } catch (JSONException e) {
                    onFailure(call, new IOException("Failed to parse JSON", e));
                }
            }
        });
    }


    private void showDatePickerDialog() {
        final Calendar c = Calendar.getInstance();
        int year = c.get(Calendar.YEAR);
        int month = c.get(Calendar.MONTH);
        int day = c.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog datePickerDialog = new DatePickerDialog(this,
                (view, y, m, d) -> {
                    // Format the date for display
                    String formattedDate = String.format("%d/%02d/%02d", y, m + 1, d);
                    etReturnDate.setText(formattedDate);
                    // Store the date in yyyyMMdd format for the API request
                    returnDate = String.format("%d%02d%02d", y, m + 1, d);
                }, year, month, day);
        datePickerDialog.show();
    }

    private void httpLendDevice(String deviceCode, String userCode, String returnDate) {
        String userId = userCode;
        String deviceId = deviceCode;

        // Get the current date in yyyyMMdd format
        String outDate = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));

        // Build the URL with parameters
        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/lend" +
                "?id=" + Uri.encode(deviceId) +
                "&curr_user=" + Uri.encode(userId) +
                "&status=false" +
                "&return_date=" + Uri.encode(returnDate) +
                "&out_date=" + Uri.encode(outDate) ;

        tvStatus.setText("送信中...");

        okhttp3.Request req = new okhttp3.Request.Builder().url(url).get().build();
        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, IOException e) {
                runOnUiThread(() -> tvStatus.setText("HTTP Failed: " + e.getMessage()));
            }
            @Override public void onResponse(okhttp3.Call call, Response resp) throws IOException {
                //String body = resp.body() != null ? resp.body().string() : "";
                runOnUiThread(() -> tvStatus.setText("貸し出し完了!"));
            }
        });
    }

}
