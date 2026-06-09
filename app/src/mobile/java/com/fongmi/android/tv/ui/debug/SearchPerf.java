package com.fongmi.android.tv.ui.debug;

import android.os.SystemClock;
import android.util.Log;

import com.fongmi.android.tv.BuildConfig;
import com.github.catvod.crawler.DebugLogStore;

import java.util.Locale;

public final class SearchPerf {

    private static final String TAG = "SearchPerf";
    private static final long SUMMARY_INTERVAL = 1000;

    private static long lastSummary;
    private static int creates;
    private static int binds;
    private static int payloadBinds;
    private static int recycles;
    private static int recycleFails;
    private static int gridImages;
    private static int listImages;
    private static int holds;
    private static int loadImages;

    private SearchPerf() {
    }

    public static boolean enabled() {
        return BuildConfig.DEBUG || DebugLogStore.isEnabled();
    }

    public static long now() {
        return enabled() ? SystemClock.uptimeMillis() : 0;
    }

    public static void log(String message, Object... args) {
        if (!enabled()) return;
        Log.d(TAG, format(message, args));
    }

    public static void slow(String label, long start, long thresholdMs, String message, Object... args) {
        if (!enabled() || start <= 0) return;
        long cost = SystemClock.uptimeMillis() - start;
        if (cost < thresholdMs) return;
        Log.d(TAG, label + " cost=" + cost + "ms " + format(message, args));
    }

    public static void create(int viewType, int columnCount, int itemCount) {
        if (!enabled()) return;
        creates++;
        flushSummary(false, viewType, columnCount, itemCount);
    }

    public static void bind(boolean payload, int position, int itemCount, int columnCount) {
        if (!enabled()) return;
        if (payload) payloadBinds++;
        else binds++;
        flushSummary(false, -1, columnCount, itemCount);
    }

    public static void image(boolean grid, boolean load, int width, int height, int itemCount) {
        if (!enabled()) return;
        if (grid) gridImages++;
        else listImages++;
        if (load) loadImages++;
        else holds++;
        flushSummary(false, -1, -1, itemCount);
    }

    public static void recycle(boolean failed, int itemCount) {
        if (!enabled()) return;
        if (failed) recycleFails++;
        else recycles++;
        flushSummary(false, -1, -1, itemCount);
    }

    public static void flushSummary() {
        flushSummary(true, -1, -1, -1);
    }

    private static void flushSummary(boolean force, int viewType, int columnCount, int itemCount) {
        long now = SystemClock.uptimeMillis();
        if (!force && now - lastSummary < SUMMARY_INTERVAL) return;
        if (creates == 0 && binds == 0 && payloadBinds == 0 && recycles == 0 && recycleFails == 0 && gridImages == 0 && listImages == 0 && holds == 0 && loadImages == 0) {
            lastSummary = now;
            return;
        }
        Log.d(TAG, String.format(Locale.US, "adapter summary create=%d bind=%d payload=%d recycle=%d recycleFail=%d gridImg=%d listImg=%d load=%d hold=%d viewType=%d columns=%d items=%d", creates, binds, payloadBinds, recycles, recycleFails, gridImages, listImages, loadImages, holds, viewType, columnCount, itemCount));
        creates = 0;
        binds = 0;
        payloadBinds = 0;
        recycles = 0;
        recycleFails = 0;
        gridImages = 0;
        listImages = 0;
        holds = 0;
        loadImages = 0;
        lastSummary = now;
    }

    private static String format(String message, Object... args) {
        try {
            return args == null || args.length == 0 ? message : String.format(Locale.US, message, args);
        } catch (Throwable e) {
            return message;
        }
    }
}
