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

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;

import com.android.deskclock.DeskClockPagerAdapter.DeskClockFragments;
import com.android.deskclock.TimerClockService.TIMER_TYPE;

/**
 * DeskClock clock view for desk docks (contains the DeskClock, StopWatch, and CountDown
 * fragments).
 */
public class DeskClock extends FragmentActivity {

    /**
     * @hide
     */
    public interface Fragmentable {
        public View getRootView();
    }

    /**
     * @hide
     */
    public interface OnBroadcastReceiver {
        public boolean isBroadcastReceiverReady();
        public void onBroadcastReceiver(Context context, Intent intent);
    }

    /**
     * @hide
     */
    public interface FocusableFragment {
        public void onLostFocus();
        public void onGainFocus();
    }

    /**
     * @hide
     */
    public interface ReportableFragment {
        public void onNotifyNewIntent(Intent intent);
        public void onNotifyUserInteraction();
        public boolean onNotifyPrepareOptionsMenu(Menu menu);
        public boolean onNotifyOptionsItemSelected(MenuItem item);
        public boolean onNotifyBackPressed();
    }

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "DeskClock"; //$NON-NLS-1$

    private ViewPager mViewPager;
    private ViewPagerIndicator mViewPagerIndicator;

    private boolean mLaunchedFromDock = false;
    private boolean mLockTransparency = false;

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (DEBUG) Log.d(LOG_TAG, "mIntentReceiver.onReceive: action="  //$NON-NLS-1$
                                                + action + ", intent=" + intent); //$NON-NLS-1$

            // Broadcast the intent to all fragments
            DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
            int count = adapter.getCount();
            for (int i = 0; i < count; i++) {
                OnBroadcastReceiver receiver = (OnBroadcastReceiver)adapter.getFragment(i);
                if (receiver.isBroadcastReceiverReady()) {
                    receiver.onBroadcastReceiver(context, intent);
                }
            }

            // Exiting intents are treated by the main activity
            if (UiModeManager.ACTION_EXIT_DESK_MODE.equals(action)) {
                if (mLaunchedFromDock) {
                    // moveTaskToBack(false);
                    finish();
                }
                mLaunchedFromDock = false;
            } else if (Intent.ACTION_DOCK_EVENT.equals(action)) {
                if (DEBUG) Log.d(LOG_TAG, "dock event extra " //$NON-NLS-1$
                        + intent.getExtras().getInt(Intent.EXTRA_DOCK_STATE));
                if (mLaunchedFromDock && intent.getExtras().getInt(Intent.EXTRA_DOCK_STATE,
                        Intent.EXTRA_DOCK_STATE_UNDOCKED) == Intent.EXTRA_DOCK_STATE_UNDOCKED) {
                    finish();
                    mLaunchedFromDock = false;
                }
            }
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart() {
        if (DEBUG) Log.d(LOG_TAG, "onStart"); //$NON-NLS-1$
        super.onStart();

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_DOCK_EVENT);
        filter.addAction(UiModeManager.ACTION_EXIT_DESK_MODE);
        filter.addAction(DeskClockFragment.ACTION_MIDNIGHT);
        registerReceiver(mIntentReceiver, filter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStop() {
        if (DEBUG) Log.d(LOG_TAG, "onStop"); //$NON-NLS-1$
        super.onStop();

        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected void onPause() {
        if (DEBUG) Log.d(LOG_TAG, "onPause"); //$NON-NLS-1$
        super.onPause();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onResume() {
        if (DEBUG) Log.d(LOG_TAG, "onResume"); //$NON-NLS-1$
        super.onResume();

        final boolean launchedFromDock = getIntent().hasCategory(Intent.CATEGORY_DESK_DOCK);
        mLaunchedFromDock = launchedFromDock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(LOG_TAG,
                "onCreate with savedInstanceState: " + savedInstanceState); //$NON-NLS-1$
        super.onCreate(savedInstanceState);

        // Set the layout with the ViewPager
        setContentView(R.layout.desk_clock_pager);

        // Create the pages of the ViewPager
        DeskClockPagerAdapter adapter = new DeskClockPagerAdapter(this);
        DeskClockFragments[] fragments = DeskClockPagerAdapter.DeskClockFragments.values();
        for (int i=0; i<fragments.length; i++) {
            adapter.add(fragments[i].getFragmentClass(), new Bundle());
        }

        // Retrieve the ViewPager
        this.mViewPager = (ViewPager) findViewById(R.id.desk_clock_pager);
        this.mViewPager.setOffscreenPageLimit(adapter.getCount()-1);
        this.mViewPager.setAdapter(adapter);

        // View pager Indicator
        this.mViewPagerIndicator = (ViewPagerIndicator) findViewById(R.id.viewpager_indicator);
        this.mViewPagerIndicator.setNumberOfPages(adapter.getCount());

        // Check if is a timer request
        setDefaultPage();

        // Set the listener
        this.mViewPager.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                // Let to know the fragments where are or not the current page
                DeskClockPagerAdapter adapter =
                        (DeskClockPagerAdapter)DeskClock.this.mViewPager.getAdapter();
                int curPage = adapter.getCurrentPage();
                FocusableFragment oldPage = (FocusableFragment)adapter.getFragment(curPage);
                FocusableFragment newPage = (FocusableFragment)adapter.getFragment(position);
                oldPage.onLostFocus();
                newPage.onGainFocus();
                adapter.setCurrentPage(position);

                // Recreate the menu for the new page
                DeskClock.this.invalidateOptionsMenu();
            }

            @Override
            public void onPageScrolled(
                    int position, float positionOffset,
                    int positionOffsetPixels) {

                // Fill/Empty transparency swiping transition
                if (position == DeskClockFragments.DESKCLOCK.ordinal() &&
                    !DeskClock.this.mLockTransparency) {
                    int transparency = (int)((Math.abs(0 - positionOffset)) * 255);
                    setFragmentTransparency(transparency);
                } else {
                    setFragmentTransparency(255);
                }

                // Move the view pager indicator
                DeskClock.this.mViewPagerIndicator.onPageScrollChange(position, positionOffset);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                DeskClock.this.mLockTransparency = false;
            }
        });
    }

    @Override
    protected void onDestroy() {
        if (DEBUG) Log.d(LOG_TAG, "onDestroy"); //$NON-NLS-1$
        super.onDestroy();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        if (DEBUG) Log.d(LOG_TAG, "onSaveInstanceState"); //$NON-NLS-1$
        super.onSaveInstanceState(outState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        if (DEBUG) Log.d(LOG_TAG, "onRestoreInstanceState"); //$NON-NLS-1$
        super.onRestoreInstanceState(savedInstanceState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUserInteraction() {
        DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
        ReportableFragment fragment =
                (ReportableFragment)(adapter.getFragment(mViewPager.getCurrentItem()));
        fragment.onNotifyUserInteraction();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onNewIntent(Intent newIntent) {
        super.onNewIntent(newIntent);
        if (DEBUG) Log.d(LOG_TAG, "onNewIntent with intent: " + newIntent); //$NON-NLS-1$

        // update our intent so that we can consult it to determine whether or
        // not the most recent launch was via a dock event
        setIntent(newIntent);

        // Check if is a timer request
        setDefaultPage();

        // Notify the new intent to all fragments
        DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
        int count = adapter.getCount();
        for (int i = 0; i < count; i++) {
            ReportableFragment fragment = (ReportableFragment)adapter.getFragment(i);
            fragment.onNotifyNewIntent(newIntent);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.desk_clock_menu, menu);
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.desk_clock_menu, menu);
        onPrepareOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return onOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int pos = this.mViewPager.getCurrentItem();

        // Only show the menu group of the current
        menu.setGroupVisible(
                R.id.menu_group_deskclock, pos == DeskClockFragments.DESKCLOCK.ordinal());
        menu.setGroupVisible(
                R.id.menu_group_stopwatch, pos == DeskClockFragments.STOPWATCH.ordinal());
        menu.setGroupVisible(
                R.id.menu_group_countdown, pos == DeskClockFragments.COUNTDOWN.ordinal());

        DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
        ReportableFragment fragment =
                (ReportableFragment)(adapter.getFragment(mViewPager.getCurrentItem()));
        fragment.onNotifyPrepareOptionsMenu(menu);
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
        ReportableFragment fragment =
                (ReportableFragment)(adapter.getFragment(mViewPager.getCurrentItem()));
        return fragment.onNotifyOptionsItemSelected(item);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
            ReportableFragment fragment =
                    (ReportableFragment)(adapter.getFragment(mViewPager.getCurrentItem()));
            if (!fragment.onNotifyBackPressed()) {
                finish();
            }
        }
        return false;
    }

    /**
     * Method that checks the type of timer that produce the application calling
     */
    private void setDefaultPage() {
        // Log the transparency transition
        this.mLockTransparency = true;
        setFragmentTransparency(255);

        // Check what timer is calling the application
        TIMER_TYPE timerType =
                (TIMER_TYPE)getIntent().
                    getSerializableExtra(TimerClockService.EXTRA_TIMER_TYPE);
        if (timerType != null
                && TimerClockService.TIMER_TYPE.COUNTDOWN.compareTo(timerType) == 0) {
            // PowerOn the screen
            setWakeLock(true);

            // Change to CountDown timer
            DeskClock.this.mViewPager.
                setCurrentItem(DeskClockFragments.COUNTDOWN.ordinal(), true);
        } else {
            this.mViewPager.setCurrentItem(DeskClockFragments.DESKCLOCK.ordinal());
        }

        // Notify the current page to the indicator
        this.mViewPagerIndicator.setCurrentPage(this.mViewPager.getCurrentItem());
    }

    /**
     * Method that sets the transparency of the {@link Fragmentable} items
     *
     * @param transparency The transparency (0-255)
     */
    private void setFragmentTransparency(int transparency) {
        DeskClockPagerAdapter adapter = (DeskClockPagerAdapter)mViewPager.getAdapter();
        for (int i=0; i<adapter.getCount(); i++) {
            Fragment f = adapter.getFragment(i);
            if (f instanceof Fragmentable) {
                View v = ((Fragmentable)adapter.getFragment(i)).getRootView();
                if (v != null && v.getBackground() != null) {
                    v.getBackground().setAlpha(transparency);
                    v.setAlpha(transparency);
                }
            }
        }
    }

    /**
     * Method that powers on the screen
     */
    private void setWakeLock(boolean hold) {
        if (DEBUG) Log.d(LOG_TAG,
                (hold ? "hold" : " releas") + //$NON-NLS-1$ //$NON-NLS-2$
                                "ing wake lock"); //$NON-NLS-1$
        Window win = getWindow();
        if (win != null) {
            win.clearFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            win.addFlags(
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                    WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON |
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                );
        }
    }
}
