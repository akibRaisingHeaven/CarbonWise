package com.example.carbonwise;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.Locale;

public class ActivityLogAdapter extends RecyclerView.Adapter<ActivityLogAdapter.ViewHolder> {

    public interface OnLogActionListener {
        void onEdit(ActivityLog log);
        void onDelete(ActivityLog log);
    }

    private final List<ActivityLog> logList;
    private final OnLogActionListener listener;

    public ActivityLogAdapter(List<ActivityLog> logList, OnLogActionListener listener) {
        this.logList = logList;
        this.listener = listener;
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
        holder.tvSubtitle.setText(log.getCategory() + " • " + log.getDate());
        holder.tvCo2.setText(String.format(Locale.getDefault(), "%.1f kg CO₂", log.getCo2Emitted()));

        // Tap item card to trigger Edit / Delete menu
        holder.itemView.setOnClickListener(v -> {
            PopupMenu popup = new PopupMenu(v.getContext(), v);
            popup.getMenu().add("Edit");
            popup.getMenu().add("Delete");
            popup.setOnMenuItemClickListener(item -> {
                if ("Edit".equals(item.getTitle())) {
                    listener.onEdit(log);
                } else if ("Delete".equals(item.getTitle())) {
                    listener.onDelete(log);
                }
                return true;
            });
            popup.show();
        });
    }

    @Override
    public int getItemCount() {
        return logList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvSubtitle, tvCo2;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvLogTitle);
            tvSubtitle = itemView.findViewById(R.id.tvLogSubtitle);
            tvCo2 = itemView.findViewById(R.id.tvLogCo2);
        }
    }
}