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

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.print.PrintHelper;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import org.json.JSONException;
import org.json.JSONObject;

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

// 新規ユーザーを登録するActivity
public class NewUserActivity extends AppCompatActivity {

    private EditText userNameEditText;
    private EditText userGradeEditText;
    private EditText userContactEditText;
    private CheckBox isAdminCheckBox;
    private Button registerButton;
    private TextView statusTextView;
    private  String generatedUserId;
    private Button printButton;
    private Bitmap qrCodeBitmap;
    private static final String BASE_HOST = "133.2.130.195"; // サーバーのIPアドレス
    private static final int    BASE_PORT = 8000;
    // OkHttpクライアント
    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_new_user);

        // UI要素の初期化
        userNameEditText = findViewById(R.id.userNameEditText);
        userGradeEditText = findViewById(R.id.userGradeEditText);
        userContactEditText = findViewById(R.id.userContactEditText);
        isAdminCheckBox = findViewById(R.id.isAdminCheckBox);
        registerButton = findViewById(R.id.registerButton);
        statusTextView = findViewById(R.id.statusTextView);
        printButton = findViewById(R.id.printButton);


        // 初期状態では印刷ボタンを非表示にする
        printButton.setVisibility(View.GONE);

        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String userName = userNameEditText.getText().toString();
                String userGrade = userGradeEditText.getText().toString();
                String userContact = userContactEditText.getText().toString();
                boolean isAdmin = isAdminCheckBox.isChecked();

                if (!userName.trim().isEmpty() && !userGrade.trim().isEmpty()) {
                    // ユーザーの登録とQRコードの生成
                    registerUser(userName, userGrade, userContact, isAdmin);
                } else {
                    statusTextView.setText("必要項目を入力してください");
                }
            }
        });

        printButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    doPhotoPrint(generatedUserId);

            }
        });
    }

    // ユーザーを登録し、API経由でサーバーに送信
    private void registerUser(String userName, String userGrade, String userContact, boolean isAdmin) {
        // デバイスIDと同様に、UUIDからベース36の文字列を生成
        String base36Id = new BigInteger(UUID.randomUUID().toString().replace("-", ""), 16).toString(36);
        // ユーザーIDが'U'で始まるように変更
        String userId = "U" + base36Id.substring(0, 7).toUpperCase(Locale.ROOT);
        generatedUserId = userId;
        statusTextView.setText("ユーザーを登録中...");

        String url = "http://192.168.10.8:8000/regis_user" +
                "?id=" + userId +
                "&name=" + userName +
                "&grade=" + userGrade +
                "&contact=" + userContact +
                "&admin=" + isAdmin;

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
                                qrCodeBitmap = generateQRCode(userId);
                                printButton.setVisibility(View.VISIBLE);
                            } else {
                                statusTextView.setText("登録失敗: " + response.code() + "\n" + response.message());
                                Log.e("NewUserActivity", "Registration failed: " + response.code() + " - " + responseBody);
                            }
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            statusTextView.setText("通信エラー: " + e.getMessage());
                            Log.e("NewUserActivity", "Network error", e);
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

    private void doPhotoPrint(String userId) {

        // サーバーへの印刷リクエスト用URLを構築
        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/print" +
                "?id=" + Uri.encode(userId);

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
                                Toast.makeText(NewUserActivity.this, "印刷リクエストを送信しました", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(NewUserActivity.this, "印刷リクエスト失敗: " + response.code(), Toast.LENGTH_SHORT).show();
                                Log.e("NewDeviceActivity", "Print request failed: " + response.code());
                            }
                        }
                    });
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(NewUserActivity.this, "通信エラー: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            Log.e("NewDeviceActivity", "Network error on print", e);
                        }
                    });
                }
            }
        }).start();

    }

}
