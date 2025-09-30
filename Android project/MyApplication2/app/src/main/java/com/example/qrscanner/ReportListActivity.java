package com.example.qrscanner;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

public class ReportListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ReportAdapter reportAdapter;
    private List<Report> reportList = new ArrayList<>();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private FloatingActionButton fabRefresh;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_list);

        // ツールバーの設定
        Toolbar toolbar = findViewById(R.id.topAppBar);
        setSupportActionBar(toolbar);

        // RecyclerViewの設定
        recyclerView = findViewById(R.id.reportRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        reportAdapter = new ReportAdapter(reportList);
        recyclerView.setAdapter(reportAdapter);

        // 更新ボタンの設定
        fabRefresh = findViewById(R.id.fabRefreshReports);
        fabRefresh.setOnClickListener(view -> {
            Toast.makeText(ReportListActivity.this, "レポートを更新しています...", Toast.LENGTH_SHORT).show();
            loadReportsFromServer();
        });

        // 画面起動時にデータをロード
        loadReportsFromServer();
    }

    private void loadReportsFromServer() {
        OkHttpClient client = new OkHttpClient();
        String url = "http://192.168.10.8:8001/get_report";

        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                handler.post(() -> Toast.makeText(ReportListActivity.this, "データの取得に失敗しました", Toast.LENGTH_SHORT).show());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful() && response.body() != null) {
                    final String responseBody = response.body().string();
                    Gson gson = new Gson();
                    Type listType = new TypeToken<ArrayList<Report>>(){}.getType();
                    final List<Report> loadedReports = gson.fromJson(responseBody, listType);

                    // ↓↓↓ ここに並べ替えのコードを追加します ↓↓↓
                    if (loadedReports != null) {
                        loadedReports.sort(new Comparator<Report>() {
                            @Override
                            public int compare(Report r1, Report r2) {
                                // IDの最初の文字を取得
                                char char1 = r1.getId() != null && !r1.getId().isEmpty() ? r1.getId().charAt(0) : ' ';
                                char char2 = r2.getId() != null && !r2.getId().isEmpty() ? r2.getId().charAt(0) : ' ';

                                // Uが先、Dが後になるように比較
                                if (char1 == 'U' && char2 == 'D') {
                                    return -1; // r1を先にする
                                } else if (char1 == 'D' && char2 == 'U') {
                                    return 1; // r2を先にする
                                } else {
                                    return 0; // それ以外は順序を維持
                                }
                            }
                        });
                    }
                    // ↑↑↑ ここまでが追加部分 ↑↑↑

                    handler.post(() -> {
                        if (loadedReports != null) {
                            reportList.clear();
                            reportList.addAll(loadedReports);
                            reportAdapter.notifyDataSetChanged();
                        }
                    });
                }
            }
        });
    }
}