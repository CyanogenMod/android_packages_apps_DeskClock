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

package com.android.deskclock;

import java.util.concurrent.TimeUnit;

/**
 * A helper class with useful method for deal with timers
 */
public final class TimerHelper {

    /**
     * A short representation (without milliseconds) of time
     */
    public static final String TIMEFORMAT_SHORT = "%d:%02d:%02d"; //$NON-NLS-1$
    /**
     * A long representation (with milliseconds) of time
     */
    public static final String TIMEFORMAT_FULL = "%d:%02d:%02d.%03d"; //$NON-NLS-1$

    /**
     * Method that format the time of the timer
     *
     * @param time The time of the timer
     * @param format The format of the time
     * @param showSign If the sign must be shown
     * @return String The formatted time
     */
    @SuppressWarnings("boxing")
    public static final String formatTime(long time, String format, boolean showSign) {
        // Convert to the appropriate amount of time
        final long absMillis = Math.abs(time);
        final long hr  = TimeUnit.MILLISECONDS.toHours(absMillis);
        final long min = TimeUnit.MILLISECONDS.toMinutes(absMillis
                         - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(absMillis
                         - TimeUnit.HOURS.toMillis(hr)
                         - TimeUnit.MINUTES.toMillis(min));
        final long ms  = TimeUnit.MILLISECONDS.toMillis(absMillis
                         - TimeUnit.HOURS.toMillis(hr)
                         - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        String formattedTime = String.format(format, hr, min, sec, ms);
        if (showSign) {
            formattedTime = (time >= 0 ? "+" : "-" ) + formattedTime; //$NON-NLS-1$ //$NON-NLS-2$
        }
        return formattedTime;
    }

    /**
     * Method that obtains the time of the timer in a
     *
     * @param time The time of the timer
     * @return long[] The time expressed as [hour, minutes, seconds, milliseconds]
     */
    public static final long[] obtainTime(long time) {
        // Convert to the appropriate amount of time
        final long absMillis = Math.abs(time);
        final long hr  = TimeUnit.MILLISECONDS.toHours(absMillis);
        final long min = TimeUnit.MILLISECONDS.toMinutes(absMillis
                         - TimeUnit.HOURS.toMillis(hr));
        final long sec = TimeUnit.MILLISECONDS.toSeconds(absMillis
                         - TimeUnit.HOURS.toMillis(hr)
                         - TimeUnit.MINUTES.toMillis(min));
        final long ms  = TimeUnit.MILLISECONDS.toMillis(absMillis
                         - TimeUnit.HOURS.toMillis(hr)
                         - TimeUnit.MINUTES.toMillis(min) - TimeUnit.SECONDS.toMillis(sec));
        return new long[]{hr, min, sec, ms};
    }

    /**
     * Method that converts the time units in time
     *
     * @param hr Hours
     * @param min Minutes
     * @param sec Seconds
     * @param millis Milliseconds
     * @return long The time of the time units
     */
    public static final long toTime(int hr, int min, int sec, int millis) {
        long time = hr * 3600000L;
        time += min * 60000L;
        time += sec * 1000L;
        time += millis;
        return time;
    }
}
