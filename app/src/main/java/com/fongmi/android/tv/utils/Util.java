package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.text.Html;
import android.text.TextUtils;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.BuildConfig;
import com.fongmi.android.tv.R;
import com.github.catvod.utils.Shell;

import java.net.NetworkInterface;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Util {

    private static final Pattern EPISODE_SEASON = Pattern.compile("[Ss](?:[0-9]{1,2})?[-._\\s]*[Ee]([0-9]{1,3})", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_CHINESE = Pattern.compile("第\\s*([零一二三四五六七八九十百千万两0-9]+)\\s*[集话話章节回期]");
    private static final Pattern EPISODE_TOKEN = Pattern.compile("\\b(?:EP|E)[-._\\s]*([0-9]{1,3})\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_BRACKET = Pattern.compile("[\\[\\]()【】（）]{1,2}([0-9]{1,3})[\\[\\]()【】（）]{1,2}");
    private static final Pattern EPISODE_FILENAME = Pattern.compile("(?<![0-9])\\b([0-9]{1,3})\\.(?:[0-9]{3,4}[pP]|[0-9]{3,4}x[0-9]{3,4}|720|1080|480|HD|SD)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern EPISODE_STANDALONE = Pattern.compile("(?:[\\s\\[\\]()【】（）\\-._]|^)([0-9]{1,3})(?![0-9])");

    public static void toggleFullscreen(Activity activity, boolean fullscreen) {
        if (fullscreen) hideSystemUI(activity);
        else showSystemUI(activity);
    }

    public static void hideSystemUI(Activity activity) {
        hideSystemUI(activity.getWindow());
    }

    public static void hideSystemUI(Window window) {
        WindowInsetsControllerCompat insets = WindowCompat.getInsetsController(window, window.getDecorView());
        insets.setSystemBarsBehavior(WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        insets.hide(WindowInsetsCompat.Type.systemBars());
    }

    public static void showSystemUI(Activity activity) {
        showSystemUI(activity.getWindow());
    }

    public static void showSystemUI(Window window) {
        WindowCompat.getInsetsController(window, window.getDecorView()).show(WindowInsetsCompat.Type.systemBars());
        window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    public static void showKeyboard(View view) {
        if (!view.requestFocus()) return;
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) view.postDelayed(() -> imm.showSoftInput(view, InputMethodManager.SHOW_IMPLICIT), 250);
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) App.get().getSystemService(Context.INPUT_METHOD_SERVICE);
        IBinder windowToken = view.getWindowToken();
        if (imm == null || windowToken == null) return;
        imm.hideSoftInputFromWindow(windowToken, 0);
    }

    public static float getBrightness(Activity activity) {
        try {
            float value = activity.getWindow().getAttributes().screenBrightness;
            if (WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL >= value && value >= WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF) return value;
            return Settings.System.getFloat(activity.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS) / 255;
        } catch (Exception e) {
            return 0.5f;
        }
    }

    public static CharSequence getClipText() {
        ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = manager == null ? null : manager.getPrimaryClip();
        if (clipData == null || clipData.getItemCount() == 0) return "";
        return clipData.getItemAt(0).getText();
    }

    public static void copy(String text) {
        try {
            ClipboardManager manager = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
            manager.setPrimaryClip(ClipData.newPlainText("", text));
            Notify.show(R.string.copied);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getNumber(String text) {
        try {
            String number = extractEpisodeNumber(text);
            return TextUtils.isEmpty(number) ? -1 : Integer.parseInt(number.replaceFirst("^0+(?!$)", ""));
        } catch (Exception e) {
            return -1;
        }
    }

    private static String extractEpisodeNumber(String title) {
        if (TextUtils.isEmpty(title)) return "";
        String text = preprocessEpisodeTitle(title);
        String standalone = extractStandaloneEpisode(text);
        if (!TextUtils.isEmpty(standalone)) return standalone;

        Matcher matcher = EPISODE_SEASON.matcher(text);
        if (matcher.find()) return matcher.group(1);

        matcher = EPISODE_CHINESE.matcher(text);
        if (matcher.find()) return normalizeEpisodeNumber(matcher.group(1));

        matcher = EPISODE_TOKEN.matcher(text);
        if (matcher.find()) return matcher.group(1);

        matcher = EPISODE_BRACKET.matcher(text);
        if (matcher.find() && !isLikelyFileSize(text, matcher.start(1), matcher.end(1))) return matcher.group(1);

        matcher = EPISODE_FILENAME.matcher(text);
        if (matcher.find() && acceptEpisodeNumber(text, matcher.group(1), matcher.start(1), matcher.end(1), true)) return matcher.group(1);

        MatchCandidate best = null;
        matcher = EPISODE_STANDALONE.matcher(text);
        while (matcher.find()) {
            String number = matcher.group(1);
            int start = matcher.start(1);
            int end = matcher.end(1);
            if (!acceptEpisodeNumber(text, number, start, end, false)) continue;
            MatchCandidate candidate = new MatchCandidate(number, start, priorityEpisodeNumber(text, number, start, end));
            if (best == null || candidate.priority > best.priority || (candidate.priority == best.priority && candidate.start > best.start)) best = candidate;
        }
        return best == null ? "" : best.number;
    }

    private static String preprocessEpisodeTitle(String title) {
        String text = title
                .replaceAll("\\[[0-9]+(?:\\.[0-9]+)?[GMK]B?\\]", " ")
                .replaceAll("\\([0-9]+(?:\\.[0-9]+)?[GMK]B?\\)", " ")
                .replaceAll("【[0-9]+(?:\\.[0-9]+)?[GMK]B?】", " ")
                .replaceAll("（[0-9]+(?:\\.[0-9]+)?[GMK]B?）", " ")
                .replaceAll("\\b(?:2160|1080|720|480)[pP]\\b", " ")
                .replaceAll("\\b(?:4K|2K|HD|SD|FHD|UHD)\\b", " ")
                .replaceAll("\\b(?:x264|x265|H264|H265|AVC|HEVC)\\b", " ")
                .replaceAll("\\b(?:AAC|AC3|EAC3|DTS|FLAC|TrueHD|DDP|Atmos|DoVi|Dolby|HDR10|HDR)\\b", " ")
                .replaceAll("\\b[0-9]{1,3}\\s*fps\\b", " ")
                .replaceAll("\\b(?:WEB[-._\\s]*DL|BluRay|BDRip|HDRip|REMUX|HDTV|HQ)\\b", " ")
                .replaceAll("\\b[vV](?:[0-9]+(?:\\.[0-9]+)?)\\b", " ")
                .replaceAll("高码率|高码", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return text;
    }

    private static String extractStandaloneEpisode(String title) {
        if (!title.matches("\\d{1,3}")) return "";
        int value = Integer.parseInt(title);
        if (value <= 0 || (value > 99 && !title.startsWith("0"))) return "";
        return String.valueOf(value);
    }

    private static boolean acceptEpisodeNumber(String title, String number, int start, int end, boolean strong) {
        int value;
        try {
            value = Integer.parseInt(number);
        } catch (NumberFormatException e) {
            return false;
        }
        if (value <= 0 || value > 999 || isLikelyFileSize(title, start, end)) return false;
        String context = getContext(title, start, end, strong ? 14 : 10).toLowerCase(Locale.ROOT);
        if (context.matches("(?i).*(ep|episode|season|第|集|话|話|章|回|期|part).*")) return true;
        if (containsMediaNoise(context)) return false;
        String before = start > 0 ? title.substring(0, start) : "";
        String after = end < title.length() ? title.substring(end) : "";
        boolean hasSeparatorBefore = start == 0 || before.matches(".*[\\s._\\-\\[【(（]$");
        boolean hasVideoTokenAfter = after.matches("(?i)^(?:[\\s._\\-\\]】)）]*)?(?:[0-9]{3,4}p|[0-9]{3,4}x[0-9]{3,4}|[1248]k|HD|SD|FHD|UHD|WEB|HDTV|BDRip|BluRay|REMUX|HDRip|m3u8|mp4|mkv|ts|flv|avi|mov|wmv|webm)\\b.*");
        boolean startsLikeEpisode = start == 0 && after.matches("^[\\s._\\-].*");
        return hasSeparatorBefore && (hasVideoTokenAfter || startsLikeEpisode);
    }

    private static int priorityEpisodeNumber(String title, String number, int start, int end) {
        int value = Integer.parseInt(number);
        int priority = value <= 99 ? 10 : 0;
        String before = start > 0 ? title.substring(Math.max(0, start - 3), start) : "";
        String after = end < title.length() ? title.substring(end, Math.min(title.length(), end + 3)) : "";
        if (before.matches(".*[\\s\\[\\]()\\-._].*")) priority += 5;
        if (after.matches("^[\\.\\s\\[\\]()\\-].*")) priority += 5;
        return priority + (start * 100 / Math.max(1, title.length()));
    }

    private static boolean isLikelyFileSize(String title, int start, int end) {
        if (start < 0 || end > title.length()) return false;
        if (start > 1) {
            String before = title.substring(Math.max(0, start - 3), start);
            if (before.matches(".*[0-9]\\.[0-9]*$")) return true;
        }
        if (end < title.length()) {
            String after = title.substring(end, Math.min(title.length(), end + 5));
            return after.matches("^(?:\\.?[0-9]*[GMK]B?\\b).*");
        }
        return false;
    }

    private static boolean containsMediaNoise(String text) {
        return !TextUtils.isEmpty(text) && text.matches("(?i).*(gb|mb|kb|fps|bitrate|码率|ddp|eac3|ac3|dts|aac|flac|atmos|truehd|dolby|hdr|h\\.?26[45]|x26[45]|web[-._\\s]*dl|bluray|bdrip|remux|hq|uhd|fhd).*");
    }

    private static String getContext(String title, int start, int end, int size) {
        return title.substring(Math.max(0, start - size), Math.min(title.length(), end + size));
    }

    private static String normalizeEpisodeNumber(String value) {
        if (TextUtils.isEmpty(value)) return "";
        value = value.trim();
        if (value.matches("\\d+")) return String.valueOf(Integer.parseInt(value.replaceFirst("^0+(?!$)", "")));
        int number = chineseEpisodeNumber(value);
        return number > 0 ? String.valueOf(number) : "";
    }

    private static int chineseEpisodeNumber(String value) {
        if (TextUtils.isEmpty(value)) return 0;
        value = value.replace("两", "二").replace("零", "");
        if (value.matches("[一二三四五六七八九]")) return chineseDigit(value.charAt(0));
        int wan = value.indexOf("万");
        if (wan >= 0) return chineseEpisodeNumber(value.substring(0, wan)) * 10000 + chineseEpisodeNumber(value.substring(wan + 1));
        int qian = value.indexOf("千");
        if (qian >= 0) return chineseEpisodeNumber(value.substring(0, qian)) * 1000 + chineseEpisodeNumber(value.substring(qian + 1));
        int bai = value.indexOf("百");
        if (bai >= 0) return chineseEpisodeNumber(value.substring(0, bai)) * 100 + chineseEpisodeNumber(value.substring(bai + 1));
        int shi = value.indexOf("十");
        if (shi >= 0) {
            int tens = shi == 0 ? 1 : chineseDigit(value.charAt(shi - 1));
            int ones = shi == value.length() - 1 ? 0 : chineseDigit(value.charAt(shi + 1));
            return tens * 10 + ones;
        }
        StringBuilder digits = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            int digit = chineseDigit(value.charAt(i));
            if (digit <= 0) return 0;
            digits.append(digit);
        }
        return digits.length() == 0 ? 0 : Integer.parseInt(digits.toString());
    }

    private static int chineseDigit(char c) {
        return switch (c) {
            case '一' -> 1;
            case '二' -> 2;
            case '三' -> 3;
            case '四' -> 4;
            case '五' -> 5;
            case '六' -> 6;
            case '七' -> 7;
            case '八' -> 8;
            case '九' -> 9;
            default -> 0;
        };
    }

    private record MatchCandidate(String number, int start, int priority) {
    }

    public static String clean(String text) {
        if (!text.contains("<")) return text;
        StringBuilder sb = new StringBuilder();
        text = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY).toString().replace("\u00A0", " ").replace("\u3000", " ");
        for (String line : text.split("\\r?\\n")) sb.append(line.trim()).append("\n");
        return substring(sb.toString()).trim();
    }

    public static String getAndroidId() {
        try {
            String id = Settings.Secure.getString(App.get().getContentResolver(), Settings.Secure.ANDROID_ID);
            if (TextUtils.isEmpty(id)) throw new NullPointerException();
            return id;
        } catch (Exception e) {
            return "0000000000000000";
        }
    }

    public static String getSerial() {
        return Shell.exec("getprop ro.serialno").replace("\n", "");
    }

    public static String getMac(String name) {
        try {
            StringBuilder sb = new StringBuilder();
            NetworkInterface nif = NetworkInterface.getByName(name);
            if (nif.getHardwareAddress() == null) return "";
            for (byte b : nif.getHardwareAddress()) sb.append(String.format("%02X:", b));
            return substring(sb.toString());
        } catch (Exception e) {
            return "";
        }
    }

    public static String getDeviceName() {
        String model = TextUtils.isEmpty(Build.MODEL) ? "Android" : Build.MODEL.trim();
        String manufacturer = TextUtils.isEmpty(Build.MANUFACTURER) ? "" : Build.MANUFACTURER.trim();
        if (TextUtils.isEmpty(manufacturer)) return model;
        return model.startsWith(manufacturer) ? model : manufacturer + " " + model;
    }

    public static String substring(String text) {
        return substring(text, 1);
    }

    public static String substring(String text, int num) {
        if (text != null && text.length() > num) return text.substring(0, text.length() - num);
        return text;
    }

    public static boolean isLeanback() {
        return "leanback".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isMobile() {
        return "mobile".equals(BuildConfig.FLAVOR_mode);
    }

    public static boolean isFullscreen(Activity activity) {
        if (activity == null || activity.getWindow() == null) return false;
        return isLeanback() || (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_FULLSCREEN) != 0;
    }

    public static boolean isFullscreenLand(Activity activity) {
        return isFullscreen(activity) && !isLeanback() && ResUtil.isLand(activity);
    }

    public static String format(StringBuilder builder, Formatter formatter, long timeMs) {
        try {
            return androidx.media3.common.util.Util.getStringForTime(builder, formatter, timeMs);
        } catch (Exception e) {
            return "";
        }
    }

    public static String timeMs(long timeMs) {
        StringBuilder sb = new StringBuilder();
        return format(sb, new Formatter(sb, Locale.getDefault()), timeMs);
    }
}
