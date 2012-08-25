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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Toast;

import com.android.deskclock.DigitalTimerClock.OnEditListener;

import java.util.Timer;
import java.util.TimerTask;

/**
 * The base class for all the timer fragments
 */
public abstract class AbstractTimerFragment extends Fragment implements
    DeskClock.Fragmentable, DeskClock.OnBroadcastReceiver, DeskClock.FocusableFragment,
    DeskClock.ReportableFragment, View.OnLongClickListener, OnEditListener {

    private static final boolean DEBUG = false;

    private boolean mBroadcastReceiverReady = false;

    private static final String PREF_TIMER_ID = "timer_id"; //$NON-NLS-1$

    private static final long DIM_ON_USER_INACTIVITY_TIME = 60000L;

    private static final long REFRESH_RATE_PORTRAIT = 50L;
    private static final long REFRESH_RATE_LANDSCAPE = 100L;

    // Opacity of black layer between clock display.
    private final float DIM_BEHIND_AMOUNT_NORMAL = 0.4f;
    private final float DIM_BEHIND_AMOUNT_DIMMED = 0.8f; // higher contrast when display dimmed

    // The timer identifier. This is use to identify the timer in the service.
    // A new identifier is returned when a timer is created
    private long mTimerId;
    private ITimerClockService mTimerClockService;
    private boolean mTimerClockServiceBound;

    private boolean mShowingCling;

    private Activity mActivity;
    private Handler mHandler;
    private Timer mDimTimer = null;

    private long mRefreshRate;

    private boolean mDimmed;

    // The timer service connection listener interface
    private final ServiceConnection mTimerClockServiceConnection = new ServiceConnection() {
        @SuppressWarnings("synthetic-access")
        public void onServiceDisconnected(ComponentName name) {
            if (DEBUG) Log.v(getLogTag(), "onServiceDisconnected"); //$NON-NLS-1$
            AbstractTimerFragment.this.mTimerClockServiceBound = false;

            // Update data?
            if (isTimerCreated()) {
                AbstractTimerFragment.this.mHandler.post(new Runnable() {
                    public void run() {
                        // Advice the user and reset timer
                        showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
                        resetTimer();
                    }
                });
            }

            // Broadcast
            AbstractTimerFragment.this.onServiceDisconnected();
        }

        @SuppressWarnings("synthetic-access")
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DEBUG) Log.v(getLogTag(), "onServiceConnected"); //$NON-NLS-1$
            AbstractTimerFragment.this.mTimerClockService = (ITimerClockService)service;
            AbstractTimerFragment.this.mTimerClockServiceBound = true;

            // Update data?
            AbstractTimerFragment.this.mHandler.post(new Runnable() {
                public void run() {
                    // Timer was created?
                    if (isTimerCreated()) {
                        try {
                            //Try to the refresh the data
                            updateTimers(false);
                            if (isTimerRunning()) {
                                // Attach the drawing handler
                                AbstractTimerFragment.this.mRedrawUiThread.reset();
                                AbstractTimerFragment.this.mHandler.post(
                                        AbstractTimerFragment.this.mRedrawUiThread);
                            }

                            // The UI need to be checked
                            onUiChanged();

                        } catch (RemoteException rEx) {
                            resetTimer();
                        }
                    }

                    // Broadcast
                    AbstractTimerFragment.this.onServiceConnected();
                }
            });
        }
    };

    // A thread to redraw the UI, implemented as a recursive handler
    private class RedrawUiThread implements Runnable {
        private boolean mCancel;
        private boolean mPaused = true;

        /**
         * Method that reset the drawing thread
         */
        public void reset() {
            this.mCancel = false;
        }

        /**
         * Method that cancels the execution of the drawing thread
         */
        public void cancel() {
            this.mCancel = true;
        }

        /**
         * Method that returns if the UI thread is paused
         */
        public boolean isPaused() {
            return this.mPaused;
        }

        @SuppressWarnings("synthetic-access")
        public void run() {
            try {
                this.mPaused = false;
                if (!this.mCancel) {
                    // Update the timers
                    updateTimers(false);
                    if (!continueRedrawing()) {
                        this.mCancel = true;
                    } else {
                        AbstractTimerFragment.this.mHandler.postDelayed(
                                                    this,
                                                    AbstractTimerFragment.this.mRefreshRate);
                    }
                    onUiChanged();
                } else {
                    Log.i(getLogTag(), "RedrawUiThread paused"); //$NON-NLS-1$
                }

            } catch (RemoteException rEx) {
                Log.e(getLogTag(), "RedrawUiThread exception", rEx); //$NON-NLS-1$

            } catch (Exception ex) {
                Log.e(getLogTag(), "RedrawUiThread exception", ex); //$NON-NLS-1$
            } finally {
                this.mPaused = true;
            }
        }
    }
    @SuppressWarnings("synthetic-access")
    private final RedrawUiThread mRedrawUiThread = new RedrawUiThread();

    // A timer task for do dim of screen on inactivity of the user
    private class DimTimerTask extends TimerTask {
        @Override
        public void run() {
            // Reduce screen brightness level
            getFragmentActivity().runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    doDim(true, true);
                }
            });
        }
    };

    /**
     * Method that set the wake lock over the screen
     * @param hold
     */
    private void setWakeLock(boolean hold) {
        if (DEBUG) Log.d(getLogTag(), (hold ? "hold" : " releas") + "ing wake lock");
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

    /**
     * Method that dim the screen to brightness level
     *
     * @param dim True if the operation is dim. Otherwise, false
     * @param dim True if the operation is dim. Otherwise, false
     */
    private void doDim(final boolean dim, final boolean fade) {
        try {
            if (getRootView() == null) return;
            View tintView = getRootView().findViewById(R.id.window_tint);
            if (tintView == null) return;

            Window win = getFragmentActivity().getWindow();
            if (win != null) {
                WindowManager.LayoutParams winParams = win.getAttributes();

                // dim the background somewhat (how much is determined below)
                winParams.flags |= (WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                if (this.mDimmed != dim) {
                    if (dim) {
                        winParams.flags |= WindowManager.LayoutParams.FLAG_FULLSCREEN;
                        winParams.dimAmount = DIM_BEHIND_AMOUNT_DIMMED;
                        winParams.buttonBrightness =
                                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_OFF;

                        // show the window tint
                        tintView.startAnimation(AnimationUtils.loadAnimation(getFragmentActivity(),
                            fade ? R.anim.dim
                                 : R.anim.dim_instant));
                        this.mDimmed = true;
                    } else {
                        winParams.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
                        winParams.dimAmount = DIM_BEHIND_AMOUNT_NORMAL;
                        winParams.buttonBrightness =
                                WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE;

                        // hide the window tint
                        tintView.startAnimation(AnimationUtils.loadAnimation(getFragmentActivity(),
                            fade ? R.anim.undim
                                 : R.anim.undim_instant));
                        this.mDimmed = false;
                    }
                }
                win.setAttributes(winParams);
            }
        } catch (Exception ex) {
            Log.e(getLogTag(), "Fails to dim screen", ex); //$NON-NLS-1$
        }
    }

    /**
     * Method that reschedule the dim timer
     */
    private void rescheduleDimTimer() {
        if (this.mDimTimer != null) {
            this.mDimTimer.cancel();
        }
        this.mDimTimer = new Timer();
        this.mDimTimer.schedule(new DimTimerTask(), DIM_ON_USER_INACTIVITY_TIME);
    }

    /**
     * {@inheritDoc}
     */
    public boolean isBroadcastReceiverReady() {
        return this.mBroadcastReceiverReady;
    }

    /**
     * {@inheritDoc}
     */
    public void onBroadcastReceiver(Context context, Intent intent) {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void onGainFocus() {
        // Check that got a shared preferences
        SharedPreferences sp = null;
        try {
            sp = getSharedPreferences();
            if ( sp == null) return;
        } catch (Exception ex) {
            return;
        }

        // Check if show the helper swipe text (only the first time)
        boolean showCling = getSharedPreferences().getBoolean(getClingPrefKey(), true);
        if (showCling && !mShowingCling && getClingView() != null) {
            // Only if fragment is on screen
            Rect r=new Rect();
            boolean ret = getClingView().getLocalVisibleRect(r);
            if (ret) {
                getClingView().postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        onPrepareCling();

                        getClingView().setVisibility(View.VISIBLE);
                        Animation animation =
                                AnimationUtils.loadAnimation(getFragmentActivity(), R.anim.dim);
                        getClingView().clearAnimation();
                        getClingView().startAnimation(animation);
                        mShowingCling = true;
                    }
                }, 1000L);
            }
        }

        if (getRootView() != null) {
            View tintView = getRootView().findViewById(R.id.window_tint);
            if (tintView != null) {
                tintView.setVisibility(View.INVISIBLE);
            }
            mDimmed = true;
            doDim(false, false);
            rescheduleDimTimer();
            setWakeLock(true);
            if (tintView != null) {
                tintView.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onLostFocus() {
        // Power on screen brightness level
        if (this.mDimTimer != null) {
            this.mDimTimer.cancel();
            this.mDimTimer = null;
        }
        if (getRootView() != null) {
            View tintView = getRootView().findViewById(R.id.window_tint);
            if (tintView != null) {
                tintView.setVisibility(View.INVISIBLE);
            }
            mDimmed = true;
            doDim(false, false);
            setWakeLock(true);
            if (tintView != null) {
                tintView.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    public void onNotifyUserInteraction() {
        // Accept cling
        if (mShowingCling && getClingView() != null) {
            mShowingCling = false;
            Editor editor = getSharedPreferences().edit();
            editor.putBoolean(getClingPrefKey(), false);
            editor.apply();
            getClingView().post(new Runnable() {
                @Override
                public void run() {
                    Animation animation =
                            AnimationUtils.loadAnimation(getFragmentActivity(), R.anim.undim);
                    animation.setAnimationListener(new Animation.AnimationListener() {
                        public void onAnimationStart(Animation animation) {/**NON BLOCK**/}

                        public void onAnimationRepeat(Animation animation) {/**NON BLOCK**/}

                        public void onAnimationEnd(Animation animation) {
                            getClingView().setVisibility(View.GONE);
                        }
                    });
                    getClingView().startAnimation(animation);
                }
            });
        }

        doDim(false, true);
        rescheduleDimTimer();
        setWakeLock(true);
    }

    /**
     * {@inheritDoc}
     */
    public void onNotifyNewIntent(Intent newIntent) {
        // Check if the ui thread need to be started
        try {
            updateTimers(false);
        } catch (Exception ex) {/**NON BLOCK**/}
        if (this.mTimerClockServiceBound
            && isTimerRunning()
            && this.mRedrawUiThread.isPaused()) {

            // Start a recursive drawing handler
            this.mRedrawUiThread.reset();
            this.mHandler.post(this.mRedrawUiThread);
        }
    }

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyPrepareOptionsMenu(Menu menu) {
        if (this.mActivity != null) {
            // Processed
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyOptionsItemSelected(MenuItem item) {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    public void onEnterEdit() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public void onExitEdit() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    public boolean onNotifyBackPressed() {
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAttach(Activity activity) {
        if (DEBUG) Log.v(getLogTag(), "onAttach: " + activity); //$NON-NLS-1$
        super.onAttach(activity);

        this.mActivity = activity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.v(getLogTag(), "onCreate"); //$NON-NLS-1$
        super.onCreate(savedInstanceState);
        this.mBroadcastReceiverReady = true;
        this.mHandler = new Handler();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        if (DEBUG) Log.v(getLogTag(), "onStart"); //$NON-NLS-1$
        super.onStart();

        // Start the service
        Intent intent1 = new Intent(this.mActivity, TimerClockService.class);
        this.mActivity.startService(intent1);

        // Bind to the service
        Intent intent2 = new Intent(this.mActivity, TimerClockService.class);
        this.mActivity.bindService(
                intent2, this.mTimerClockServiceConnection,
                Context.BIND_ABOVE_CLIENT | Context.BIND_AUTO_CREATE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        if (DEBUG) Log.v(getLogTag(), "onStop"); //$NON-NLS-1$
        super.onStop();

        // Unbind the service
        this.mActivity.unbindService(this.mTimerClockServiceConnection);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPause() {
        if (DEBUG) Log.v(getLogTag(), "onPause"); //$NON-NLS-1$
        super.onPause();

        // Stop a the UI thread
        this.mRedrawUiThread.cancel();

        // Save all state data to preferences file
        saveData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        if (DEBUG) Log.v(getLogTag(), "onResume"); //$NON-NLS-1$
        super.onResume();

        // Calculate refresh rate based on orientation (portrait performs better)
        int orientation = getResources().getConfiguration().orientation;
        this.mRefreshRate =
                ( orientation == Configuration.ORIENTATION_LANDSCAPE )
                ? REFRESH_RATE_LANDSCAPE
                : REFRESH_RATE_PORTRAIT;

        // Restore dimm
        try {
            mDimmed = true;
            doDim(false, false);
        } catch (Exception ex){/**NON BLOCK**/}

        // Restore the instance from preferences file
        readData();

        // Request a UI changed broadcast
        onUiChanged();
    }

    /**
     * {@inheritDoc}
     */
    public boolean onLongClick(View v) {
        Toast.makeText(this.mActivity, v.getContentDescription(), Toast.LENGTH_SHORT).show();
        return true;
    }

    /**
     * Method that returns if there are a valid timer reference
     *
     * @return boolean If there are a valid timer reference
     */
    protected boolean isTimerCreated() {
        return this.mTimerId != 0;
    }

    /**
     * Method that returns if the timer has a valid reference
     *
     * @return boolean If the timer has a valid reference
     */
    protected boolean isTimerValid() {
        try {
            if (!isTimerCreated()
                || !this.mTimerClockServiceBound
                || this.mTimerClockService == null) {
                return false;
            }
            this.mTimerClockService.isRunning(this.mTimerId);
            return true;
        } catch (RemoteException rEx) {
            return false;
        }
    }

    /**
     * Method that returns if the timer is running
     *
     * @return boolean If the timer is running
     */
    protected boolean isTimerRunning() {
        // If has a valid reference and service returns that service is running
        try {
            return isTimerCreated() &&
                    (this.mTimerClockServiceBound &&
                     this.mTimerClockService.isRunning(this.mTimerId));
        } catch (RemoteException rEx) {
            this.mTimerId = 0;
            return false;
        }
    }

    /**
     * Method that shows a warning dialog
     *
     * @param logMsg The message to log
     * @param msg The message to display
     * @param ex The exception
     */
    protected void showExceptionDialog(String logMsg, Exception ex, int msg) {
        if (logMsg != null && ex != null) {
            Log.e(getLogTag(), logMsg, ex);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this.mActivity);
        builder.setIcon(android.R.drawable.ic_dialog_alert);
        builder.setTitle(android.R.string.dialog_alert_title);
        builder.setMessage(msg);
        builder.setPositiveButton(android.R.string.ok, null);
        builder.create().show();
    }

    /**
     * Method that returns the activity of fragment
     *
     * @return Activity The activity of fragment
     */
    protected Activity getFragmentActivity() {
        return this.mActivity;
    }

    /**
     * Method that returns the timer identifier
     *
     * @return long The timer identifier
     */
    public long getTimerId() {
        return this.mTimerId;
    }

    /**
     * Method that returns the timer service
     *
     * @return ITimerClockService The timer service
     */
    public ITimerClockService getTimerClockService() {
        return this.mTimerClockService;
    }

    /**
     * Method that starts the timer
     *
     * @param type The kind of timer
     */
    protected void startTimer(TimerClockService.TIMER_TYPE type) {
        // Ensure that timer service was started
        if (!this.mTimerClockServiceBound) {
            showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
        }
        if (isTimerRunning()) {
            return;
        }

        try {
            // Is a new timer necessary?
            if (!isTimerValid()) {
                // Create a new timer
                this.mTimerId =
                        this.mTimerClockService.createTimer(
                                type.ordinal());

                // Broadcast a timer creation
                onCreateTimer();
            }

            // Start the timer
            this.mTimerClockService.startTimer(this.mTimerId);

            // Broadcast a timer start
            onStartTimer();

            // Start a recursive drawing handler
            this.mRedrawUiThread.reset();
            this.mHandler.post(this.mRedrawUiThread);

        } catch (RemoteException rEx) {
            // If timer was created, destroy
            if (this.mTimerId != 0) {
                try {
                    this.mTimerClockService.destroyTimer(this.mTimerId);

                    // Broadcast a timer destroy
                    onDestroyTimer();
                } catch (Exception e) {/**NON BLOCK**/}
            }

            // Show the exception
            showExceptionDialog(
                    "TimerClockService exception", //$NON-NLS-1$
                    rEx, R.string.msg_timer_creation_fails);

        } catch (Exception ex) {
            // If timer was created, destroy
            if (this.mTimerId != 0) {
                try {
                    this.mTimerClockService.destroyTimer(this.mTimerId);

                    // Broadcast a timer destroy
                    onDestroyTimer();
                } catch (Exception e) {/**NON BLOCK**/}
            }

            // Show the exception
            showExceptionDialog(
                    "UIThread exception", ex, R.string.msg_timer_ui_thread_fails); //$NON-NLS-1$
        }
    }

    /**
     * Method that stops the timer
     *
     * @param reset Indicates if the caller origin is a reset request
     */
    protected void stopTimer(boolean reset) {
        // Ensure that timer service was started
        if (!this.mTimerClockServiceBound) {
            showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
            return;
        }
        if (!isTimerRunning()) {
            return;
        }

        try {
            // Stop the timer and take partial
            this.mTimerClockService.stopTimer(this.mTimerId);

            // Broadcast a timer stop
            onStopTimer(reset);

            // Stop the UI thread and force a last redraw
            this.mRedrawUiThread.cancel();
            updateTimers(!reset);

        } catch (RemoteException rEx) {
            showExceptionDialog(
                    "TimerClockService exception",  //$NON-NLS-1$
                    rEx, R.string.msg_timer_creation_fails);

        } catch (Exception ex) {
            showExceptionDialog(
                    "UIThread exception",  //$NON-NLS-1$
                    ex, R.string.msg_timer_ui_thread_fails);
        }
    }

    /**
     * Method that reset the timer
     */
    protected void resetTimer() {
        // Ensure that timer service was started. Then destroy the timer
        stopTimer(true);
        try {
            updateTimers(false);
        } catch (Exception e) {/**NON BLOCK**/}
        try {
            this.mTimerClockService.destroyTimer(this.mTimerId);
        } catch (Exception e) {/**NON BLOCK**/}
        this.mTimerId = 0;

        // Remove notification?
        try {
            this.mTimerClockService.removeNotification(AbstractTimerFragment.this.mTimerId);
        } catch (Exception e) {/**NON BLOCK**/}
    }

    /**
     * Method that queries the timer service for the elapsed time
     *
     * @return long The elapsed time
     * @throws RemoteException If an error occurs accesing the service
     */
    protected long queryTimer() throws RemoteException {
        return this.mTimerClockService.queryTime(this.mTimerId);
    }

    /**
     * Method that runs in the UI thread
     *
     * @param runnable What to run in the UI thread
     */
    protected void runInUiHandler(Runnable runnable) {
        this.mHandler.post(runnable);
    }

    /**
     * Method that save the data to disk
     */
    protected void saveData() {
        // Save the timer state to preference
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putLong(PREF_TIMER_ID, this.mTimerId);
        editor.commit();
    }

    /**
     * Method that read the data from disk
     */
    protected void readData() {
        // Read the timer state to preference
        SharedPreferences sp = getSharedPreferences();
        this.mTimerId = sp.getLong(PREF_TIMER_ID, 0);
    }

    /**
     * Method invoked to query if the drawing thread must be stopped
     */
    @SuppressWarnings("static-method")
    protected boolean continueRedrawing() {
        return true;
    }

    /**
     * Method invoked when a timer is created
     */
    protected void onCreateTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is started
     */
    protected void onStartTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is stopped
     *
     * @param reset Indicates if the caller origin is a reset request
     */
    protected void onStopTimer(boolean reset) {/**NON BLOCK**/}

    /**
     * Method invoked when a timer is destroyed
     */
    protected void onDestroyTimer() {/**NON BLOCK**/}

    /**
     * Method invoked when a serviced is connected
     */
    protected void onServiceConnected() {/**NON BLOCK**/}

    /**
     * Method invoked when a serviced is disconnected
     */
    protected void onServiceDisconnected() {/**NON BLOCK**/}

    /**
     * Method that returns the {@link SharedPreferences} where to save the data
     *
     * @return SharedPreferences Where to save the data
     */
    protected abstract SharedPreferences getSharedPreferences();

    /**
     * Method that update the timers of the fragments
     *
     * @param stop Indicates if the update is when timer is stopped
     * @throws RemoteException If there some error accessing the timer service
     */
    protected abstract void updateTimers(boolean stop) throws RemoteException;

    /**
     * Method that returns the tag to show in logs
     */
    protected abstract String getLogTag();

    /**
     * Method that invoked when the UI need to be updated
     */
    protected abstract void onUiChanged();

    /**
     * Method that returns the key preferences for cling.
     *
     * @return String The key of the shared preference for cling.
     */
    protected abstract String getClingPrefKey();

    /**
     * Method that returns the view to use for cling.
     *
     * @return View The view to use for cling.
     */
    protected View getClingView() {
        return null;
    }

    /**
     * Method that invoked when the cling is need to be prepared before it be shown.
     */
    protected void onPrepareCling() {/**NON BLOCK**/}

}

