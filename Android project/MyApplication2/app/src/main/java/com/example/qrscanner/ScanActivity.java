package com.example.qrscanner;

import android.Manifest;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.graphics.ImageDecoder;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
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
import com.google.zxing.RGBLuminanceSource;
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
 * 持续扫码 + 多码解析 + U/D 分类并发起HTTP请求（返回即停、缓存3秒）
 */
public class ScanActivity extends AppCompatActivity {

    private PreviewView previewView;
    private OverlayView overlay;
    private TextView tvResult;

    private ExecutorService cameraExecutor;
    private ProcessCameraProvider cameraProvider;
    private Camera camera;

    // 解码节流
    private volatile boolean isDecoding = false;
    private volatile boolean scanningEnabled = true;

    // 识别后短暂冷却，避免同一帧/同一对象反复触发 UI
    private long lastHitTs = 0L;
    private static final long HIT_COOLDOWN_MS = 800;

    // —— 业务：U/D 两类扫码拼一次请求 ——
    @Nullable private String userCode = null;    // UCVJWTF
    @Nullable private String deviceCode = null;  // D3K050K

    // 3 秒缓存清空
    private final android.os.Handler mainHandler =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static final long CACHE_TTL_MS = 3000L;
    private final Runnable clearCacheRunnable = () -> {
        userCode = null;
        deviceCode = null;
        runOnUiThread(() -> tvResult.setText("キャッシュがクリアされました。スキャンを待機中…"));
    };

    // 提交去抖
    @Nullable private String lastSubmittedKey = null;
    private long lastSubmitTs = 0L;
    private static final long SUBMIT_COOLDOWN_MS = 1200L;

    // 服务器配置
    private static final String BASE_HOST = "133.2.130.204";
    private static final int    BASE_PORT = 8000;

    // 保存分析用例，便于 stop
    private ImageAnalysis analysisRef;

    // ZXing 解码器（单实例复用）
    private final MultiFormatReader baseReader = new MultiFormatReader();
    private final MultipleBarcodeReader multiReader = new GenericMultipleBarcodeReader(baseReader);

    // 相机权限
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else finish();
            });

    // 可选：系统相册选择器
    private final ActivityResultLauncher<PickVisualMediaRequest> pickImage =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(), uri -> {
                if (uri != null) decodeFromGallery(uri);
            });

    // HTTP 客户端
    private final okhttp3.OkHttpClient http = new okhttp3.OkHttpClient();

    @Override protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan);

        previewView = findViewById(R.id.previewView);
        overlay     = findViewById(R.id.overlay);
        tvResult    = findViewById(R.id.tvResult);

        cameraExecutor = Executors.newSingleThreadExecutor();

        // ZXing hints：只扫二维码更快
        Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
        hints.put(DecodeHintType.POSSIBLE_FORMATS, Arrays.asList(BarcodeFormat.QR_CODE));
        hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
        baseReader.setHints(hints);

        cameraPermissionLauncher.launch(Manifest.permission.CAMERA);

        // 点击切换手电筒
        previewView.setOnClickListener(v -> {
            if (camera != null) {
                boolean newTorch = camera.getCameraInfo().getTorchState().getValue() !=
                        androidx.camera.core.TorchState.ON;
                camera.getCameraControl().enableTorch(newTorch);
            }
        });

        // 如需相册选择：取消注释
        // pickImage.launch(new PickVisualMediaRequest.Builder()
        //         .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
        //         .build());
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
                // 仅取 Y 平面
                ImageProxy.PlaneProxy yPlane = image.getPlanes()[0];
                ByteBuffer yBuf = yPlane.getBuffer();
                byte[] yData = new byte[yBuf.remaining()];
                yBuf.get(yData);

                // ROI 映射
                Rect roi = mapViewRectToImageRect(overlay.getScanRect(), previewView, image);

                // ZXing 输入
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

                        // 每次识别到任意码：启动/刷新3秒倒计时
                        mainHandler.removeCallbacks(clearCacheRunnable);
                        mainHandler.postDelayed(clearCacheRunnable, CACHE_TTL_MS);

                        // 去重显示 & 分类缓存
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
                            runOnUiThread(() -> tvResult.setText("Scanned:\n" + all));
                        }
                    }
                } catch (NotFoundException ignore) {
                    // 本帧无码
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

    /** Overlay(视图坐标) -> 分析帧(传感器坐标) */
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
                out.set(clamp((int) (srcH - (y + h)), 0, imgW),
                        clamp((int) x, 0, imgH),
                        clamp((int) (srcH - y), 0, imgW),
                        clamp((int) (x + w), 0, imgH));
                break;
        }
        if (out.width() <= 0 || out.height() <= 0) return new Rect(0, 0, imgW, imgH);
        return out;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(v, max));
    }

    // ———————————— 业务：U/D 分类、提交与停止 ————————————

    private boolean isUser(String s){ return s!=null && s.length()>=2 && (s.charAt(0)=='U'||s.charAt(0)=='u'); }
    private boolean isDevice(String s){ return s!=null && s.length()>=2 && (s.charAt(0)=='D'||s.charAt(0)=='d'); }
    private String stripPrefix(String s){ return (s==null||s.length()<2) ? "" : s.substring(1); }

    private void handleScannedText(String raw) {
        if (raw == null) return;
        String text = raw.trim();
        if (text.isEmpty()) return;

        boolean changed = false;
        if (isUser(text)) {
            if (!text.equals(userCode)) { userCode = text; changed = true; }
        } else if (isDevice(text)) {
            if (!text.equals(deviceCode)) { deviceCode = text; changed = true; }
        } else {
            return; // 非法前缀
        }

        if (changed) {
            runOnUiThread(() -> tvResult.setText(
                    "User: " + (userCode==null?"—":userCode) + " | Device: " + (deviceCode==null?"—":deviceCode)
            ));
        }

        // 只有两者同时具备时才尝试提交
        maybeSubmitWhenBothReady();
    }

    private void maybeSubmitWhenBothReady() {
        if (userCode == null || deviceCode == null) return;

        // 两者齐了：立即提交，并停止3秒清空计时
        mainHandler.removeCallbacks(clearCacheRunnable);

        String key = userCode + "|" + deviceCode;
        long now = SystemClock.uptimeMillis();
        if (key.equals(lastSubmittedKey) && (now - lastSubmitTs) < SUBMIT_COOLDOWN_MS) return;
        lastSubmittedKey = key;
        lastSubmitTs = now;

        String user = stripPrefix(userCode);     // CVJWTF
        String device = stripPrefix(deviceCode); // 3K050K
        httpGetDeviceUser(device, user);
    }

    // GET http://133.2.130.204:8000/?device=3K050K&user=CVJWTF
    private void httpGetDeviceUser(String device, String user) {
        String dev = urlEncode(device);
        String usr = urlEncode(user);
        String url = "http://" + BASE_HOST + ":" + BASE_PORT + "/?device=" + dev + "&user=" + usr;

        okhttp3.Request req = new okhttp3.Request.Builder().url(url).get().build();
        http.newCall(req).enqueue(new okhttp3.Callback() {
            @Override public void onFailure(okhttp3.Call call, java.io.IOException e) {
                runOnUiThread(() -> tvResult.setText("HTTP Failed: " + e.getMessage()));
                // 如果失败也要停，可以在这里调用 stopScanning();
            }
            @Override public void onResponse(okhttp3.Call call, okhttp3.Response resp) throws java.io.IOException {
                String body = resp.body() != null ? resp.body().string() : "";
                runOnUiThread(() -> tvResult.setText("HTTP " + resp.code() + ":\n" + body));

                // 收到响应就停止扫描；若只想在 body == "ok" 时停，请改为判定后再调用
                stopScanning();
            }
        });
    }

    private void stopScanning() {
        scanningEnabled = false;
        mainHandler.removeCallbacks(clearCacheRunnable);
        userCode = null; deviceCode = null;

        if (analysisRef != null) analysisRef.clearAnalyzer();
        // 如需彻底解绑：
        // if (cameraProvider != null) cameraProvider.unbindAll();
    }

    private String urlEncode(String s) {
        try { return java.net.URLEncoder.encode(s, "UTF-8"); }
        catch (Exception e) { return s; }
    }

    // ———————————— 相册图片解码（可选） ————————————
    private void decodeFromGallery(Uri uri) {
        try {
            Bitmap bmp;
            if (Build.VERSION.SDK_INT >= 28) {
                ImageDecoder.Source src = ImageDecoder.createSource(getContentResolver(), uri);
                bmp = ImageDecoder.decodeBitmap(src);
            } else {
                bmp = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
            }
            int w = bmp.getWidth(), h = bmp.getHeight();
            int[] intArray = new int[w * h];
            bmp.getPixels(intArray, 0, w, 0, 0, w, h);

            LuminanceSource source = new RGBLuminanceSource(w, h, intArray);
            BinaryBitmap binary = new BinaryBitmap(new HybridBinarizer(source));
            Result[] results = multiReader.decodeMultiple(binary);

            StringBuilder sb = new StringBuilder();
            for (Result r : results) {
                String t = r.getText();
                if (t == null) continue;
                sb.append(t).append("\n");
                handleScannedText(t);
            }
            String all = sb.toString().trim();
            runOnUiThread(() -> tvResult.setText(all.isEmpty() ? "No code in photo" : all));
        } catch (NotFoundException nf) {
            runOnUiThread(() -> tvResult.setText("No code in photo"));
        } catch (Exception e) {
            runOnUiThread(() -> tvResult.setText("Decode failed: " + e.getMessage()));
        } finally {
            baseReader.reset();
        }
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
    }
}
