package com.fongmi.android.tv.ui.fragment;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.ScrollView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.api.config.VodConfig;
import com.fongmi.android.tv.bean.Collect;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.bean.Site;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.FragmentCollectBinding;
import com.fongmi.android.tv.model.SiteViewModel;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.ui.debug.SearchPerf;
import com.fongmi.android.tv.utils.Notify;
import com.fongmi.android.tv.utils.ResUtil;
import com.google.android.material.textview.MaterialTextView;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final int MENU_GROUP_ALL = 1;
    private static final int MENU_GROUP_OFFSET = 100;
    private static final int SEARCH_DEFAULT_GRID_COUNT = 2;
    private static final long SEARCH_UPDATE_DELAY = 120;
    private static final long SEARCH_SCROLL_DELAY = 220;
    private static final long SEARCH_FIRST_IMAGE_DELAY = 0;
    private static final long SEARCH_AFTER_SCROLL_DELAY = 500;
    private static final long SEARCH_IMAGE_DELAY = 120;
    private static final int SEARCH_IMAGE_BATCH_SIZE = 1;
    private static final int SEARCH_ITEM_CACHE_SIZE = 8;
    private static final int SEARCH_RECYCLED_LIST_SIZE = 48;
    private static final int SEARCH_RECYCLED_GRID_SIZE = 32;
    private static final int SEARCH_RENDER_INITIAL_LIST = 60;
    private static final int SEARCH_RENDER_INITIAL_GRID = 96;
    private static final int SEARCH_RENDER_MORE_LIST = 16;
    private static final int SEARCH_RENDER_MORE_GRID = 24;
    private static final int SEARCH_RENDER_THRESHOLD_LIST = 8;
    private static final int SEARCH_RENDER_THRESHOLD_GRID = 16;
    private static final int SEARCH_RENDER_MAX_LIST = 160;
    private static final int SEARCH_RENDER_MAX_GRID = 240;
    private static final int SEARCH_RENDER_KEEP_LIST = 48;
    private static final int SEARCH_RENDER_KEEP_GRID = 72;
    private static final int COLLECT_BATCH_SIZE = 8;
    private static final int GROUP_POPUP_ITEM_HEIGHT = 44;
    private static final int GROUP_POPUP_MAX_ITEMS = 8;

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private List<String> mGroups;
    private final List<Collect> mAllCollectItems;
    private final List<Collect> pendingCollectItems;
    private final Runnable flushSearchUpdates;
    private final Runnable restoreSearchImages;
    private final Runnable renderSearchWindow;
    private PopupWindow groupPopup;
    private List<Vod> renderedSearchSource;
    private String mFilterGroup;
    private int pendingImageStart;
    private int pendingImageEnd;
    private int loadedImageStart;
    private int loadedImageEnd;
    private int renderedSearchStart;
    private int renderedSearchEnd;
    private int desiredSearchColumn;
    private int appliedSearchColumn;
    private boolean pendingSearchRender;
    private boolean pendingActiveSearchRender;
    private boolean windowRenderScheduled;
    private boolean imageRestoreScheduled;
    private boolean searchFlushScheduled;
    private boolean searchScrolling;

    public CollectFragment() {
        mAllCollectItems = new ArrayList<>();
        pendingCollectItems = new ArrayList<>();
        flushSearchUpdates = this::flushSearchUpdates;
        restoreSearchImages = this::restoreSearchImages;
        renderSearchWindow = this::renderSearchWindow;
        renderedSearchSource = List.of();
        mFilterGroup = "";
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        loadedImageStart = RecyclerView.NO_POSITION;
        loadedImageEnd = RecyclerView.NO_POSITION;
        renderedSearchStart = 0;
        renderedSearchEnd = 0;
        desiredSearchColumn = SEARCH_DEFAULT_GRID_COUNT;
        appliedSearchColumn = RecyclerView.NO_POSITION;
    }

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        return newInstance(keyword, siteKey, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey, String group) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
        args.putString("group", group);
        CollectFragment fragment = new CollectFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private String getKeyword() {
        return getArguments().getString("keyword");
    }

    private String getSiteKey() {
        String siteKey = getArguments().getString("siteKey");
        return siteKey == null ? "" : siteKey;
    }

    private String getSearchGroup() {
        String group = getArguments().getString("group");
        return group == null ? "" : group;
    }

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
    }

    private boolean isGroupSearch() {
        return !TextUtils.isEmpty(getSearchGroup());
    }

    @Override
    protected ViewBinding getBinding(@NonNull LayoutInflater inflater, @Nullable ViewGroup container) {
        return mBinding = FragmentCollectBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initMenu() {
        if (isHidden()) return;
        AppCompatActivity activity = (AppCompatActivity) requireActivity();
        activity.setSupportActionBar(mBinding.toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
        activity.setTitle(getTitleText());
    }

    private String getTitleText() {
        if (isSiteSearch()) return getString(R.string.search_result_current, getKeyword());
        if (isGroupSearch()) return getString(R.string.search_result_group, getSearchGroup(), getKeyword());
        return getString(R.string.search_result_all, getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setSites();
        setSearchLayout();
        search();
    }

    @Override
    protected void initEvent() {
        mBinding.toolbar.setOnClickListener(v -> {
            Bundle result = new Bundle();
            result.putBoolean("edit", true);
            getParentFragmentManager().setFragmentResult("result", result);
            getParentFragmentManager().popBackStack();
        });
    }

    private void setRecyclerView() {
        mBinding.collect.setItemAnimator(null);
        mBinding.collect.setHasFixedSize(true);
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this, isHorizontalUi()));
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setHasFixedSize(true);
        mBinding.recycler.setItemViewCacheSize(SEARCH_ITEM_CACHE_SIZE);
        RecyclerView.RecycledViewPool pool = mBinding.recycler.getRecycledViewPool();
        pool.setMaxRecycledViews(SearchAdapter.VIEW_TYPE_LIST, SEARCH_RECYCLED_LIST_SIZE);
        pool.setMaxRecycledViews(SearchAdapter.VIEW_TYPE_GRID, SEARCH_RECYCLED_GRID_SIZE);
        mBinding.recycler.setLayoutManager(new SearchGridLayoutManager(requireActivity(), SEARCH_DEFAULT_GRID_COUNT));
        mBinding.recycler.addOnScrollListener(mScroller);
        mBinding.recycler.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                boolean scrolling = newState != RecyclerView.SCROLL_STATE_IDLE;
                if (searchScrolling == scrolling) return;
                searchScrolling = scrolling;
                if (scrolling) {
                    if (mSearchAdapter != null) mSearchAdapter.setLoadImages(false);
                    App.removeCallbacks(flushSearchUpdates, restoreSearchImages);
                    searchFlushScheduled = false;
                    imageRestoreScheduled = false;
                    resetLoadedSearchImages();
                } else {
                    App.removeCallbacks(flushSearchUpdates);
                    searchFlushScheduled = false;
                    scheduleVisibleSearchImages(SEARCH_AFTER_SCROLL_DELAY);
                    scheduleSearchFlush(SEARCH_AFTER_SCROLL_DELAY);
                }
            }

            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy != 0) scheduleSearchWindowRender();
            }
        });
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (manager instanceof GridLayoutManager layoutManager) layoutManager.setItemPrefetchEnabled(false);
    }

    private static class SearchGridLayoutManager extends GridLayoutManager {

        private SearchGridLayoutManager(Context context, int spanCount) {
            super(context, spanCount);
            setItemPrefetchEnabled(false);
        }

        @Override
        public boolean supportsPredictiveItemAnimations() {
            return false;
        }
    }

    private void scheduleVisibleSearchImages(long delayMillis) {
        if (mBinding == null || mSearchAdapter == null || searchScrolling) return;
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager layoutManager)) return;
        int first = layoutManager.findFirstVisibleItemPosition();
        int last = layoutManager.findLastVisibleItemPosition();
        if (first == RecyclerView.NO_POSITION || last < first) return;
        if (isSearchImageRangeLoaded(first, last)) return;
        if (imageRestoreScheduled) {
            if (pendingImageStart == RecyclerView.NO_POSITION || pendingImageEnd < pendingImageStart) pendingImageStart = getFirstUnloadedSearchImage(first, last);
            pendingImageEnd = Math.max(pendingImageEnd, last);
            return;
        }
        pendingImageStart = getFirstUnloadedSearchImage(first, last);
        pendingImageEnd = last;
        if (pendingImageStart == RecyclerView.NO_POSITION || pendingImageEnd < pendingImageStart) return;
        SearchPerf.log("schedule images delay=%d first=%d last=%d pendingStart=%d pendingEnd=%d grid=%s", delayMillis, first, last, pendingImageStart, pendingImageEnd, mSearchAdapter.isGridMode());
        imageRestoreScheduled = true;
        App.post(restoreSearchImages, delayMillis);
    }

    private void restoreSearchImages() {
        long start = SearchPerf.now();
        imageRestoreScheduled = false;
        if (mBinding == null || mSearchAdapter == null || searchScrolling) return;
        if (pendingImageStart == RecyclerView.NO_POSITION || pendingImageEnd < pendingImageStart) return;
        int count = Math.min(SEARCH_IMAGE_BATCH_SIZE, pendingImageEnd - pendingImageStart + 1);
        int restored = 0;
        for (int i = 0; i < count; i++) {
            int position = pendingImageStart + i;
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                mSearchAdapter.loadImage(holder);
                markSearchImageLoaded(position);
                restored++;
            }
        }
        SearchPerf.slow("restoreImages", start, 8, "restored=%d count=%d start=%d end=%d", restored, count, pendingImageStart, pendingImageEnd);
        pendingImageStart += count;
        if (pendingImageStart <= pendingImageEnd) {
            imageRestoreScheduled = true;
            App.post(restoreSearchImages, SEARCH_IMAGE_DELAY);
        } else {
            pendingImageStart = RecyclerView.NO_POSITION;
            pendingImageEnd = RecyclerView.NO_POSITION;
            if (!mSearchAdapter.isGridMode()) mSearchAdapter.setLoadImages(true);
        }
    }

    private void resetSearchImageLoading() {
        App.removeCallbacks(restoreSearchImages);
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        imageRestoreScheduled = false;
        resetLoadedSearchImages();
    }

    private void scheduleSearchWindowRender() {
        if (windowRenderScheduled) return;
        windowRenderScheduled = true;
        App.post(renderSearchWindow);
    }

    private void renderSearchWindow() {
        windowRenderScheduled = false;
        if (mBinding == null || mSearchAdapter == null) return;
        if (renderPreviousSearchItems()) return;
        renderMoreSearchItems();
    }

    private void addSearchItems(List<Vod> items) {
        if (items.isEmpty()) return;
        SearchPerf.log("addSearchItems count=%d totalBefore=%d grid=%s scrolling=%s", items.size(), mSearchAdapter.getItemCount(), mSearchAdapter.isGridMode(), searchScrolling);
        mSearchAdapter.setLoadImages(false);
        mSearchAdapter.addAll(items, () -> scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY));
    }

    private void setSearchItems(List<Vod> items, Runnable runnable) {
        long start = SearchPerf.now();
        App.removeCallbacks(flushSearchUpdates, renderSearchWindow);
        searchFlushScheduled = false;
        windowRenderScheduled = false;
        pendingSearchRender = false;
        pendingActiveSearchRender = false;
        resetSearchImageLoading();
        mSearchAdapter.setLoadImages(false);
        List<Vod> source = items == null ? List.of() : items;
        renderedSearchSource = source;
        renderedSearchStart = 0;
        renderedSearchEnd = Math.min(source.size(), getSearchInitialRenderSize(source.size()));
        List<Vod> visible = new ArrayList<>(source.subList(renderedSearchStart, renderedSearchEnd));
        SearchPerf.log("setSearchItems source=%d visible=%d totalBefore=%d grid=%s", source.size(), visible.size(), mSearchAdapter.getItemCount(), mSearchAdapter.isGridMode());
        mSearchAdapter.setItems(visible, () -> {
            if (runnable != null) runnable.run();
            scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
            SearchPerf.slow("setSearchItems", start, 16, "source=%d visible=%d start=%d end=%d", source.size(), visible.size(), renderedSearchStart, renderedSearchEnd);
        });
    }

    private void resetSearchWindow(Runnable runnable) {
        Collect activated = mCollectAdapter == null || mCollectAdapter.getItemCount() == 0 ? null : mCollectAdapter.getActivated();
        setSearchItems(activated == null ? List.of() : activated.getList(), runnable);
    }

    private boolean renderMoreSearchItems() {
        return renderMoreSearchItems(false);
    }

    private boolean renderMoreSearchItems(boolean force) {
        if (mBinding == null || mSearchAdapter == null || renderedSearchSource == null) return false;
        int total = renderedSearchSource.size();
        if (renderedSearchEnd >= total) return false;
        if (!force && !shouldAppendSearchWindow()) return false;
        int batch = mSearchAdapter.getItemCount() == 0 ? getSearchInitialRenderSize(total) : getSearchRenderMoreSize();
        int add = Math.min(batch, total - renderedSearchEnd);
        List<Vod> items = new ArrayList<>(renderedSearchSource.subList(renderedSearchEnd, renderedSearchEnd + add));
        renderedSearchEnd += add;
        SearchPerf.log("render append count=%d start=%d end=%d source=%d adapter=%d", add, renderedSearchStart, renderedSearchEnd, total, mSearchAdapter.getItemCount());
        addSearchItems(items);
        if (mBinding != null) mBinding.recycler.post(this::trimSearchWindowFromTopIfNeeded);
        return true;
    }

    private boolean renderPreviousSearchItems() {
        if (mBinding == null || mSearchAdapter == null || renderedSearchSource == null) return false;
        if (renderedSearchStart <= 0 || !shouldPrependSearchWindow()) return false;
        int add = Math.min(getSearchRenderMoreSize(), renderedSearchStart);
        int newStart = renderedSearchStart - add;
        List<Vod> visible = new ArrayList<>(renderedSearchSource.subList(newStart, renderedSearchEnd));
        int first = getFirstVisibleSearchPosition();
        int firstTop = getFirstVisibleSearchTop();
        renderedSearchStart = newStart;
        resetSearchImageLoading();
        mSearchAdapter.setLoadImages(false);
        SearchPerf.log("render prepend count=%d start=%d end=%d source=%d adapter=%d", add, renderedSearchStart, renderedSearchEnd, renderedSearchSource.size(), mSearchAdapter.getItemCount());
        mSearchAdapter.setItems(visible, () -> {
            if (mBinding == null) return;
            scrollSearchWindowToOffset(first == RecyclerView.NO_POSITION ? add : first + add, firstTop);
            scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
            mBinding.recycler.post(this::trimSearchWindowFromBottomIfNeeded);
        });
        return true;
    }

    private void trimSearchWindowFromTopIfNeeded() {
        int max = getSearchRenderMaxSize();
        int count = renderedSearchEnd - renderedSearchStart;
        if (count <= max || !canTrimSearchWindowFromTop()) return;
        int first = getFirstVisibleSearchPosition();
        int keep = getSearchRenderKeepSize();
        int remove = Math.min(count - max, Math.max(0, first - keep));
        if (remove <= 0) return;
        int firstTop = getFirstVisibleSearchTop();
        renderedSearchStart += remove;
        List<Vod> visible = new ArrayList<>(renderedSearchSource.subList(renderedSearchStart, renderedSearchEnd));
        resetSearchImageLoading();
        mSearchAdapter.setLoadImages(false);
        SearchPerf.log("render trimTop remove=%d start=%d end=%d visible=%d first=%d", remove, renderedSearchStart, renderedSearchEnd, visible.size(), first);
        mSearchAdapter.setItems(visible, () -> {
            if (mBinding == null) return;
            scrollSearchWindowToOffset(Math.max(0, first - remove), firstTop);
            scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
        });
    }

    private void trimSearchWindowFromBottomIfNeeded() {
        int max = getSearchRenderMaxSize();
        int count = renderedSearchEnd - renderedSearchStart;
        if (count <= max) return;
        int last = getLastVisibleSearchPosition();
        if (last == RecyclerView.NO_POSITION) return;
        int keep = getSearchRenderKeepSize();
        int visibleAfter = count - last - 1;
        int remove = Math.min(count - max, Math.max(0, visibleAfter - keep));
        if (remove <= 0) return;
        renderedSearchEnd -= remove;
        List<Vod> visible = new ArrayList<>(renderedSearchSource.subList(renderedSearchStart, renderedSearchEnd));
        resetSearchImageLoading();
        mSearchAdapter.setLoadImages(false);
        SearchPerf.log("render trimBottom remove=%d start=%d end=%d visible=%d last=%d", remove, renderedSearchStart, renderedSearchEnd, visible.size(), last);
        mSearchAdapter.setItems(visible, () -> scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY));
    }

    private boolean shouldAppendSearchWindow() {
        int last = getLastVisibleSearchPosition();
        if (last == RecyclerView.NO_POSITION) return true;
        return last >= mSearchAdapter.getItemCount() - getSearchRenderThreshold();
    }

    private boolean shouldPrependSearchWindow() {
        int first = getFirstVisibleSearchPosition();
        if (first == RecyclerView.NO_POSITION) return false;
        return first <= getSearchRenderThreshold();
    }

    private boolean canTrimSearchWindowFromTop() {
        int first = getFirstVisibleSearchPosition();
        return first != RecyclerView.NO_POSITION && first > getSearchRenderKeepSize();
    }

    private int getFirstVisibleSearchPosition() {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager layoutManager)) return RecyclerView.NO_POSITION;
        return layoutManager.findFirstVisibleItemPosition();
    }

    private int getLastVisibleSearchPosition() {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager layoutManager)) return RecyclerView.NO_POSITION;
        return layoutManager.findLastVisibleItemPosition();
    }

    private int getFirstVisibleSearchTop() {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (!(manager instanceof LinearLayoutManager layoutManager)) return 0;
        int first = layoutManager.findFirstVisibleItemPosition();
        View view = first == RecyclerView.NO_POSITION ? null : layoutManager.findViewByPosition(first);
        return view == null ? 0 : view.getTop() - mBinding.recycler.getPaddingTop();
    }

    private void scrollSearchWindowToOffset(int position, int topOffset) {
        RecyclerView.LayoutManager manager = mBinding.recycler.getLayoutManager();
        if (manager instanceof LinearLayoutManager layoutManager) layoutManager.scrollToPositionWithOffset(position, topOffset);
    }

    private int getSearchInitialRenderSize(int total) {
        int initial = mSearchAdapter != null && mSearchAdapter.isGridMode() ? SEARCH_RENDER_INITIAL_GRID : SEARCH_RENDER_INITIAL_LIST;
        return Math.min(total, initial);
    }

    private int getSearchRenderMoreSize() {
        return mSearchAdapter != null && mSearchAdapter.isGridMode() ? SEARCH_RENDER_MORE_GRID : SEARCH_RENDER_MORE_LIST;
    }

    private int getSearchRenderThreshold() {
        return mSearchAdapter != null && mSearchAdapter.isGridMode() ? SEARCH_RENDER_THRESHOLD_GRID : SEARCH_RENDER_THRESHOLD_LIST;
    }

    private int getSearchRenderMaxSize() {
        return mSearchAdapter != null && mSearchAdapter.isGridMode() ? SEARCH_RENDER_MAX_GRID : SEARCH_RENDER_MAX_LIST;
    }

    private int getSearchRenderKeepSize() {
        return mSearchAdapter != null && mSearchAdapter.isGridMode() ? SEARCH_RENDER_KEEP_GRID : SEARCH_RENDER_KEEP_LIST;
    }

    private int getFirstUnloadedSearchImage(int first, int last) {
        if (loadedImageStart == RecyclerView.NO_POSITION || first < loadedImageStart || first > loadedImageEnd) return first;
        int position = loadedImageEnd + 1;
        return position <= last ? position : RecyclerView.NO_POSITION;
    }

    private boolean isSearchImageRangeLoaded(int first, int last) {
        return loadedImageStart != RecyclerView.NO_POSITION && first >= loadedImageStart && last <= loadedImageEnd;
    }

    private void markSearchImageLoaded(int position) {
        if (position == RecyclerView.NO_POSITION) return;
        if (loadedImageStart == RecyclerView.NO_POSITION) {
            loadedImageStart = position;
            loadedImageEnd = position;
            return;
        }
        loadedImageStart = Math.min(loadedImageStart, position);
        loadedImageEnd = Math.max(loadedImageEnd, position);
    }

    private void resetLoadedSearchImages() {
        loadedImageStart = RecyclerView.NO_POSITION;
        loadedImageEnd = RecyclerView.NO_POSITION;
    }

    private void setViewModel() {
        mViewModel = new ViewModelProvider(this).get(SiteViewModel.class).init();
        mViewModel.getSearch().observe(this, this::setCollect);
        mViewModel.getResult().observe(this, this::setSearch);
    }

    private void setSites() {
        String siteKey = getSiteKey();
        String group = getSearchGroup();
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (!TextUtils.isEmpty(siteKey) && !site.getKey().equals(siteKey)) continue;
            if (!TextUtils.isEmpty(group) && !site.inGroup(group)) continue;
            mSites.add(site);
        }
        SiteHealthStore.sortSites(mSites);
        mGroups = isSiteSearch() || isGroupSearch() ? new ArrayList<>() : Site.getGroups(mSites);
    }

    private void search() {
        if (mSites.isEmpty()) {
            if (isSiteSearch()) Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        Collect all = Collect.all();
        mAllCollectItems.clear();
        mAllCollectItems.add(all);
        mCollectAdapter.setItems(List.of(all), () -> setSearchItems(all.getList(), () -> mViewModel.searchContent(mSites, getKeyword(), false)));
    }

    private void setSearchLayout() {
        boolean horizontal = isHorizontalUi();
        int gap = ResUtil.dp2px(8);
        mBinding.content.setOrientation(horizontal ? LinearLayoutCompat.VERTICAL : LinearLayoutCompat.HORIZONTAL);
        mBinding.collect.setLayoutManager(new LinearLayoutManager(requireActivity(), horizontal ? LinearLayoutManager.HORIZONTAL : LinearLayoutManager.VERTICAL, false));
        mCollectAdapter.setHorizontal(horizontal);

        LinearLayoutCompat.LayoutParams collectParams = (LinearLayoutCompat.LayoutParams) mBinding.collect.getLayoutParams();
        LinearLayoutCompat.LayoutParams recyclerParams = (LinearLayoutCompat.LayoutParams) mBinding.recycler.getLayoutParams();
        collectParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : getCollectWidth();
        collectParams.height = horizontal ? ViewGroup.LayoutParams.WRAP_CONTENT : ViewGroup.LayoutParams.MATCH_PARENT;
        collectParams.weight = 0;
        collectParams.topMargin = -gap;
        recyclerParams.width = horizontal ? ViewGroup.LayoutParams.MATCH_PARENT : 0;
        recyclerParams.height = horizontal ? 0 : ViewGroup.LayoutParams.MATCH_PARENT;
        recyclerParams.weight = 1;
        recyclerParams.topMargin = horizontal ? 0 : -gap;
        mBinding.collect.setPadding(horizontal ? gap : ResUtil.dp2px(6), 0, horizontal ? gap : 0, horizontal ? 0 : gap);
        mBinding.recycler.setPadding(horizontal ? gap : 0, 0, horizontal ? gap : ResUtil.dp2px(6), gap);
        mBinding.collect.setLayoutParams(collectParams);
        mBinding.recycler.setLayoutParams(recyclerParams);

        setSearchColumn(getCount(), false);
    }

    private int getCollectWidth() {
        int width = 0;
        int space = ResUtil.dp2px(48);
        int minWidth = ResUtil.dp2px(128);
        int maxWidth = ResUtil.dp2px(160);
        for (Site site : mSites) width = Math.max(width, ResUtil.getTextWidth(site.getName(), 14));
        return Math.max(minWidth, Math.min(width + space, maxWidth));
    }

    private boolean isHorizontalUi() {
        return Setting.getSearchUi() == 0;
    }

    private int getCount() {
        int column = Setting.getSearchColumn();
        if (column > 0) return column;
        return ResUtil.isLand(requireActivity()) || ResUtil.isPad() ? SEARCH_DEFAULT_GRID_COUNT : 1;
    }

    private void setSearchColumn(int count, boolean persist) {
        desiredSearchColumn = Math.max(1, count);
        if (persist) Setting.putSearchColumn(desiredSearchColumn > 1 ? 2 : 1);
        App.removeCallbacks(restoreSearchImages);
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        imageRestoreScheduled = false;
        resetLoadedSearchImages();
        int safeCount = getSafeSearchColumn(desiredSearchColumn);
        applySearchColumn(safeCount);
        postUpdateGridWidth();
        if (safeCount > 1) mBinding.recycler.post(() -> scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY));
    }

    private void setSearchView(boolean grid) {
        setSearchColumn(grid ? SEARCH_DEFAULT_GRID_COUNT : 1, true);
        resetSearchWindow(() -> mBinding.recycler.scrollToPosition(0));
        requireActivity().invalidateOptionsMenu();
    }

    private boolean isSearchGridDesired() {
        return desiredSearchColumn > 1;
    }

    private void postUpdateGridWidth() {
        mBinding.recycler.post(this::updateGridWidth);
    }

    private void updateGridWidth() {
        long start = SearchPerf.now();
        int width = mBinding.recycler.getWidth() - mBinding.recycler.getPaddingStart() - mBinding.recycler.getPaddingEnd();
        if (width <= 0) return;
        int safeCount = getSafeSearchColumn(desiredSearchColumn, width);
        SearchPerf.log("gridWidth width=%d desired=%d safe=%d applied=%d total=%d", width, desiredSearchColumn, safeCount, appliedSearchColumn, mSearchAdapter.getItemCount());
        applySearchColumn(safeCount);
        mSearchAdapter.setGridWidth(width);
        if (safeCount > 1) scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
        SearchPerf.slow("updateGridWidth", start, 16, "width=%d desired=%d safe=%d total=%d", width, desiredSearchColumn, safeCount, mSearchAdapter.getItemCount());
    }

    private int getSafeSearchColumn(int desiredCount) {
        int width = mBinding.recycler.getWidth() - mBinding.recycler.getPaddingStart() - mBinding.recycler.getPaddingEnd();
        return getSafeSearchColumn(desiredCount, width);
    }

    private int getSafeSearchColumn(int desiredCount, int availableWidth) {
        int desired = Math.max(1, desiredCount);
        if (desired <= 1 || availableWidth <= 0) return desired;
        int minItemWidth = ResUtil.dp2px(SearchAdapter.GRID_MIN_WIDTH_DP);
        int itemMargin = ResUtil.dp2px(16);
        int maxCount = Math.max(1, availableWidth / (minItemWidth + itemMargin));
        return Math.max(1, Math.min(desired, maxCount));
    }

    private void applySearchColumn(int count) {
        int safeCount = Math.max(1, count);
        mSearchAdapter.setLoadImages(safeCount <= 1);
        if (appliedSearchColumn == safeCount) return;
        long start = SearchPerf.now();
        appliedSearchColumn = safeCount;
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(safeCount);
        mSearchAdapter.setColumnCount(safeCount);
        requireActivity().invalidateOptionsMenu();
        SearchPerf.slow("applyColumn", start, 16, "safe=%d desired=%d total=%d", safeCount, desiredSearchColumn, mSearchAdapter.getItemCount());
        SearchPerf.log("applyColumn safe=%d desired=%d total=%d", safeCount, desiredSearchColumn, mSearchAdapter.getItemCount());
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        Collect collect = addMasterCollect(result.getList());
        if (!matchFilter(collect.getSite())) return;
        addVisibleCollectItems(result.getList());
        if (!hasCollect(mCollectAdapter.getItems(), collect) && !hasCollect(pendingCollectItems, collect)) pendingCollectItems.add(collect);
        Collect activated = mCollectAdapter.getActivated();
        if (mCollectAdapter.getPosition() == 0) pendingSearchRender = true;
        else if (activated != null && activated.getSite().getKey().equals(collect.getSite().getKey())) pendingActiveSearchRender = true;
        scheduleSearchFlush();
    }

    private Collect addMasterCollect(List<Vod> items) {
        mAllCollectItems.get(0).getList().addAll(items);
        Site site = items.get(0).getSite();
        Collect collect = findCollect(mAllCollectItems, site.getKey());
        if (collect == null) {
            collect = new Collect(site, new ArrayList<>());
            mAllCollectItems.add(collect);
        }
        collect.getList().addAll(items);
        return collect;
    }

    private void addVisibleCollectItems(List<Vod> items) {
        if (mCollectAdapter.getItemCount() == 0) return;
        if (!mAllCollectItems.isEmpty() && mCollectAdapter.getItem(0) == mAllCollectItems.get(0)) return;
        mCollectAdapter.add(items);
    }

    private boolean hasCollect(List<Collect> items, Collect collect) {
        return findCollect(items, collect.getSite().getKey()) != null;
    }

    private Collect findCollect(List<Collect> items, String siteKey) {
        for (Collect item : items) if (item.getSite().getKey().equals(siteKey)) return item;
        return null;
    }

    private boolean matchFilter(Site site) {
        return TextUtils.isEmpty(mFilterGroup) || site.inGroup(mFilterGroup);
    }

    private void scheduleSearchFlush() {
        scheduleSearchFlush(searchScrolling ? SEARCH_SCROLL_DELAY : SEARCH_UPDATE_DELAY);
    }

    private void scheduleSearchFlush(long delayMillis) {
        if (searchFlushScheduled) return;
        searchFlushScheduled = true;
        SearchPerf.log("scheduleFlush delay=%d scrolling=%s pendingCollect=%d render=%s activeRender=%s total=%d", delayMillis, searchScrolling, pendingCollectItems.size(), pendingSearchRender, pendingActiveSearchRender, mSearchAdapter == null ? 0 : mSearchAdapter.getItemCount());
        App.post(flushSearchUpdates, delayMillis);
    }

    private void flushSearchUpdates() {
        long start = SearchPerf.now();
        int collectAdded = 0;
        boolean searchRendered = false;
        boolean activeRendered = false;
        searchFlushScheduled = false;
        if (mBinding == null) return;
        if (searchScrolling && mSearchAdapter.getItemCount() > 0) {
            SearchPerf.log("flush deferred scrolling total=%d pendingCollect=%d render=%s activeRender=%s", mSearchAdapter.getItemCount(), pendingCollectItems.size(), pendingSearchRender, pendingActiveSearchRender);
            return;
        }
        if (!pendingCollectItems.isEmpty()) {
            int count = Math.min(COLLECT_BATCH_SIZE, pendingCollectItems.size());
            List<Collect> items = new ArrayList<>(pendingCollectItems.subList(0, count));
            pendingCollectItems.subList(0, count).clear();
            mCollectAdapter.addAll(items);
            collectAdded = count;
        }
        if (pendingSearchRender && mCollectAdapter.getPosition() == 0) {
            pendingSearchRender = false;
            searchRendered = renderMoreSearchItems();
        } else if (mCollectAdapter.getPosition() != 0) {
            pendingSearchRender = false;
        }
        if (pendingActiveSearchRender) {
            pendingActiveSearchRender = false;
            activeRendered = renderMoreSearchItems();
        }
        SearchPerf.slow("flush", start, 16, "collect=%d render=%s activeRender=%s remainCollect=%d total=%d window=%d-%d source=%d grid=%s", collectAdded, searchRendered, activeRendered, pendingCollectItems.size(), mSearchAdapter.getItemCount(), renderedSearchStart, renderedSearchEnd, renderedSearchSource == null ? 0 : renderedSearchSource.size(), mSearchAdapter.isGridMode());
        SearchPerf.log("flush collect=%d render=%s activeRender=%s remainCollect=%d total=%d window=%d-%d source=%d grid=%s", collectAdded, searchRendered, activeRendered, pendingCollectItems.size(), mSearchAdapter.getItemCount(), renderedSearchStart, renderedSearchEnd, renderedSearchSource == null ? 0 : renderedSearchSource.size(), mSearchAdapter.isGridMode());
        SearchPerf.flushSummary();
        if (!pendingCollectItems.isEmpty() || pendingSearchRender || pendingActiveSearchRender) scheduleSearchFlush();
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
        if (!same) return;
        if (renderedSearchSource == mCollectAdapter.getActivated().getList()) {
            pendingActiveSearchRender = true;
            scheduleSearchFlush();
        }
    }

    @Override
    public void onItemClick(int position, Collect item) {
        flushSearchUpdates();
        setSearchItems(item.getList(), () -> mBinding.recycler.scrollToPosition(0));
        mCollectAdapter.setSelected(position);
        mScroller.setPage(item.getPage());
    }

    @Override
    public void onItemClick(Vod item) {
        if (item.isFolder()) FolderActivity.start(requireActivity(), item.getSiteKey(), Result.folder(item));
        else VideoActivity.collect(requireActivity(), item.getSiteKey(), item.getId(), item.getName(), item.getPic());
    }

    @Override
    public boolean onLoadMore(String page) {
        Collect activated = mCollectAdapter.getActivated();
        if (renderMoreSearchItems(true)) return false;
        if ("all".equals(activated.getSite().getKey())) return false;
        mViewModel.searchContent(activated.getSite(), getKeyword(), false, page);
        activated.setPage(Integer.parseInt(page));
        return true;
    }

    @Override
    public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
        menuInflater.inflate(R.menu.menu_collect, menu);
    }

    @Override
    public void onPrepareMenu(@NonNull Menu menu) {
        MenuItem item = menu.findItem(R.id.action_view);
        if (item != null && mSearchAdapter != null) item.setIcon(isSearchGridDesired() ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
        MenuItem group = menu.findItem(R.id.action_group);
        if (group != null) {
            group.setVisible(canFilterGroup());
            group.setTitle(TextUtils.isEmpty(mFilterGroup) ? getString(R.string.search_scope_all) : mFilterGroup);
        }
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (menuItem.getItemId() == R.id.action_view) {
            setSearchView(!isSearchGridDesired());
            return true;
        }
        if (menuItem.getItemId() == R.id.action_group) {
            onGroupFilter();
            return true;
        }
        return true;
    }

    private boolean canFilterGroup() {
        return !isSiteSearch() && !isGroupSearch() && mGroups != null && !mGroups.isEmpty();
    }

    private void onGroupFilter() {
        if (!canFilterGroup()) return;
        View anchor = mBinding.toolbar.findViewById(R.id.action_group);
        showGroupPopup(anchor == null ? mBinding.toolbar : anchor);
    }

    private void showGroupPopup(View anchor) {
        if (groupPopup != null) groupPopup.dismiss();
        int width = getGroupPopupWidth();
        int height = getGroupPopupHeight();
        ScrollView scroll = new ScrollView(requireContext());
        LinearLayoutCompat content = new LinearLayoutCompat(requireContext());
        content.setOrientation(LinearLayoutCompat.VERTICAL);
        content.setPadding(0, ResUtil.dp2px(6), 0, ResUtil.dp2px(6));
        scroll.setBackground(getGroupPopupBackground());
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.addView(content, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addGroupPopupItem(content, getString(R.string.search_scope_all), MENU_GROUP_ALL);
        for (int i = 0; i < mGroups.size(); i++) addGroupPopupItem(content, mGroups.get(i), MENU_GROUP_OFFSET + i);
        groupPopup = new PopupWindow(scroll, width, height, true);
        groupPopup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        groupPopup.setOutsideTouchable(true);
        groupPopup.setElevation(ResUtil.dp2px(6));
        groupPopup.showAsDropDown(anchor, anchor.getWidth() - width, 0);
    }

    private GradientDrawable getGroupPopupBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.WHITE);
        drawable.setCornerRadius(ResUtil.dp2px(6));
        return drawable;
    }

    private int getGroupPopupWidth() {
        int width = ResUtil.getTextWidth(getString(R.string.search_scope_all), 16);
        for (String group : mGroups) width = Math.max(width, ResUtil.getTextWidth(group, 16));
        int contentWidth = width + ResUtil.dp2px(36);
        int maxWidth = ResUtil.getScreenWidth(requireContext()) - ResUtil.dp2px(32);
        return Math.min(contentWidth, maxWidth);
    }

    private int getGroupPopupHeight() {
        int itemHeight = ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT);
        int padding = ResUtil.dp2px(12);
        int contentHeight = (mGroups.size() + 1) * itemHeight + padding;
        int maxHeight = Math.min(ResUtil.getScreenHeight(requireContext()) - mBinding.toolbar.getHeight() - ResUtil.dp2px(32), GROUP_POPUP_MAX_ITEMS * itemHeight + padding);
        return Math.min(contentHeight, Math.max(itemHeight + padding, maxHeight));
    }

    private void addGroupPopupItem(LinearLayoutCompat content, String text, int itemId) {
        MaterialTextView view = new MaterialTextView(requireContext());
        view.setText(text);
        view.setSingleLine(true);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setIncludeFontPadding(false);
        view.setTextColor(0xFF202124);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        view.setPadding(ResUtil.dp2px(18), 0, ResUtil.dp2px(18), 0);
        view.setBackgroundResource(getSelectableItemBackground());
        view.setOnClickListener(v -> {
            if (groupPopup != null) groupPopup.dismiss();
            onGroupFilterSelected(itemId);
        });
        content.addView(view, new LinearLayoutCompat.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ResUtil.dp2px(GROUP_POPUP_ITEM_HEIGHT)));
    }

    private int getSelectableItemBackground() {
        TypedValue value = new TypedValue();
        requireContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, value, true);
        return value.resourceId;
    }

    private boolean onGroupFilterSelected(int itemId) {
        if (itemId == MENU_GROUP_ALL) {
            setFilterGroup("");
        } else if (itemId >= MENU_GROUP_OFFSET) {
            int index = itemId - MENU_GROUP_OFFSET;
            if (index >= 0 && index < mGroups.size()) setFilterGroup(mGroups.get(index));
        }
        return true;
    }

    private void setFilterGroup(String group) {
        flushSearchUpdates();
        mFilterGroup = group == null ? "" : group;
        applyFilterGroup(getActiveSiteKey());
        requireActivity().invalidateOptionsMenu();
    }

    private String getActiveSiteKey() {
        if (mCollectAdapter == null || mCollectAdapter.getItemCount() == 0) return "all";
        return mCollectAdapter.getActivated().getSite().getKey();
    }

    private void applyFilterGroup(String activeSiteKey) {
        clearPendingSearchItems();
        List<Collect> items = getFilteredCollectItems(activeSiteKey);
        Collect activated = getSelectedCollect(items);
        mCollectAdapter.setItems(items, () -> {
            setSearchItems(activated.getList(), () -> mBinding.recycler.scrollToPosition(0));
            mScroller.setPage(activated.getPage());
        });
    }

    private List<Collect> getFilteredCollectItems(String activeSiteKey) {
        List<Collect> items = new ArrayList<>();
        boolean allGroup = TextUtils.isEmpty(mFilterGroup);
        Collect all = allGroup && !mAllCollectItems.isEmpty() ? mAllCollectItems.get(0) : Collect.all();
        all.setSelected(false);
        items.add(all);
        for (int i = 1; i < mAllCollectItems.size(); i++) {
            Collect item = mAllCollectItems.get(i);
            if (!matchFilter(item.getSite())) continue;
            if (!allGroup) all.getList().addAll(item.getList());
            item.setSelected(item.getSite().getKey().equals(activeSiteKey));
            items.add(item);
        }
        if (getSelectedCollect(items) == all) all.setSelected(true);
        return items;
    }

    private Collect getSelectedCollect(List<Collect> items) {
        for (Collect item : items) if (item.isSelected()) return item;
        return items.get(0);
    }

    private void clearPendingSearchItems() {
        pendingCollectItems.clear();
        pendingSearchRender = false;
        pendingActiveSearchRender = false;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.removeCallbacks(flushSearchUpdates, restoreSearchImages, renderSearchWindow);
        if (groupPopup != null) groupPopup.dismiss();
        groupPopup = null;
        pendingCollectItems.clear();
        mAllCollectItems.clear();
        renderedSearchSource = List.of();
        renderedSearchStart = 0;
        renderedSearchEnd = 0;
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        resetLoadedSearchImages();
        pendingSearchRender = false;
        pendingActiveSearchRender = false;
        windowRenderScheduled = false;
        imageRestoreScheduled = false;
        searchFlushScheduled = false;
        searchScrolling = false;
        mViewModel.stopSearch();
        SiteHealthStore.flush();
        requireActivity().removeMenuProvider(this);
        mBinding = null;
    }
}
