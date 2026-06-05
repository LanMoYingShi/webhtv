package com.fongmi.android.tv.ui.adapter;

import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Vod;
import com.fongmi.android.tv.databinding.AdapterSearchBinding;
import com.fongmi.android.tv.databinding.AdapterVodRectBinding;
import com.fongmi.android.tv.utils.ImgUtil;
import com.fongmi.android.tv.utils.ResUtil;

public class SearchAdapter extends BaseDiffAdapter<Vod, RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_LIST = 1;
    private static final int VIEW_TYPE_GRID = 2;

    private final OnClickListener listener;
    private int columnCount = 1;

    public SearchAdapter(OnClickListener listener) {
        this.listener = listener;
    }

    public interface OnClickListener {

        void onItemClick(Vod item);
    }

    public void setColumnCount(int columnCount) {
        int count = Math.max(1, columnCount);
        if (this.columnCount == count) return;
        this.columnCount = count;
        notifyDataSetChanged();
    }

    private boolean isGrid() {
        return columnCount > 1;
    }

    private void loadGridImage(Vod item, ImageView image) {
        image.setScaleType(ImageView.ScaleType.CENTER_CROP);
        image.setImageDrawable(null);
        ColorDrawable placeholder = new ColorDrawable(ResUtil.getColor(R.color.black_20));
        if (TextUtils.isEmpty(item.getPic())) {
            image.setImageDrawable(placeholder);
            return;
        }
        Glide.with(image).load(ImgUtil.getUrl(item.getPic())).placeholder(placeholder).error(placeholder).centerCrop().into(image);
    }

    @Override
    public int getItemViewType(int position) {
        return isGrid() ? VIEW_TYPE_GRID : VIEW_TYPE_LIST;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_GRID) return new GridViewHolder(AdapterVodRectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false)).size(parent, columnCount);
        return new ListViewHolder(AdapterSearchBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Vod item = getItem(position);
        if (holder instanceof GridViewHolder grid) {
            grid.bind(item);
            return;
        }
        ((ListViewHolder) holder).bind(item);
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof GridViewHolder grid) Glide.with(grid.binding.image).clear(grid.binding.image);
        if (holder instanceof ListViewHolder list) Glide.with(list.binding.image).clear(list.binding.image);
    }

    public class ListViewHolder extends RecyclerView.ViewHolder {

        private final AdapterSearchBinding binding;

        ListViewHolder(@NonNull AdapterSearchBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void bind(Vod item) {
            binding.name.setText(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.site.setVisibility(item.getSiteVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            ImgUtil.load(item.getName(), item.getPic(), binding.image);
        }
    }

    public class GridViewHolder extends RecyclerView.ViewHolder {

        private final AdapterVodRectBinding binding;

        GridViewHolder(@NonNull AdapterVodRectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private GridViewHolder size(ViewGroup parent, int columnCount) {
            int margin = ResUtil.dp2px(16);
            int available = parent.getWidth() - parent.getPaddingStart() - parent.getPaddingEnd();
            if (available <= 0) available = ResUtil.getScreenWidth(parent.getContext());
            int width = Math.max(ResUtil.dp2px(96), available / Math.max(1, columnCount) - margin);
            binding.getRoot().getLayoutParams().width = width;
            binding.image.getLayoutParams().height = (int) (width / 0.75f);
            return this;
        }

        private void bind(Vod item) {
            if (binding.getRoot().getParent() instanceof ViewGroup parent) size(parent, columnCount);
            binding.name.setText(item.getName());
            binding.site.setText(item.getSiteName());
            binding.remark.setText(item.getRemarks());
            binding.year.setVisibility(item.getYearVisible());
            binding.year.setText(item.getYear());
            binding.site.setVisibility(item.getSiteVisible());
            binding.name.setVisibility(item.getNameVisible());
            binding.remark.setVisibility(item.getRemarkVisible());
            binding.getRoot().setOnClickListener(v -> listener.onItemClick(item));
            loadGridImage(item, binding.image);
        }
    }
}
