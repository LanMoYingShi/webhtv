package com.fongmi.android.tv.setting;

import android.Manifest;
import android.content.pm.ApplicationInfo;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;

import androidx.core.content.ContextCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbMatchCache;
import com.fongmi.android.tv.utils.WebViewUtil;
import com.github.catvod.crawler.DebugLogStore;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Prefers;

public class Setting {

    public static final int DETAIL_OPEN_FUSION = 0;
    public static final int DETAIL_OPEN_ENHANCED = 1;
    public static final int DETAIL_OPEN_DIRECT = 2;
    public static final int DETAIL_OPEN_CINEMA = 3;

    public static String getDoh() {
        return Prefers.getString("doh");
    }

    public static void putDoh(String doh) {
        Prefers.put("doh", doh);
    }

    public static String getKeyword() {
        return Prefers.getString("keyword");
    }

    public static void putKeyword(String keyword) {
        Prefers.put("keyword", keyword);
    }

    public static String getHot() {
        return Prefers.getString("hot");
    }

    public static void putHot(String hot) {
        Prefers.put("hot", hot);
    }

    public static String getUa() {
        return Prefers.getString("ua");
    }

    public static void putUa(String ua) {
        Prefers.put("ua", ua);
    }

    public static int getWall() {
        return Prefers.getInt("wall", 1);
    }

    public static void putWall(int wall) {
        Prefers.put("wall", wall);
    }

    public static int getWallType() {
        return Prefers.getInt("wall_type", 0);
    }

    public static void putWallType(int type) {
        Prefers.put("wall_type", type);
    }

    public static int getReset() {
        return Prefers.getInt("reset", 0);
    }

    public static void putReset(int reset) {
        Prefers.put("reset", reset);
    }

    public static int getSiteMode() {
        return Prefers.getInt("site_mode", 1);
    }

    public static void putSiteMode(int mode) {
        Prefers.put("site_mode", mode);
    }

    public static int getSyncMode() {
        return Prefers.getInt("sync_mode");
    }

    public static void putSyncMode(int mode) {
        Prefers.put("sync_mode", mode);
    }

    public static String getSyncPaths() {
        return Prefers.getString("sync_paths", "TV\nTVBox\nTVData");
    }

    public static void putSyncPaths(String paths) {
        Prefers.put("sync_paths", paths);
    }

    public static boolean isIncognito() {
        return Prefers.getBoolean("incognito");
    }

    public static void putIncognito(boolean incognito) {
        Prefers.put("incognito", incognito);
    }

    public static boolean isHomeHistory() {
        return Prefers.getBoolean("home_history", true);
    }

    public static void putHomeHistory(boolean homeHistory) {
        Prefers.put("home_history", homeHistory);
    }

    public static boolean isDriveCheck() {
        return Prefers.getBoolean("drive_check", true);
    }

    public static void putDriveCheck(boolean driveCheck) {
        Prefers.put("drive_check", driveCheck);
    }

    public static int getSearchThread() {
        return clampSearchThread(Prefers.getInt("search_thread", 10));
    }

    public static void putSearchThread(int thread) {
        Prefers.put("search_thread", clampSearchThread(thread));
    }

    public static int getSearchUi() {
        return Prefers.getInt("search_ui", 1) == 0 ? 0 : 1;
    }

    public static void putSearchUi(int ui) {
        Prefers.put("search_ui", ui == 0 ? 0 : 1);
    }

    public static int getSearchColumn() {
        return clampSearchColumn(Prefers.getInt("search_column", 0));
    }

    public static void putSearchColumn(int column) {
        Prefers.put("search_column", clampSearchColumn(column));
    }

    private static int clampSearchThread(int thread) {
        return Math.max(1, Math.min(thread, 32));
    }

    private static int clampSearchColumn(int column) {
        return column == 1 ? 1 : 0;
    }

    public static boolean isDebugLog() {
        return DebugLogStore.isEnabled();
    }

    public static void putDebugLog(boolean debugLog) {
        DebugLogStore.setEnabled(debugLog);
        if (debugLog) logDebugEnvironment("enable");
    }

    public static void logDebugEnvironment(String reason) {
        boolean hardwareAccelerated = (App.get().getApplicationInfo().flags & ApplicationInfo.FLAG_HARDWARE_ACCELERATED) != 0;
        SpiderDebug.log("env", "reason=%s app=%s(%s) mode=%s abi=%s debug=%s hardware=%s android=%s sdk=%s incremental=%s manufacturer=%s brand=%s model=%s device=%s product=%s supportedAbis=%s",
                reason,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                BuildConfig.FLAVOR_mode,
                BuildConfig.FLAVOR_abi,
                BuildConfig.DEBUG,
                hardwareAccelerated,
                Build.VERSION.RELEASE,
                Build.VERSION.SDK_INT,
                Build.VERSION.INCREMENTAL,
                Build.MANUFACTURER,
                Build.BRAND,
                Build.MODEL,
                Build.DEVICE,
                Build.PRODUCT,
                String.join(",", Build.SUPPORTED_ABIS));
        WebViewUtil.logProvider("debug-env");
    }

    public static boolean isShellProxy() {
        return Prefers.getBoolean("shell_proxy");
    }

    public static void putShellProxy(boolean shellProxy) {
        Prefers.put("shell_proxy", shellProxy);
        ProxySetting.apply();
    }

    public static String getShellProxyRules() {
        return Prefers.getString("shell_proxy_rules");
    }

    public static void putShellProxyRules(String rules) {
        Prefers.put("shell_proxy_rules", rules);
        ProxySetting.apply();
    }

    public static void putShellProxyConfig(String url, String rules) {
        Prefers.put("shell_proxy_url", url);
        Prefers.put("shell_proxy_rules", rules);
        Prefers.put("shell_proxy_hosts", "*");
        ProxySetting.apply();
    }

    public static String getShellProxyUrl() {
        return Prefers.getString("shell_proxy_url");
    }

    public static void putShellProxyUrl(String url) {
        Prefers.put("shell_proxy_url", url);
        ProxySetting.apply();
    }

    public static String getShellProxyHosts() {
        return Prefers.getString("shell_proxy_hosts", "*");
    }

    public static void putShellProxyHosts(String hosts) {
        Prefers.put("shell_proxy_hosts", hosts);
        ProxySetting.apply();
    }

    public static String getTmdbConfig() {
        return Prefers.getString("tmdb_config");
    }

    public static void putTmdbConfig(String value) {
        Prefers.put("tmdb_config", value);
    }

    public static String getAudioConfig() {
        return Prefers.getString("audio_config");
    }

    public static void putAudioConfig(String value) {
        Prefers.put("audio_config", value);
    }

    public static boolean isAudioSiteEnabled(String key, String name) {
        return AudioConfig.objectFrom(getAudioConfig()).isSiteEnabled(key, name);
    }

    public static String getShortDramaConfig() {
        return Prefers.getString("short_drama_config");
    }

    public static void putShortDramaConfig(String value) {
        Prefers.put("short_drama_config", value);
    }

    public static boolean isShortDramaSiteEnabled(String key, String name) {
        return ShortDramaConfig.objectFrom(getShortDramaConfig()).isSiteEnabled(key, name);
    }

    public static TmdbMatchCache getTmdbMatchCache() {
        return TmdbMatchCache.objectFrom(Prefers.getString("tmdb_match_cache"));
    }

    public static void putTmdbMatchCache(TmdbMatchCache cache) {
        Prefers.put("tmdb_match_cache", App.gson().toJson(cache));
    }

    public static boolean isTmdbReady() {
        return TmdbConfig.objectFrom(getTmdbConfig()).isReady();
    }

    public static boolean isTmdbSiteEnabled(String key, String name) {
        return TmdbConfig.objectFrom(getTmdbConfig()).isSiteEnabled(key, name);
    }

    public static int getDetailOpenMode() {
        int mode;
        if (Prefers.getPrefers().contains("detail_open_mode")) {
            mode = clampDetailOpenMode(Prefers.getInt("detail_open_mode", DETAIL_OPEN_ENHANCED));
        } else if (Prefers.getPrefers().contains("search_detail_page")) {
            mode = Prefers.getBoolean("search_detail_page") ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT;
        } else {
            mode = isTmdbReady() ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT;
        }
        return isTmdbMode(mode) && !isTmdbReady() ? DETAIL_OPEN_DIRECT : mode;
    }

    public static void putDetailOpenMode(int mode) {
        Prefers.put("detail_open_mode", clampDetailOpenMode(mode));
    }

    public static int nextDetailOpenMode() {
        return (getDetailOpenMode() + 1) % 4;
    }

    public static boolean isSearchDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_ENHANCED;
    }

    public static boolean isCinemaDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_CINEMA;
    }

    public static boolean isFusionDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_FUSION;
    }

    public static boolean isDirectDetailPage() {
        return getDetailOpenMode() == DETAIL_OPEN_DIRECT;
    }

    public static void putSearchDetailPage(boolean enabled) {
        putDetailOpenMode(enabled ? DETAIL_OPEN_ENHANCED : DETAIL_OPEN_DIRECT);
    }

    private static int clampDetailOpenMode(int mode) {
        if (mode < DETAIL_OPEN_FUSION || mode > DETAIL_OPEN_CINEMA) return DETAIL_OPEN_ENHANCED;
        return mode;
    }

    public static boolean isTmdbMode(int mode) {
        return mode == DETAIL_OPEN_FUSION || mode == DETAIL_OPEN_ENHANCED || mode == DETAIL_OPEN_CINEMA;
    }

    public static int getTmdbDetailTheme() {
        return Prefers.getInt("tmdb_detail_theme", 0);
    }

    public static void putTmdbDetailTheme(int theme) {
        Prefers.put("tmdb_detail_theme", Math.max(0, Math.min(theme, 2)));
    }

    public static boolean getUpdate() {
        return Prefers.getBoolean("update", true);
    }

    public static void putUpdate(boolean update) {
        Prefers.put("update", update);
    }

    public static boolean isAdblock() {
        return Prefers.getBoolean("adblock", true);
    }

    public static void putAdblock(boolean adblock) {
        Prefers.put("adblock", adblock);
    }

    public static boolean isZhuyin() {
        return Prefers.getBoolean("zhuyin");
    }

    public static void putZhuyin(boolean zhuyin) {
        Prefers.put("zhuyin", zhuyin);
    }

    public static int getThemeColor() {
        return Prefers.getInt("theme_color", -1);
    }

    public static void putThemeColor(int color) {
        Prefers.put("theme_color", color);
    }

    public static int getWallColor() {
        return Prefers.getInt("wall_color", 0);
    }

    public static void putWallColor(int color) {
        Prefers.put("wall_color", color);
    }

    public static int getDynamicColor() {
        int color = getThemeColor();
        if (color == -1) return 0;
        return color != 0 ? color : getWallColor();
    }

    public static boolean hasFileAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return Environment.isExternalStorageManager();
        return ContextCompat.checkSelfPermission(App.get(), Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED && ContextCompat.checkSelfPermission(App.get(), Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean hasFileManager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false;
        return new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + App.get().getPackageName())).resolveActivity(App.get().getPackageManager()) != null || new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).resolveActivity(App.get().getPackageManager()) != null;
    }
}
