package com.example.qrscanner;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Report_DeviceReportResultActivity extends AppCompatActivity {

    private EditText reportAboutDeviceEditText;
    private EditText deviceUrlEditText;
    private Button requestButton;
    private TextView statusTextView;

    private TextView deviceName;
    private TextView suggestTextView;
    private Bitmap qrCodeBitmap;

    private String deviceId;

    private static final String BASE_HOST = "192.168.10.8"; // サーバーのIPアドレス
    private static final int    BASE_PORT = 8000;


    private final OkHttpClient client = new OkHttpClient();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_report_result);

        reportAboutDeviceEditText = findViewById(R.id.reportAboutDevice);
        requestButton = findViewById(R.id.requestButton);
        deviceName= findViewById(R.id.deviceId);
        statusTextView= findViewById(R.id.statusTextView);
        suggestTextView = findViewById(R.id.suggestTextView);

        // Get the codes passed from LendActivity
        if (getIntent().getExtras() != null) {
            deviceId = getIntent().getStringExtra("USER_CODE");
        }

        deviceName.setText(deviceId);



        requestButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String reports = reportAboutDeviceEditText.getText().toString();
                if (reports != null &&!reports.isEmpty()) {
                    reportingDevice(reports);
                } else {
                    statusTextView.setText("必須項目を入力してください");
                }
            }
        });
    }


    // デバイス申請をサーバーに送信
    private void reportingDevice(String reports) {
        statusTextView.setText("デバイスを申請中...");

        String url = "http://192.168.10.8:8001/report" +
                "?id=" + deviceId +
                "&report=" + reports;

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
                            if (response.isSuccessful() && responseBody != null) {
                                try {
                                    // JSON応答をパースしてAIの回答を取得
                                    JSONObject jsonResponse = new JSONObject(responseBody);
                                    String supportMessage = jsonResponse.getString("support");

                                    statusTextView.setText("送信成功!");
                                    suggestTextView.setText(supportMessage);

                                } catch (JSONException e) {
                                    statusTextView.setText("サーバー応答のパースに失敗しました");
                                    Log.e("NewDeviceActivity", "JSON parsing error", e);
                                }
                            } else {
                                statusTextView.setText("送信失敗: " + response.code() + "\n" + response.message());
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
