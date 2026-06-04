package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.bean.TmdbPerson;
import com.fongmi.android.tv.databinding.AdapterTmdbPersonBinding;
import com.fongmi.android.tv.utils.ImgUtil;

import java.util.ArrayList;
import java.util.List;

public class TmdbPersonAdapter extends RecyclerView.Adapter<TmdbPersonAdapter.ViewHolder> {

    public interface Listener {
        void onItemClick(TmdbPerson item);
    }

    private final Listener listener;
    private final List<TmdbPerson> items = new ArrayList<>();
    private boolean cinema;

    public TmdbPersonAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<TmdbPerson> values) {
        items.clear();
        items.addAll(values);
        notifyDataSetChanged();
    }

    public void setCinema(boolean cinema) {
        this.cinema = cinema;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterTmdbPersonBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        TmdbPerson item = items.get(position);
        holder.binding.name.setText(item.getName());
        holder.binding.subtitle.setText(item.getSubtitle());
        holder.binding.name.setTextColor(0xFFFFFFFF);
        holder.binding.subtitle.setTextColor(cinema ? 0xB3FFFFFF : 0x99FFFFFF);
        applyCardStyle(holder);
        TmdbCardFocusHelper.bind(holder.binding.getRoot(), cinema ? 0x00000000 : 0xFF16202A, cinema ? 0x00FFFFFF : 0x33FFFFFF, cinema ? 0 : 1);
        ImgUtil.load(item.getName(), item.getProfileUrl(), holder.binding.photo);
        holder.binding.getRoot().setOnClickListener(view -> listener.onItemClick(item));
    }

    private void applyCardStyle(ViewHolder holder) {
        ViewGroup.LayoutParams params = holder.binding.getRoot().getLayoutParams();
        params.width = dp(holder.itemView, cinema ? 250 : 90);
        holder.binding.getRoot().setLayoutParams(params);
        if (params instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMarginEnd(dp(holder.itemView, cinema ? 18 : 12));
            holder.binding.getRoot().setLayoutParams(marginParams);
        }
        holder.binding.content.setOrientation(cinema ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        holder.binding.content.setGravity(cinema ? android.view.Gravity.CENTER_VERTICAL : 0);
        ViewGroup.LayoutParams photoParams = holder.binding.photo.getLayoutParams();
        photoParams.width = dp(holder.itemView, cinema ? 86 : 90);
        photoParams.height = dp(holder.itemView, cinema ? 86 : 118);
        holder.binding.photo.setLayoutParams(photoParams);
        if (photoParams instanceof ViewGroup.MarginLayoutParams marginParams) {
            marginParams.setMargins(0, 0, 0, 0);
            holder.binding.photo.setLayoutParams(marginParams);
        }
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(cinema ? 0 : ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, cinema ? 1f : 0f);
        textParams.setMarginStart(dp(holder.itemView, cinema ? 14 : 0));
        holder.binding.text.setLayoutParams(textParams);
        holder.binding.text.setPadding(dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8), dp(holder.itemView, cinema ? 0 : 8));
        holder.binding.name.setTextSize(cinema ? 17f : 12f);
        holder.binding.subtitle.setTextSize(cinema ? 13f : 10f);
        holder.binding.subtitle.setMaxLines(cinema ? 1 : 2);
        holder.binding.photo.setClipToOutline(true);
        if (cinema) holder.binding.photo.setBackgroundColor(0x26FFFFFF);
    }

    private int dp(View view, int value) {
        return Math.round(value * view.getResources().getDisplayMetrics().density);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterTmdbPersonBinding binding;

        ViewHolder(@NonNull AdapterTmdbPersonBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
