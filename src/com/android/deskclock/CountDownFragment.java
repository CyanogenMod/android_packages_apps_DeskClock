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

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.BaseColumns;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.RelativeLayout;

import com.android.deskclock.SavedCountDownTimers.OnSavedCountDownTimerEvent;
import com.android.deskclock.preferences.CountDownPreferences;

/**
 * A {@link Fragment} class for the <code>CountDown</code> of
 * {@link DeskClock} activity.<br/>
 * <br/>
 * For all operations, this class make use of the {@link TimerClockService}, that
 * holds all the timers of the {@link DeskClock}<br/>
 * <br/>
 * This countdown implementation has the next features:
 * <ul>
 * <li>Start/Stop: This start or stop the timer. This action doesn't destroy the
 * timer. Only stop the timer allowing when pushed start again. When start is
 * pushed if not there is a timer created, then a new timer creation is requested
 * to the service. No action is allowed if the countdown timer is in 0 milliseconds</li>
 * <li>Reset: This stop and destroy the current timer (if exists). Also, initializes the
 * timer and the clock to his the default (the startup time of the countdown).</li>
 * <li>Editing: The main clock time is allow edit of clock, if the timer is not running,
 * by long clicking over the clock. This shows some numerical pickers to select the
 * hours, minutes, seconds and milliseconds. On edition, user can set the clock time
 * or cancel the clock edition.</li>
 * <br/>
 * This fragment present the next elements:<br/>
 * <ul>
 * <li>The main clock. Displays the total amount of time (down running time) elapsed from the
 * starting time of the clock.</li>
 * <li>A list of saved startup countdown times to easy reset the timer to. This list allows
 * to create new timer or delete an existing one.</li>
 * <ul><br/>
 * <br/>
 * A system notification for this clock is created in background, so when the timer is complete
 * the system opens this timer view to reports the final countdown.
 */
public class CountDownFragment extends AbstractTimerFragment implements View.OnClickListener {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "CountDownFragment"; //$NON-NLS-1$

    private static final String KEY_CLING = "cling_countdown"; //$NON-NLS-1$

    private static final int SETTINGS_REQUEST_CODE = 1;

    private static final long DEFAULT_COUNTDOWN_TIME = 60000L;

    // The widgets references
    private ViewGroup mRootLayout;
    private CountDownTimerClock mMainClock;
    private ViewGroup mControlButtonGroup;
    private Button mMainButton;
    private Button mResetButton;
    private ViewGroup mEditButtonGroup;
    private Button mCancelButton;
    private Button mSetButton;
    private SavedCountDownTimers mSavedCountDownTimers;

    private View mCling;
    private View mClingImage;

    private long mClockTime;

    private boolean mIsCountDownReset;

    // The listener when SavedCountDownTimers notify his events
    private final OnSavedCountDownTimerEvent mOnSavedCountDownTimerEvent =
        new OnSavedCountDownTimerEvent() {

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void onRequestAddSavedCountDownTimer() {
            final DialogInterface.OnClickListener clickListener =
                                        new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        // User confirms the operation
                        try {
                            ContentResolver cr =
                                  CountDownFragment.this.
                                          getFragmentActivity().getContentResolver();
                            long time = CountDownFragment.this.mMainClock.getTime();
                            ContentValues values = new ContentValues();
                            values.put(CountDownTimer.Columns.TIMER, Long.valueOf(time));
                            Uri uri = cr.insert(CountDownTimer.Columns.CONTENT_URI, values);
                            if (uri == null) {
                                showExceptionDialog(null, null, R.string.msg_operation_fails);
                            }
                        } catch (Exception e) {
                            showExceptionDialog(
                                    "Fails to insert or update countdown timer",  //$NON-NLS-1$
                                    e, R.string.msg_operation_fails);
                        }
                        break;
                    default:
                        dialog.dismiss();
                        break;
                    }
                }
            };

            // Ask the user about delete the timer
            AlertDialog.Builder builder =
                    new AlertDialog.Builder(CountDownFragment.this.getFragmentActivity());
            builder.setTitle(R.string.countdown_panel_title);
            builder.setMessage(R.string.countdown_dialog_add);
            builder.setPositiveButton(android.R.string.yes, clickListener);
            builder.setNegativeButton(android.R.string.cancel, clickListener);
            builder.show();
        }

        /**
         * {@inheritDoc}
         */
        public void onRequestDeleteSavedCountDownTimer(final long id, final String timer) {
            final DialogInterface.OnClickListener clickListener =
                                    new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        // User confirms the operation
                        try {
                            ContentResolver cr =
                                    CountDownFragment.this.
                                        getFragmentActivity().getContentResolver();
                              String where = BaseColumns._ID + "=?"; //$NON-NLS-1$
                              String[] whereArgs = { String.valueOf(id) };
                              int count = cr.delete(
                                          CountDownTimer.Columns.CONTENT_URI, where, whereArgs);
                              if (count == 0) {
                                  showExceptionDialog(null, null, R.string.msg_operation_fails);
                              }
                        } catch (Exception e) {
                            showExceptionDialog(
                                    "Fails to delete countdown timer",  //$NON-NLS-1$
                                    e, R.string.msg_operation_fails);
                        }
                        break;
                    default:
                        dialog.dismiss();
                        break;
                    }
                }
            };

            // Ask the user about delete the timer
            AlertDialog.Builder builder =
                    new AlertDialog.Builder(CountDownFragment.this.getFragmentActivity());
            builder.setTitle(R.string.countdown_panel_title);
            builder.setMessage(getString(R.string.countdown_dialog_delete, timer));
            builder.setPositiveButton(android.R.string.yes, clickListener);
            builder.setNegativeButton(android.R.string.cancel, clickListener);
            builder.show();
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("synthetic-access")
        public void onSavedCountDownTimerClick(long timer) {
            CountDownFragment.this.mMainClock.setDefaultValue(timer, false);
            resetTimer();
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onNotifyPrepareOptionsMenu(Menu menu) {
        if (getFragmentActivity() != null) {
            MenuItem editor = menu.findItem(R.id.menu_item_countdown_editor);
            editor.setTitle(
                    !this.mMainClock.isEditMode()
                    ? R.string.countdown_menu_open_timer_editor
                    : R.string.countdown_menu_close_timer_editor);
            editor.setEnabled(!isTimerRunning());

            // Processed
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onNotifyOptionsItemSelected(MenuItem item) {
        // Check what item was pushed
        switch (item.getItemId()) {
        case R.id.menu_item_countdown_settings:
            Intent settings = new Intent(getFragmentActivity(), CountDownPreferences.class);
            startActivityForResult(settings, SETTINGS_REQUEST_CODE);
            break;

        case R.id.menu_item_countdown_editor:
            try {
                if (this.mMainClock.isEditMode()) {
                    this.mMainClock.cancelEditor();
                } else {
                    this.mMainClock.showEditor();
                }
            } catch (Exception e) {
                Log.e(LOG_TAG, "Fails open clock editor.", e); //$NON-NLS-1$
            }
            break;

        default:
            break;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onNotifyBackPressed() {
        if (this.mMainClock.isEditMode()) {
            try {
                this.mMainClock.cancelEditor();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Fails close clock editor.", e); //$NON-NLS-1$
            }
            return true;
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnterEdit() {
        this.mEditButtonGroup.setVisibility(View.VISIBLE);
        this.mControlButtonGroup.setVisibility(View.INVISIBLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onExitEdit() {
        this.mControlButtonGroup.setVisibility(View.VISIBLE);
        this.mEditButtonGroup.setVisibility(View.INVISIBLE);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SETTINGS_REQUEST_CODE) {
            updateSettings();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        if (DEBUG) Log.v(LOG_TAG, "onCreateView"); //$NON-NLS-1$
        this.mRootLayout = (ViewGroup)inflater.inflate(R.layout.countdown_fragment, null);

        // Obtain the clock
        this.mMainClock =
                (CountDownTimerClock)this.mRootLayout.findViewById(R.id.main_timer_clock);
        this.mMainClock.setOnEditListener(this);

        // Buttons
        this.mControlButtonGroup =
                (ViewGroup)this.mRootLayout.findViewById(R.id.vg_timer_control_layout);
        this.mMainButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_main);
        this.mMainButton.setOnClickListener(this);
        this.mMainButton.setOnLongClickListener(this);
        this.mResetButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_reset);
        this.mResetButton.setOnClickListener(this);
        this.mResetButton.setOnLongClickListener(this);
        this.mEditButtonGroup =
                (ViewGroup)this.mRootLayout.findViewById(R.id.vg_timer_edit_layout);
        this.mCancelButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_cancel);
        this.mCancelButton.setOnClickListener(this);
        this.mCancelButton.setOnLongClickListener(this);
        this.mSetButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_set);
        this.mSetButton.setOnClickListener(this);
        this.mSetButton.setOnLongClickListener(this);

        // Panel
        this.mSavedCountDownTimers =
                (SavedCountDownTimers)this.mRootLayout.findViewById(
                                            R.id.countdown_saved_timers_panel);
        this.mSavedCountDownTimers.setOnSavedCountDownTimerEvent(this.mOnSavedCountDownTimerEvent);

        // Cling
        this.mCling = this.mRootLayout.findViewById(R.id.countdown_cling);
        this.mClingImage = this.mRootLayout.findViewById(R.id.cling_image);

        // Return the layout
        onGainFocus();
        return this.mRootLayout;
    }

    /**
     * {@inheritDoc}
     */
    public View getRootView() {
        return this.mRootLayout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        super.onResume();

        //Check final countdown
        checkFinalCountDown(false);
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.bt_timer_main:
            if (!isTimerCreated() || !isTimerRunning()) {
                startTimer(TimerClockService.TIMER_TYPE.COUNTDOWN);
            } else {
                stopTimer(false);
            }
            break;
        case R.id.bt_timer_reset:
            resetTimer();
            break;

        case R.id.bt_timer_cancel:
            try {
                this.mMainClock.cancelEditor();
            } catch (Exception e) {
                Log.e(LOG_TAG, "Fails close clock editor.", e); //$NON-NLS-1$
            }
            break;
        case R.id.bt_timer_set:
            try {
                this.mMainClock.setEditor();
                this.resetTimer();
            } catch (Exception e) {/**NON BLOCK**/}
            resetTimer();
            break;

        default:
            break;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getLogTag() {
        return LOG_TAG;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected String getClingPrefKey() {
        return KEY_CLING;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected View getClingView() {
        return this.mCling;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onUiChanged() {
        // Clocks
        boolean valid = isTimerValid();
        boolean running = isTimerRunning();

        // Set the current button bar
        this.mControlButtonGroup.setVisibility(
                this.mMainClock.isEditMode() ? View.INVISIBLE : View.VISIBLE);
        this.mEditButtonGroup.setVisibility(
                this.mMainClock.isEditMode() ? View.VISIBLE : View.INVISIBLE);

        // Buttons
        this.mMainButton.setEnabled( this.mClockTime > 0 );
        if (!running) {
            if (!valid || this.mClockTime == 0 || this.mMainClock.isInDefaultValue()) {
                // Start. If not initialized or not running
                this.mMainButton.setText(getString(R.string.countdown_actions_start));
                this.mMainButton.setContentDescription(
                        getString(R.string.countdown_actions_start_cd));
            } else {
                // Resume. If not initialized or not running
                this.mMainButton.setText(getString(R.string.countdown_actions_resume));
                this.mMainButton.setContentDescription(
                        getString(R.string.countdown_actions_resume_cd));
            }
        } else {
            // Stop. If initialized and running
            this.mMainButton.setText(getString(R.string.countdown_actions_stop));
            this.mMainButton.setContentDescription(getString(R.string.countdown_actions_stop_cd));
        }

        // Reset button
        this.mResetButton.setText(getString(R.string.countdown_actions_reset));
        this.mResetButton.setContentDescription(getString(R.string.countdown_actions_reset_cd));

        // Clock Editor
        if (running) {
            try {
                this.mMainClock.cancelEditor();
            } catch (Throwable _throw) {/**NON BLOCK**/}
        }
        this.mMainClock.setEditable(!running);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPrepareCling() {
        // Calculate location
        int[] location = new int[2];
        this.mMainClock.getLocationInWindow(location);
        int bw = this.mMainClock.getMeasuredWidth();
        int bh = this.mMainClock.getMeasuredHeight();
        int iw = this.mClingImage.getWidth();
        int ih = this.mClingImage.getHeight();
        int l = (location[0] + bw/2) - (iw/2);
        int t = (location[1] + bh/2) - (ih/2);

        // Set the margins and display the image
        RelativeLayout.LayoutParams lp =
                (RelativeLayout.LayoutParams) this.mClingImage.getLayoutParams();
        lp.leftMargin = l;
        lp.topMargin = t;
        this.mClingImage.setLayoutParams(lp);
        this.mClingImage.setVisibility(View.VISIBLE);
    }

    /* (non-Javadoc)
     * @see com.android.deskclock.AbstractTimerFragment#onServiceConnected()
     */
    @Override
    protected void onServiceConnected() {
        checkFinalCountDown(true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreateTimer() {
        try {
            // Set the default timer countdown
            getTimerClockService().setCountDownTime(getTimerId(), this.mMainClock.getTime());

        } catch (RemoteException rEx) {
            // Show the exception
            showExceptionDialog(
                    "TimerClockService exception", //$NON-NLS-1$
                    rEx, R.string.msg_timer_creation_fails);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStartTimer() {
        //Notify the changes to the UI
        onUiChanged();
        getFragmentActivity().invalidateOptionsMenu();

        // Not in reset
        this.mIsCountDownReset = false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStopTimer(boolean reset) {
        //Notify the changes to the UI
        onUiChanged();
        getFragmentActivity().invalidateOptionsMenu();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void resetTimer() {
        super.resetTimer();

        //Force the reset of the main clock
        this.mMainClock.reset();
        this.mMainClock.unblink();
        this.mMainClock.setColor(getResources().getColor(R.color.main_timer_clock));
        this.mClockTime = this.mMainClock.getDefaultValue();
        this.mMainClock.updateTime(this.mClockTime);

        // Now is in reset
        this.mIsCountDownReset = true;

        //Notify the changes to the UI
        onUiChanged();

        // Persist data
        saveData();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateTimers(boolean stop) throws RemoteException {
        // Queries the current elapsed time
        long elapsed = queryTimer();

        // Update all clock
        this.mClockTime = elapsed;
        this.mMainClock.updateTime(elapsed);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SharedPreferences getSharedPreferences() {
        return getFragmentActivity().getSharedPreferences(
                CountDownPreferences.PREFERENCES_FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Method that update the settings. This method is invoked after returns from
     * settings activity
     */
    private void updateSettings() {/**NON BLOCK**/}

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveData() {
        super.saveData();

        // Save all countdown state to preference
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putLong(
                CountDownPreferences.PREF_DEFAULT_COUNTDOWN_TIME,
                this.mMainClock.getDefaultValue());
        editor.putLong(
                CountDownPreferences.PREF_COUNTDOWN_TIME,
                this.mClockTime);
        editor.putBoolean(
                CountDownPreferences.PREF_COUNTDOWN_IS_RESET,
                this.mIsCountDownReset);
        editor.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readData() {
        super.readData();

        // Read all countdown state to preference
        SharedPreferences sp = getSharedPreferences();
        long defaultTime = sp.getLong(
                                CountDownPreferences.PREF_DEFAULT_COUNTDOWN_TIME,
                                DEFAULT_COUNTDOWN_TIME);
        //--
        this.mMainClock.setDefaultValue(defaultTime, false);
        this.mClockTime = sp.getLong(
                                CountDownPreferences.PREF_COUNTDOWN_TIME, DEFAULT_COUNTDOWN_TIME);
        this.mMainClock.updateTime(this.mClockTime);
        //--
        this.mIsCountDownReset = sp.getBoolean(
                CountDownPreferences.PREF_COUNTDOWN_IS_RESET, false);

    }

    /**
     * Method that checks is a final count down is done
     *
     * @param onlyCheck Only check. Not set variables
     */
    private void checkFinalCountDown(boolean onlyCheck) {
        // Is countdown end?
        boolean endCountDown = false;
        String action =
                getFragmentActivity().
                    getIntent().getStringExtra(TimerClockService.EXTRA_TIMER_ACTION);
        boolean endCountDownAction = action != null
                                    && TimerClockService.ACTION_END_COUNTDOWN.equals(action)
                                    && !this.mIsCountDownReset;
        boolean endCountDownActivity = !this.mIsCountDownReset && this.mClockTime == 0;
        if (endCountDownAction || endCountDownActivity) {
            // Reach the countdown
            if (!onlyCheck) {
                this.mClockTime = 0;
                this.mMainClock.updateTime(0);
            }
            try {
                this.mMainClock.cancelEditor();
            } catch (Exception e) {/**NON BLOCK**/}

            if (!onlyCheck) {
                // End of countdown
                endCountDown = true;
            }

            // Show the animation until user reset the clock
            this.mMainClock.unblink();
            this.mMainClock.blink(false, Animation.INFINITE);
            endCountDown = true;
        }

        // Show the color of the clock
        this.mMainClock.setColor(
                !endCountDown
                ? getResources().getColor(R.color.main_timer_clock)
                : getResources().getColor(R.color.main_timer_clock_alerted));
        onUiChanged();
    }

    /* (non-Javadoc)
     * @see com.android.deskclock.AbstractTimerFragment#continueRedrawing()
     */
    @Override
    protected boolean continueRedrawing() {
        if (this.mClockTime == 0) {
            checkFinalCountDown(false);
            return false;
        }
        return true;
    }

}
