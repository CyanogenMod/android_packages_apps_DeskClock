/**
 * Copyright (c) 2015, The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.deskclock;

import android.app.IntentService;
import android.content.Intent;

import android.util.Log;
import com.android.deskclock.provider.Alarm;
import cyanogenmod.alarmclock.CyanogenModAlarmClock;

/**
 * Allows third parties to modify alarms.
 */
public class AlarmModificationIntentService extends IntentService {
    private static final String TAG = "AlarmModificationIntentService";

    public AlarmModificationIntentService() {
        super(TAG);
    }

    public AlarmModificationIntentService(String name) {
        super(name);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        String action = intent.getAction();
        if (CyanogenModAlarmClock.ACTION_SET_ALARM_ENABLED.equals(action)) {
            long alarmId = intent.getLongExtra(CyanogenModAlarmClock.EXTRA_ALARM_ID, -1L);
            boolean enabled = intent.getBooleanExtra(CyanogenModAlarmClock.EXTRA_ENABLED, true);
            if (alarmId != -1L) {
                boolean success = Alarm.setAlarmEnabled(this, alarmId, enabled);
                if (!success) {
                    Log.w(TAG, "Failed to set alarm enabled status - alarmId: " + alarmId
                            + ", enabled: " + enabled);
                }
            } else {
                Log.w(TAG, "Unable to set alarm enabled status, invalid ID");
            }
        }
    }
}
