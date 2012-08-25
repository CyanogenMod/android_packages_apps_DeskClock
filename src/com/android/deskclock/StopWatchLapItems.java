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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.deskclock.StopWatchLap.StopWatchSector;

/**
 * A class that display the information of one or more laps
 */
public class StopWatchLapItems extends LinearLayout {

    private static final int ROW_IDENTIFIER_BASE = 99999;

    private static final String NULL_TIME = "-:--:--.---"; //$NON-NLS-1$

    private int mSectors;

    /**
     * Constructor of <code>StopWatchLapItems</code>.
     *
     * @param context The current context
     */
    public StopWatchLapItems(Context context) {
        super(context);
        init();
    }

    /**
     * Constructor of <code>StopWatchLapItems</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public StopWatchLapItems(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Method that initializes the layout
     */
    private void init() {
        // Set the orientation
        setOrientation(LinearLayout.VERTICAL);

        // Declare the default sectors
        this.mSectors = 0;
    }

    /**
     * Method that removes all laps information
     */
    public void clearLaps() {
        removeAllViews();
    }

    /**
     * Method that update the information of the lap
     *
     * @param sectors The number of sectors that holds the lap structure
     * @param lap The lap structure with information
     */
    public void updateLap(int sectors, StopWatchLap lap) {
        List<StopWatchLap> laps = new ArrayList<StopWatchLap>(1);
        laps.add(lap);
        updateLaps(sectors, laps, false);
    }

    /**
     * Method that update the information of the laps
     *
     * @param sectors The number of sectors that holds the laps structure
     * @param laps The laps structure with information about all the laps
     */
    public void updateLaps(int sectors, List<StopWatchLap> laps) {
        updateLaps(sectors, laps, true);
    }

    /**
     * Method that update the information of the laps
     *
     * @param sectors The number of sectors that holds the laps structure
     * @param laps The laps structure with information about all the laps
     * @param addDivider Add a divider between every row
     */
    private void updateLaps(int sectors, List<StopWatchLap> laps, boolean addDivider) {

        Context ctx = getContext();
        Resources res = getContext().getResources();

        int lapColor = res.getColor(android.R.color.primary_text_dark);
        int sectorColor = res.getColor(android.R.color.secondary_text_dark);
        int dividerColor = res.getColor(android.R.color.secondary_text_light);

        // Is necessary to update the view
        if (this.mSectors != sectors) {
            // Remove all view, and recreate the view data
            removeAllViews();
        }

        // Add or update all laps
        Collections.sort(laps);
        for (int i = 0; i < laps.size(); i++) {
            StopWatchLap lap = laps.get(i);

            // Ensure the views structure of the lap
            ensureLapInfo(res, lap, addDivider, dividerColor);

            //Update lap data
            String lapTitle =
                    ctx.getString(
                                R.string.stopwatch_info_panel_lap_label,
                                String.valueOf(lap.getLap()));
            if (lap.getTitle() != null) {
                lapTitle = lap.getTitle();
            }
            updateItemRow(
                    getLapRow(lap),
                    lapTitle,
                    formatTime(lap.getDiff(), true),
                    formatTime(lap.getTime(), false),
                    lapColor
            );

            //Update sectors data
            for (int j = 0; j < lap.getSectorsCount(); j++) {
                StopWatchSector sector = lap.getSector(j);
                updateItemRow(
                        getLapSectorRow(lap, sector.getSector()),
                        "",  //$NON-NLS-1$
                        ctx.getString(
                                R.string.stopwatch_info_panel_lap_sector,
                                String.valueOf(sector.getSector())),
                        formatTime(sector.getTime(), false),
                        sectorColor
                        );
            }
        }
    }

    /**
     * Method that ensures that the row view of a lap and his children
     * has been create before return the StopWatchLap reference. Otherwise
     * the row lap is created
     *
     * @param res The layout resources
     * @param StopWatchLap The lap reference
     * @param lap The lap
     * @param addDivider Add a divider between every row
     */
    private ViewGroup ensureLapInfo(
            Resources res, StopWatchLap lap, boolean addDivider, int dividerColor) {

        ViewGroup row = (ViewGroup)findViewById(ROW_IDENTIFIER_BASE + lap.getLap());
        if (row == null) {
            row = new LinearLayout(getContext());
            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                            android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            row.setLayoutParams(params);
            ((LinearLayout)row).setOrientation(LinearLayout.VERTICAL);
            row.setId(ROW_IDENTIFIER_BASE + lap.getLap());

            // Load the lap row
            ViewGroup lapRow =
                    (ViewGroup)inflate(getContext(), R.layout.stopwatch_panel_item, null);
            params =
                new LinearLayout.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lapRow.setLayoutParams(params);
            row.addView(lapRow);

            // Load the lap sector rows
            ViewGroup[] sectorRows = new ViewGroup[lap.getSectorsCount()];
            for (int i = 0; i < sectorRows.length; i++) {
                sectorRows[i] =
                        (ViewGroup)inflate(getContext(), R.layout.stopwatch_panel_item, null);
                params =
                        new LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
                sectorRows[i].setLayoutParams(params);
                row.addView(sectorRows[i]);
            }

            // Add the row view
            addView(row);

            // Add a separator?
            if (addDivider) {
                TextView divider = new TextView(getContext());
                int margin = res.getDimensionPixelSize(R.dimen.stopwatch_info_panel_divider_margin);
                int dividerSize =
                        res.getDimensionPixelSize(R.dimen.stopwatch_info_panel_divider_size);
                params =
                        new LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                dividerSize, 1.0f);
                params.setMargins(margin, 0, margin, 0);
                divider.setLayoutParams(params);
                divider.setBackgroundColor(dividerColor);
                addView(divider);
            }
        }

        // Remove sectors obsoleted
        for (int i = row.getChildCount()-1; i > lap.getSectorsCount(); i++) {
            removeViewAt(i);
        }

        // Returns the row
        return row;
    }

    /**
     * Method that returns the lap item row
     *
     * @param lap The lap item row
     * @return ViewGroup The view
     */
    private ViewGroup getItem(StopWatchLap lap) {
        return (ViewGroup)findViewById(ROW_IDENTIFIER_BASE + lap.getLap());
    }

    /**
     * Method that returns the lap row
     *
     * @param lap The lap item row
     * @param sector The lap to retrieve
     * @return ViewGroup The view lap row
     */
    private ViewGroup getLapRow(StopWatchLap lap) {
        return (ViewGroup)getItem(lap).getChildAt(0);
    }

    /**
     * Method that returns the lap sector row
     *
     * @param row The lap item row
     * @param sector The sector to retrieve
     * @return ViewGroup The view lap sector row
     */
    private ViewGroup getLapSectorRow(StopWatchLap lap, int sector) {
        return (ViewGroup)getItem(lap).getChildAt(sector);
    }


    /**
     * Method that update a item row
     *
     * @param row The row to be update
     * @param field1 The text of the field 1
     * @param field2 The text of the field 2
     * @param field3 The text of the field 3
     * @param color The color of the row
     */
    private static void updateItemRow(
            ViewGroup row, String field1, String field2, String field3, int color) {
        TextView tvField1 = (TextView)row.findViewById(R.id.info_panel_item_field1);
        TextView tvField2 = (TextView)row.findViewById(R.id.info_panel_item_field2);
        TextView tvField3 = (TextView)row.findViewById(R.id.info_panel_item_field3);
        tvField1.setText(field1);
        tvField2.setText(field2);
        tvField3.setText(field3);
        tvField1.setTextColor(color);
        tvField2.setTextColor(color);
        tvField3.setTextColor(color);
    }

    /**
     * Method that format an elapsed time
     *
     * @param elapsed The elapsed time to format
     * @param showSign If show positive/negative sign
     * @return String The formatted elapsed time
     */
    private static String formatTime(Long elapsed, boolean showSign) {

        // Check if have a valid elapsed time
        if (elapsed == null) {
            return NULL_TIME;
        }

        // Convert to the appropriate amount of time
        return TimerHelper.formatTime(elapsed.longValue(), TimerHelper.TIMEFORMAT_FULL, showSign);
    }
}

