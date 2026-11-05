package com.example.carbonwise;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;

public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.ViewHolder> {

    private final List<ActivityLog> logList;

    public ActivityLogAdapter(List<ActivityLog> logList) {
        this.logList = logList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_activity_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ActivityLog log = logList.get(position);
        holder.tvTitle.setText(log.getTitle());
        holder.tvCategory.setText(log.getCategory() + " • " + log.getDate());
        holder.tvCo2.setText(String.format("%.1f kg CO₂", log.getCo2Emitted()));
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvCategory, tvCo2;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvActivityTitle);
            tvCategory = itemView.findViewById(R.id.tvActivityCategory);
            tvCo2 = itemView.findViewById(R.id.tvCo2Amount);
        }
    }
}