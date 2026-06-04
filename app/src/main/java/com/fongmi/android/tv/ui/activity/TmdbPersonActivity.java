package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.viewbinding.ViewBinding;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.SiteApi;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.bean.TmdbItem;
import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.ActivityTmdbPersonBinding;
import com.fongmi.android.tv.service.TmdbService;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.adapter.TmdbPersonPhotoAdapter;
import com.fongmi.android.tv.ui.adapter.TmdbWorkAdapter;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.KeyUtil;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.fongmi.android.tv.utils.Task;
import com.fongmi.android.tv.utils.Util;
import com.google.android.material.button.MaterialButton;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class TmdbPersonActivity extends BaseActivity {

    private final TmdbService tmdbService = new TmdbService();
    private final List<String> personPhotos = new ArrayList<>();
    private final List<TmdbItem> allWorks = new ArrayList<>();
    private final List<TmdbItem> castWorks = new ArrayList<>();
    private final List<TmdbItem> crewWorks = new ArrayList<>();
    private ActivityTmdbPersonBinding binding;
    private TmdbPersonPhotoAdapter photoAdapter;
    private TmdbWorkAdapter workAdapter;
    private TmdbConfig tmdbConfig;
    private String filter = "all";
    private String siteKey;
    private int detailMode;
    private boolean light;

    public static void start(Activity activity, TmdbPerson person, String siteKey) {
        start(activity, person, siteKey, Setting.getDetailOpenMode());
    }

    public static void start(Activity activity, TmdbPerson person, String siteKey, int detailMode) {
        if (activity == null || person == null || person.getPersonId() <= 0) return;
        Intent intent = new Intent(activity, TmdbPersonActivity.class);
        intent.putExtra("person_id", person.getPersonId());
        intent.putExtra("person_name", person.getName());
        intent.putExtra("person_subtitle", person.getSubtitle());
        intent.putExtra("person_profile", person.getProfileUrl());
        intent.putExtra("person_department", person.getKnownForDepartment());
        intent.putExtra("person_biography", person.getBiography());
        intent.putExtra("site_key", siteKey);
        intent.putExtra("detail_mode", normalizeDetailMode(detailMode));
        activity.startActivity(intent);
    }

    @Override
    protected ViewBinding getBinding() {
        return binding = ActivityTmdbPersonBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        tmdbConfig = TmdbConfig.objectFrom(Setting.getTmdbConfig());
        siteKey = getIntent().getStringExtra("site_key");
        detailMode = normalizeDetailMode(getIntent().getIntExtra("detail_mode", Setting.getDetailOpenMode()));
        light = resolveLightTheme();
        setThemeColors();
        setInitialPerson();
        setAdapters();
        loadPerson();
    }

    @Override
    protected void initEvent() {
        binding.close.setOnClickListener(view -> finish());
        binding.filterAll.setOnClickListener(view -> setFilter("all"));
        binding.filterCast.setOnClickListener(view -> setFilter("cast"));
        binding.filterCrew.setOnClickListener(view -> setFilter("crew"));
        binding.filterDirector.setOnClickListener(view -> setFilter("director"));
        binding.filterMovie.setOnClickListener(view -> setFilter("movie"));
        binding.filterTv.setOnClickListener(view -> setFilter("tv"));
    }

    private void setInitialPerson() {
        binding.name.setText(textExtra("person_name"));
        binding.subtitle.setVisibility(View.GONE);
        binding.personalInfo.setText(textExtra("person_subtitle"));
        binding.biography.setText(coalesce(textExtra("person_biography"), getString(R.string.detail_person_empty)));
        ImgUtil.load(textExtra("person_name"), textExtra("person_profile"), binding.photo);
    }

    private void setAdapters() {
        photoAdapter = new TmdbPersonPhotoAdapter(this::showPhotoDialog);
        photoAdapter.setLight(light);
        binding.photos.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        binding.photos.setAdapter(photoAdapter);

        workAdapter = new TmdbWorkAdapter(this::openWork);
        workAdapter.setLight(light);
        binding.works.setLayoutManager(new LinearLayoutManager(this));
        binding.works.setAdapter(workAdapter);
    }

    private void openWork(TmdbItem item) {
        Site site = VodConfig.get().getSite(siteKey);
        if (site == null || site.isEmpty() || !site.isSearchable()) {
            SearchActivity.direct(this, item.getTitle());
            return;
        }
        Notify.show(getString(R.string.detail_work_searching, item.getTitle()));
        Task.execute(() -> {
            Vod match = searchCurrentSite(item.getTitle(), site);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (match == null) {
                    Notify.show(getString(R.string.detail_work_global_searching, item.getTitle()));
                    SearchActivity.direct(this, item.getTitle());
                    return;
                }
                TmdbDetailActivity.start(this, site.getKey(), match.getId(), match.getName(), match.getPic(), "", item, detailMode);
            });
        });
    }

    private Vod searchCurrentSite(String keyword, Site site) {
        try {
            Result result = SiteApi.searchContent(site, keyword, false, "1");
            return bestVod(result != null ? result.getList() : List.of(), keyword);
        } catch (Throwable e) {
            return null;
        }
    }

    private Vod bestVod(List<Vod> items, String keyword) {
        if (items == null || items.isEmpty()) return null;
        Vod best = null;
        int score = Integer.MIN_VALUE;
        for (Vod item : items) {
            int current = scoreVod(item, keyword);
            if (current > score) {
                score = current;
                best = item;
            }
        }
        return score > 0 ? best : null;
    }

    private int scoreVod(Vod item, String keyword) {
        if (item == null) return Integer.MIN_VALUE;
        String normalizedKeyword = normalizeTitle(keyword);
        String name = normalizeTitle(item.getName());
        if (name.isEmpty()) return Integer.MIN_VALUE;
        if (name.equals(normalizedKeyword)) return 300;
        if (name.contains(normalizedKeyword) || normalizedKeyword.contains(name)) return 220;
        String remarks = normalizeTitle(item.getRemarks());
        if (!remarks.isEmpty() && (remarks.contains(normalizedKeyword) || normalizedKeyword.contains(remarks))) return 120;
        return 0;
    }

    private String normalizeTitle(String text) {
        return TextUtils.isEmpty(text) ? "" : text.replaceAll("[\\s·•・._\\-_/\\\\|()（）\\[\\]【】《》<>]+", "").trim().toLowerCase(Locale.ROOT);
    }

    private void loadPerson() {
        if (!tmdbConfig.isReady()) {
            binding.progress.setVisibility(View.GONE);
            Notify.show(R.string.detail_tmdb_need_key);
            return;
        }
        int personId = getIntent().getIntExtra("person_id", 0);
        Task.execute(() -> {
            try {
                JsonObject detail = tmdbService.person(personId, tmdbConfig);
                TmdbPerson profile = tmdbService.personProfile(detail, tmdbConfig);
                List<String> photos = tmdbService.personPhotos(detail, tmdbConfig);
                List<TmdbItem> cast = tmdbService.personCastWorks(detail, tmdbConfig);
                List<TmdbItem> crew = tmdbService.personCrewWorks(detail, tmdbConfig);
                List<TmdbItem> all = tmdbService.personWorks(detail, tmdbConfig);
                runOnUiThread(() -> bindPerson(profile, photos, all, cast, crew));
            } catch (Throwable e) {
                runOnUiThread(() -> {
                    binding.progress.setVisibility(View.GONE);
                    Notify.show(TextUtils.isEmpty(e.getMessage()) ? getString(R.string.detail_person_empty) : e.getMessage());
                });
            }
        });
    }

    private void bindPerson(TmdbPerson profile, List<String> photos, List<TmdbItem> all, List<TmdbItem> cast, List<TmdbItem> crew) {
        if (isFinishing() || isDestroyed()) return;
        binding.progress.setVisibility(View.GONE);
        binding.name.setText(profile.getName());
        binding.subtitle.setVisibility(View.GONE);
        binding.personalInfo.setText(profile.getSubtitle());
        binding.personalInfo.setVisibility(profile.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
        binding.personalTitle.setVisibility(profile.getSubtitle().isEmpty() ? View.GONE : View.VISIBLE);
        binding.biography.setText(personBiography(profile));
        ImgUtil.load(profile.getName(), profile.getProfileUrl(), binding.photo);

        allWorks.clear();
        allWorks.addAll(all);
        castWorks.clear();
        castWorks.addAll(cast);
        crewWorks.clear();
        crewWorks.addAll(crew);
        personPhotos.clear();
        personPhotos.addAll(photos);

        binding.photosTitle.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);
        binding.photos.setVisibility(photos.isEmpty() ? View.GONE : View.VISIBLE);
        photoAdapter.setItems(photos);
        refreshFilterButtons();
        setFilter(filter);
    }

    private void setFilter(String value) {
        if (filterCount(value) <= 0) value = "all";
        filter = value;
        List<TmdbItem> filtered = filteredWorks(filter);
        binding.worksCount.setText(getString(R.string.detail_person_work_count, filtered.size()));
        binding.empty.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        binding.works.setVisibility(filtered.isEmpty() ? View.GONE : View.VISIBLE);
        workAdapter.setItems(filtered);
        updateFilters();
    }

    private List<TmdbItem> filteredWorks(String value) {
        List<TmdbItem> source = switch (value) {
            case "cast" -> castWorks;
            case "crew", "director" -> crewWorks;
            default -> allWorks;
        };
        List<TmdbItem> filtered = new ArrayList<>();
        for (TmdbItem item : source) {
            if ("movie".equals(value) && !"movie".equals(item.getMediaType())) continue;
            if ("tv".equals(value) && !"tv".equals(item.getMediaType())) continue;
            if ("director".equals(value) && !isDirector(item)) continue;
            filtered.add(item);
        }
        return filtered;
    }

    private void refreshFilterButtons() {
        List<FilterButton> buttons = new ArrayList<>();
        buttons.add(new FilterButton("cast", binding.filterCast, filterCount("cast")));
        buttons.add(new FilterButton("crew", binding.filterCrew, filterCount("crew")));
        buttons.add(new FilterButton("director", binding.filterDirector, filterCount("director")));
        buttons.add(new FilterButton("movie", binding.filterMovie, filterCount("movie")));
        buttons.add(new FilterButton("tv", binding.filterTv, filterCount("tv")));
        buttons.sort(Comparator.comparingInt(FilterButton::count).reversed());
        binding.filterGroup.removeAllViews();
        if (filterCount("all") > 0) binding.filterGroup.addView(binding.filterAll);
        for (FilterButton item : buttons) {
            item.button().setVisibility(item.count() > 0 ? View.VISIBLE : View.GONE);
            if (item.count() > 0) binding.filterGroup.addView(item.button());
        }
        layoutFilterButtons();
        if (filterCount(filter) <= 0) filter = "all";
    }

    private void layoutFilterButtons() {
        int count = binding.filterGroup.getChildCount();
        boolean compact = isPhoneWidth();
        binding.filterScroll.setFillViewport(compact);
        ViewGroup.LayoutParams groupParams = binding.filterGroup.getLayoutParams();
        groupParams.width = compact ? ViewGroup.LayoutParams.MATCH_PARENT : ViewGroup.LayoutParams.WRAP_CONTENT;
        binding.filterGroup.setLayoutParams(groupParams);
        for (int i = 0; i < count; i++) {
            View child = binding.filterGroup.getChildAt(i);
            if (child instanceof MaterialButton button) {
                button.setMinWidth(0);
                button.setMinimumWidth(0);
                button.setInsetLeft(0);
                button.setInsetRight(0);
                button.setSingleLine(true);
                button.setPadding(dp(12), 0, dp(12), 0);
            }
            LinearLayout.LayoutParams params = compact
                    ? new LinearLayout.LayoutParams(0, dp(36), 1f)
                    : new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(36));
            params.setMarginEnd(i == count - 1 ? 0 : dp(8));
            child.setLayoutParams(params);
        }
    }

    private boolean isPhoneWidth() {
        return getResources().getConfiguration().smallestScreenWidthDp < 600;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private int filterCount(String value) {
        return filteredWorks(value).size();
    }

    private String personBiography(TmdbPerson profile) {
        String biography = profile.getBiography();
        if (TextUtils.isEmpty(biography) || normalize(biography).equals(normalize(profile.getSubtitle()))) return getString(R.string.detail_person_empty);
        return biography;
    }

    private String normalize(String text) {
        return TextUtils.isEmpty(text) ? "" : text.replaceAll("\\s+", "").trim();
    }

    private boolean isDirector(TmdbItem item) {
        String credit = item.getCredit().toLowerCase(Locale.ROOT);
        return credit.contains("director") || credit.contains("directing") || credit.contains("导演");
    }

    private void updateFilters() {
        updateFilter(binding.filterAll, "all");
        updateFilter(binding.filterCast, "cast");
        updateFilter(binding.filterCrew, "crew");
        updateFilter(binding.filterDirector, "director");
        updateFilter(binding.filterMovie, "movie");
        updateFilter(binding.filterTv, "tv");
    }

    private void updateFilter(MaterialButton button, String value) {
        boolean selected = filter.equals(value);
        int bg = selected ? (light ? 0xFFDBEAFE : 0xFF2F4F6F) : (light ? 0xFFF5F8FB : 0xFF1A2530);
        int fg = light ? 0xFF12202D : 0xFFFFFFFF;
        int stroke = selected ? 0xFF6DA8E8 : (light ? 0x33424B57 : 0x33FFFFFF);
        button.setTextColor(fg);
        button.setBackgroundTintList(ColorStateList.valueOf(bg));
        button.setStrokeColor(ColorStateList.valueOf(stroke));
        button.setStrokeWidth(selected ? 2 : 1);
    }

    private void setThemeColors() {
        int background = light ? 0xFFF4F7FA : 0xFF101820;
        int primary = light ? 0xFF12202D : 0xFFFFFFFF;
        int secondary = light ? 0xB312202D : 0xB3FFFFFF;
        int control = light ? 0xFFE7EDF3 : 0xFF263442;
        binding.root.setBackgroundColor(background);
        tint(binding.name, primary);
        tint(binding.pageTitle, primary);
        tint(binding.personalTitle, primary);
        tint(binding.photosTitle, primary);
        tint(binding.worksTitle, primary);
        tint(binding.subtitle, secondary);
        tint(binding.personalInfo, secondary);
        tint(binding.worksCount, secondary);
        tint(binding.empty, secondary);
        binding.biography.setTextColor(light ? 0xDD12202D : 0xDDEAF2F8);
        binding.close.setTextColor(primary);
        binding.close.setBackgroundTintList(ColorStateList.valueOf(control));
    }

    private void tint(TextView view, int color) {
        view.setTextColor(color);
    }

    private boolean resolveLightTheme() {
        int mode = Setting.getTmdbDetailTheme();
        if (mode == 1) return false;
        if (mode == 2) return true;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES;
    }

    private void showPhotoDialog(int position, String url) {
        if (TextUtils.isEmpty(url)) return;
        List<String> photos = new ArrayList<>(personPhotos);
        if (photos.isEmpty()) photos.add(url);
        int start = position >= 0 && position < photos.size() ? position : Math.max(0, photos.indexOf(url));
        int[] current = new int[]{Math.max(0, start)};

        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ImageView image = new ImageView(this);
        image.setFocusable(true);
        image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        image.setBackgroundColor(Color.BLACK);
        image.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        ProgressBar progress = new ProgressBar(this);
        progress.setIndeterminate(true);
        progress.setVisibility(View.GONE);
        FrameLayout.LayoutParams progressParams = new FrameLayout.LayoutParams(ResUtil.dp2px(38), ResUtil.dp2px(38), android.view.Gravity.CENTER);
        progress.setLayoutParams(progressParams);

        FrameLayout content = new FrameLayout(this);
        content.setBackgroundColor(Color.BLACK);
        content.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        content.addView(image);
        content.addView(progress);
        dialog.setContentView(content);
        int[] request = new int[]{0};
        int[] photoOrientation = new int[]{ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED};
        int originalOrientation = getRequestedOrientation();
        boolean wasFullscreen = Util.isFullscreen(this);
        dialog.setOnDismissListener(instance -> {
            setRequestedOrientation(originalOrientation);
            if (!wasFullscreen) Util.toggleFullscreen(this, false);
        });
        GestureDetector gesture = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent event) {
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent event) {
                if (photos.size() <= 1) {
                    dialog.dismiss();
                    return true;
                }
                float x = event.getX();
                int width = image.getWidth();
                if (x < width * 0.33f) {
                    showPhotoAt(image, progress, photos, current, request, photoOrientation, -1);
                } else if (x > width * 0.67f) {
                    showPhotoAt(image, progress, photos, current, request, photoOrientation, 1);
                } else {
                    dialog.dismiss();
                }
                return true;
            }

            @Override
            public boolean onFling(MotionEvent down, MotionEvent up, float velocityX, float velocityY) {
                if (photos.size() <= 1 || down == null || up == null) return false;
                float distanceX = up.getX() - down.getX();
                if (Math.abs(distanceX) < ResUtil.dp2px(48) || Math.abs(velocityX) < 120f) return false;
                showPhotoAt(image, progress, photos, current, request, photoOrientation, distanceX < 0 ? 1 : -1);
                return true;
            }
        });
        image.setOnTouchListener((view, event) -> gesture.onTouchEvent(event));
        image.setOnClickListener(view -> dialog.dismiss());
        dialog.setOnKeyListener((instance, keyCode, event) -> {
            if (!KeyUtil.isActionUp(event)) return false;
            if (KeyUtil.isLeftKey(event)) {
                showPhotoAt(image, progress, photos, current, request, photoOrientation, -1);
                return true;
            }
            if (KeyUtil.isRightKey(event)) {
                showPhotoAt(image, progress, photos, current, request, photoOrientation, 1);
                return true;
            }
            if (KeyUtil.isEnterKey(event) || keyCode == KeyEvent.KEYCODE_BACK) {
                dialog.dismiss();
                return true;
            }
            return false;
        });
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) return;
        window.setBackgroundDrawableResource(android.R.color.black);
        window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        Util.hideSystemUI(window);
        image.requestFocus();
        loadPhotoImage(image, progress, photos.get(current[0]), current[0], request, photoOrientation);
    }

    private void showPhotoAt(ImageView image, ProgressBar progress, List<String> photos, int[] current, int[] request, int[] photoOrientation, int direction) {
        if (photos.isEmpty()) return;
        int next = (current[0] + direction + photos.size()) % photos.size();
        if (next == current[0]) return;
        current[0] = next;
        loadPhotoImage(image, progress, photos.get(current[0]), current[0], request, photoOrientation);
    }

    private void loadPhotoImage(ImageView image, ProgressBar progress, String url, int position, int[] request, int[] photoOrientation) {
        int token = ++request[0];
        progress.setVisibility(View.VISIBLE);
        try {
            Glide.with(image)
                    .load(ImgUtil.getUrl(highResTmdbImage(url)))
                    .fitCenter()
                    .into(new CustomTarget<Drawable>() {
                        @Override
                        public void onResourceReady(@NonNull Drawable resource, @Nullable Transition<? super Drawable> transition) {
                            if (token != request[0]) return;
                            image.setImageDrawable(resource);
                            progress.setVisibility(View.GONE);
                            applyPhotoOrientation(resource, photoOrientation);
                        }

                        @Override
                        public void onLoadFailed(@Nullable Drawable errorDrawable) {
                            if (token != request[0]) return;
                            if (image.getDrawable() == null && errorDrawable != null) image.setImageDrawable(errorDrawable);
                            progress.setVisibility(View.GONE);
                        }

                        @Override
                        public void onLoadCleared(@Nullable Drawable placeholder) {
                        }
                    });
        } catch (Throwable e) {
            progress.setVisibility(View.GONE);
            Notify.show(R.string.detail_tmdb_empty);
        }
    }

    private void applyPhotoOrientation(Drawable resource, int[] photoOrientation) {
        if (!Util.isMobile() || resource == null) return;
        int width = resource.getIntrinsicWidth();
        int height = resource.getIntrinsicHeight();
        if (width <= 0 || height <= 0) return;
        int target = width >= height ? ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT;
        if (photoOrientation[0] == target) return;
        photoOrientation[0] = target;
        setRequestedOrientation(target);
    }

    private String highResTmdbImage(String url) {
        int marker = url.indexOf("/t/p/");
        if (marker < 0) return url;
        int sizeStart = marker + "/t/p/".length();
        int sizeEnd = url.indexOf('/', sizeStart);
        if (sizeEnd < 0) return url;
        return url.substring(0, sizeStart) + "original" + url.substring(sizeEnd);
    }

    private String textExtra(String key) {
        String value = getIntent().getStringExtra(key);
        return value == null ? "" : value;
    }

    private String coalesce(String... values) {
        for (String value : values) if (!TextUtils.isEmpty(value)) return value;
        return "";
    }

    private static int normalizeDetailMode(int mode) {
        return Setting.isTmdbMode(mode) ? mode : Setting.DETAIL_OPEN_ENHANCED;
    }

    private record FilterButton(String key, MaterialButton button, int count) {
    }
}
