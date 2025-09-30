package com.example.qrscanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DeviceListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private DeviceAdapter deviceAdapter;
    private List<Device> deviceList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FloatingActionButton fabRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);

        recyclerView = findViewById(R.id.deviceRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        deviceAdapter = new DeviceAdapter(deviceList);
        recyclerView.setAdapter(deviceAdapter);

        fabRefresh = findViewById(R.id.fabRefresh);
        fabRefresh.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Toast.makeText(DeviceListActivity.this, "更新しています...", Toast.LENGTH_SHORT).show();
                loadDevicesFromServer();
            }
        });

        loadDevicesFromServer();
    }

    private void loadDevicesFromServer() {
        OkHttpClient client = new OkHttpClient();
        String url = "http://192.168.10.8:8000/get_device";

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                handler.post(() -> {
                    Toast.makeText(DeviceListActivity.this, "データの取得に失敗しました", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    final String responseBody = response.body().string();
                    Gson gson = new Gson();
                    Type listType = new TypeToken<ArrayList<Device>>() {}.getType();
                    final List<Device> loadedDevices = gson.fromJson(responseBody, listType);

                    if (loadedDevices != null) {
                        loadedDevices.sort(new Comparator<Device>() {
                            @Override
                            public int compare(Device d1, Device d2) {
                                // d1.status と d2.status を比較して並べ替える
                                return Integer.compare(d1.getStatus(), d2.getStatus());
                            }
                        });
                    }

                    handler.post(() -> {
                        if (loadedDevices != null) {
                            deviceList.clear();
                            deviceList.addAll(loadedDevices);
                            deviceAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }
}