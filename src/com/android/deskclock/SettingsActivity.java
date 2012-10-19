/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock;

import java.util.Locale;

import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.provider.Settings;

/**
 * Settings for the Alarm Clock.
 */
public class SettingsActivity extends PreferenceActivity
        implements Preference.OnPreferenceChangeListener {

    private static final int ALARM_STREAM_TYPE_BIT =
            1 << AudioManager.STREAM_ALARM;

    private static final String KEY_ALARM_IN_SILENT_MODE =
            "alarm_in_silent_mode";
    private static final String KEY_HIDE_STATUS_BAR_ICON =
            "hide_status_bar_icon";
    static final String KEY_ALARM_SNOOZE =
            "snooze_duration";
    static final String KEY_VOLUME_BEHAVIOR =
            "volume_button_setting";
    static final String KEY_DEFAULT_RINGTONE =
            "default_ringtone";
    static final String KEY_AUTO_SILENCE =
            "auto_silence";
    static final String KEY_FLIP_ACTION =
            "flip_action";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.settings);

        final AlarmPreference ringtone =
                (AlarmPreference) findPreference(KEY_DEFAULT_RINGTONE);
        Uri alert = RingtoneManager.getActualDefaultRingtoneUri(this,
                RingtoneManager.TYPE_ALARM);
        if (alert != null) {
            ringtone.setAlert(alert);
        }
        ringtone.setChangeDefault();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen,
            Preference preference) {
        if (KEY_ALARM_IN_SILENT_MODE.equals(preference.getKey())) {
            CheckBoxPreference pref = (CheckBoxPreference) preference;
            int ringerModeStreamTypes = Settings.System.getInt(
                    getContentResolver(),
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);

            if (pref.isChecked()) {
                ringerModeStreamTypes &= ~ALARM_STREAM_TYPE_BIT;
            } else {
                ringerModeStreamTypes |= ALARM_STREAM_TYPE_BIT;
            }

            Settings.System.putInt(getContentResolver(),
                    Settings.System.MODE_RINGER_STREAMS_AFFECTED,
                    ringerModeStreamTypes);

            return true;
        }
        else if (KEY_HIDE_STATUS_BAR_ICON.equals(preference.getKey())) {
            CheckBoxPreference pref = (CheckBoxPreference) preference;
            int showIcon;
            if(pref.isChecked()) {
                showIcon = 0;
            } else {
                showIcon = 1;
            }

            Settings.System.putInt(getContentResolver(),
                    Settings.System.STATUS_BAR_ALARM,
                    showIcon);

            // Check if any alarms are active. If yes and
            // we allow showing the alarm icon, the icon will be shown.
            Alarms.setNextAlert(getApplicationContext());
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    public boolean onPreferenceChange(Preference pref, Object newValue) {
        if (KEY_ALARM_SNOOZE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            final int idx = listPref.findIndexOfValue((String) newValue);
            listPref.setSummary(listPref.getEntries()[idx]);
        } else if (KEY_AUTO_SILENCE.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String delay = (String) newValue;
            updateAutoSnoozeSummary(listPref, delay);
        } else if (KEY_FLIP_ACTION.equals(pref.getKey())) {
            final ListPreference listPref = (ListPreference) pref;
            String action = (String) newValue;
            updateFlipActionSummary(listPref, action);
        }
        return true;
    }

    private void updateAutoSnoozeSummary(ListPreference listPref,
            String delay) {
        int i = Integer.parseInt(delay);
        if (i == -1) {
            listPref.setSummary(R.string.auto_silence_never);
        } else {
            listPref.setSummary(getString(R.string.auto_silence_summary, i));
        }
    }

    private void updateFlipActionSummary(ListPreference listPref,
            String action) {
        int i = Integer.parseInt(action);
        if (Locale.getDefault().getLanguage().equals("de")) {
            listPref.setSummary(
                    getString(R.string.flip_action_summary,
                    getResources().getStringArray(R.array.flip_action_entries)[i]));
        } else {
            listPref.setSummary(
                    getString(R.string.flip_action_summary,
                    getResources().getStringArray(R.array.flip_action_entries)[i].toLowerCase()));
        }
    }

    private void refresh() {
        final CheckBoxPreference alarmInSilentModePref =
                (CheckBoxPreference) findPreference(KEY_ALARM_IN_SILENT_MODE);
        final int silentModeStreams =
                Settings.System.getInt(getContentResolver(),
                        Settings.System.MODE_RINGER_STREAMS_AFFECTED, 0);
        alarmInSilentModePref.setChecked(
                (silentModeStreams & ALARM_STREAM_TYPE_BIT) == 0);

        final CheckBoxPreference hideStatusBarIconPref =
                (CheckBoxPreference) findPreference(KEY_HIDE_STATUS_BAR_ICON);
        final int showIcon =
                Settings.System.getInt(getContentResolver(),
                        Settings.System.STATUS_BAR_ALARM,  0);
        hideStatusBarIconPref.setChecked(showIcon == 0);

        ListPreference listPref =
                (ListPreference) findPreference(KEY_ALARM_SNOOZE);
        listPref.setSummary(listPref.getEntry());
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_AUTO_SILENCE);
        String delay = listPref.getValue();
        updateAutoSnoozeSummary(listPref, delay);
        listPref.setOnPreferenceChangeListener(this);

        listPref = (ListPreference) findPreference(KEY_FLIP_ACTION);
        String action = listPref.getValue();
        updateFlipActionSummary(listPref, action);
        listPref.setOnPreferenceChangeListener(this);
    }
}
