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

import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.Vibrator;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.widget.Toast;

import com.android.deskclock.preferences.CountDownPreferences;

/**
 * The timer clock service that runs the timers requested by {@link DeskClock} application
 * in the background.
 */
public class TimerClockService extends Service {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "TimerClockService";  //$NON-NLS-1$

    private static final String BROADCAST_END_COUNTDOWN =
                                "com.android.deskclock.broadcasts.END_COUNTDOWN"; //$NON-NLS-1$

    private static final String EXTRA_TIMER_ID = "timerId"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String EXTRA_TIMER_TYPE = "timerType"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String EXTRA_TIMER_ACTION = "timerAction"; //$NON-NLS-1$

    /**
     * @hide
     */
    public static final String ACTION_SHOW_COUNTDOWN =
                                "com.android.deskclock.actions.SHOW_COUNTDOWN"; //$NON-NLS-1$;

    /**
     * @hide
     */
    public static final String ACTION_END_COUNTDOWN =
                                "com.android.deskclock.actions.END_COUNTDOWN"; //$NON-NLS-1$;

    /**
     * The types of timer supported by this services
     */
    public enum TIMER_TYPE {
        /**
         * A stopwatch timer
         */
        STOPWATCH,
        /**
         * A countdown timer
         */
        COUNTDOWN
    }

    /**
     * A class that represent a database of the timers running in the service
     */
    private class Timer {
        public Timer() {
        }
        public long mId;
        public TIMER_TYPE mType;
        public long mStartMillisecond;
        public long mEndMillisecond;
        public long mCountDownTime;
        public long mElapsed;  // The sum time from multiples stops
        public boolean mRunning;

        public Object mSync;
        public PendingIntent mNotityIntent;
        public int mNotificationId;

        public MediaPlayer mMediaPlayer;

        public Handler mHandler;
        public Notification.BigTextStyle mNotificationBuilder;
        public UpdateNotificationTask mNotificationTask;
    }

    private class UpdateNotificationTask implements Runnable {

        private final Timer mTimer;

        /**
         * Constructor of <code>UpdateNotificationTask</code>
         *
         *  @param timer The associated timer
         */
        public UpdateNotificationTask(Timer timer) {
            super();
            this.mTimer = timer;
        }

        @SuppressWarnings("synthetic-access")
        public void run() {
            String remaining =
                    TimerHelper.formatTime(
                            internalQueryTime(this.mTimer),
                            TimerHelper.TIMEFORMAT_SHORT,
                            false);
            String summary = getString(
                                R.string.service_countdown_notification_summary,
                                remaining);

            // Set the remaining time
            this.mTimer.mNotificationBuilder.setSummaryText(summary);
            updateCountDownNotification(this.mTimer);
            this.mTimer.mHandler.postDelayed(this, 1000L);
        }
    }

    /**
     * A broadcast receiver to listen timer events
     */
    private final BroadcastReceiver mTimerEventsReceiver = new BroadcastReceiver() {
        @Override
        @SuppressWarnings("synthetic-access")
        public void onReceive(Context context, Intent intent) {
            if (intent == null) return;
            if (DEBUG) Log.d(LOG_TAG, "onReceive: " + intent); //$NON-NLS-1$
            String action = intent.getAction();
            if (BROADCAST_END_COUNTDOWN.equals(action)) {
                // A timer countdown has finished
                try {
                    long timerId = intent.getLongExtra(EXTRA_TIMER_ID, 0);
                    Timer timer = getTimer(Long.valueOf(timerId));
                    // It's a real countdown ending?
                    long elapsed = internalQueryTime(timerId);
                    if (elapsed > 0) {
                        Log.w(LOG_TAG,
                                String.format(
                                        "This timer is not ended!!!. Elapsed: %d", //$NON-NLS-1$
                                        Long.valueOf(elapsed)));
                        return;
                    }

                    // Now is not running
                    timer.mRunning = false;
                    Log.i(LOG_TAG,
                            String.format(
                                    "Timer %s reached his countdown timer", //$NON-NLS-1$
                                    Long.valueOf(elapsed)));

                    int  callState = TimerClockService.this.mTelephonyManager.getCallState();
                    boolean onCall = callState != TelephonyManager.CALL_STATE_IDLE;

                    // Update the notification, vibrate, sound and show screen
                    showFinalCountDownNotification(timer);
                    vibrateOnFinalCountDown(timer, onCall);
                    playAlarmOnFinalCountDown(timer, onCall);
                    showFinalCountDownView(timer, onCall);


                } catch (Exception e) {
                    Log.e(LOG_TAG, "Fails to notify the countdown ending", e); //$NON-NLS-1$
                }
            }
        }
    };


    // The in-memory database of timers
    private Map<Long, Timer> mTimers;
    private static int sNotificationsIds = 0;

    private NotificationManager mNotificationManager;
    private AlarmManager mAlarmManager;
    private TelephonyManager mTelephonyManager;
    private AudioManager mAudioManager;
    private Vibrator mVibrator;
    private PowerManager mPowerManager;

    private PowerManager.WakeLock mWakeLock;

    private final Object mServiceSync = new Object();
    private final Object mTimerSync = new Object();

    /**
     * Service implementation
     */
    private final ITimerClockService.Stub mBinder = new ITimerClockService.Stub() {
        @SuppressWarnings("synthetic-access")
        public long createTimer(int type) throws RemoteException {
            synchronized (TimerClockService.this.mServiceSync) {
                // Check that a valid type of timer is passed
                if (DEBUG) Log.d(LOG_TAG, "createTimer: " + type); //$NON-NLS-1$
                TIMER_TYPE[] types = TIMER_TYPE.values();
                if (type < 0 || type > (types.length-1) ) {
                    throw new RemoteException("Invalid timer type");  //$NON-NLS-1$
                }
                TIMER_TYPE timerType = types[type];

                // Create a new wake lock, if no previous lock exists, for ensure
                // that cpu is active while timers are live.
                synchronized (TimerClockService.this.mTimerSync) {
                    if (TimerClockService.this.mTimers.size() == 0) {
                        Log.i(LOG_TAG, "Acquiring cpu wakelock");
                        TimerClockService.this.mWakeLock.acquire();
                    }
                }

                // Create a new timer
                Timer timer = new Timer();
                // This should be sufficient to create unique identifiers
                timer.mId = System.nanoTime();
                timer.mType = timerType;
                timer.mRunning = false;
                timer.mStartMillisecond = 0;
                timer.mEndMillisecond = 0;
                timer.mCountDownTime = 0;
                timer.mElapsed = 0;
                timer.mSync = new Object();
                timer.mMediaPlayer = new MediaPlayer();
                timer.mHandler = new Handler();

                TimerClockService.this.mTimers.put(Long.valueOf(timer.mId), timer);

                Log.i(LOG_TAG, String.format(
                        "Created new %s timer with id: %d",  //$NON-NLS-1$
                        timer.mType, Long.valueOf(timer.mId)));
                return timer.mId;
            }
        }

        @SuppressWarnings("synthetic-access")
        public void destroyTimer(long timerId) throws RemoteException {
            synchronized (TimerClockService.this.mServiceSync) {
                if (DEBUG) Log.d(LOG_TAG, "destroyTimer: " + timerId); //$NON-NLS-1$
                if (!TimerClockService.this.mTimers.containsKey(Long.valueOf(timerId))) {
                    throw new RemoteException("Invalid timer");  //$NON-NLS-1$
                }

                // Remove notification if exist
                Timer timer = getTimer(Long.valueOf(timerId));
                if (timer.mType.compareTo(TIMER_TYPE.COUNTDOWN) == 0) {
                    TimerClockService.this.mNotificationManager.cancel(timer.mNotificationId);
                }

                // Remove timer
                removeTimer(Long.valueOf(timerId));

                // Release the wake lock (if no more is needed)
                synchronized (TimerClockService.this.mTimerSync) {
                    if (TimerClockService.this.mTimers.size() == 0) {
                        Log.i(LOG_TAG, "Releasing cpu wakelock");
                        try {
                            TimerClockService.this.mWakeLock.release();
                        } catch (Exception e) {/**NON BLOCK**/}
                    }
                }

                Log.i(LOG_TAG, String.format(
                        "Remove timer with id: %d", Long.valueOf(timerId)));  //$NON-NLS-1$
            }
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void startTimer(long timerId) throws RemoteException {
            if (DEBUG) Log.d(LOG_TAG, "startTimer: " + timerId); //$NON-NLS-1$
            Timer timer = getTimer(Long.valueOf(timerId));
            synchronized (timer.mSync) {
                if (timer.mRunning) {
                    // Prevent multiple calls to a non running timer
                    return;
                }

                switch (timer.mType) {
                case STOPWATCH:
                    // Save a new milliseconds reference is necessary in order to
                    // compute the time from this moment. Elapse time is create in
                    // stop to returns the real milliseconds on time query.
                    timer.mStartMillisecond = System.currentTimeMillis();
                    break;

                case COUNTDOWN:
                    // Save the new end time adjusting according countdown time.
                    if (timer.mCountDownTime <= 0) {
                        // Time need to be greater than 0 so a countdown can be created
                        throw new RemoteException(
                                "Invalid operation! CountDown time must be > 0.");  //$NON-NLS-1$
                    }
                    timer.mEndMillisecond = System.currentTimeMillis() + timer.mCountDownTime;

                    // Creates a new notification intent
                    createOnEndCountDownNotityIntent(timer);

                    // Method that shows a notification in the status bar
                    boolean showNotification = getCountDownPreferences().getBoolean(
                                        CountDownPreferences.PREF_ONCOUNTDOWN_NOTIFICATION, true);
                    if (showNotification) {
                        // Show the remaining time
                        String remaining =
                                TimerHelper.formatTime(
                                        internalQueryTime(timerId),
                                        TimerHelper.TIMEFORMAT_SHORT,
                                        false);
                        createCountDownNotification(
                                    timer,
                                    getString(R.string.service_countdown_notification_title),
                                    getString(R.string.service_countdown_notification_msg_progress),
                                    getString(
                                            R.string.service_countdown_notification_summary,
                                            remaining),
                                    false);

                        // Start notification countdown progress
                        timer.mNotificationTask = new UpdateNotificationTask(timer);
                        timer.mHandler.postDelayed(timer.mNotificationTask, 1000L);
                    }
                    break;

                default:
                    break;
                }

                timer.mRunning = true;
            }
            Log.i(LOG_TAG, String.format(
                    "Start timer with id: %d", Long.valueOf(timerId)));  //$NON-NLS-1$
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void stopTimer(long timerId) throws RemoteException {
            if (DEBUG) Log.d(LOG_TAG, "stopTimer: " + timerId); //$NON-NLS-1$
            Timer timer = getTimer(Long.valueOf(timerId));
            synchronized (timer.mSync) {
                if (!timer.mRunning) {
                    // Prevent multiple calls to a non running timer
                    return;
                }

                // Retrieves the current time
                long now = System.currentTimeMillis();

                switch (timer.mType) {
                case STOPWATCH:
                    // Stop don't destroy the timer, only stop the computing
                    // of time. For this reason is necessary to computing the elapsed
                    // time to add in new time queries.
                    timer.mElapsed = internalQueryTime(timer, now);
                    break;

                case COUNTDOWN:
                    // Stop don't destroy the timer, only stop the computing
                    // of time. In this case we need to adjust the countdown time
                    //to reflect the new countdown time
                    timer.mCountDownTime = internalQueryTime(timer, now);

                    // Cancel the current notify intent if exists
                    try {
                        if (timer.mHandler != null && timer.mNotificationTask != null) {
                            timer.mHandler.removeCallbacks(timer.mNotificationTask);
                        }
                    } catch (Exception e) {/**NON BLOCK**/}
                    try {
                        if (timer.mNotityIntent != null) {
                            timer.mNotityIntent.cancel();
                            timer.mNotityIntent = null;
                        }
                    } catch (Exception e) {/**NON BLOCK**/}

                    // Freeze the time, so new calls to query time return the same value
                    timer.mStartMillisecond = now;
                    break;

                default:
                    break;
                }

                // Not running
                timer.mRunning = false;
            }
            Log.i(LOG_TAG, String.format(
                    "Stop timer with id: %d", Long.valueOf(timerId)));  //$NON-NLS-1$
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public long queryTime(long timerId) throws RemoteException {
            if (DEBUG) Log.d(LOG_TAG, "queryTime: " + timerId); //$NON-NLS-1$
            long now = System.currentTimeMillis();
            return internalQueryTime(timerId, now);
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void setCountDownTime(long timerId, long time) throws RemoteException {
            Timer timer = getTimer(Long.valueOf(timerId));
            synchronized (timer.mSync) {
                if (timer.mType.compareTo(TIMER_TYPE.COUNTDOWN) != 0) {
                    // Is timer a countdown timer
                    throw new RemoteException(
                            "Invalid operation! Timer is not a COUNTDOWN timer.");  //$NON-NLS-1$
                }
                if (timer.mRunning) {
                    // Prevent set countdown end time if the timer is running
                    throw new RemoteException(
                            "Invalid operation! Timer is running.");  //$NON-NLS-1$
                }
                if (time <= 0) {
                    // Time need to be greater than 0 so a countdown can be created
                    throw new RemoteException(
                            "Invalid operation! CountDown time must be > 0.");  //$NON-NLS-1$
                }
                timer.mCountDownTime = time;
            }
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public boolean isRunning(long timerId) throws RemoteException {
            Timer timer = getTimer(Long.valueOf(timerId));
            synchronized (timer.mSync) {
                return timer.mRunning;
            }
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings({"synthetic-access"})
        public void removeNotification(long timerId) throws RemoteException {
            Timer timer = getTimer(Long.valueOf(timerId));
            if (timer.mNotificationId != 0) {
                TimerClockService.this.mNotificationManager.cancel(timer.mNotificationId);
            }
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        if (DEBUG) Log.d(LOG_TAG, "onCreate"); //$NON-NLS-1$
        super.onCreate();

        // Create a new reference of the database of timers
        this.mTimers = Collections.synchronizedMap( new HashMap<Long, Timer>() );

        // Get common services
        this.mNotificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        this.mAlarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
        this.mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        this.mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        this.mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);

        // Creates a wake lock for use when a timer is running
        this.mWakeLock = this.mPowerManager.newWakeLock(
                                    PowerManager.PARTIAL_WAKE_LOCK,
                                    "TimerClockService WakeLock"); //$NON-NLS-1$

        // Register broadcast
        IntentFilter filter = new IntentFilter();
        filter.addAction(BROADCAST_END_COUNTDOWN);
        registerReceiver(this.mTimerEventsReceiver, filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        if (DEBUG) Log.d(LOG_TAG, "onDestroy"); //$NON-NLS-1$

        // Unregister broadcast
        unregisterReceiver(this.mTimerEventsReceiver);

        //Remove the active timers
        Iterator<Long> it = this.mTimers.keySet().iterator();
        while (it.hasNext()) {
            try {
                Long timerId = it.next();
                removeTimer(timerId);
            } catch (Exception e) {/**NON BLOCK**/}
        }
        this.mTimers.clear();

        // Remove wake lock (never more needed)
        try {
            this.mWakeLock.release();
        } catch (Exception e) {/**NON BLOCK**/}

        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
        if (DEBUG) Log.d(LOG_TAG, "onBind: " + intent); //$NON-NLS-1$
        return this.mBinder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onUnbind(Intent intent) {
        if (DEBUG) Log.d(LOG_TAG, "onUnbind: " + intent); //$NON-NLS-1$
        // If no timers are active then shutdown the service
        synchronized (this.mServiceSync) {
            if (this.mTimers.size() == 0) {
                stopSelf();
            }
        }
        return true;
    }

    /**
     * Method that remove a timer
     *
     * @param timerId The timer identifier to remove
     */
    private void removeTimer(Long timerId) {
        if (this.mTimers.containsKey(timerId)) {
            Timer timer = this.mTimers.get(timerId);
            try {
                // Release mediaplayer
                if (timer.mMediaPlayer != null) {
                    try {
                        if (timer.mMediaPlayer.isPlaying()) {
                            timer.mMediaPlayer.stop();
                        }
                    } catch (Throwable _throw) {/**NON BLOCK**/}
                    try {
                        timer.mMediaPlayer.release();
                    } catch (Throwable _throw) {/**NON BLOCK**/}
                    timer.mMediaPlayer = null;
                }
            } catch (Exception ex) {/**NON BLOCK**/}
            try {
                // Cancel notification countdown
                if (timer.mHandler != null && timer.mNotificationTask != null) {
                    timer.mHandler.removeCallbacks(timer.mNotificationTask);
                    timer.mHandler = null;
                }
            } catch (Exception ex) {/**NON BLOCK**/}
            try {
                if (timer != null && timer.mNotityIntent != null) {
                    timer.mNotityIntent.cancel();
                }
            } catch (Exception ex) {/**NON BLOCK**/}
            try {
                if (timer != null && timer.mNotificationId != 0) {
                    this.mNotificationManager.cancel(timer.mNotificationId);
                }
            } catch (Exception ex) {/**NON BLOCK**/}
            this.mTimers.remove(timerId);
        }
    }

    /**
     * Method that returns a timer reference
     *
     * @param timerId The timer identifier
     * @return Timer The timer reference
     * @throws RemoteException If the timer identifier is invalid
     */
    private Timer getTimer(Long timerId) throws RemoteException {
        if (!this.mTimers.containsKey(timerId)) {
            throw new RemoteException("Invalid timer");  //$NON-NLS-1$
        }
        return this.mTimers.get(timerId);
    }

    /**
     * Method that returns the time of the timer
     *
     * @param timerId The timer identifier
     * @return long The time in milliseconds
     * @throws RemoteException If the timer identifier is invalid
     */
    private long internalQueryTime(long timerId) throws RemoteException {
        return internalQueryTime(timerId, System.currentTimeMillis());
    }

    /**
     * Method that returns the time of the timer
     *
     * @param timerId The timer identifier
     * @param now The current time in milliseconds
     * @return long The time in milliseconds
     * @throws RemoteException If the timer identifier is invalid
     */
    private long internalQueryTime(long timerId, long now) throws RemoteException {
        Timer timer = getTimer(Long.valueOf(timerId));
        return internalQueryTime(timer, now);
    }

    /**
     * Method that returns the time of the timer
     *
     * @param timerId The timer identifier
     * @return long The time in milliseconds
     */
    private static long internalQueryTime(Timer timer) {
        return internalQueryTime(timer, System.currentTimeMillis());
    }

    /**
     * Method that returns the time of the timer
     *
     * @param timerId The timer identifier
     * @param now The current time in milliseconds
     * @return long The time in milliseconds
     */
    private static long internalQueryTime(Timer timer, long now) {
        synchronized (timer.mSync) {
            switch (timer.mType) {
            case STOPWATCH:
                if (timer.mRunning) {
                    return Math.max(now - timer.mStartMillisecond + timer.mElapsed, 0);
                }
                return Math.max(timer.mElapsed, 0);

            case COUNTDOWN:
                if (timer.mRunning) {
                    return Math.max(timer.mEndMillisecond - now, 0);
                }
                if (timer.mStartMillisecond == 0) return 0;
                return Math.max(timer.mEndMillisecond - timer.mStartMillisecond, 0);

            default:
                break;
            }
            return 0; // What is this?
        }
    }

    /**
     * Method that create an scheduler {@link PendingIntent} for broadcast a
     * {@link BROADCAST_END_COUNTDOWN} notification
     *
     * @param timer The timer reference that need the notification
     */
    private void createOnEndCountDownNotityIntent(Timer timer) {
        // Create a broadcast pending intent with a BROADCAST_END_COUNTDOWN event
        Intent intent = new Intent(TimerClockService.BROADCAST_END_COUNTDOWN);
        intent.putExtra(EXTRA_TIMER_ID, timer.mId);
        timer.mNotityIntent = PendingIntent.getBroadcast(
                                TimerClockService.this,
                                0,
                                intent,
                                PendingIntent.FLAG_ONE_SHOT);
        this.mAlarmManager.set(AlarmManager.RTC, timer.mEndMillisecond, timer.mNotityIntent);

        if (DEBUG) Log.i(LOG_TAG, String.format(
                "Planning intent if timer %d on %d milliseconds",  //$NON-NLS-1$
                Long.valueOf(timer.mId), Long.valueOf(timer.mEndMillisecond)));
    }

    /**
     * Method that update the countdown notification
     *
     * @param timer The timer to update
     * @param msg The title to show to the user
     * @param msg The message to show to the user
     * @param summary The summary to show to the user
     * @param endCountDown If the notification is an end of countdown
     */
    private synchronized void createCountDownNotification(
            Timer timer, String title, String msg, String summary, boolean endCountDown) {

        // Cancels active notification
        if (timer.mNotificationId != 0) {
            TimerClockService.this.mNotificationManager.cancel(timer.mNotificationId);
        }

        // Create the intent to associate to notification
        Intent countDownActivityIntent = new Intent(TimerClockService.this, DeskClock.class);
        countDownActivityIntent.putExtra(EXTRA_TIMER_ID, Long.valueOf(timer.mId));
        countDownActivityIntent.putExtra(EXTRA_TIMER_TYPE, TIMER_TYPE.COUNTDOWN);
        countDownActivityIntent.putExtra(EXTRA_TIMER_ACTION,
                endCountDown ? ACTION_END_COUNTDOWN : ACTION_SHOW_COUNTDOWN);
        PendingIntent intent =
                PendingIntent.getActivity(
                        this, 0, countDownActivityIntent,
                                Intent.FLAG_ACTIVITY_NEW_TASK |
                                Intent.FLAG_FROM_BACKGROUND);

        // Create the notification
        Notification.Builder builder = new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.stat_notify_timer);
        builder.setWhen(System.currentTimeMillis());
        builder.setContentIntent(intent);

        // Use the BigTextStyle to build the notification
        Notification.BigTextStyle bigBuilder = new Notification.BigTextStyle(builder);
        bigBuilder.setBigContentTitle(title);
        bigBuilder.bigText(msg);
        bigBuilder.setSummaryText(summary);
        timer.mNotificationBuilder = bigBuilder;

        // Send notification
        updateCountDownNotification(timer);
    }

    /**
     * Method that update notification request to the system
     *
     * @param timer The timer
     */
    private void updateCountDownNotification(Timer timer) {
        // Build notification
        Notification notification = timer.mNotificationBuilder.build();
        notification.flags = Notification.FLAG_ONGOING_EVENT;
        if (timer.mNotificationId == 0) {
            timer.mNotificationId = ++sNotificationsIds;
        }
        // Send notification
        this.mNotificationManager.notify(timer.mNotificationId, notification);
    }

    /**
     * Method that shows the final countdown notification (according to the preferences)
     *
     * @param timer The timer
     */
    private void showFinalCountDownNotification(Timer timer) {
        boolean showNotification = getCountDownPreferences().getBoolean(
                CountDownPreferences.PREF_ONFINALCOUNTDOWN_NOTIFICATION, true);
        if (showNotification) {
            createCountDownNotification(
                    timer,
                    getString(R.string.service_countdown_notification_title),
                    getString(R.string.service_countdown_notification_msg_end),
                    getString(
                            R.string.service_countdown_notification_summary,
                            TimerHelper.formatTime(
                                    0,
                                    TimerHelper.TIMEFORMAT_SHORT,
                                    false)),
                    true);
        } else {
            // In case the notification preference has changed
            // Cancels active notification
            if (timer.mNotificationId != 0) {
                TimerClockService.this.
                    mNotificationManager.cancel(timer.mNotificationId);
            }
        }
    }

    /**
     * Method that shows the clock view (according to the preferences)
     *
     * @param timer The timer
     * @param onCall If the phone is on a call
     */
    private void showFinalCountDownView(Timer timer, boolean onCall) {
        boolean openClockView = getCountDownPreferences().getBoolean(
                CountDownPreferences.PREF_ONFINALCOUNTDOWN_OPEN_CLOCK_SCREEN, true);
        if (!onCall && openClockView) {
            Intent countDownActivityIntent = new Intent(TimerClockService.this, DeskClock.class);
            countDownActivityIntent.putExtra(EXTRA_TIMER_ID, Long.valueOf(timer.mId));
            countDownActivityIntent.putExtra(EXTRA_TIMER_TYPE, TIMER_TYPE.COUNTDOWN);
            countDownActivityIntent.putExtra(EXTRA_TIMER_ACTION, ACTION_END_COUNTDOWN);
            countDownActivityIntent.addFlags(
                                        Intent.FLAG_ACTIVITY_NEW_TASK |
                                        Intent.FLAG_FROM_BACKGROUND);
            // If screen is off when need to force the use of FLAG_ACTIVITY_CLEAR_TASK
            // to ensure the screen is turn on when the onNewIntent is received
            if (!this.mPowerManager.isScreenOn()) {
                countDownActivityIntent.addFlags(
                                        Intent.FLAG_ACTIVITY_CLEAR_TASK);
            }

            startActivity(countDownActivityIntent);
        } else {
            // Show a message (this don't disrupt user activity)
            Toast.makeText(
                    TimerClockService.this,
                    R.string.service_countdown_notification_msg_end,
                    Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Method that plays an alarm on final countdown (according to the preferences)
     *
     * @param timer The timer
     * @param onCall If the phone is on a call
     */
    private void playAlarmOnFinalCountDown(final Timer timer, final boolean onCall) {
        // Check that phone is not on a call. Otherwise don't disrupt user
        if (onCall) return;

        // Run in background
        Thread t = new Thread() {
            @Override
            @SuppressWarnings("synthetic-access")
            public void run() {
                final MediaPlayer player = timer.mMediaPlayer;
                try {
                    // Retrieve user preference
                    final String ringtone =
                            getCountDownPreferences().getString(
                                 CountDownPreferences.
                                     PREF_ONFINALCOUNTDOWN_RINGTONE, ""); //$NON-NLS-1$
                    if (ringtone == null || ringtone.trim().length() == 0) {
                        // Silent. Do nothing
                        return;
                    }
                    Uri alert = Uri.parse(ringtone);

                    final long alarmStart = System.currentTimeMillis();

                    // Play the ringtone with mediaplayer
                    player.setDataSource(TimerClockService.this, alert);
                    // do not play alarms if stream volume is 0
                    // (typically because ringer mode is silent).
                    int volume = TimerClockService.this.mAudioManager.
                                        getStreamVolume(AudioManager.STREAM_ALARM);
                    if (volume != 0) {
                        player.setAudioStreamType(AudioManager.STREAM_ALARM);
                        player.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                            public void onCompletion(MediaPlayer mp) {
                                // Repeat only FINAL_COUNTDOWN_ALARM_MAX_TIME miliseconds
                                final long alarmEnd = System.currentTimeMillis();
                                if (alarmEnd - alarmStart >
                                        CountDownPreferences.FINAL_COUNTDOWN_ALARM_MAX_TIME) {
                                    if (mp.isPlaying()) {
                                        mp.stop();
                                    }
                                    mp.reset();
                                } else {
                                    // New looping
                                    mp.seekTo(0);
                                    mp.start();
                                }
                            }
                        });
                        player.prepare();
                        player.start();
                    }
                } catch (Throwable e) {
                    Log.e(LOG_TAG, "PlayAlarmOnFinalCountDown fails.", e); //$NON-NLS-1$
                    try {
                        player.reset();
                    } catch (Exception e2) {/**NON BLOCK**/}
                }

            }
        };
        t.start();
    }

    /**
     * Method that vibrate on final countdown (according to the preferences)
     *
     * @param timer The timer
     * @param onCall If the phone is on a call
     */
    private void vibrateOnFinalCountDown(final Timer timer, final boolean onCall) {
        // Check that phone is not on a call. Otherwise check user preference
        final boolean vibrateOnCall =
                getCountDownPreferences().getBoolean(
                        CountDownPreferences.PREF_ONFINALCOUNTDOWN_VIBRATE_ON_CALL, false);
        if (onCall && !vibrateOnCall) return;

        // Run in background
        Thread t = new Thread() {
            @Override
            @SuppressWarnings("synthetic-access")
            public void run() {

                // Determine vibrator behaviour (according to the preferences)
                final int vibrateFlag =
                     Integer.valueOf(
                        getCountDownPreferences().getString(
                            CountDownPreferences.PREF_ONFINALCOUNTDOWN_VIBRATE,
                            String.valueOf(CountDownPreferences.VIBRATE_TYPE.NEVER.ordinal()))
                        ).intValue();
                final boolean isRingerSilent =
                        TimerClockService.this.mAudioManager.
                                getRingerMode() != AudioManager.RINGER_MODE_NORMAL;
                final boolean vibrate =
                        vibrateFlag == CountDownPreferences.VIBRATE_TYPE.ALWAYS.ordinal();
                final boolean vibrateWhenSilent =
                        vibrateFlag == CountDownPreferences.VIBRATE_TYPE.ONLY_ON_SILENT.ordinal();

                // Vibrate ??
                if (vibrate || (vibrateWhenSilent && isRingerSilent)) {
                        TimerClockService.this.mVibrator.
                            vibrate(CountDownPreferences.VIBRATOR_PATTERN, -1);
                }
            }
        };
        t.start();
    }

    /**
     * Method that returns the shared preferences of the CountDown timer
     */
    private SharedPreferences getCountDownPreferences() {
        return getSharedPreferences(
                CountDownPreferences.PREFERENCES_FILENAME, Context.MODE_PRIVATE);
    }

}
