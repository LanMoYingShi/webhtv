package com.fongmi.android.tv.utils;

import static android.widget.ImageView.ScaleType.CENTER_CROP;
import static android.widget.ImageView.ScaleType.FIT_CENTER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.DecodeFormat;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.model.GlideUrl;
import com.bumptech.glide.load.model.LazyHeaders;
import com.bumptech.glide.load.resource.bitmap.DownsampleStrategy;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;
import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.impl.CustomTarget;
import com.github.catvod.utils.Json;
import com.google.common.net.HttpHeaders;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import jahirfiquitiva.libs.textdrawable.TextDrawable;

public class ImgUtil {

    private static final Set<String> failed = Collections.synchronizedSet(new HashSet<>());
    private static final int THUMB_MAX_WIDTH = 300;
    private static final int THUMB_MAX_HEIGHT = 400;
    private static final RequestOptions BASE_OPTIONS = new RequestOptions()
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate();
    private static final RequestOptions THUMB_OPTIONS = new RequestOptions()
            .format(DecodeFormat.PREFER_RGB_565)
            .downsample(DownsampleStrategy.AT_MOST)
            .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
            .dontAnimate();

    public static void logo(ImageView view) {
        try {
            Glide.with(view).load(UrlUtil.convert(VodConfig.get().getConfig().getLogo())).circleCrop().override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL).error(R.drawable.ic_logo).into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String url, CustomTarget<Bitmap> target) {
        try {
            Glide.with(App.get()).asBitmap().load(getUrl(url)).override(ResUtil.dp2px(96), ResUtil.dp2px(96)).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(Context context, String url, CustomTarget<Drawable> target) {
        try {
            Glide.with(context).load(getUrl(url)).override(ResUtil.getScreenWidth(), ResUtil.getScreenHeight()).error(R.drawable.artwork).into(target);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void load(String text, String url, ImageView view) {
        load(text, url, view, true);
    }

    public static void load(String text, String url, ImageView view, boolean vod) {
        load(text, url, view, vod, 0, 0);
    }

    public static void load(String text, String url, ImageView view, int width, int height) {
        load(text, url, view, true, width, height, false);
    }

    public static void loadThumb(String text, String url, ImageView view, int width, int height) {
        load(text, url, view, true, clampThumbWidth(width), clampThumbHeight(height), true);
    }

    public static void hold(String url, ImageView view, boolean vod) {
        hold(url, view, vod, true);
    }

    public static void hold(String url, ImageView view, boolean vod, boolean cancel) {
        Object oldTag = view.getTag(R.id.image);
        boolean same = TextUtils.equals(url, oldTag instanceof String ? (String) oldTag : null);
        if (cancel && !same) cancel(view);
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        view.setTag(R.id.image, url);
        if (!same) view.setImageDrawable(null);
    }

    private static void load(String text, String url, ImageView view, boolean vod, int width, int height) {
        load(text, url, view, vod, width, height, false);
    }

    private static void load(String text, String url, ImageView view, boolean vod, int width, int height, boolean thumb) {
        view.setScaleType(vod ? CENTER_CROP : FIT_CENTER);
        if (!vod) view.setVisibility(TextUtils.isEmpty(url) ? View.GONE : View.VISIBLE);
        Object oldTag = view.getTag(R.id.image);
        view.setTag(R.id.image, url);
        Object model = getUrl(url);
        if (model == null || failed.contains(url)) {
            view.setImageDrawable(getTextDrawable(text, vod));
            if (!TextUtils.isEmpty(url)) failed.add(url);
            return;
        }
        if (!TextUtils.equals(url, oldTag instanceof String ? (String) oldTag : null)) view.setImageDrawable(null);
        if (width > 0 && height > 0) load(text, url, view, vod, model, width, height, thumb);
        else view.post(() -> load(text, url, view, vod, model, width > 0 ? width : view.getWidth(), height > 0 ? height : view.getHeight(), thumb));
    }

    private static void load(String text, String url, ImageView view, boolean vod, Object model, int width, int height, boolean thumb) {
        if (!url.equals(view.getTag(R.id.image))) return;
        try {
            if (thumb) {
                width = clampThumbWidth(width);
                height = clampThumbHeight(height);
            }
            RequestBuilder<Drawable> builder = Glide.with(view).load(model).apply(thumb ? THUMB_OPTIONS : BASE_OPTIONS).listener(getListener(text, url, view, vod));
            if (width > 0 && height > 0) builder.override(width, height);
            if (vod) builder.centerCrop().into(view);
            else builder.fitCenter().into(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static int clampThumbWidth(int width) {
        return width <= 0 ? 0 : Math.min(width, THUMB_MAX_WIDTH);
    }

    private static int clampThumbHeight(int height) {
        return height <= 0 ? 0 : Math.min(height, THUMB_MAX_HEIGHT);
    }

    public static void clear(ImageView view) {
        try {
            view.setTag(R.id.image, null);
            Glide.with(view).clear(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static void cancel(ImageView view) {
        try {
            Glide.with(view).clear(view);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static boolean isCurrent(String url, ImageView view) {
        Object tag = view.getTag(R.id.image);
        return TextUtils.equals(url, tag instanceof String ? (String) tag : null);
    }

    public static Object getUrl(String url) {
        String param = null;
        url = normalize(url);
        if (TextUtils.isEmpty(url)) return null;
        if (url.startsWith("data:")) return url;
        LazyHeaders.Builder builder = new LazyHeaders.Builder();
        if (url.contains("@Headers=")) addHeader(builder, param = url.split("@Headers=")[1].split("@")[0]);
        if (url.contains("@Cookie=")) builder.addHeader(HttpHeaders.COOKIE, param = url.split("@Cookie=")[1].split("@")[0]);
        if (url.contains("@Referer=")) builder.addHeader(HttpHeaders.REFERER, param = url.split("@Referer=")[1].split("@")[0]);
        if (url.contains("@User-Agent=")) builder.addHeader(HttpHeaders.USER_AGENT, param = url.split("@User-Agent=")[1].split("@")[0]);
        url = param == null ? url : url.split("@")[0];
        String scheme = UrlUtil.scheme(url);
        if ("http".equals(scheme) || "https".equals(scheme)) return new GlideUrl(url, builder.build());
        if ("file".equals(scheme) || "content".equals(scheme) || "android.resource".equals(scheme)) return url;
        return null;
    }

    private static String normalize(String url) {
        if (TextUtils.isEmpty(url)) return "";
        url = UrlUtil.convert(url.trim());
        if (url.startsWith("//")) url = "https:" + url;
        return url;
    }

    private static void addHeader(LazyHeaders.Builder builder, String header) {
        Map<String, String> map = Json.toMap(Json.parse(header));
        for (Map.Entry<String, String> entry : map.entrySet()) builder.addHeader(UrlUtil.fixHeader(entry.getKey()), entry.getValue());
    }

    private static Drawable getTextDrawable(String text, boolean vod) {
        TextDrawable.Builder builder = new TextDrawable.Builder();
        text = TextUtils.isEmpty(text) ? "！" : text.substring(0, 1);
        if (vod) builder.buildRect(text, ColorGenerator.get400(text));
        return builder.buildRoundRect(text, ColorGenerator.get400(text), ResUtil.dp2px(4));
    }

    private static RequestListener<Drawable> getListener(String text, String url, ImageView view, boolean vod) {
        return new RequestListener<>() {
            @Override
            public boolean onLoadFailed(@Nullable GlideException e, Object model, @NonNull Target<Drawable> target, boolean isFirstResource) {
                if (!isCurrent(url, view)) return true;
                view.setImageDrawable(getTextDrawable(text, vod));
                failed.add(url);
                return true;
            }

            @Override
            public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                return !isCurrent(url, view);
            }
        };
    }
}
