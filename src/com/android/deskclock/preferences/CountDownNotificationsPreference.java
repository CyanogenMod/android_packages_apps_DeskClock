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

import android.content.Context;
import android.content.SharedPreferences;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.RingtonePreference;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import com.android.deskclock.R;

/**
 * A class that manages the user CountDown notification preferences.
 */
public class CountDownNotificationsPreference extends PreferenceFragment {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "CountDownNotificationsPreference"; //$NON-NLS-1$

    private RingtonePreference mRingtone;
    private ListPreference mVibrate;

    private final OnPreferenceChangeListener mOnChangeListener =
            new OnPreferenceChangeListener() {
        public boolean onPreferenceChange(final Preference preference, final Object newValue) {
            String key = preference.getKey();
            if (DEBUG) Log.d(LOG_TAG,
                    String.format("New value for %s: %s",  //$NON-NLS-1$
                            key,
                            String.valueOf(newValue)));
            try {
                // Ringtone
                if (CountDownPreferences.PREF_ONFINALCOUNTDOWN_RINGTONE.compareTo(key) == 0) {
                    if (newValue == null || ((String)newValue).trim().length() == 0) {
                        //Silent
                        preference.setSummary(
                                R.string.
                                    countdown_notification_onfinalcountdown_ringtone_silent);
                    } else {
                        //Load the ringtone in an asynchronous way
                        Uri ringtone = Uri.parse((String)newValue);
                        preference.setSummary(" "); //$NON-NLS-1$
                        AsyncTask<Uri, Void, String> ringtoneTask =
                                new AsyncTask<Uri, Void, String>() {
                            @Override
                            protected String doInBackground(Uri... params) {
                                Context ctx = getActivity();
                                Ringtone r =
                                     RingtoneManager.getRingtone(ctx, params[0]);
                                if (r == null) {
                                    r = RingtoneManager.getRingtone(ctx,
                                            Settings.System.DEFAULT_ALARM_ALERT_URI);
                                }
                                if (r != null) {
                                    return r.getTitle(ctx);
                                }
                                return null;
                            }

                            @Override
                            protected void onPostExecute(String title) {
                                if (!isCancelled()) {
                                    preference.setSummary(title);
                                }
                            }
                        };
                        ringtoneTask.execute(ringtone);
                    }

                    System.out.println(newValue);
                // Vibrate
                } else if (
                        CountDownPreferences.PREF_ONFINALCOUNTDOWN_VIBRATE.compareTo(key) == 0) {
                    int value = Integer.valueOf((String)newValue).intValue();
                    String[] summary = getResources().getStringArray(
                            R.array.countdown_vibrate_labels);
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
     * Constructor of <code>CountDownNotificationsPreference</code>
     */
    public CountDownNotificationsPreference() {
        super();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Change the preference manager
        getPreferenceManager().setSharedPreferencesName(CountDownPreferences.PREFERENCES_FILENAME);
        getPreferenceManager().setSharedPreferencesMode(CountDownPreferences.MODE_PRIVATE);
        SharedPreferences sp = getPreferenceManager().getSharedPreferences();

        // Add the preferences
        addPreferencesFromResource(R.xml.countdown_preferences_notifications);

        // Ringtone
        this.mRingtone = (RingtonePreference)findPreference(
                                CountDownPreferences.PREF_ONFINALCOUNTDOWN_RINGTONE);
        this.mRingtone.setOnPreferenceChangeListener(this.mOnChangeListener);
        this.mOnChangeListener.onPreferenceChange(
                this.mRingtone,
                sp.getString(
                        CountDownPreferences.PREF_ONFINALCOUNTDOWN_RINGTONE, "")); //$NON-NLS-1$

        // Vibrate
        this.mVibrate = (ListPreference)findPreference(
                                CountDownPreferences.PREF_ONFINALCOUNTDOWN_VIBRATE);
        this.mVibrate.setOnPreferenceChangeListener(this.mOnChangeListener);
        this.mOnChangeListener.onPreferenceChange(
                this.mVibrate,
                this.mVibrate.getValue());
    }
}
