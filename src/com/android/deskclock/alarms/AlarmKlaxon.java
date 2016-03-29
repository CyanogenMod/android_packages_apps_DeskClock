/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.deskclock.alarms;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Vibrator;

import com.cyanogen.ambient.alarmmusic.AlarmMusicRequest;
import com.cyanogen.ambient.alarmmusic.IAlarmMusicListener;


import com.android.alarmmusic.AlarmMusicHelper;
import com.android.alarmmusic.AlarmMusicListenerImpl;
import com.android.deskclock.AsyncRingtonePlayer;
import com.android.deskclock.LogUtils;
import com.android.deskclock.provider.AlarmInstance;

/**
 * Manages playing ringtone and vibrating the device.
 */
public final class AlarmKlaxon {
    private static final long[] sVibratePattern = {500, 500};

    private static boolean sStarted = false;
    private static AsyncRingtonePlayer sAsyncRingtonePlayer;

    private AlarmKlaxon() {}

    public static void stop(Context context) {
        LogUtils.v("AlarmKlaxon.stop()");

        if (sStarted) {
            ComponentName component = AlarmMusicHelper.getComponent(0);
            AlarmMusicRequest request = new AlarmMusicRequest();
            AlarmMusicHelper.stop(
                    component,
                    request,
                    AlarmMusicListenerImpl.getInstance(component));
            /*
            sStarted = false;
            getAsyncRingtonePlayer(context).stop();
            ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE)).cancel();
            */
        }
    }

    public static void start(Context context, AlarmInstance instance) {
        LogUtils.v("AlarmKlaxon.start()");
        // Make sure we are stopped before starting
        stop(context);

        ComponentName component = AlarmMusicHelper.getComponent(0);
        AlarmMusicRequest request = new AlarmMusicRequest();
        request.mUriString = AlarmMusicHelper.getUri();
        request.mDurationSec = 0;
        AlarmMusicHelper.play(
                component,
                request,
                AlarmMusicListenerImpl.getInstance(component));

        /*

        if (!AlarmInstance.NO_RINGTONE_URI.equals(instance.mRingtone)) {
            getAsyncRingtonePlayer(context).play(instance.mRingtone, instance.mIncreasingVolume);
        }

        if (instance.mVibrate) {
            final Vibrator vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                vibrator.vibrate(sVibratePattern, 0, new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build());
            } else {
                vibrator.vibrate(sVibratePattern, 0);
            }
        }
        */

        sStarted = true;
    }

    private static synchronized AsyncRingtonePlayer getAsyncRingtonePlayer(Context context) {
        if (sAsyncRingtonePlayer == null) {
            sAsyncRingtonePlayer = new AsyncRingtonePlayer(context.getApplicationContext());
        }

        return sAsyncRingtonePlayer;
    }
}