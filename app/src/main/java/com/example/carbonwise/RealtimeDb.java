package com.example.carbonwise;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Single access point for Firebase Realtime Database.
 *
 * Firestore keeps the activity logs, settings and budgets; Realtime Database keeps the generated
 * reports. The database URL comes from google-services.json ("firebase_url") or, when that is
 * missing, from BuildConfig.REALTIME_DB_URL (local.properties key "firebase.database.url").
 */
public final class RealtimeDb {

    private static FirebaseDatabase instance;

    private RealtimeDb() {}

    /**
     * @throws IllegalStateException with a readable message when the database URL is not configured
     */
    public static synchronized FirebaseDatabase get() {
        if (instance == null) {
            try {
                String url = BuildConfig.REALTIME_DB_URL;
                FirebaseDatabase db = (url == null || url.trim().isEmpty())
                        ? FirebaseDatabase.getInstance()
                        : FirebaseDatabase.getInstance(url.trim());

                // Keeps reports readable offline. Must be switched on before the first use.
                try {
                    db.setPersistenceEnabled(true);
                } catch (RuntimeException alreadyInUse) {
                    // Harmless: the instance was already used, so persistence stays as it is
                }
                instance = db;
            } catch (RuntimeException e) {
                throw new IllegalStateException("Realtime Database is not set up. Create it in the Firebase "
                        + "console and add its URL as firebase.database.url in local.properties.", e);
            }
        }
        return instance;
    }

    /** users/{uid}/saved_reports */
    public static DatabaseReference userReports(String userId) {
        return get().getReference("users").child(userId).child("saved_reports");
    }
}
