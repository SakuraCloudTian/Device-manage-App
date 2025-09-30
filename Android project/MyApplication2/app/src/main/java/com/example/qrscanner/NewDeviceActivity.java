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
//Thank you gemini

package com.example.qrscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.print.PrintHelper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.math.BigInteger;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

// 新規デバイスを登録するActivity
public class NewDeviceActivity extends AppCompatActivity {

    private EditText deviceNameEditText;
    private Button registerButton;
    private Button printButton;
    private TextView statusTextView;
    private EditText locationEditText;
    private Bitmap qrCodeBitmap;
    private  String generatedDeviceId;
    // OkHttpクライアント
    private final OkHttpClient client = new OkHttpClient();
    private static final String BASE_HOST = "133.2.130.195"; // サーバーのIPアドレス
    private static final int    BASE_PORT = 8000;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_device);

        // UI要素の初期化
        deviceNameEditText = findViewById(R.id.deviceNameEditText);
        registerButton = findViewById(R.id.registerButton);
        statusTextView = findViewById(R.id.statusTextView);
        locationEditText = findViewById(R.id.locationEditText);
        printButton = findViewById(R.id.printButton);

        // 初期状態では印刷ボタンを非表示にする
        printButton.setVisibility(View.GONE);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String deviceName = deviceNameEditText.getText().toString();
                String location = locationEditText.getText().toString();
                if (!deviceName.trim().isEmpty()) {
                    // デバイスの登録とQRコードの生成
                    registerDevice(deviceName,location);
                } else {
                    statusTextView.setText("必要事項を入力してください");
                }
            }
        });

        printButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    doPhotoPrint(generatedDeviceId);
            }
        });
    }

    // デバイスを登録し、API経由でサーバーに送信
    private void registerDevice(String deviceName, String location) {
        // ユニークなデバイスIDを生成
        // デバイスIDが'D'で始まるように変更
        // Pythonのコードを参考に、UUIDからベース36の文字列を生成
        String base36Id = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).toString(36);
        // デバイスIDが'D'で始まるように変更
        String deviceId = "D" + base36Id.substring(0, 7).toUpperCase(Locale.ROOT);
        generatedDeviceId= deviceId;

        statusTextView.setText("デバイスを登録中...");

        String url = "http://192.168.10.8:8000/regis_device" +
                "?id=" + deviceId +
                "&name=" + deviceName +
                "&status=true" +
                "&location=" + location;

        // GETリクエストを作成
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        // 非同期でAPIを呼び出す
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Response response = client.newCall(request).execute();
                    final String responseBody = response.body() != null ? response.body().string() : null;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response.isSuccessful()) {
                                statusTextView.setText("登録成功！");
                                printButton.setVisibility(View.VISIBLE);
                            } else {
                                statusTextView.setText("登録失敗: " + response.code() + "\n" + response.message());
                                Log.e("NewDeviceActivity", "Registration failed: " + response.code() + " - " + responseBody);
                            }
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusTextView.setText("通信エラー: " + e.getMessage());
                            Log.e("NewDeviceActivity", "Network error", e);
                        }
                    });
                }
            }
        }).start();
    }


    private Bitmap generateQRCode(String text) {
        int size = 512; // QRコードのサイズ（px）
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
        BitMatrix bitMatrix;
        try {
            bitMatrix = new MultiFormatWriter().encode(
                    text, BarcodeFormat.QR_CODE, size, size, hints
            );
        } catch (Exception e) {
            Log.e("NewDeviceActivity", "QR Code generation failed", e);
            return null;
        }

        int[] pixels = new int[size * size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels[y * size + x] = bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE;
            }
        }

        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size);
        return bitmap;
    }

    private void doPhotoPrint(String deviceId) {

        // サーバーへの印刷リクエスト用URLを構築
        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/print" +
                "?id=" + Uri.encode(deviceId);

        // 非同期でAPIを呼び出す
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Request request = new Request.Builder().url(url).get().build();
                    Response response = client.newCall(request).execute();

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response.isSuccessful()) {
                                // サーバーへのリクエスト成功後、印刷ダイアログを開く
                                Toast.makeText(NewDeviceActivity.this, "印刷リクエストを送信しました", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(NewDeviceActivity.this, "印刷リクエスト失敗: " + response.code(), Toast.LENGTH_SHORT).show();
                                Log.e("NewDeviceActivity", "Print request failed: " + response.code());
                            }
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(NewDeviceActivity.this, "通信エラー: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("NewDeviceActivity", "Network error on print", e);
                        }
                    });
                }
            }
        }).start();

    }

}

