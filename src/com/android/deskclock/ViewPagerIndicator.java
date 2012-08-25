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

import java.util.Timer;
import java.util.TimerTask;

import android.content.Context;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.RelativeLayout;
import android.widget.TextView;

/**
 * A widget for display an indicator associated with a {@link ViewPager}
 */
public class ViewPagerIndicator extends RelativeLayout {

    private static final long HIDE_INDICATOR_WAIT = 650L;

    // A timer task for do hide the indicator
    private class HideTimerTask extends TimerTask {
        @Override
        public void run() {
            // Reduce screen brightness level
            ViewPagerIndicator.this.post(new Runnable() {
                public void run() {
                    hideIndicator(true);
                }
            });
        }
    }

    private int mBackgroundWidth;
    private int mBackgroundgHeight;
    private int mForegroundWidth;

    private int mNumberOfPages;
    private int mCurrentPage;

    private View mRootView;
    private View mBackgroundBar;
    private View mForegroundBar;

    private Timer mHideTimer = null;

    /**
     * Constructor of <code>ViewPagerIndicator</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public ViewPagerIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor of <code>ViewPagerIndicator</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public ViewPagerIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * Method that initializes the widget. This method creates the 7 {@link TextView}
     * that displays the timer clock
     */
    private void init() {
        // Inflate the layout into the view
        LayoutInflater li =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        // By default 1 pages
        this.mNumberOfPages = -1;
        this.mCurrentPage = -1;

        // Create the timer
        this.mHideTimer = new Timer();

        // Retrieve the root view
        this.mRootView = li.inflate(R.layout.viewpager_indicator, null);
        this.mBackgroundBar = this.mRootView.findViewById(R.id.viewpager_indicator_bg);
        this.mForegroundBar = this.mRootView.findViewById(R.id.viewpager_indicator_fg);
        this.mForegroundBar.setVisibility(View.INVISIBLE);

        // Add the root view as a child
        addView(this.mRootView);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        // Saved the background bar size
        this.mBackgroundWidth = this.mBackgroundBar.getMeasuredWidth();
        this.mBackgroundgHeight = this.mBackgroundBar.getMeasuredHeight();
        calculatePageIndicatorSize();
        onPageScrollChange(this.mCurrentPage, 0.0f);
    }


    /**
     * Method that sets the number of pages that this indicator has
     *
     * @param numberOfPages The number of pages that this indicator has
     */
    public void setNumberOfPages(int numberOfPages) {
        this.mNumberOfPages = numberOfPages;
        calculatePageIndicatorSize();
        onPageScrollChange(this.mCurrentPage, 0.0f);
    }

    /**
     * Method that sets the current page of the indicator
     *
     * @param currentPage The current page
     * @exception IllegalArgumentException If the page is out of range
     */
    public void setCurrentPage(int currentPage) {
        if (currentPage < 0 || currentPage >= this.mNumberOfPages) {
            throw new IllegalArgumentException("Invalid page: " + currentPage); //$NON-NLS-1$
        }
        this.mCurrentPage = currentPage;
        // Now show the indicator
        if (this.mCurrentPage != -1) {
            onPageScrollChange(this.mCurrentPage, 0.0f);
            this.mForegroundBar.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Method that change the scroll of the indicator
     *
     * @param page The page that is changing
     * @param scrollOffSet The scroll offset
     */
    public void onPageScrollChange(int page, float scrollOffSet) {
        // Set the new position
        int lm = ((page * this.mForegroundWidth)
                 + (this.mForegroundWidth == 0 ?
                        0 :
                        (int)(scrollOffSet * this.mForegroundWidth)));
        RelativeLayout.LayoutParams params =
                (RelativeLayout.LayoutParams)this.mForegroundBar.getLayoutParams();
        params.leftMargin = lm;
        this.mForegroundBar.setLayoutParams(params);

        // Reschedule the timer
        rescheduleDimTimer();
        showIndicator(false);
    }

    /**
     * Method that calculate and sets the page indicator size based on the
     * number of pages.
     */
    private void calculatePageIndicatorSize() {
        // Only if number of pages has set
        if (this.mNumberOfPages < 1) return;

        //Set the layout preferences
        this.mForegroundWidth = this.mBackgroundWidth / this.mNumberOfPages;
        RelativeLayout.LayoutParams params =
                        new RelativeLayout.LayoutParams(
                                this.mForegroundWidth, this.mBackgroundgHeight);
        this.mForegroundBar.setLayoutParams(params);
    }

    /**
     * Method that reschedule the timer for hide the indicator
     */
    @SuppressWarnings("synthetic-access")
    private void rescheduleDimTimer() {
        cancelHideTimer();
        this.mHideTimer = new Timer();
        this.mHideTimer.schedule(new HideTimerTask(), HIDE_INDICATOR_WAIT);
    }

    /**
     * Method that show the indicator
     *
     * @param fade While showing, makes a fade animation
     */
    public void showIndicator(final boolean fade) {
        this.mRootView.startAnimation(
                AnimationUtils.loadAnimation(getContext(),
                                fade ? R.anim.dim
                                     : R.anim.dim_instant));
    }

    /**
     * Method that hide the indicator
     *
     * @param fade While hiding, makes a fade animation
     */
    public void hideIndicator(final boolean fade) {
        this.mRootView.startAnimation(
                AnimationUtils.loadAnimation(getContext(),
                                fade ? R.anim.undim
                                     : R.anim.undim_instant));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDetachedFromWindow() {
        cancelHideTimer();
    }

    /**
     * Method that cancels the timer for hide the indicator
     */
    private void cancelHideTimer() {
        try {
            if (this.mHideTimer != null) {
                this.mHideTimer.cancel();
                this.mHideTimer = null;
            }
        } catch (Exception e) {/**NON BLOCK**/}
    }
}
