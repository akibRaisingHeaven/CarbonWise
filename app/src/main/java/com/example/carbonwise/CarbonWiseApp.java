package com.example.carbonwise;

import android.app.Application;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

/**
 * Keeps the home-screen widget live: a ContentObserver on the carbon content provider redraws
 * the widget whenever new numbers are published (by the dashboard or the live tracker service).
 * An AppWidgetProvider only lives for a moment, so the observer sits here, in the Application,
 * which stays alive as long as the app's process does.
 */
public class CarbonWiseApp extends Application {

    private static final long DEBOUNCE_MS = 250;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshWidget = () -> CarbonWidgetProvider.refreshAll(CarbonWiseApp.this);

    private final ContentObserver observer = new ContentObserver(handler) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            // publish() notifies two URIs back to back; merge them into one redraw
            handler.removeCallbacks(refreshWidget);
            handler.postDelayed(refreshWidget, DEBOUNCE_MS);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        getContentResolver().registerContentObserver(CarbonContentProvider.BASE_URI, true, observer);
    }
}
