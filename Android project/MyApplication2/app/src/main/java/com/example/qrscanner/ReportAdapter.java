package com.example.qrscanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import com.google.android.material.card.MaterialCardView; // これをインポート

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    private List<Report> reportList;

    public ReportAdapter(List<Report> reportList) {
        this.reportList = reportList;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        Report report = reportList.get(position);

        // --- 時間のフォーマット処理 ---
        String rawTime = report.getTime();
        String formattedTime = rawTime;
        if (rawTime != null && rawTime.contains(".")) {
            formattedTime = rawTime.substring(0, rawTime.indexOf('.'));
        }

        // --- データを4つのTextViewにそれぞれセット ---
        holder.reportTime.setText(formattedTime);
        holder.reportId.setText("ID: " + report.getId());
        holder.reportDeviceName.setText("Device: " + report.getName()); // ★追加
        holder.reportContent.setText(report.getReport());

        // ↓↓↓ ここに背景色を変更するコードを追加します ↓↓↓
        String id = report.getId();
        if (id != null) {
            if (id.startsWith("U")) {
                // IDがUで始まる場合、カードの背景をユーザー用の色に設定
                ((MaterialCardView) holder.itemView).setCardBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.report_user_background)
                );
            } else {
                // それ以外（Dで始まるなど）の場合は、デフォルトの色（白）に設定
                ((MaterialCardView) holder.itemView).setCardBackgroundColor(
                        ContextCompat.getColor(holder.itemView.getContext(), R.color.report_device_background)
                );
            }
        }

        // --- IDに応じてデバイス名/ユーザー名の表示を切り替え ---
        String namePrefix;
        if (id != null && id.startsWith("U")) {
            namePrefix = "User: ";
        } else {
            namePrefix = "Device: ";
        }
        holder.reportDeviceName.setText(namePrefix + report.getName());
    }

    @Override
    public int getItemCount() {
        return reportList != null ? reportList.size() : 0;
    }

    // --- ViewHolderを修正 ---
    public static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView reportTime;
        TextView reportId;
        TextView reportDeviceName; // ★追加
        TextView reportContent;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            reportTime = itemView.findViewById(R.id.textViewReportTime);
            reportId = itemView.findViewById(R.id.textViewReportId);
            reportDeviceName = itemView.findViewById(R.id.textViewReportDeviceName); // ★追加
            reportContent = itemView.findViewById(R.id.textViewReportContent);
        }
    }
}