package com.fongmi.android.tv.ui.fragment;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
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
import com.fongmi.android.tv.setting.SiteHealthStore;
import com.fongmi.android.tv.ui.activity.FolderActivity;
import com.fongmi.android.tv.ui.activity.VideoActivity;
import com.fongmi.android.tv.ui.adapter.CollectAdapter;
import com.fongmi.android.tv.ui.adapter.SearchAdapter;
import com.fongmi.android.tv.ui.base.BaseFragment;
import com.fongmi.android.tv.ui.custom.CustomScroller;
import com.fongmi.android.tv.utils.Notify;

import java.util.ArrayList;
import java.util.List;

public class CollectFragment extends BaseFragment implements MenuProvider, CollectAdapter.OnClickListener, SearchAdapter.OnClickListener, CustomScroller.Callback {

    private static final int SEARCH_COLUMN_COUNT = 2;
    private static final long SEARCH_UPDATE_DELAY = 80;
    private static final long SEARCH_SCROLL_DELAY = 180;
    private static final long SEARCH_FIRST_IMAGE_DELAY = 0;
    private static final long SEARCH_AFTER_SCROLL_DELAY = 240;
    private static final long SEARCH_IMAGE_DELAY = 80;
    private static final int SEARCH_BATCH_SIZE = 24;
    private static final int SEARCH_IMAGE_BATCH_SIZE = 2;
    private static final int COLLECT_BATCH_SIZE = 8;

    private FragmentCollectBinding mBinding;
    private CollectAdapter mCollectAdapter;
    private SearchAdapter mSearchAdapter;
    private CustomScroller mScroller;
    private SiteViewModel mViewModel;
    private List<Site> mSites;
    private final List<Collect> pendingCollectItems;
    private final List<Vod> pendingSearchItems;
    private final List<Vod> pendingActiveSearchItems;
    private final Runnable flushSearchUpdates;
    private final Runnable restoreSearchImages;
    private String pendingActiveSiteKey;
    private int pendingImageStart;
    private int pendingImageEnd;
    private int loadedImageStart;
    private int loadedImageEnd;
    private boolean imageRestoreScheduled;
    private boolean searchScrolling;

    public CollectFragment() {
        pendingCollectItems = new ArrayList<>();
        pendingSearchItems = new ArrayList<>();
        pendingActiveSearchItems = new ArrayList<>();
        flushSearchUpdates = this::flushSearchUpdates;
        restoreSearchImages = this::restoreSearchImages;
        pendingActiveSiteKey = "";
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        loadedImageStart = RecyclerView.NO_POSITION;
        loadedImageEnd = RecyclerView.NO_POSITION;
    }

    public static CollectFragment newInstance(String keyword) {
        return newInstance(keyword, "");
    }

    public static CollectFragment newInstance(String keyword, String siteKey) {
        Bundle args = new Bundle();
        args.putString("keyword", keyword);
        args.putString("siteKey", siteKey);
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

    private boolean isSiteSearch() {
        return !TextUtils.isEmpty(getSiteKey());
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
        activity.setTitle(isSiteSearch() ? getString(R.string.search_result_current, getKeyword()) : getKeyword());
    }

    @Override
    protected void initView() {
        mScroller = new CustomScroller(this);
        setRecyclerView();
        setViewModel();
        setSites();
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
        mBinding.collect.setAdapter(mCollectAdapter = new CollectAdapter(this));
        mBinding.recycler.setItemAnimator(null);
        mBinding.recycler.setHasFixedSize(true);
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
                    imageRestoreScheduled = false;
                    resetLoadedSearchImages();
                } else {
                    scheduleVisibleSearchImages(SEARCH_AFTER_SCROLL_DELAY);
                    App.post(flushSearchUpdates, SEARCH_AFTER_SCROLL_DELAY);
                }
            }
        });
        mBinding.recycler.setAdapter(mSearchAdapter = new SearchAdapter(this));
        setFixedSearchGrid();
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
        imageRestoreScheduled = true;
        App.post(restoreSearchImages, delayMillis);
    }

    private void restoreSearchImages() {
        imageRestoreScheduled = false;
        if (mBinding == null || mSearchAdapter == null || searchScrolling) return;
        if (pendingImageStart == RecyclerView.NO_POSITION || pendingImageEnd < pendingImageStart) return;
        int count = Math.min(SEARCH_IMAGE_BATCH_SIZE, pendingImageEnd - pendingImageStart + 1);
        for (int i = 0; i < count; i++) {
            int position = pendingImageStart + i;
            RecyclerView.ViewHolder holder = mBinding.recycler.findViewHolderForAdapterPosition(position);
            if (holder != null) {
                mSearchAdapter.loadImage(holder);
                markSearchImageLoaded(position);
            }
        }
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

    private void addSearchItems(List<Vod> items) {
        if (items.isEmpty()) return;
        if (mSearchAdapter.isGridMode()) mSearchAdapter.setLoadImages(false);
        mSearchAdapter.addAll(items, () -> scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY));
    }

    private void setSearchItems(List<Vod> items, Runnable runnable) {
        resetLoadedSearchImages();
        if (mSearchAdapter.isGridMode()) mSearchAdapter.setLoadImages(false);
        mSearchAdapter.setItems(items, () -> {
            runnable.run();
            scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
        });
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
        mSites = new ArrayList<>();
        for (Site site : VodConfig.get().getSites()) {
            if (!site.isSearchable()) continue;
            if (!TextUtils.isEmpty(siteKey) && !site.getKey().equals(siteKey)) continue;
            mSites.add(site);
        }
        SiteHealthStore.sortSites(mSites);
    }

    private void search() {
        if (mSites.isEmpty()) {
            if (isSiteSearch()) Notify.show(R.string.detail_site_not_searchable);
            return;
        }
        mCollectAdapter.setItems(List.of(Collect.all()), () -> mViewModel.searchContent(mSites, getKeyword(), false));
    }

    private void setFixedSearchGrid() {
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(SEARCH_COLUMN_COUNT);
        mSearchAdapter.setColumnCount(SEARCH_COLUMN_COUNT);
        postUpdateGridWidth();
    }

    private void setSearchView(boolean grid) {
        int count = grid ? SEARCH_COLUMN_COUNT : 1;
        App.removeCallbacks(restoreSearchImages);
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        imageRestoreScheduled = false;
        resetLoadedSearchImages();
        mSearchAdapter.setLoadImages(!grid);
        ((GridLayoutManager) (mBinding.recycler.getLayoutManager())).setSpanCount(count);
        mSearchAdapter.setColumnCount(count);
        mBinding.recycler.post(() -> {
            updateGridWidth();
            if (grid) scheduleVisibleSearchImages(SEARCH_FIRST_IMAGE_DELAY);
        });
        requireActivity().invalidateOptionsMenu();
    }

    private void postUpdateGridWidth() {
        mBinding.recycler.post(this::updateGridWidth);
    }

    private void updateGridWidth() {
        int width = mBinding.recycler.getWidth() - mBinding.recycler.getPaddingStart() - mBinding.recycler.getPaddingEnd();
        if (width > 0) mSearchAdapter.setGridWidth(width);
    }

    private void setCollect(Result result) {
        if (result == null || result.getList().isEmpty()) return;
        mCollectAdapter.add(result.getList());
        pendingCollectItems.add(Collect.create(result.getList()));
        if (mCollectAdapter.getPosition() == 0) pendingSearchItems.addAll(result.getList());
        scheduleSearchFlush();
    }

    private void scheduleSearchFlush() {
        App.post(flushSearchUpdates, searchScrolling ? SEARCH_SCROLL_DELAY : SEARCH_UPDATE_DELAY);
    }

    private void flushSearchUpdates() {
        if (mBinding == null) return;
        if (searchScrolling && mSearchAdapter.getItemCount() > 0) {
            App.post(flushSearchUpdates, SEARCH_SCROLL_DELAY);
            return;
        }
        if (!pendingCollectItems.isEmpty()) {
            int count = Math.min(COLLECT_BATCH_SIZE, pendingCollectItems.size());
            List<Collect> items = new ArrayList<>(pendingCollectItems.subList(0, count));
            pendingCollectItems.subList(0, count).clear();
            mCollectAdapter.addAll(items);
        }
        if (!pendingSearchItems.isEmpty()) {
            int count = Math.min(SEARCH_BATCH_SIZE, pendingSearchItems.size());
            List<Vod> items = new ArrayList<>(pendingSearchItems.subList(0, count));
            pendingSearchItems.subList(0, count).clear();
            if (mCollectAdapter.getPosition() == 0) addSearchItems(items);
        }
        if (!pendingActiveSearchItems.isEmpty()) {
            Collect activated = mCollectAdapter.getActivated();
            boolean same = activated != null && activated.getSite().getKey().equals(pendingActiveSiteKey);
            if (same) {
                int count = Math.min(SEARCH_BATCH_SIZE, pendingActiveSearchItems.size());
                List<Vod> items = new ArrayList<>(pendingActiveSearchItems.subList(0, count));
                pendingActiveSearchItems.subList(0, count).clear();
                addSearchItems(items);
            } else {
                pendingActiveSearchItems.clear();
                pendingActiveSiteKey = "";
            }
        }
        if (!pendingCollectItems.isEmpty() || !pendingSearchItems.isEmpty() || !pendingActiveSearchItems.isEmpty()) scheduleSearchFlush();
    }

    private void setSearch(Result result) {
        if (result == null) return;
        mScroller.endLoading(result);
        boolean same = !result.getList().isEmpty() && mCollectAdapter.getActivated().getSite().equals(result.getVod().getSite());
        if (same) mCollectAdapter.getActivated().getList().addAll(result.getList());
        if (!same) return;
        if (searchScrolling && mSearchAdapter.getItemCount() > 0) {
            addPendingActiveSearchItems(result.getVod().getSite().getKey(), result.getList());
            return;
        }
        addSearchItems(result.getList());
    }

    private void addPendingActiveSearchItems(String siteKey, List<Vod> items) {
        if (!pendingActiveSiteKey.equals(siteKey)) {
            pendingActiveSearchItems.clear();
            pendingActiveSiteKey = siteKey;
        }
        pendingActiveSearchItems.addAll(items);
        scheduleSearchFlush();
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
        if (item != null && mSearchAdapter != null) item.setIcon(mSearchAdapter.isGridMode() ? R.drawable.ic_action_list : R.drawable.ic_action_grid);
    }

    @Override
    public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            requireActivity().getOnBackPressedDispatcher().onBackPressed();
            return true;
        }
        if (menuItem.getItemId() == R.id.action_view) {
            setSearchView(!mSearchAdapter.isGridMode());
            return true;
        }
        return true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        if (hidden) requireActivity().removeMenuProvider(this);
        else initMenu();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        App.removeCallbacks(flushSearchUpdates, restoreSearchImages);
        pendingCollectItems.clear();
        pendingSearchItems.clear();
        pendingActiveSearchItems.clear();
        pendingActiveSiteKey = "";
        pendingImageStart = RecyclerView.NO_POSITION;
        pendingImageEnd = RecyclerView.NO_POSITION;
        resetLoadedSearchImages();
        imageRestoreScheduled = false;
        searchScrolling = false;
        mViewModel.stopSearch();
        SiteHealthStore.flush();
        requireActivity().removeMenuProvider(this);
        mBinding = null;
    }
}
