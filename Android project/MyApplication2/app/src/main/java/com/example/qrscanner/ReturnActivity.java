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

import android.Manifest;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.GenericMultipleBarcodeReader;
import com.google.zxing.multi.MultipleBarcodeReader;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 貸し出しスキャンページ：デバイスとユーザーのQRコードをスキャンし、結果画面へ遷移する
 */
public class ReturnActivity extends AppCompatActivity {

    private PreviewView previewView;
    private OverlayView overlay;
    private TextView tvResult;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    // Decoding throttle
    private volatile boolean isDecoding = false;
    private volatile boolean scanningEnabled = true;

    // Cooldown after recognition
    private long lastHitTs = 0L;
    private static final long HIT_COOLDOWN_MS = 800;

    // --- Business: Scan U/D codes and combine them into a single request ---
    @Nullable private String userCode = null;
    @Nullable private String deviceCode = null;

    // 3-second cache clear
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long CACHE_TTL_MS = 3000L;
    private final Runnable clearCacheRunnable = () -> {
        userCode = null;
        deviceCode = null;
        runOnUiThread(() -> tvResult.setText("キャッシュがクリアされました。スキャンを待機中…"));
    };

    // Keep a reference to the analyzer
    private ImageAnalysis analysisRef;

    // ZXing decoder (re-used single instance)
    private final MultiFormatReader baseReader = new MultiFormatReader();
    private final MultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(baseReader);

    // Camera permission launcher
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else finish();
            });

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.return_device); // Use existing lend layout for scanning

        previewView = findViewById(R.id.previewView);
        overlay = findViewById(R.id.overlay);
        tvResult = findViewById(R.id.tvResult);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // ZXing hints: faster when only scanning QR codes
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        baseReader.setHints(hints);

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);

        // Tap to toggle flashlight
        previewView.setOnClickListener(v -> {
            if (camera != null) {
                boolean newTorch = camera.getCameraInfo().getTorchState().getValue() !=
                        androidx.camera.core.TorchState.ON;
                camera.getCameraControl().enableTorch(newTorch);
            }
        });
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                cameraProvider = future.get();
                bindPreviewAndAnalyzer();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void bindPreviewAndAnalyzer() {
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        analysisRef = new ImageAnalysis.Builder()
                .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build();

        analysisRef.setAnalyzer(cameraExecutor, image -> {
            if (!scanningEnabled) { image.close(); return; }
            if (isDecoding) { image.close(); return; }
            isDecoding = true;

            try {
                // Get only the Y plane
                ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
                ByteBuffer yBuf = yPlane.getBuffer();
                byte[] yData = new byte[yBuf.remaining()];
                yBuf.get(yData);

                // Map ROI
                Rect roi = mapViewRectToImageRect(overlay.getScanRect(), previewView, image);

                // ZXing input
                PlanarYUVLuminanceSource src = new PlanarYUVLuminanceSource(
                        yData, image.getWidth(), image.getHeight(),
                        roi.left, roi.top, roi.width(), roi.height(),
                        false);
                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(src));

                long now = SystemClock.uptimeMillis();
                boolean inCooldown = now - lastHitTs < HIT_COOLDOWN_MS;

                try {
                    Result[] results = multiReader.decodeMultiple(bitmap);
                    if (results != null && results.length > 0) {
                        mainHandler.removeCallbacks(clearCacheRunnable);
                        mainHandler.postDelayed(clearCacheRunnable, CACHE_TTL_MS);

                        Set<String> set = new HashSet<>();
                        StringBuilder sb = new StringBuilder();
                        for (Result r : results) {
                            String t = r.getText();
                            if (t == null) continue;
                            if (set.add(t)) {
                                sb.append(t).append("\n");
                                handleScannedText(t);
                            }
                        }
                        if (!inCooldown && sb.length() > 0) {
                            lastHitTs = now;
                            String all = sb.toString().trim();
                            runOnUiThread(() -> tvResult.setText("スキャン結果:\n" + all));
                        }
                    }
                } catch (NotFoundException ignore) {
                    // No code in this frame
                }
            } catch (Throwable t) {
                t.printStackTrace();
            } finally {
                baseReader.reset();
                image.close();
                isDecoding = false;
            }
        });

        camera = cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysisRef);
    }

    /** Map Overlay (view coordinates) -> Analysis frame (sensor coordinates) */
    private Rect mapViewRectToImageRect(Rect viewRect, PreviewView pv, ImageProxy image) {
        int rotation = image.getImageInfo().getRotationDegrees();
        int imgW = image.getWidth();
        int imgH = image.getHeight();

        float viewW = pv.getWidth();
        float viewH = pv.getHeight();

        int srcW = (rotation == 90 || rotation == 270) ? imgH : imgW;
        int srcH = (rotation == 90 || rotation == 270) ? imgW : imgH;

        float scale = Math.max(viewW / srcW, viewH / srcH);
        float scaledW = srcW * scale;
        float scaledH = srcH * scale;
        float dx = (viewW - scaledW) / 2f;
        float dy = (viewH - scaledH) / 2f;

        float x = (viewRect.left - dx) / scale;
        float y = (viewRect.top  - dy) / scale;
        float w = viewRect.width()  / scale;
        float h = viewRect.height() / scale;

        Rect out = new Rect();
        switch (rotation) {
            case 0:
                out.set(clamp((int) x, 0, imgW),
                        clamp((int) y, 0, imgH),
                        clamp((int) (x + w), 0, imgW),
                        clamp((int) (y + h), 0, imgH));
                break;
            case 90:
                out.set(clamp((int) y, 0, imgW),
                        clamp((int) (srcW - (x + w)), 0, imgH),
                        clamp((int) (y + h), 0, imgW),
                        clamp((int) (srcW - x), 0, imgH));
                break;
            case 180:
                out.set(clamp((int) (srcW - (x + w)), 0, imgW),
                        clamp((int) (srcH - (y + h)), 0, imgH),
                        clamp((int) (srcW - x), 0, imgW),
                        clamp((int) (srcH - y), 0, imgH));
                break;
            case 270:
                out.set(clamp((int) (srcH - (y + h)), 0, imgH),
                        clamp((int) x, 0, imgW),
                        clamp((int) (srcH - y), 0, imgH),
                        clamp((int) (x + w), 0, imgW));
                break;
        }
        if (out.width() <= 0 || out.height() <= 0) return new Rect(0, 0, imgW, imgH);
        return out;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    // --- Business: Classify U/D, submit, and stop ---

    private boolean isUser(String s){ return s!=null && s.length()>=2 && (s.charAt(0)=='U'||s.charAt(0)=='u'); }
    private boolean isDevice(String s){ return s!=null && s.length()>=2 && (s.charAt(0)=='D'||s.charAt(0)=='d'); }

    private void handleScannedText(String raw) {
        if (raw == null) return;
        String text = raw.trim();
        if (text.isEmpty()) return;

        boolean changed = false;
        if (isUser(text)) {
            if (!text.equals(userCode)) {
                userCode = text;
                changed = true;
            }
        } else if (isDevice(text)) {
            if (!text.equals(deviceCode)) {
                deviceCode = text;
                changed = true;
            }
        } else {
            return; // Invalid prefix
        }

        if (changed) {
            runOnUiThread(() -> {
                tvResult.setText(
                        "ユーザー: " + (userCode==null?"—":userCode) + " | デバイス: " + (deviceCode==null?"—":deviceCode)
                );
            });
        }

        // Both codes are ready, so navigate to the next activity
        maybeNavigateToResult();
    }

    private void maybeNavigateToResult() {
        if (userCode != null && deviceCode != null) {
            scanningEnabled = false; // Stop scanning to avoid double-triggers

            // Create an Intent to start the next activity
            Intent intent = new Intent(this, ReturnResultActivity.class);
            // Pass the scanned codes to the next activity
            intent.putExtra("USER_CODE", userCode);
            intent.putExtra("DEVICE_CODE", deviceCode);
            startActivity(intent);
            finish(); // Finish this activity so the user cannot go back to it
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
