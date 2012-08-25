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

import com.android.deskclock.CountDownFragment;
import com.android.deskclock.R;

/**
 * The {@link CountDownFragment} preferences
 */
public class CountDownPreferences extends PreferenceActivity {

    /**
     * The vibration pattern (start_inmediate, dash, short_silent, dash)
     * @hide
     */
    public static final long[] VIBRATOR_PATTERN = {0, 500, 200, 500};

    /**
     * The max time an alarm will sound (in milliseconds)
     * @hide
     */
    public static final long FINAL_COUNTDOWN_ALARM_MAX_TIME = 30000L;

    /**
     * @hide
     */
    public static final String PREFERENCES_FILENAME = "CountDown"; //$NON-NLS-1$

    // Shared preferences keys
    /**
     * @hide
     */
    public static final String PREF_DEFAULT_COUNTDOWN_TIME =
                                    "countdown_default_time"; //$NON-NLS-1$
    /**
     * @hide
     */
    public static final String PREF_COUNTDOWN_TIME =
                                    "countdown_time"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_COUNTDOWN_IS_RESET =
                                    "countdown_is_reset"; //$NON-NLS-1$


    /**
     * @hide
     */
    public static final String PREF_ONCOUNTDOWN_NOTIFICATION =
                                    "oncountdown_notification"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_ONFINALCOUNTDOWN_NOTIFICATION =
                                   "onfinalcountdown_notification"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_ONFINALCOUNTDOWN_OPEN_CLOCK_SCREEN =
                                  "onfinalcountdown_open_clock_screen"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_ONFINALCOUNTDOWN_RINGTONE =
                                  "onfinalcountdown_ringtone"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_ONFINALCOUNTDOWN_VIBRATE =
                                  "onfinalcountdown_vibrate"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String PREF_ONFINALCOUNTDOWN_VIBRATE_ON_CALL =
                                  "onfinalcountdown_vibrate_on_call"; //$NON-NLS-1$


    /**
     * Vibrate options
     */
    public enum VIBRATE_TYPE {
        /**
         * Vibrate always
         */
        ALWAYS,
        /**
         * Vibrate only on silent
         */
        ONLY_ON_SILENT,
        /**
         * No vibrate
         */
        NEVER
    }




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
        loadHeadersFromResource(R.xml.countdown_preferences_headers, target);
    }

}