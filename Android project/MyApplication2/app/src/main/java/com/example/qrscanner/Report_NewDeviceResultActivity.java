package com.example.qrscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Report_NewDeviceResultActivity extends AppCompatActivity {

    private EditText deviceNameEditText;
    private EditText deviceUrlEditText;
    private Button requestButton;
    private TextView statusTextView;
    private Bitmap qrCodeBitmap;

    private String userId;

    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_new_device_result);

        deviceNameEditText = findViewById(R.id.deviceNameEditText);
        deviceUrlEditText = findViewById(R.id.deviceURLEditText);
        requestButton = findViewById(R.id.requestButton);
        statusTextView= findViewById(R.id.statusTextView);

        // Get the codes passed from LendActivity
        if (getIntent().getExtras() != null) {
            userId = getIntent().getStringExtra("USER_CODE");
        }


        requestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String deviceName = deviceNameEditText.getText().toString();
                String deviceUrl = deviceUrlEditText.getText().toString();

                if (!deviceName.trim().isEmpty()) {
                    registerDevice(deviceName, deviceUrl, userId);
                } else {
                    statusTextView.setText("必須項目を入力してください");
                }
            }
        });
    }

    // デバイス申請をサーバーに送信
    private void registerDevice(String deviceName, String deviceUrl, String userId) {
        statusTextView.setText("デバイスを申請中...");

        String url = "http://192.168.10.8:8001/report" +
                "?id=" + userId +
                "&report=" + deviceName ;

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

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
                                statusTextView.setText("申請成功!");
                            } else {
                                statusTextView.setText("申請失敗: " + response.code() + "\n" + response.message());
                                Log.e("NewDeviceActivity", "Device request failed: " + response.code() + " - " + responseBody);
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
        int size = 512;
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
}
