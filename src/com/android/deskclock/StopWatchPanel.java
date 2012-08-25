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
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A widget for display timing information
 */
public class StopWatchPanel extends LinearLayout {

    private TextView mNoData;
    private StopWatchLapItems mItems;

    /**
     * Constructor of <code>StopWatchPanel</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public StopWatchPanel(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StopWatchPanel);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Constructor of <code>StopWatchPanel</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public StopWatchPanel(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.StopWatchPanel, defStyle, 0);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Method that initialized the layout
     *
     * @param tarray The TypedArray
     */
    private void init(TypedArray tarray) {
        // Inflate the layout
        ViewGroup rootView =
                (ViewGroup)inflate(getContext(), R.layout.stopwatch_panel, null);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rootView.setLayoutParams(params);

        // Set the title of the panel
        TextView title = (TextView)rootView.findViewById(R.id.stopwatch_panel_title);
        title.setText( tarray.getString(R.styleable.StopWatchPanel_panel_title) );

        this.mNoData = (TextView)rootView.findViewById(R.id.stopwatch_panel_no_data);

        // Retrieve the widget that holds the items
        this.mItems = (StopWatchLapItems)rootView.findViewById(R.id.stopwatch_panel_items);

        // Add the root view
        addView(rootView);
    }

    /**
     * Method that update the information of the lap
     *
     * @param sectors The number of sectors that holds the lap structure
     * @param lap The lap structure with information
     */
    public void updateLap(int sectors, StopWatchLap lap) {
        this.mItems.updateLap(sectors, lap);
        this.mNoData.setVisibility( this.mItems.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Method that update the information of the laps
     *
     * @param sectors The number of sectors that holds the laps structure
     * @param laps The laps structure with information about all the laps
     */
    public void updateLaps(int sectors, List<StopWatchLap> laps) {
        this.mItems.updateLaps(sectors, laps);
        this.mNoData.setVisibility( this.mItems.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    /**
     * Method that removes all laps information
     */
    public void clearLaps() {
        this.mItems.clearLaps();
        this.mNoData.setVisibility(View.VISIBLE);
    }
}

