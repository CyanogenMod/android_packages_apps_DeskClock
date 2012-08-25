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

import java.util.List;

import android.content.Context;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;

/**
 * A widget for display information of laps and sectors.
 */
public class StopWatchInfoPanel extends LinearLayout {

    /**
     * An enumeration of the panels used by the info panel
     */
    public enum STOPWATCH_PANELS {
        /**
         * The last lap panel
         */
        LAST_LAP,
        /**
         * The best lap panel
         */
        BEST_LAP,
        /**
         * The laps panel
         */
        LAPS
    }

    private StopWatchPanel mLastLap;
    private StopWatchPanel mBestLap;
    private StopWatchPanel mLaps;

    /**
     * Constructor of <code>StopWatchInfoPanel</code>.
     *
     * @param context The current context
     */
    public StopWatchInfoPanel(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor of <code>StopWatchInfoPanel</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public StopWatchInfoPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor of <code>StopWatchInfoPanel</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public StopWatchInfoPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * Method that initialized the layout
     */
    private void init() {
        // Inflate the layout
        ViewGroup rootView =
                (ViewGroup)inflate(getContext(), R.layout.stopwatch_info_panel, null);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rootView.setLayoutParams(params);

        // Retrieve the panels
        this.mLastLap = (StopWatchPanel)rootView.findViewById(R.id.info_panel_last_lap);
        this.mBestLap = (StopWatchPanel)rootView.findViewById(R.id.info_panel_best_lap);
        this.mLaps= (StopWatchPanel)rootView.findViewById(R.id.info_panel_laps);

        // Add the root view
        addView(rootView);
    }

    /**
     * Method that returns the panel requested
     *
     * @param what What panel is returned
     * @return StopWatchPanel The panel
     */
    public StopWatchPanel getPanel(STOPWATCH_PANELS what) {
        switch (what) {
        case LAST_LAP:
            return this.mLastLap;
        case BEST_LAP:
            return this.mBestLap;
        case LAPS:
            return this.mLaps;
        default:
            break;
        }
        return null; //??
    }

    /**
     * Method that removes all laps information
     */
    public void clear() {
        this.mLastLap.clearLaps();
        this.mBestLap.clearLaps();
        this.mLaps.clearLaps();
    }

    /**
     * Method that update the best lap information
     *
     * @param lap The lap information
     */
    public void updateLastLap(StopWatchLap lap) {
        this.mLastLap.updateLap(lap.getSectorsCount(), lap);
    }

    /**
     * Method that update the best lap information
     *
     * @param lap The lap information
     */
    public void updateBestLap(StopWatchLap lap) {
        this.mBestLap.updateLap(lap.getSectorsCount(), lap);
    }

    /**
     * Method that update the information of the laps
     *
     * @param laps The information of the laps
     */
    public void updateLaps(List<StopWatchLap> laps) {
        if (laps.size() == 0) {
            this.mLaps.updateLaps(0, laps);
        }
        else {
            this.mLaps.updateLaps(laps.get(0).getSectorsCount(), laps);
        }
    }
}

