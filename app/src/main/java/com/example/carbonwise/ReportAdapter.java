package com.example.carbonwise;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;
import java.util.Locale;

public class ReportAdapter extends RecyclerView.Adapter<ReportAdapter.ReportViewHolder> {

    public interface OnReportActionListener {
        void onClick(SavedReport report);
        void onDelete(SavedReport report);
    }

    private final List<SavedReport> reportList;
    private final OnReportActionListener listener;

    public ReportAdapter(List<SavedReport> reportList, OnReportActionListener listener) {
        this.reportList = reportList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ReportViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_report, parent, false);
        return new ReportViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ReportViewHolder holder, int position) {
        SavedReport report = reportList.get(position);
        holder.tvTitle.setText(report.getTitle());
        holder.tvDate.setText("Generated: " + report.getDateGenerated());
        holder.tvDetails.setText(String.format(Locale.getDefault(), "Total: %.1f kg CO₂ | Daily Avg: %.1f kg", report.getTotalCo2(), report.getDailyAvg()));

        holder.itemView.setOnClickListener(v -> listener.onClick(report));
        holder.btnDelete.setOnClickListener(v -> listener.onDelete(report));
    }

    @Override
    public int getItemCount() {
        return reportList.size();
    }

    static class ReportViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitle, tvDate, tvDetails;
        ImageButton btnDelete;

        public ReportViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTitle = itemView.findViewById(R.id.tvReportTitle);
            tvDate = itemView.findViewById(R.id.tvReportDate);
            tvDetails = itemView.findViewById(R.id.tvReportDetails);
            btnDelete = itemView.findViewById(R.id.btnDeleteReport);
        }
    }
}