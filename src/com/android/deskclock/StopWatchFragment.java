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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.app.Fragment;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.android.deskclock.StopWatchInfoPanel.STOPWATCH_PANELS;
import com.android.deskclock.StopWatchLap.StopWatchSector;
import com.android.deskclock.preferences.StopWatchPreferences;

/**
 * A {@link Fragment} class for the <code>StopWatch</code> of
 * {@link DeskClock} activity.<br/>
 * <br/>
 * For all operations, this class make use of the {@link TimerClockService}, that
 * holds all the timers of the {@link DeskClock}<br/>
 * <br/>
 * This stopwatch implementation has the next features:
 * <ul>
 * <li>Start/Stop/Resume: This start, stop or resume the timer. This action doesn't destroy the
 * timer. Only stop the timer allowing the timer to be resume later. When start is
 * pushed if a timer not exits, then a new timer creation is requested
 * to the service. The resume action will continue the timer at the point it stopped.</li>
 * <li>Reset: This stop and destroy the current timer (if exists). Also, initializes
 * all the timer clocks and partial results, pushing the screen in his initial
 * state.</li>
 * <li>Lap/Step: This allows to take snapshot of the current elapsed time for laps
 * and lap sectors.<br/>
 * A lap is the main partial reference of time. Every lap create a new entry in the
 * result time list.
 * An sector is a segment of a lap. The timer can be configured for used with n sectors for
 * every lap. Using sectors require to take n sectors to complete the lap, being n the
 * time of the lap.</li>
 * <ul><br />
 * <br />
 * This fragment present the next elements:<br/>
 * <ul>
 * <li>The main clock. Displays the total amount of time (running time) elapsed since the timer
 * was created.</li>
 * <li>The partial clock. Displays the partial amount of time of an sector or a lap.</li>
 * <li>The diff clock. Displays the difference between this sector or lap and the last or
 * best (as it is configured) sector or lap. Positive and negative differences are
 * shown in different colors</li>
 * <li>The partial result list. Displays the list of all partial snapshots that was taken, sorting
 * the entries by laps. A lap also can display his partial sectors.</li>
 * <ul><br />
 */
public class StopWatchFragment extends AbstractTimerFragment implements View.OnClickListener {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "StopWatchFragment"; //$NON-NLS-1$

    // The milliseconds that the partial clock is locked when a lap is set
    private static final long TIME_LOCKED_PARTIAL_CLOCK_AFTER_LAP = 3000L;

    private static final String KEY_CLING = "cling_stopwatch"; //$NON-NLS-1$

    private static final float SMALL_CLOCK_FORM_FACTOR = 0.30f;

    // Number of flashed showed in the diff clock
    private static final int DIFF_CLOCK_BLINK_COUNT = 5;

    private static final int SETTINGS_REQUEST_CODE = 1;

    // The lock object to synchronized the statistics computation
    private final Object mStatsSync = new Object();

    private int mSectors;
    private int mCurrentSector;
    private int mCurrentLap;
    private long mLastSectorTime;
    private long mLastLapTime;
    private int mBestLapBehaviour;

    private StopWatchLap mLastLap;
    private StopWatchLap mBestLap;
    private List<StopWatchLap> mLaps;

    // The widgets references
    private ViewGroup mRootLayout;
    private StopWatchTimerClock mMainClock;
    private StopWatchTimerClock mPartialClock;
    private StopWatchTimerClock mDiffClock;
    private Button mMainButton;
    private Button mResetButton;
    private Button mPartialButton;
    private StopWatchInfoPanel mInfoPanel;

    private View mCling;
    private View mClingImage;

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onNotifyPrepareOptionsMenu(Menu menu) {
        if (getFragmentActivity() != null) {
            // Retrieve panels show/hide status
            MenuItem lastLap = menu.findItem(R.id.menu_item_stopwatch_hide_last_lap);
            MenuItem bestLap = menu.findItem(R.id.menu_item_stopwatch_hide_best_lap);
            MenuItem laps = menu.findItem(R.id.menu_item_stopwatch_hide_laps);
            SharedPreferences pref = getSharedPreferences();
            lastLap.setChecked(
                    pref.getBoolean(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL, false) );
            bestLap.setChecked(
                    pref.getBoolean(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL, false) );
            laps.setChecked(
                    pref.getBoolean(StopWatchPreferences.PREF_HIDE_LAPS_PANEL, false) );

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
        SharedPreferences pref = getSharedPreferences();

        // Check what item was pushed
        switch (item.getItemId()) {
        case R.id.menu_item_stopwatch_settings:
            Intent settings = new Intent(getFragmentActivity(), StopWatchPreferences.class);
            startActivityForResult(settings, SETTINGS_REQUEST_CODE);
            break;

        case R.id.menu_item_stopwatch_hide_last_lap:
            boolean status =
                pref.getBoolean(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL, false);
            hideShowLapPanel(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL, !status);
            break;

        case R.id.menu_item_stopwatch_hide_best_lap:
            status = pref.getBoolean(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL, false);
            hideShowLapPanel(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL, !status);
            break;

        case R.id.menu_item_stopwatch_hide_laps:
            status = pref.getBoolean(StopWatchPreferences.PREF_HIDE_LAPS_PANEL, false);
            hideShowLapPanel(StopWatchPreferences.PREF_HIDE_LAPS_PANEL, !status);
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
        this.mRootLayout = (ViewGroup)inflater.inflate(R.layout.stopwatch_fragment, null);

        // Obtain the clocks
        this.mMainClock =
                (StopWatchTimerClock)this.mRootLayout.findViewById(R.id.main_timer_clock);
        this.mPartialClock =
                (StopWatchTimerClock)this.mRootLayout.findViewById(R.id.partial_timer_clock);
        this.mDiffClock =
                (StopWatchTimerClock)this.mRootLayout.findViewById(R.id.diff_timer_clock);
        this.mMainClock.setOnResizeListener(new DigitalTimerClock.OnResizeListener() {
            @Override
            public void onResize(int newSize, int extraRightMargin) {
                // Set the new size of small clocks based on size of main clock
                StopWatchFragment.this.mPartialClock.setClockTextSize(
                                                newSize * SMALL_CLOCK_FORM_FACTOR);
                StopWatchFragment.this.mDiffClock.setClockTextSize(
                                                newSize * SMALL_CLOCK_FORM_FACTOR);

                try {
                    LinearLayout.LayoutParams params =
                            (LinearLayout.LayoutParams)
                                StopWatchFragment.this.mPartialClock.getLayoutParams();
                    params.rightMargin = extraRightMargin;
                    StopWatchFragment.this.mPartialClock.setLayoutParams(params);
                } catch (Exception ex) {/**NON BLOCK**/}
            }
        });

        // Buttons
        this.mMainButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_main);
        this.mMainButton.setOnClickListener(this);
        this.mMainButton.setOnLongClickListener(this);
        this.mResetButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_reset);
        this.mResetButton.setOnClickListener(this);
        this.mResetButton.setOnLongClickListener(this);
        this.mPartialButton =
                (Button)this.mRootLayout.findViewById(R.id.bt_timer_partial);
        this.mPartialButton.setOnClickListener(this);
        this.mPartialButton.setOnLongClickListener(this);

        // Info Panel
        this.mInfoPanel =
                (StopWatchInfoPanel)this.mRootLayout.findViewById(R.id.stopwatch_info_panel);

        // Cling
        this.mCling = this.mRootLayout.findViewById(R.id.stopwatch_cling);
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

        // Initialize the timers, result data and buttons
        boolean created = isTimerCreated();
        if (!created) {
            initializeTimers();
        }
        initializePanelInfo(!created);
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(View v) {
        switch (v.getId()) {
        case R.id.bt_timer_main:
            if (!isTimerCreated() || !isTimerRunning()) {
                startTimer(TimerClockService.TIMER_TYPE.STOPWATCH);
            } else {
                stopTimer(false);
            }
            onUiChanged();
            break;
        case R.id.bt_timer_reset:
            resetTimer();
            break;
        case R.id.bt_timer_partial:
            takePartial();
            break;

        default:
            break;
        }
    }

    /**
     * Method that initializes the structures
     */
    private void initilizeLaps() {
        // Create empty laps
        this.mLastLap = createFixedLap();
        this.mLastLap.setTitle(getString(R.string.stopwatch_info_panel_empty_lap));
        this.mBestLap = createFixedLap();
        this.mBestLap.setTitle(getString(R.string.stopwatch_info_panel_empty_lap));
        this.mLaps = new ArrayList<StopWatchLap>();
    }

    /**
     * Method that initializes the timer clocks
     *
     * @param force Force initialization
     */
    private void initializeTimers() {
        // Clocks
        this.mMainClock.resetTime();
        this.mPartialClock.resetTime();
        this.mDiffClock.resetTime();
        this.mDiffClock.setVisibility(View.INVISIBLE);
    }

    /**
     * Method that initializes the panel info
     *
     * @param force Force initialization
     */
    private void initializePanelInfo(final boolean force) {
        // Update results in background
        runInUiHandler(new Runnable() {
            @SuppressWarnings("synthetic-access")
            public void run() {
                // Info panel (create a default structure if not was restore previously)
                if (force ||
                    StopWatchFragment.this.mLastLap == null ||
                    StopWatchFragment.this.mBestLap == null ||
                    StopWatchFragment.this.mLaps == null) {

                    initilizeLaps();
                }
                StopWatchFragment.this.mInfoPanel.clear();
                StopWatchFragment.this.mInfoPanel.updateLastLap(StopWatchFragment.this.mLastLap);
                StopWatchFragment.this.mInfoPanel.updateBestLap(StopWatchFragment.this.mBestLap);
                StopWatchFragment.this.mInfoPanel.updateLaps(StopWatchFragment.this.mLaps);

                // Check that panels and current number of sectors are synchronized
                if ( StopWatchFragment.this.mSectors !=
                        StopWatchFragment.this.mLastLap.getSectorsCount() ) {
                    resetTimer();
                }
            }
        });
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

        // Main button
        if (!running) {
            if (!valid) {
                // Start. If not initialized or not running
                this.mMainButton.setText(getString(R.string.stopwatch_actions_start));
                this.mMainButton.setContentDescription(
                        getString(R.string.stopwatch_actions_start_cd));
            } else {
                // Resume. If not initialized or not running
                this.mMainButton.setText(getString(R.string.stopwatch_actions_resume));
                this.mMainButton.setContentDescription(
                        getString(R.string.stopwatch_actions_resume_cd));
            }
        } else {
            // Stop. If initialized and running
            this.mMainButton.setText(getString(R.string.stopwatch_actions_stop));
            this.mMainButton.setContentDescription(getString(R.string.stopwatch_actions_stop_cd));
        }
        // Reset button
        this.mResetButton.setText(getString(R.string.stopwatch_actions_reset));
        this.mResetButton.setContentDescription(getString(R.string.stopwatch_actions_reset_cd));
        // Partial button
        this.mPartialButton.setEnabled(running);
        // Lap/Sector
        if ((this.mSectors != 0) && this.mCurrentSector < this.mSectors) {
            this.mPartialButton.setText(getString(R.string.stopwatch_actions_sector));
            this.mPartialButton.setContentDescription(
                    getString(R.string.stopwatch_actions_sector_cd));
        } else {
            this.mPartialButton.setText(getString(R.string.stopwatch_actions_lap));
            this.mPartialButton.setContentDescription(
                    getString(R.string.stopwatch_actions_lap_cd));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPrepareCling() {
        // Calculate location
        int[] location = new int[2];
        this.mPartialButton.getLocationInWindow(location);
        int bw = this.mPartialButton.getMeasuredWidth();
        int bh = this.mPartialButton.getMeasuredHeight();
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

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onCreateTimer() {
        // The lap title must be updated
        this.mCurrentSector = 0;
        this.mCurrentLap = 1;
        this.mLastLap = createFixedLap();
        this.mLastLap.setTitle(
                getString(
                        R.string.stopwatch_info_panel_lap_label,
                        Integer.valueOf(1)));
        this.mInfoPanel.updateLastLap(this.mLastLap);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStartTimer() {
        // Unlock the clock
        this.mPartialClock.unlock();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onStopTimer(boolean reset) {
        if (!reset) {
            takePartial();
        } else {
            // Unlock the clock
            this.mPartialClock.unlock();
            // Hide diff clock
            this.mDiffClock.unblink();
            this.mDiffClock.resetTime();
            this.mDiffClock.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void resetTimer() {
        super.resetTimer();

        // Reset variables
        this.mCurrentSector = 0;
        this.mCurrentLap = 1;
        this.mLastSectorTime = 0;
        this.mLastLapTime = 0;
        saveData();

        // Reset UI
        initializeTimers();
        initializePanelInfo(true);
        onUiChanged();
    }

    /**
     * Method that take a partial (lap or sector)
     */
    private void takePartial() {
        try {
            //Obtain the partial data
            final int sector = this.mCurrentSector;
            final int lap = this.mCurrentLap;
            final long elapsed = queryTimer();
            final long elapsedSector = elapsed-this.mLastSectorTime;
            final long elapsedLap = elapsed-this.mLastLapTime;
            this.mLastSectorTime = elapsed;
            this.mCurrentSector++;

            // Is lap
            final boolean isLap =
                    (this.mSectors == 0)
                    || ((this.mSectors != 0) && (sector == this.mSectors));
            if (isLap) {
                this.mCurrentSector = 0;
                this.mCurrentLap++;
                this.mLastLapTime = elapsed;
                this.mPartialClock.updateTimeAndLock(
                        elapsedLap, TIME_LOCKED_PARTIAL_CLOCK_AFTER_LAP);
            }

            // Ensure buttons state
            onUiChanged();

            // Refresh the info panel in background
            runInUiHandler(new Runnable() {
                @SuppressWarnings("synthetic-access")
                public void run() {
                    synchronized (StopWatchFragment.this.mStatsSync) {
                        refreshInfoPanel(lap, sector, elapsedLap, elapsedSector, isLap);
                    }
                }
            });

        } catch (Exception e) {
            // Advise the user and reset timer
            showExceptionDialog(null, null, R.string.msg_timer_service_is_not_ready);
            resetTimer();
            return;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateTimers(boolean stop) throws RemoteException {
        // Queries the current elapsed time
        long elapsed = queryTimer();

        // Update all clock
        this.mMainClock.updateTime(elapsed);
        if (!stop) {
            this.mPartialClock.updateTime(elapsed-this.mLastLapTime);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected SharedPreferences getSharedPreferences() {
        return getFragmentActivity().getSharedPreferences(
                StopWatchPreferences.PREFERENCES_FILENAME, Context.MODE_PRIVATE);
    }

    /**
     * Method that hides or shows a lap panel
     *
     * @param key Preference key
     * @param hide Indicates if the panel must be hidden
     */
    private void hideShowLapPanel(String key, boolean hide) {

        // Show/Hide the panel
        StopWatchPanel panel = null;
        if (key.compareTo(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL) == 0) {
            panel = this.mInfoPanel.getPanel(STOPWATCH_PANELS.LAST_LAP);
        } else if (key.compareTo(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL) == 0) {
            panel = this.mInfoPanel.getPanel(STOPWATCH_PANELS.BEST_LAP);
        } else if (key.compareTo(StopWatchPreferences.PREF_HIDE_LAPS_PANEL) == 0) {
            panel = this.mInfoPanel.getPanel(STOPWATCH_PANELS.LAPS);
        }
        panel.setVisibility(hide ? View.GONE : View.VISIBLE);

        // Saved user preference
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putBoolean(key, hide);
        editor.apply();
    }

    /**
     * Method that return the creates a fixed lap (used a unique id not
     * based on the lap identifier)
     *
     * @return StopWatchLap The fixed lap
     */
    private StopWatchLap createFixedLap() {
        return new StopWatchLap(0, (this.mSectors != 0) ? this.mSectors : 0 );
    }

    /**
     * Method that refresh the info panel
     *
     * @param lap The current lap
     * @param sector The current sector
     * @param elapsedLap The elapsed time of the lap
     * @param elapsedSector The elapsed time of the sector
     * @param isLap Indicates if must compute as lap
     */
    public void refreshInfoPanel(
            int lap, int sector, long elapsedLap, long elapsedSector, boolean isLap) {
        // Update the sector information
        if ((this.mSectors != 0) && sector < this.mSectors) {
            // Is a new lap (new sector)?
            if (sector == 0) {
                // Add the last lap to the array of laps
                if (this.mCurrentLap > 1) {
                    this.mInfoPanel.updateLaps(this.mLaps);
                }

                // Reset the last lap and increase his title lap
                this.mLastLap = createFixedLap();
                this.mLastLap.setTitle(
                                      getString(
                                              R.string.stopwatch_info_panel_lap_label,
                                              Integer.valueOf(lap)));
            }

            // Update current sector
            StopWatchSector s = this.mLastLap.getSector(sector);
            s.setTime(Long.valueOf(elapsedSector));
        }
        // If sectors is not enabled, then need to refresh the lap
        if (this.mSectors == 0) {
            this.mLastLap.setTitle(
                    getString(
                            R.string.stopwatch_info_panel_lap_label,
                            Integer.valueOf(lap)));
        }

        // On a new lap
        if (isLap) {
            // Compute best lap
            StopWatchLap oldBestLap = (StopWatchLap)computeBestLap().clone();

            // Update last lap
            this.mLastLap.setTime(Long.valueOf(elapsedLap));

            // Add the last lap to laps
            StopWatchLap lastLap = (StopWatchLap)this.mLastLap.clone();
            lastLap.setLap(lap);
            this.mLaps.add(lastLap);

            // Compute best lap
            StopWatchLap bestLap = (StopWatchLap)computeBestLap().clone();
            if ( bestLap.getTime() != null ) {
                // If not is a fixed lap, then the diff timing must be +0
                bestLap.setLap(0); // Fixed
                bestLap.setDiff(Long.valueOf(0));
            }

            // Refresh all the diff timing
            updateLapsDiff(bestLap);

            // Update last lap diff
            Long lastLapDiff = computeDiff(this.mLastLap, oldBestLap);
            if (lastLapDiff == null) {
                lastLapDiff = Long.valueOf(0);
            }
            this.mLastLap.setDiff(lastLapDiff);
            this.mDiffClock.updateTime(lastLapDiff.longValue());
            this.mDiffClock.setVisibility(View.VISIBLE);
            this.mDiffClock.blink(true, DIFF_CLOCK_BLINK_COUNT);

            // Update best data
            this.mBestLap = (StopWatchLap)bestLap.clone();
            this.mInfoPanel.updateBestLap(bestLap);
            this.mInfoPanel.updateLaps(this.mLaps);
        }

        // Update the last lap
        this.mInfoPanel.updateLastLap(this.mLastLap);
    }

    /**
     * Method that computes the times of all the laps and returns the best lap of all
     *
     * @return StopWatchLap The best lap (or a fixed one if no ones exists)
     */
    private StopWatchLap computeBestLap() {
        StopWatchLap bestLap = createFixedLap();
        for (int i = 0; i < this.mLaps.size(); i++ ) {
            StopWatchLap lap = this.mLaps.get(i);
            // Determines if a lap is better lap using PREF_LOWER_TIMES_ARE_BETTER property
            if (bestLap.getTime() == null ||
                (bestLap.getTime() != null && lap.getTime() != null &&
                (this.mBestLapBehaviour == 0 && bestLap.getTime().compareTo(lap.getTime()) > 0 ||
                 this.mBestLapBehaviour != 0 && bestLap.getTime().compareTo(lap.getTime()) < 0) ) )
            {
                bestLap = lap;
            }
        }
        return bestLap;
    }

    /**
     * Method that update all the laps diff vs the best lap
     *
     * @param bestLap The best lap
     */
    private void updateLapsDiff(final StopWatchLap bestLap) {
        // Only if best lap if not a fixed lap
        if (bestLap.getTime() != null) {
            for (int i = 0; i < this.mLaps.size(); i++) {
                StopWatchLap lap = this.mLaps.get(i);
                lap.setDiff( computeDiff(lap, bestLap) );
            }
        }
    }

    /**
     * Method that computes the differences between two laps
     *
     * @param lap1 One lap
     * @param lap2 Other lap
     * @return Long The difference between the laps (or null if any of the laps has null time)
     */
    private static Long computeDiff(final StopWatchLap lap1, final StopWatchLap lap2) {
        if (lap1.getTime() != null && lap2.getTime() != null) {
            long p1 = lap1.getTime().longValue();
            long p2 = lap2.getTime().longValue();
            long diff = p1 - p2;  // The lap always be equals or greater to best lap
            return Long.valueOf(diff);
        }
        return null;
    }

    /**
     * Method that update the settings. This method is invoked after returns from
     * settings activity
     */
    private void updateSettings() {
        SharedPreferences sp = getSharedPreferences();
        boolean changed = false;

        // Sectors was changed?
        int curSector = sp.getInt(StopWatchPreferences.PREF_SECTORS, 0);
        if (this.mSectors != curSector) {
            this.mSectors = curSector;
            changed = true;
        }

        // Best Lap Behaviour was changed?
        int bestLapBehaviour =
                Integer.valueOf(
                        sp.getString(
                                StopWatchPreferences.PREF_BEST_LAP_BEHAVIOUR,
                                "0")).intValue(); //$NON-NLS-1$
        if (this.mBestLapBehaviour != bestLapBehaviour) {
            this.mBestLapBehaviour = bestLapBehaviour;
            changed = true;
        }


        // Some change?? Then reset the structures and destroy the timer
        if (changed) {
            resetTimer();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void saveData() {
        super.saveData();

        // Save all stopwatch state to preference
        SharedPreferences.Editor editor = getSharedPreferences().edit();
        editor.putLong(StopWatchPreferences.PREF_LAST_SECTOR, this.mLastSectorTime);
        editor.putLong(StopWatchPreferences.PREF_LAST_LAP, this.mLastLapTime);
        editor.putInt(StopWatchPreferences.PREF_CURRENT_SECTOR, this.mCurrentSector);
        editor.putInt(StopWatchPreferences.PREF_CURRENT_LAP, this.mCurrentLap);
        editor.putLong(StopWatchPreferences.PREF_CLOCK_MAIN, this.mMainClock.getTime());
        editor.putLong(StopWatchPreferences.PREF_CLOCK_PARTIAL, this.mPartialClock.getTime());
        editor.putLong(StopWatchPreferences.PREF_CLOCK_DIFF, this.mDiffClock.getTime());
        editor.putString(StopWatchPreferences.PREF_BEST_LAP_BEHAVIOUR,
                                    String.valueOf(this.mBestLapBehaviour));
        editor.putString(StopWatchPreferences.PREF_CURRENT_DATA, serializeData());
        editor.commit();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void readData() {
        super.readData();

        // Read all stopwatch state to preference
        SharedPreferences sp = getSharedPreferences();
        this.mLastLapTime = sp.getLong(StopWatchPreferences.PREF_LAST_LAP, 0);
        this.mCurrentSector = sp.getInt(StopWatchPreferences.PREF_CURRENT_SECTOR, 0);
        this.mCurrentLap = sp.getInt(StopWatchPreferences.PREF_CURRENT_LAP, 1);
        this.mMainClock.updateTime( sp.getLong(StopWatchPreferences.PREF_CLOCK_MAIN, 0) );
        this.mPartialClock.updateTime( sp.getLong(StopWatchPreferences.PREF_CLOCK_PARTIAL, 0) );
        this.mDiffClock.updateTime( sp.getLong(StopWatchPreferences.PREF_CLOCK_DIFF, 0) );
        this.mSectors = sp.getInt(StopWatchPreferences.PREF_SECTORS, 0);
        this.mBestLapBehaviour =
                Integer.valueOf(
                        sp.getString(
                                StopWatchPreferences.PREF_BEST_LAP_BEHAVIOUR,
                                "0")).intValue(); //$NON-NLS-1$
        String data = sp.getString(StopWatchPreferences.PREF_CURRENT_DATA, null);
        if (data != null) {
            deserializeData(data);
        }

        // Panels
        hideShowLapPanel(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL,
                sp.getBoolean(StopWatchPreferences.PREF_HIDE_LAST_LAP_PANEL, false));
        hideShowLapPanel(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL,
                sp.getBoolean(StopWatchPreferences.PREF_HIDE_BEST_LAP_PANEL, false));
        hideShowLapPanel(StopWatchPreferences.PREF_HIDE_LAPS_PANEL,
                sp.getBoolean(StopWatchPreferences.PREF_HIDE_LAPS_PANEL, false));
    }

    /**
     * Method that serializes the panel information
     */
    private String serializeData() {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(baos);
            oos.writeObject(this.mLastLap);
            oos.writeObject(this.mBestLap);
            oos.writeObject(this.mLaps);
            oos.close();
            return new String(Base64.encode(baos.toByteArray(), Base64.DEFAULT));
        } catch (Exception e) {
            Log.e(LOG_TAG, "Fails to serialize panel data", e); //$NON-NLS-1$
        }
        return ""; //$NON-NLS-1$
    }

    /**
     * Method that deserializes the object of the panel information
     */
    @SuppressWarnings("unchecked")
    private boolean deserializeData(String serializedData) {
        try {
            byte[] data = Base64.decode(serializedData, Base64.DEFAULT);
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);
            StopWatchLap lastLap = (StopWatchLap)ois.readObject();
            StopWatchLap bestLap = (StopWatchLap)ois.readObject();
            List<StopWatchLap> laps = (List<StopWatchLap>)ois.readObject();
            ois.close();

            this.mLastLap = lastLap;
            this.mBestLap = bestLap;
            this.mLaps = laps;
            return true;

        } catch (Exception e) {
            Log.e(LOG_TAG, "Fails to deserialize panel data", e); //$NON-NLS-1$
        }
        return false;
    }

}

