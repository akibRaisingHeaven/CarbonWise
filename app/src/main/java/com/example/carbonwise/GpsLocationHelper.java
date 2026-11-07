package com.example.carbonwise;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

/**
 * Gets the device's current position (GPS, with network location as a helper) using the
 * built-in Android LocationManager, so no extra library is needed.
 *
 * All callbacks run on the main thread. The caller is responsible for asking the user for
 * the location permission (see SettingsActivity); this class only checks it.
 */
public class GpsLocationHelper {

    public interface LocationCallback {
        void onLocation(double latitude, double longitude);
        void onError(String reason);
    }

    /** A cached system fix newer than this is good enough for choosing a grid zone. */
    private static final long MAX_CACHED_AGE_MS = 15 * 60 * 1000L;
    /** How long to wait for a fresh fix before giving up. */
    private static final long TIMEOUT_MS = 20 * 1000L;

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    public static void getCurrentLocation(Context context, LocationCallback callback) {
        Context app = context.getApplicationContext();

        if (!hasPermission(app)) {
            callback.onError("location permission not granted");
            return;
        }

        LocationManager lm = (LocationManager) app.getSystemService(Context.LOCATION_SERVICE);
        if (lm == null) {
            callback.onError("location service unavailable");
            return;
        }

        final List<String> providers = new ArrayList<>();
        boolean fineGranted = ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (fineGranted && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        if (providers.isEmpty()) {
            callback.onError("turn on Location in system settings");
            return;
        }

        // 1) A recent position already known to the system is instant and accurate enough.
        Location lastKnown = bestLastKnown(lm, providers);
        if (lastKnown != null && System.currentTimeMillis() - lastKnown.getTime() < MAX_CACHED_AGE_MS) {
            callback.onLocation(lastKnown.getLatitude(), lastKnown.getLongitude());
            return;
        }

        // 2) Otherwise ask for a fresh fix. The first provider to answer wins.
        final Handler handler = new Handler(Looper.getMainLooper());
        final boolean[] finished = {false};
        final LocationListener[] listenerRef = new LocationListener[1];

        final Runnable stop = () -> {
            try {
                lm.removeUpdates(listenerRef[0]);
            } catch (SecurityException ignored) {
            }
        };

        listenerRef[0] = new LocationListener() {
            @Override
            public void onLocationChanged(@NonNull Location location) {
                if (finished[0]) return;
                finished[0] = true;
                handler.removeCallbacksAndMessages(null);
                stop.run();
                callback.onLocation(location.getLatitude(), location.getLongitude());
            }

            @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
            @Override public void onProviderEnabled(@NonNull String provider) {}
            @Override public void onProviderDisabled(@NonNull String provider) {}
        };

        try {
            for (String provider : providers) {
                lm.requestLocationUpdates(provider, 0, 0, listenerRef[0], Looper.getMainLooper());
            }
        } catch (SecurityException e) {
            callback.onError("location permission not granted");
            return;
        }

        handler.postDelayed(() -> {
            if (finished[0]) return;
            finished[0] = true;
            stop.run();
            // Timed out: an older system position is still better than nothing.
            if (lastKnown != null) {
                callback.onLocation(lastKnown.getLatitude(), lastKnown.getLongitude());
            } else {
                callback.onError("could not get a GPS fix (try going outdoors)");
            }
        }, TIMEOUT_MS);
    }

    private static Location bestLastKnown(LocationManager lm, List<String> providers) {
        Location best = null;
        for (String provider : providers) {
            try {
                Location loc = lm.getLastKnownLocation(provider);
                if (loc != null && (best == null || loc.getTime() > best.getTime())) {
                    best = loc;
                }
            } catch (SecurityException ignored) {
            }
        }
        return best;
    }
}
