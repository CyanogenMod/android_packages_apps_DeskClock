/*
 * Copyright (C) 2012 The CyanogenMod Project
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

package com.android.deskclock.preferences;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.util.Log;
import android.widget.Toast;

import com.android.deskclock.R;

import java.text.MessageFormat;

/**
 * A class that manages the user StopWatch lap/sectors preferences.
 */
public class StopWatchLapsSectorsPreference extends PreferenceFragment {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "StopWatchLapsSectorsPreference"; //$NON-NLS-1$

    private SectorsDialogPreference mSectors;
    private ListPreference mBestLapBehaviour;

    private final OnPreferenceChangeListener mOnChangeListener =
            new OnPreferenceChangeListener() {
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            String key = preference.getKey();
            if (DEBUG) Log.d(LOG_TAG,
                    String.format("New value for %s: %s",  //$NON-NLS-1$
                            key,
                            String.valueOf(newValue)));
            try {
                // Sectors
                if (StopWatchPreferences.PREF_SECTORS.compareTo(key) == 0) {
                    int value = ((Integer)newValue).intValue();
                    String fmt = getString(R.string.stopwatch_preferences_sectors_summary);
                    preference.setSummary(MessageFormat.format(fmt, Integer.valueOf(value)));

                // Best Lap Behaviour
                } else if (StopWatchPreferences.PREF_BEST_LAP_BEHAVIOUR.compareTo(key) == 0) {
                    int value = Integer.valueOf((String)newValue).intValue();
                    String[] summary = getResources().getStringArray(
                            R.array.stopwatch_best_lap_behaviour_summary);
                    preference.setSummary(summary[value]);
                }

            } catch (Exception e) {
                Log.e(LOG_TAG,
                        String.format("Fails to change preference: %s", key),  //$NON-NLS-1$
                        e);
                Toast.makeText(
                        getActivity(),
                        R.string.msg_pref_update_fails,
                        Toast.LENGTH_SHORT).show();
                return false;
            }
            return true;
        }
    };

    /**
     * Constructor of <code>StopWatchLapsSectorsPreference</code>
     */
    public StopWatchLapsSectorsPreference() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Change the preference manager
        getPreferenceManager().setSharedPreferencesName(StopWatchPreferences.PREFERENCES_FILENAME);
        getPreferenceManager().setSharedPreferencesMode(StopWatchPreferences.MODE_PRIVATE);

        // Add the preferences
        addPreferencesFromResource(R.xml.stopwatch_preferences_laps_sectors);

        // Sectors
        this.mSectors = (SectorsDialogPreference)findPreference(StopWatchPreferences.PREF_SECTORS);
        this.mSectors.setOnPreferenceChangeListener(this.mOnChangeListener);
        this.mOnChangeListener.onPreferenceChange(
                this.mSectors,
                Integer.valueOf(this.mSectors.getValue()));

        // Best Lap Behaviour
        this.mBestLapBehaviour = (ListPreference)findPreference(
                                        StopWatchPreferences.PREF_BEST_LAP_BEHAVIOUR);
        this.mBestLapBehaviour.setOnPreferenceChangeListener(this.mOnChangeListener);
        this.mOnChangeListener.onPreferenceChange(
                this.mBestLapBehaviour,
                this.mBestLapBehaviour.getValue());
    }

}
