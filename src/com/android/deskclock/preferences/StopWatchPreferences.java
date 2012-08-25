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

import java.util.List;

import android.os.Bundle;
import android.preference.PreferenceActivity;

import com.android.deskclock.StopWatchFragment;
import com.android.deskclock.R;

/**
 * The {@link StopWatchFragment} preferences
 */
public class StopWatchPreferences extends PreferenceActivity {

    /**
     * @hide
     */
    public static final String PREFERENCES_FILENAME = "StopWatch"; //$NON-NLS-1$

    // Shared preferences keys
    /**
     * @hide
     */
    public static final String PREF_LAST_SECTOR =
                                    "stopwatch_last_sector"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_LAST_LAP =
                                    "stopwatch_last_lap"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CURRENT_SECTOR =
                                    "stopwatch_current_sector"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CURRENT_LAP =
                                    "stopwatch_current_lap"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CLOCK_MAIN =
                                    "stopwatch_clock_main"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CLOCK_PARTIAL =
                                    "stopwatch_clock_partial"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CLOCK_DIFF =
                                    "stopwatch_clock_diff"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_CURRENT_DATA =
                                    "stopwatch_current_data"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_SECTORS =
                                    "stopwatch_sectors"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_HIDE_LAST_LAP_PANEL =
                                    "stopwatch_hide_last_lap_panel"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_HIDE_BEST_LAP_PANEL =
                                    "stopwatch_hide_best_lap_panel"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_HIDE_LAPS_PANEL =
                                    "stopwatch_hide_laps_panel"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_BEST_LAP_BEHAVIOUR =
                                    "stopwatch_best_lap_behaviour"; //$NON-NLS-1$

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.stopwatch_preferences_headers, target);
    }

}
