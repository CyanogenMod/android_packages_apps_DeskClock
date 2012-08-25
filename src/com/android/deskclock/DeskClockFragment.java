/*
 * Copyright (C) 2009 The Android Open Source Project
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

import static android.os.BatteryManager.BATTERY_STATUS_UNKNOWN;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v4.app.Fragment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.AbsoluteLayout;
import android.widget.TextView;

import java.util.Calendar;
import java.util.Date;
import java.util.Random;

/**
 * A {@link Fragment} class for the <code>DeskClock</code> of {@link DeskClock} activity.
 */
public class DeskClockFragment extends Fragment implements
        DeskClock.OnBroadcastReceiver, DeskClock.FocusableFragment, DeskClock.ReportableFragment {
    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "DeskClockFragment";

    // Intent to broadcast for dock settings.
    private static final String DOCK_SETTINGS_ACTION = "com.android.settings.DOCK_SETTINGS";

    // Alarm action for midnight (so we can update the date display).
    /**
     * @hide
     */
    public static final String ACTION_MIDNIGHT = "com.android.deskclock.MIDNIGHT";
    private static final String KEY_DIMMED = "dimmed";
    private static final String KEY_SCREEN_SAVER = "screen_saver";

    private static final String KEY_CLING = "cling_deskclock";

    // This controls whether or not we will show a battery display when plugged
    // in.
    private static final boolean USE_BATTERY_DISPLAY = false;

    // Delay before engaging the burn-in protection mode (green-on-black).
    private final long SCREEN_SAVER_TIMEOUT = 5 * 60 * 1000; // 5 min

    // Repositioning delay in screen saver.
    public static final long SCREEN_SAVER_MOVE_DELAY = 60 * 1000; // 1 min

    // Color to use for text & graphics in screen saver mode.
    private int SCREEN_SAVER_COLOR = 0xFF006688;
    private int SCREEN_SAVER_COLOR_DIM = 0xFF001634;

    // Opacity of black layer between clock display and wallpaper.
    private final float DIM_BEHIND_AMOUNT_NORMAL = 0.4f;
    private final float DIM_BEHIND_AMOUNT_DIMMED = 0.8f; // higher contrast when display dimmed

    private final int SCREEN_SAVER_TIMEOUT_MSG   = 0x2000;
    private final int SCREEN_SAVER_MOVE_MSG      = 0x2001;

    // State variables follow.
    private DigitalClock mTime;
    private TextView mDate;

    private TextView mNextAlarm = null;
    private TextView mBatteryDisplay;

    private View mAlarmButton;

    private ViewGroup mFragmentView;

    private View mCling;
    private boolean mShowingCling;

    private boolean mDimmed = false;
    private boolean mScreenSaverMode = false;

    private String mDateFormat;

    private int mBatteryLevel = -1;
    private boolean mPluggedIn = false;

    private Random mRNG;

    private PendingIntent mMidnightIntent;

    private boolean mBroadcastReceiverReady = false;

    private AlarmManager mAlarmManager;

    private Activity mActivity;

    private boolean mHasFocus = true;

    @Override
    public boolean isBroadcastReceiverReady() {
        return mBroadcastReceiverReady;
    }

    @Override
    public void onBroadcastReceiver(Context context, Intent intent) {
        final String action = intent.getAction();
        if (Intent.ACTION_DATE_CHANGED.equals(action) || ACTION_MIDNIGHT.equals(action)) {
            refreshDate();
        } else if (Intent.ACTION_BATTERY_CHANGED.equals(action)) {
            handleBatteryUpdate(
                intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0),
                intent.getIntExtra(BatteryManager.EXTRA_STATUS, BATTERY_STATUS_UNKNOWN),
                intent.getIntExtra(BatteryManager.EXTRA_LEVEL, 0));
        }
    }

    private final Handler mHandy = new Handler() {
        @Override
        public void handleMessage(Message m) {
            if (m.what == SCREEN_SAVER_TIMEOUT_MSG) {
                saveScreen();
            } else if (m.what == SCREEN_SAVER_MOVE_MSG) {
                moveScreenSaver();
            }
        }
    };

    private void moveScreenSaver() {
        moveScreenSaverTo(-1,-1);
    }
    private void moveScreenSaverTo(int x, int y) {
        if (!mScreenSaverMode) return;

        final View saver_view = mFragmentView.findViewById(R.id.saver_view);

        DisplayMetrics metrics = new DisplayMetrics();
        getFragmentActivity().getWindowManager().getDefaultDisplay().getMetrics(metrics);

        if (x < 0 || y < 0) {
            int myWidth = saver_view.getMeasuredWidth();
            int myHeight = saver_view.getMeasuredHeight();
            x = (int)(mRNG.nextFloat()*(metrics.widthPixels - myWidth));
            y = (int)(mRNG.nextFloat()*(metrics.heightPixels - myHeight));
        }

        if (DEBUG) Log.d(LOG_TAG, String.format("screen saver: %d: jumping to (%d,%d)",
                System.currentTimeMillis(), x, y));

        saver_view.setLayoutParams(new AbsoluteLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            x,
            y));

        // Synchronize our jumping so that it happens exactly on the second.
        mHandy.sendEmptyMessageDelayed(SCREEN_SAVER_MOVE_MSG,
            SCREEN_SAVER_MOVE_DELAY +
            (1000 - (System.currentTimeMillis() % 1000)));
    }

    private void setWakeLock(boolean hold) {
        if (!this.mHasFocus) return;
        if (DEBUG) Log.d(LOG_TAG, (hold ? "hold" : " releas") + "ing wake lock");
        Window win = getFragmentActivity().getWindow();
        if (win != null) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.flags |= (WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON
                    | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
            if (hold)
                winParams.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
            else
                winParams.flags &= (~WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            win.setAttributes(winParams);
        }
    }

    private void scheduleScreenSaver() {
        if (!getResources().getBoolean(R.bool.config_requiresScreenSaver)) {
            return;
        }

        // reschedule screen saver
        mHandy.removeMessages(SCREEN_SAVER_TIMEOUT_MSG);
        mHandy.sendMessageDelayed(
            Message.obtain(mHandy, SCREEN_SAVER_TIMEOUT_MSG),
            SCREEN_SAVER_TIMEOUT);
    }

    /**
     * Restores the screen by quitting the screensaver. This should be called only when
     * {@link #mScreenSaverMode} is true.
     */
    private void restoreScreen() {
        if (!mScreenSaverMode) return;
        if (DEBUG) Log.d(LOG_TAG, "restoreScreen");
        mScreenSaverMode = false;

        initViews();
        doDim(false); // restores previous dim mode

        scheduleScreenSaver();
        refreshAll();
    }

    /**
     * Start the screen-saver mode. This is useful for OLED displays that burn in quickly.
     * This should only be called when {@link #mScreenSaverMode} is false;
     */
    private void saveScreen() {
        if (mScreenSaverMode) return;
        if (DEBUG) Log.d(LOG_TAG, "saveScreen");

        // quickly stash away the x/y of the current date
        final View oldTimeDate = mFragmentView.findViewById(R.id.time_date);
        int oldLoc[] = new int[2];
        oldLoc[0] = oldLoc[1] = -1;
        if (oldTimeDate != null) { // monkeys tell us this is not always around
            oldTimeDate.getLocationOnScreen(oldLoc);
        }

        mScreenSaverMode = true;
        Window win = getFragmentActivity().getWindow();
        if (win != null) {
            WindowManager.LayoutParams winParams = win.getAttributes();
            winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
            win.setAttributes(winParams);
        }

        // give up any internal focus before we switch layouts
        final View focused = getFragmentActivity().getCurrentFocus();
        if (focused != null) focused.clearFocus();

        setFragmentContentView(R.layout.desk_clock_saver);

        mTime = (DigitalClock) mFragmentView.findViewById(R.id.time);
        mDate = (TextView) mFragmentView.findViewById(R.id.date);

        final int color = mDimmed ? SCREEN_SAVER_COLOR_DIM : SCREEN_SAVER_COLOR;

        ((AndroidClockTextView)mFragmentView.findViewById(R.id.timeDisplay)).setTextColor(color);
        ((AndroidClockTextView)mFragmentView.findViewById(R.id.am_pm)).setTextColor(color);
        mDate.setTextColor(color);

        mTime.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE);

        mBatteryDisplay = null;

        refreshDate();
        refreshAlarm();

        if (oldLoc[0] >= 0) {
            moveScreenSaverTo(oldLoc[0], oldLoc[1]);
        } else {
            moveScreenSaver();
        }
    }

    // Adapted from KeyguardUpdateMonitor.java
    private void handleBatteryUpdate(int plugged, int status, int level) {
        final boolean pluggedIn = (plugged != 0);
        if (pluggedIn != mPluggedIn) {
            setWakeLock(pluggedIn);
        }
        if (pluggedIn != mPluggedIn || level != mBatteryLevel) {
            mBatteryLevel = level;
            mPluggedIn = pluggedIn;
            refreshBattery();
        }
    }

    private void refreshBattery() {
        // UX wants the battery level removed. This makes it not visible but
        // allows it to be easily turned back on if they change their mind.
        if (!USE_BATTERY_DISPLAY)
            return;
        if (mBatteryDisplay == null) return;

        if (mPluggedIn /* || mBatteryLevel < LOW_BATTERY_THRESHOLD */) {
            mBatteryDisplay.setCompoundDrawablesWithIntrinsicBounds(
                0, 0, android.R.drawable.ic_lock_idle_charging, 0);
            mBatteryDisplay.setText(
                getString(R.string.battery_charging_level, mBatteryLevel));
            mBatteryDisplay.setVisibility(View.VISIBLE);
        } else {
            mBatteryDisplay.setVisibility(View.INVISIBLE);
        }
    }

    private void refreshDate() {
        final Date now = new Date();
        if (DEBUG) Log.d(LOG_TAG, "refreshing date..." + now);
        mDate.setText(DateFormat.format(mDateFormat, now));
    }

    private void refreshAlarm() {
        if (mNextAlarm == null) return;

        String nextAlarm = Settings.System.getString(getFragmentActivity().getContentResolver(),
                Settings.System.NEXT_ALARM_FORMATTED);
        if (!TextUtils.isEmpty(nextAlarm)) {
            mNextAlarm.setText(getString(R.string.control_set_alarm_with_existing, nextAlarm));
            mNextAlarm.setVisibility(View.VISIBLE);
        } else if (mAlarmButton != null) {
            mNextAlarm.setVisibility(View.INVISIBLE);
        } else {
            mNextAlarm.setText(R.string.control_set_alarm);
            mNextAlarm.setVisibility(View.VISIBLE);
        }
    }

    private void refreshAll() {
        refreshDate();
        refreshAlarm();
        refreshBattery();
    }

    private void doDim(boolean fade) {
        if (mFragmentView == null) return;
        View tintView = mFragmentView.findViewById(R.id.window_tint);
        if (tintView == null) return;

        mTime.setSystemUiVisibility(mDimmed ? View.SYSTEM_UI_FLAG_LOW_PROFILE
                : View.SYSTEM_UI_FLAG_VISIBLE);

        Window win = getFragmentActivity().getWindow();
        if (win != null) {
            WindowManager.LayoutParams winParams = win.getAttributes();

            winParams.flags |= (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
            winParams.flags |= (WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

            // dim the wallpaper somewhat (how much is determined below)
            winParams.flags |= (WindowManager.LayoutParams.FLAG_DIM_BEHIND);

            if (mDimmed) {
                winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                winParams.dimAmount = DIM_BEHIND_AMOUNT_DIMMED;
                winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;

                // show the window tint
                tintView.startAnimation(AnimationUtils.loadAnimation(getFragmentActivity(),
                    fade ? R.anim.dim
                         : R.anim.dim_instant));
            } else {
                winParams.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                winParams.dimAmount = DIM_BEHIND_AMOUNT_NORMAL;
                winParams.buttonBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

                // hide the window tint
                tintView.startAnimation(AnimationUtils.loadAnimation(getFragmentActivity(),
                    fade ? R.anim.undim
                         : R.anim.undim_instant));
            }

            win.setAttributes(winParams);
        }
    }

    @Override
    public void onStart() {
        super.onStart();

        SCREEN_SAVER_COLOR = getResources().getColor(R.color.screen_saver_color);
        SCREEN_SAVER_COLOR_DIM = getResources().getColor(R.color.screen_saver_dim_color);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) Log.d(LOG_TAG, "onResume with intent: " + getFragmentActivity().getIntent());

        // reload the date format in case the user has changed settings
        // recently
        mDateFormat = getString(R.string.full_wday_month_day_no_year);

        // Elaborate mechanism to find out when the day rolls over
        Calendar today = Calendar.getInstance();
        today.set(Calendar.HOUR_OF_DAY, 0);
        today.set(Calendar.MINUTE, 0);
        today.set(Calendar.SECOND, 0);
        today.add(Calendar.DATE, 1);
        long alarmTimeUTC = today.getTimeInMillis();

        mMidnightIntent = PendingIntent.getBroadcast(
                getFragmentActivity(), 0, new Intent(ACTION_MIDNIGHT), 0);
        mAlarmManager =
                (AlarmManager) getFragmentActivity().getSystemService(Context.ALARM_SERVICE);
        mAlarmManager.setRepeating(
                AlarmManager.RTC, alarmTimeUTC, AlarmManager.INTERVAL_DAY, mMidnightIntent);
        if (DEBUG) Log.d(LOG_TAG, "set repeating midnight event at UTC: "
            + alarmTimeUTC + " ("
            + (alarmTimeUTC - System.currentTimeMillis())
            + " ms from now) repeating every "
            + AlarmManager.INTERVAL_DAY + " with intent: " + mMidnightIntent);

        // Adjust the display to reflect the currently chosen dim mode.
        doDim(false);
        if (!mScreenSaverMode) {
            restoreScreen(); // disable screen saver
        } else {
            // we have to set it to false because savescreen returns early if
            // it's true
            mScreenSaverMode = false;
            saveScreen();
        }
        refreshAll();
        setWakeLock(mPluggedIn);
        scheduleScreenSaver();

        // Check if show the helper swipe text (only the first time)
        SharedPreferences sp = this.getFragmentActivity().getPreferences(Context.MODE_PRIVATE);
        boolean showCling = sp.getBoolean(KEY_CLING, true);
        if (showCling && !mShowingCling && mCling != null) {
            mShowingCling = true;
            mCling.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mCling.setVisibility(View.VISIBLE);
                    Animation animation =
                            AnimationUtils.loadAnimation(getFragmentActivity(), R.anim.dim);
                    mCling.clearAnimation();
                    mCling.startAnimation(animation);
                }
            }, 1000L);
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) Log.d(LOG_TAG, "onPause");

        // Turn off the screen saver and cancel any pending timeouts.
        // (But don't un-dim.)
        mHandy.removeMessages(SCREEN_SAVER_TIMEOUT_MSG);

        mAlarmManager.cancel(mMidnightIntent);

        super.onPause();
    }

    @Override
    public void onGainFocus() {
        this.mHasFocus = true;
        if (!mScreenSaverMode) {
            restoreScreen();
        }
        setWakeLock(mPluggedIn);
    }

    @Override
    public void onLostFocus() {
        mDimmed = false;
        doDim(true);
        this.mHasFocus = false;
    }

    private void initViews() {
        // give up any internal focus before we switch layouts
        final View focused = getFragmentActivity().getCurrentFocus();
        if (focused != null) focused.clearFocus();

        setFragmentContentView(R.layout.desk_clock);

        mTime = (DigitalClock) mFragmentView.findViewById(R.id.time);
        mDate = (TextView) mFragmentView.findViewById(R.id.date);
        mBatteryDisplay = (TextView) mFragmentView.findViewById(R.id.battery);

        mCling = mFragmentView.findViewById(R.id.deskclock_cling);

        mTime.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        mTime.getRootView().requestFocus();

        final View.OnClickListener alarmClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mHasFocus = true;
                if (mDimmed) {
                    mDimmed = false;
                    doDim(true);
                }
                startActivity(new Intent(getFragmentActivity(), AlarmClock.class));
            }
        };

        mNextAlarm = (TextView) mFragmentView.findViewById(R.id.nextAlarm);
        mNextAlarm.setOnClickListener(alarmClickListener);

        mAlarmButton = mFragmentView.findViewById(R.id.alarm_button);
        View alarmControl =
                mAlarmButton != null ? mAlarmButton : mFragmentView.findViewById(R.id.nextAlarm);
        alarmControl.setOnClickListener(alarmClickListener);

        View touchView = mFragmentView.findViewById(R.id.window_touch);
        touchView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // If cling is visible ignore user click
                if (mCling != null && mCling.getVisibility() == View.VISIBLE) return;
                // If the screen saver is on let onUserInteraction handle it
                mHasFocus = true;
                if (!mScreenSaverMode) {
                    mDimmed = !mDimmed;
                    doDim(true);
                }
            }
        });
        touchView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                saveScreen();
                return true;
            }
        });
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (mScreenSaverMode) {
            moveScreenSaver();
        } else {
            initViews();
            doDim(false);
            refreshAll();
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mRNG = new Random();
        if (icicle != null) {
            mDimmed = icicle.getBoolean(KEY_DIMMED, false);
            mScreenSaverMode = icicle.getBoolean(KEY_SCREEN_SAVER, false);
        }
        mBroadcastReceiverReady = true;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mFragmentView = (ViewGroup)inflater.inflate(R.layout.desk_clock_fragment, null);
        initViews();
        return mFragmentView;
    }

    @Override
    public void onNotifyUserInteraction() {
        if (mShowingCling && mCling != null) {
            mShowingCling = false;
            SharedPreferences sp = this.getFragmentActivity().getPreferences(Context.MODE_PRIVATE);
            Editor editor = sp.edit();
            editor.putBoolean(KEY_CLING, false);
            editor.apply();

            mCling.post(new Runnable() {
                @Override
                public void run() {
                    Animation animation =
                            AnimationUtils.loadAnimation(getFragmentActivity(), R.anim.undim);
                    animation.setAnimationListener(new Animation.AnimationListener() {
                        public void onAnimationStart(Animation animation) {/**NON BLOCK**/}

                        public void onAnimationRepeat(Animation animation) {/**NON BLOCK**/}

                        public void onAnimationEnd(Animation animation) {
                            mCling.setVisibility(View.GONE);
                        }
                    });
                    mCling.startAnimation(animation);
                }
            });
        }
        if (mScreenSaverMode) {
            restoreScreen();
        }
    }

    @Override
    public boolean onNotifyPrepareOptionsMenu(Menu menu) {
      boolean isDockSupported =
              (getFragmentActivity().getPackageManager().resolveActivity(
                      new Intent(DOCK_SETTINGS_ACTION), 0) != null);
      menu.findItem(R.id.menu_item_dock_settings).setVisible(isDockSupported);
      return true;
    }

    @Override
    public boolean onNotifyOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_item_dock_settings:
                startActivity(new Intent(DOCK_SETTINGS_ACTION));
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onNotifyNewIntent(Intent newIntent) {/**NON BLOCK**/}

    @Override
    public boolean onNotifyBackPressed() {
        return false;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(KEY_DIMMED, mDimmed);
        outState.putBoolean(KEY_SCREEN_SAVER, mScreenSaverMode);
    }

    private void setFragmentContentView(int content) {
        mFragmentView.removeAllViews();
        LayoutInflater li =
                (LayoutInflater) getFragmentActivity().
                    getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View v = (View)li.inflate(content, mFragmentView, false);
        mFragmentView.addView(v);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.v(LOG_TAG, "onAttach: " + activity); //$NON-NLS-1$
        super.onAttach(activity);

        this.mActivity = activity;
    }

    /**
     * Method that returns the activity of fragment
     *
     * @return Activity The activity of fragment
     */
    protected Activity getFragmentActivity() {
        return this.mActivity;
    }
}
