package com.example.carbonwise;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Saved carbon reports, stored in Firebase Realtime Database under
 * users/{uid}/saved_reports/{reportId}. Everything else in the app stays in Firestore.
 */
public class ReportsRepository {

    public interface ReportsCallback {
        void onReportsLoaded(List<SavedReport> reports);
        void onError(String error);
    }

    public interface OperationCallback {
        void onSuccess(String message);
        void onError(String error);
    }

    /** Handle for a live listener. Call remove() when the screen goes away. */
    public interface Subscription {
        void remove();
    }

    private final String userId;

    public ReportsRepository() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        this.userId = user != null ? user.getUid() : "";
    }

    /** Live list of the user's reports, newest first. The callback fires again on every change. */
    public Subscription listenToReports(ReportsCallback callback) {
        final DatabaseReference ref = referenceOrNull(callback::onError);
        if (ref == null) return () -> { };

        ValueEventListener listener = new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot snapshot) {
                List<SavedReport> reports = new ArrayList<>();
                for (DataSnapshot child : snapshot.getChildren()) {
                    SavedReport report;
                    try {
                        report = child.getValue(SavedReport.class);
                    } catch (RuntimeException malformed) {
                        continue; // skip an entry that does not look like a report
                    }
                    if (report == null) continue;
                    if (report.getId() == null) report.setId(child.getKey());
                    reports.add(report);
                }
                // Newest first (a Realtime Database query can only sort ascending)
                Collections.sort(reports, (a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
                callback.onReportsLoaded(reports);
            }

            @Override
            public void onCancelled(DatabaseError error) {
                callback.onError(error.getMessage());
            }
        };

        ref.addValueEventListener(listener);
        return () -> ref.removeEventListener(listener);
    }

    public void saveGeneratedReport(SavedReport report, OperationCallback callback) {
        DatabaseReference ref = referenceOrNull(callback::onError);
        if (ref == null) return;

        ref.child(report.getId())
                .setValue(report)
                .addOnSuccessListener(aVoid -> callback.onSuccess("Report generated & saved!"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    public void deleteReport(String reportId, OperationCallback callback) {
        DatabaseReference ref = referenceOrNull(callback::onError);
        if (ref == null) return;

        ref.child(reportId)
                .removeValue()
                .addOnSuccessListener(aVoid -> callback.onSuccess("Report deleted"))
                .addOnFailureListener(e -> callback.onError(e.getMessage()));
    }

    /** The user's reports node, or null after reporting why it is not available. */
    private DatabaseReference referenceOrNull(ErrorSink onError) {
        if (userId.isEmpty()) {
            onError.report("Not signed in");
            return null;
        }
        try {
            return RealtimeDb.userReports(userId);
        } catch (IllegalStateException notConfigured) {
            onError.report(notConfigured.getMessage());
            return null;
        }
    }

    private interface ErrorSink {
        void report(String message);
    }
}
