package com.example.qrscanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.chip.Chip; // Chipをインポート
import java.util.List;

public class DeviceAdapter extends RecyclerView.Adapter<DeviceAdapter.DeviceViewHolder> {

    private List<Device> deviceList;

    public DeviceAdapter(List<Device> deviceList) {
        this.deviceList = deviceList;
    }

    @NonNull
    @Override
    public DeviceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_device, parent, false);
        return new DeviceViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull DeviceViewHolder holder, int position) {
        Device device = deviceList.get(position);

        holder.deviceName.setText(device.getName());
        holder.currUser.setText("Current User: " + (device.getCurr_user() != null ? device.getCurr_user() : "None"));
        holder.location.setText("Location: " + device.getLocation());

        // Chipの表示を更新
        if (device.getStatus() == 1) { // 貸出中
            holder.chipStatus.setText("貸出中");
            holder.chipStatus.setChipBackgroundColorResource(R.color.checked_out_gray);
        } else { // 利用可能
            holder.chipStatus.setText("利用可能");
            holder.chipStatus.setChipBackgroundColorResource(R.color.available_green);
        }
    }

    @Override
    public int getItemCount() {
        return deviceList != null ? deviceList.size() : 0;
    }

    // ViewHolderクラスを新しいレイアウトに合わせて修正
    public static class DeviceViewHolder extends RecyclerView.ViewHolder {
        TextView deviceName, currUser, location;
        Chip chipStatus; // TextView status の代わりに Chip を使う
        // hisUser と userInfoLayout は省略されているため、ここでは不要

        public DeviceViewHolder(@NonNull View itemView) {
            super(itemView);
            deviceName = itemView.findViewById(R.id.textViewDeviceName);
            currUser = itemView.findViewById(R.id.textViewCurrUser);
            location = itemView.findViewById(R.id.textViewLocation);
            chipStatus = itemView.findViewById(R.id.chipStatus); // IDをchipStatusに変更
        }
    }
}