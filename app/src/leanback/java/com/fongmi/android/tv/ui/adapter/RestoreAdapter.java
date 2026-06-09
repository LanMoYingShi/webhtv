package com.fongmi.android.tv.ui.adapter;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.databinding.AdapterRestoreBinding;
import com.fongmi.android.tv.utils.Formatters;
import com.fongmi.android.tv.utils.ResUtil;
import com.github.catvod.utils.Path;

import java.io.File;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RestoreAdapter extends RecyclerView.Adapter<RestoreAdapter.ViewHolder> {

    private static final String BACKUP_PREFIX = "WebHomeTV-";
    private static final String LEGACY_BACKUP_PREFIX = "tv-";
    private static final Pattern DATE_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}");

    private final OnClickListener listener;
    private final List<File> mItems;

    public RestoreAdapter(OnClickListener listener) {
        this.mItems = new ArrayList<>();
        this.listener = listener;
        this.addAll();
    }

    public interface OnClickListener {

        void onItemClick(File item);

        void onDeleteClick(File item);
    }

    public void addAll() {
        File[] files = Path.tv().listFiles();
        if (files == null) files = new File[0];
        for (File file : files) if (isBackup(file)) mItems.add(file);
        if (!mItems.isEmpty()) mItems.sort((f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified()));
        notifyDataSetChanged();
    }

    private boolean isBackup(File file) {
        String name = file.getName();
        return name.endsWith(".bk.gz") && (name.startsWith(BACKUP_PREFIX) || name.startsWith(LEGACY_BACKUP_PREFIX));
    }

    private String displayDate(File file) {
        String name = file.getName();
        Matcher matcher = DATE_PATTERN.matcher(name);
        return matcher.find() ? matcher.group() : name;
    }

    private String displayName(File file) {
        return getSource(file.getName());
    }

    private String displayMeta(File file) {
        String date = displayDate(file);
        String modified = Formatters.TIME_SEC.format(Instant.ofEpochMilli(file.lastModified()));
        return ResUtil.getString(R.string.restore_backup_meta, date, modified);
    }

    private String getSource(String name) {
        Matcher matcher = DATE_PATTERN.matcher(name);
        if (!matcher.find()) return name;
        if (name.startsWith(BACKUP_PREFIX)) return "WebHomeTV" + getOwner(name, matcher);
        if (name.startsWith(LEGACY_BACKUP_PREFIX)) return "tv";
        return name;
    }

    private String getOwner(String name, Matcher matcher) {
        String base = name.substring(BACKUP_PREFIX.length(), name.length() - ".bk.gz".length());
        String before = base.substring(0, matcher.start() - BACKUP_PREFIX.length()).replaceAll("^-+|-+$", "");
        String after = base.substring(matcher.end() - BACKUP_PREFIX.length()).replaceAll("^-+|-+$", "");
        String owner = after.isEmpty() ? before.substring(before.lastIndexOf('-') + 1) : after;
        return owner.isEmpty() ? "" : " - " + owner;
    }

    public int remove(File item) {
        int position = mItems.indexOf(item);
        if (position == -1) return -1;
        Path.clear(item);
        mItems.remove(position);
        notifyItemRemoved(position);
        return getItemCount();
    }

    @Override
    public int getItemCount() {
        return mItems.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(AdapterRestoreBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        File item = mItems.get(position);
        holder.binding.name.setText(displayName(item));
        holder.binding.meta.setText(displayMeta(item));
        holder.binding.content.setOnClickListener(v -> listener.onItemClick(item));
        holder.binding.delete.setOnClickListener(v -> listener.onDeleteClick(item));
        holder.binding.content.setOnFocusChangeListener((v, hasFocus) -> holder.updateCardFocus());
        holder.binding.delete.setOnFocusChangeListener((v, hasFocus) -> holder.updateCardFocus());
        holder.updateCardFocus();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {

        private final AdapterRestoreBinding binding;

        public ViewHolder(@NonNull AdapterRestoreBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        private void updateCardFocus() {
            binding.card.setActivated(binding.content.hasFocus() || binding.delete.hasFocus());
        }
    }
}
