package com.fongmi.android.tv.ui.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.viewbinding.ViewBinding;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.AudioConfig;
import com.fongmi.android.tv.bean.ShortDramaConfig;
import com.fongmi.android.tv.bean.TmdbConfig;
import com.fongmi.android.tv.databinding.ActivitySettingEnhanceBinding;
import com.fongmi.android.tv.setting.CustomCspSetting;
import com.fongmi.android.tv.setting.ProxySetting;
import com.fongmi.android.tv.setting.Setting;
import com.fongmi.android.tv.ui.base.BaseActivity;
import com.fongmi.android.tv.ui.dialog.CustomCspDialog;
import com.fongmi.android.tv.ui.dialog.DebugLogDialog;
import com.fongmi.android.tv.ui.dialog.FeatureConfigDialog;
import com.fongmi.android.tv.ui.dialog.ManagePageDialog;
import com.fongmi.android.tv.ui.dialog.OneKeySyncDialog;
import com.fongmi.android.tv.ui.dialog.ShellProxyDialog;
import com.fongmi.android.tv.utils.Notify;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class SettingEnhanceActivity extends BaseActivity {

    private static final int[] SEARCH_THREADS = {1, 2, 4, 6, 8, 10, 12, 16, 20, 32};
    private static final int[] DETAIL_OPEN_MODES = {Setting.DETAIL_OPEN_FUSION, Setting.DETAIL_OPEN_ENHANCED, Setting.DETAIL_OPEN_CINEMA, Setting.DETAIL_OPEN_DIRECT};

    private ActivitySettingEnhanceBinding mBinding;

    public static void start(Activity activity) {
        activity.startActivity(new Intent(activity, SettingEnhanceActivity.class));
    }

    private String getSwitch(boolean value) {
        return getString(value ? R.string.setting_enable : R.string.setting_disable);
    }

    @Override
    protected ViewBinding getBinding() {
        return mBinding = ActivitySettingEnhanceBinding.inflate(getLayoutInflater());
    }

    @Override
    protected void initView(Bundle savedInstanceState) {
        mBinding.detailOpenMode.requestFocus();
        setText();
    }

    @Override
    protected void initEvent() {
        mBinding.detailOpenMode.setOnClickListener(this::setDetailOpenMode);
        mBinding.searchThread.setOnClickListener(this::setSearchThread);
        mBinding.tmdbConfig.setOnClickListener(this::setTmdbConfig);
        mBinding.audioConfig.setOnClickListener(this::setAudioConfig);
        mBinding.shortDramaConfig.setOnClickListener(this::setShortDramaConfig);
        mBinding.driveCheck.setOnClickListener(this::setDriveCheck);
        mBinding.debugLog.setOnClickListener(this::setDebugLog);
        mBinding.managePage.setOnClickListener(view -> ManagePageDialog.show(this));
        mBinding.shellProxy.setOnClickListener(this::setShellProxy);
        mBinding.shellProxyConfig.setOnClickListener(this::setShellProxyConfig);
        mBinding.customCsp.setOnClickListener(view -> CustomCspDialog.show(this, this::setText));
        mBinding.oneKeySync.setOnClickListener(this::setOneKeySync);
    }

    private void setText() {
        mBinding.detailOpenModeText.setText(getDetailOpenMode());
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
        mBinding.tmdbConfigText.setText(TmdbConfig.objectFrom(Setting.getTmdbConfig()).isReady() ? R.string.setting_configured : R.string.setting_unconfigured);
        mBinding.audioConfigText.setText(AudioConfig.objectFrom(Setting.getAudioConfig()).getDisplayRules());
        mBinding.shortDramaConfigText.setText(ShortDramaConfig.objectFrom(Setting.getShortDramaConfig()).getDisplayRules());
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        mBinding.managePageText.setText(R.string.manage_page_web);
        mBinding.shellProxyText.setText(getSwitch(Setting.isShellProxy()));
        mBinding.shellProxyConfig.setVisibility(Setting.isShellProxy() ? View.VISIBLE : View.GONE);
        mBinding.shellProxyConfigText.setText(getShellProxyConfigText());
        CustomCspSetting.Registry registry = CustomCspSetting.load();
        CustomCspSetting.Count count = CustomCspSetting.count();
        mBinding.customCspText.setText(getSwitch(registry.isEnabled()) + " · " + getString(R.string.setting_custom_csp_count, count.active(), count.enabled()));
    }

    private String getShellProxyConfigText() {
        int count = ProxySetting.getRules().size();
        return count > 0 ? getString(R.string.setting_proxy_rule_count, count) : Setting.getShellProxyUrl();
    }

    private String getDetailOpenMode() {
        String[] labels = getDetailOpenModes();
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return labels[i];
        return labels[1];
    }

    private String[] getDetailOpenModes() {
        return new String[]{getString(R.string.setting_detail_open_fusion), getString(R.string.setting_detail_open_enhanced), getString(R.string.setting_detail_open_cinema), getString(R.string.setting_detail_open_direct)};
    }

    private void setSearchThread(View view) {
        int index = 0;
        for (int i = 0; i < SEARCH_THREADS.length; i++) if (SEARCH_THREADS[i] == Setting.getSearchThread()) index = i;
        Setting.putSearchThread(SEARCH_THREADS[(index + 1) % SEARCH_THREADS.length]);
        mBinding.searchThreadText.setText(String.valueOf(Setting.getSearchThread()));
    }

    private void setDetailOpenMode(View view) {
        new MaterialAlertDialogBuilder(this).setTitle(R.string.setting_detail_open_mode).setNegativeButton(R.string.dialog_negative, null).setSingleChoiceItems(getDetailOpenModes(), getDetailOpenModeIndex(), (dialog, which) -> {
            int mode = DETAIL_OPEN_MODES[which];
            if (requiresTmdb(mode) && !Setting.isTmdbReady()) {
                dialog.dismiss();
                Notify.show(R.string.detail_tmdb_need_key);
                FeatureConfigDialog.create(this).type(FeatureConfigDialog.TMDB).onDismiss(() -> {
                    if (Setting.isTmdbReady()) Setting.putDetailOpenMode(mode);
                    setText();
                }).show();
                return;
            }
            Setting.putDetailOpenMode(mode);
            mBinding.detailOpenModeText.setText(getDetailOpenMode());
            dialog.dismiss();
        }).show();
    }

    private int getDetailOpenModeIndex() {
        int mode = Setting.getDetailOpenMode();
        for (int i = 0; i < DETAIL_OPEN_MODES.length; i++) if (DETAIL_OPEN_MODES[i] == mode) return i;
        return 1;
    }

    private boolean requiresTmdb(int mode) {
        return Setting.isTmdbMode(mode);
    }

    private void setTmdbConfig(View view) {
        FeatureConfigDialog.create(this).type(FeatureConfigDialog.TMDB).onDismiss(this::setText).show();
    }

    private void setAudioConfig(View view) {
        FeatureConfigDialog.create(this).type(FeatureConfigDialog.AUDIO).onDismiss(this::setText).show();
    }

    private void setShortDramaConfig(View view) {
        FeatureConfigDialog.create(this).type(FeatureConfigDialog.SHORT_DRAMA).onDismiss(this::setText).show();
    }

    private void setDriveCheck(View view) {
        Setting.putDriveCheck(!Setting.isDriveCheck());
        mBinding.driveCheckText.setText(getSwitch(Setting.isDriveCheck()));
    }

    private void setDebugLog(View view) {
        if (!Setting.isDebugLog()) Setting.putDebugLog(true);
        mBinding.debugLogText.setText(getSwitch(Setting.isDebugLog()));
        DebugLogDialog.show(this, this::setText);
    }

    private void setShellProxy(View view) {
        Setting.putShellProxy(!Setting.isShellProxy());
        setText();
    }

    private void setShellProxyConfig(View view) {
        ShellProxyDialog.show(this, this::setText);
    }

    private void setOneKeySync(View view) {
        OneKeySyncDialog.create().show(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        setText();
    }
}
