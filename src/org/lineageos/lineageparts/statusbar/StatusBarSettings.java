/*
 * SPDX-FileCopyrightText: 2014-2015 The CyanogenMod Project
 * SPDX-FileCopyrightText: 2017-2024 The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */
package org.lineageos.lineageparts.statusbar;

import android.content.Context;
import android.content.Intent;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.text.TextUtils;
import android.util.ArraySet;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.android.settingslib.fuelgauge.BatteryUtils;

import lineageos.preference.LineageSystemSettingListPreference;
import lineageos.providers.LineageSettings;

import org.lineageos.lineageparts.R;
import org.lineageos.lineageparts.SettingsPreferenceFragment;
import org.lineageos.lineageparts.search.BaseSearchIndexProvider;
import org.lineageos.lineageparts.search.Searchable;
import org.lineageos.lineageparts.utils.DeviceUtils;

import java.util.Set;

public class StatusBarSettings extends SettingsPreferenceFragment
        implements Preference.OnPreferenceChangeListener, Searchable {

    private static final String CATEGORY_BATTERY = "status_bar_battery_key";
    private static final String CATEGORY_CLOCK = "status_bar_clock_key";

    private static final String ICON_BLACKLIST = "icon_blacklist";

    private static final String STATUS_BAR_CLOCK_STYLE = "status_bar_clock";
    private static final String STATUS_BAR_AM_PM = "status_bar_am_pm";
    private static final String STATUS_BAR_BATTERY_STYLE = "status_bar_battery_style";
    private static final String STATUS_BAR_SHOW_BATTERY_PERCENT = "status_bar_show_battery_percent";
    private static final String STATUS_BAR_QUICK_QS_PULLDOWN = "qs_quick_pulldown";

    private static final int STATUS_BAR_BATTERY_STYLE_TEXT = 2;

    private static final int PULLDOWN_DIR_NONE = 0;
    private static final int PULLDOWN_DIR_RIGHT = 1;
    private static final int PULLDOWN_DIR_LEFT = 2;

    private static final String NETWORK_TRAFFIC_SETTINGS = "network_traffic_settings";

    private LineageSystemSettingListPreference mQuickPulldown;
    private LineageSystemSettingListPreference mStatusBarClock;
    private LineageSystemSettingListPreference mStatusBarAmPm;
    private LineageSystemSettingListPreference mStatusBarBatteryShowPercent;

    private PreferenceCategory mStatusBarBatteryCategory;
    private PreferenceCategory mStatusBarClockCategory;
    private Preference mNetworkTrafficPref;

    private boolean mHasCenteredCutout;

    private boolean mBatteryPresent;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.status_bar_settings);

        mNetworkTrafficPref = findPreference(NETWORK_TRAFFIC_SETTINGS);

        mHasCenteredCutout = DeviceUtils.hasCenteredCutout(getActivity());

        mStatusBarAmPm = findPreference(STATUS_BAR_AM_PM);
        mStatusBarClock = findPreference(STATUS_BAR_CLOCK_STYLE);
        mStatusBarClock.setOnPreferenceChangeListener(this);

        mStatusBarClockCategory = getPreferenceScreen().findPreference(CATEGORY_CLOCK);

        mStatusBarBatteryShowPercent = findPreference(STATUS_BAR_SHOW_BATTERY_PERCENT);
        LineageSystemSettingListPreference statusBarBattery =
                findPreference(STATUS_BAR_BATTERY_STYLE);
        statusBarBattery.setOnPreferenceChangeListener(this);
        enableStatusBarBatteryDependents(statusBarBattery.getIntValue(2));

        Intent intent = BatteryUtils.getBatteryIntent(getContext());
        if (intent != null) {
            mBatteryPresent = intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, true);
        }
        mStatusBarBatteryCategory = getPreferenceScreen().findPreference(CATEGORY_BATTERY);

        mQuickPulldown = findPreference(STATUS_BAR_QUICK_QS_PULLDOWN);
        mQuickPulldown.setOnPreferenceChangeListener(this);
        updateQuickPulldownSummary(mQuickPulldown.getIntValue(0));
    }

    @Override
    public void onResume() {
        super.onResume();

        final String curIconBlacklist = Settings.Secure.getString(getContext().getContentResolver(),
                ICON_BLACKLIST);

        if (TextUtils.delimitedStringContains(curIconBlacklist, ',', "clock")) {
            getPreferenceScreen().removePreference(mStatusBarClockCategory);
        } else {
            getPreferenceScreen().addPreference(mStatusBarClockCategory);
        }

        if (!mBatteryPresent ||
                TextUtils.delimitedStringContains(curIconBlacklist, ',', "battery")) {
            getPreferenceScreen().removePreference(mStatusBarBatteryCategory);
        } else {
            getPreferenceScreen().addPreference(mStatusBarBatteryCategory);
        }

        if (DateFormat.is24HourFormat(getActivity())) {
            mStatusBarAmPm.setEnabled(false);
            mStatusBarAmPm.setSummary(R.string.status_bar_am_pm_info);
        }

        final boolean isRTL = getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;

        mStatusBarClock.setEntries(
            isRTL
                ? mHasCenteredCutout
                    ? R.array.status_bar_clock_position_entries_notch_rtl
                    : R.array.status_bar_clock_position_entries_rtl
                : mHasCenteredCutout
                    ? R.array.status_bar_clock_position_entries_notch
                    : R.array.status_bar_clock_position_entries
        );

        mStatusBarClock.setEntryValues(
            mHasCenteredCutout
                ? R.array.status_bar_clock_position_values_notch
                : R.array.status_bar_clock_position_values
        );

        mQuickPulldown.setEntries(
            isRTL
                ? R.array.status_bar_quick_qs_pulldown_entries_rtl
                : R.array.status_bar_quick_qs_pulldown_entries
        );
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        int value = Integer.parseInt((String) newValue);
        String key = preference.getKey();
        switch (key) {
            case STATUS_BAR_QUICK_QS_PULLDOWN:
                updateQuickPulldownSummary(value);
                break;
            case STATUS_BAR_BATTERY_STYLE:
                enableStatusBarBatteryDependents(value);
                break;
        }
        return true;
    }

    private void enableStatusBarBatteryDependents(int batteryIconStyle) {
        mStatusBarBatteryShowPercent.setEnabled(batteryIconStyle != STATUS_BAR_BATTERY_STYLE_TEXT);
    }

    private void updateQuickPulldownSummary(int value) {
        String summary="";
        switch (value) {
            case PULLDOWN_DIR_NONE:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_off);
                break;

            case PULLDOWN_DIR_LEFT:
            case PULLDOWN_DIR_RIGHT:
                summary = getResources().getString(
                    R.string.status_bar_quick_qs_pulldown_summary,
                    getResources().getString(
                        (value == PULLDOWN_DIR_LEFT) ^
                        (getResources().getConfiguration().getLayoutDirection()
                            == View.LAYOUT_DIRECTION_RTL)
                        ? R.string.status_bar_quick_qs_pulldown_summary_left
                        : R.string.status_bar_quick_qs_pulldown_summary_right));
                break;
        }
        mQuickPulldown.setSummary(summary);
    }

    public static final Searchable.SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

        @Override
        public Set<String> getNonIndexableKeys(Context context) {
            final Set<String> result = new ArraySet<String>();

            if (DeviceUtils.hasCenteredCutout(context)) {
                result.add(NETWORK_TRAFFIC_SETTINGS);
            }
            return result;
        }
    };
}
