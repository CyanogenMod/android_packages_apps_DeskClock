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
import java.util.List;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

/**
 * A widget for display the saved countdown timers.
 */
public class SavedCountDownTimers extends LinearLayout
        implements LoaderCallbacks<Cursor>, View.OnClickListener {

    /**
     * An interface for listen <code>SavedCountDownTimer</code> events
     */
    public interface OnSavedCountDownTimerEvent {
        /**
         * Invoked when a request to saved the current countdown timer is done.
         */
        public void onRequestAddSavedCountDownTimer();

        /**
         * Invoked when a delete a saved countdown timer is done.
         *
         * @param id The identifier of the saved countdown timer
         * @param timer The text formatted of the timer
         */
        public void onRequestDeleteSavedCountDownTimer(long id, String timer);

        /**
         * Invoked when a delete a saved countdown timer is done.
         *
         * @param timer The time of timer in milliseconds
         */
        public void onSavedCountDownTimerClick(long timer);
    }

    private static final int COUNTDOWN_SAVED_TIMERS_LOADER_ID = 0x01;

    private ListView mSavedTimers;
    private SavedCountDownTimersAdapter mAdapter;
    private OnSavedCountDownTimerEvent mOnSavedCountDownTimerEvent;

    /**
     * Constructor of <code>SavedCountDownTimers</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public SavedCountDownTimers(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /**
     * Constructor of <code>SavedCountDownTimers</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public SavedCountDownTimers(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    /**
     * Method that initialized the layout
     */
    private void init() {
        // Inflate the layout
        ViewGroup rootView =
                (ViewGroup)inflate(getContext(), R.layout.countdown_timers, null);
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(
                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        rootView.setLayoutParams(params);

        // Gets the saved timers
        this.mSavedTimers = (ListView)rootView.findViewById(R.id.countdown_saved_timers_listview);
        List<CountDownTimer> data = new ArrayList<CountDownTimer>();
        this.mAdapter = new SavedCountDownTimersAdapter(getContext(), data);
        this.mSavedTimers.setAdapter(this.mAdapter);

        // Initialize the loader
        LoaderManager lm = ((FragmentActivity)getContext()).getSupportLoaderManager();
        lm.restartLoader(COUNTDOWN_SAVED_TIMERS_LOADER_ID, null, this);

        // Add the root view
        addView(rootView);
    }

    /**
     * {@inheritDoc}
     */
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        // Select all countdown timers
        CursorLoader loader =
                new CursorLoader(
                        getContext(),
                        CountDownTimer.Columns.CONTENT_URI,
                        CountDownTimer.Columns.COUNTDOWN_TIMER_QUERY_COLUMNS,
                        null,
                        null,
                        CountDownTimer.Columns.DEFAULT_SORT_ORDER);
        return loader;
    }

    /**
     * {@inheritDoc}
     */
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // Retrieve all the data
        List<CountDownTimer> list = new ArrayList<CountDownTimer>();
        if (data != null) {
            while (data.moveToNext()) {
                CountDownTimer timer = new CountDownTimer(data);
                list.add(timer);
            }
        }

        // Add to list view
        this.mAdapter.clear();
        this.mAdapter.addAll(list);
        this.mAdapter.notifyDataSetChanged();
        this.mSavedTimers.setSelection(0);
    }

    /**
     * {@inheritDoc}
     */
    public void onLoaderReset(Loader<Cursor> loader) {
        // Clear the listview
        this.mAdapter.clear();
        this.mAdapter.notifyDataSetChanged();
        this.mSavedTimers.setSelection(0);
    }

    /**
     * Method that sets the listener for return events of this widget
     *
     * @param onSavedCountDownTimerEvent The listener where return the events
     */
    public void setOnSavedCountDownTimerEvent(
            OnSavedCountDownTimerEvent onSavedCountDownTimerEvent) {
        this.mOnSavedCountDownTimerEvent = onSavedCountDownTimerEvent;
    }

    /**
     * {@inheritDoc}
     */
    public void onClick(View v) {
        if (this.mOnSavedCountDownTimerEvent != null) {
            if (v.getId() == R.id.countdown_saved_timer_image &&
                v.getTag() != null &&
                v.getTag() instanceof Long) {

                // Retrieve the id of the timer
                long id = ((Long)v.getTag()).longValue();
                if (id == -1) {
                    // Add
                    this.mOnSavedCountDownTimerEvent.onRequestAddSavedCountDownTimer();
                } else {
                    // Delete
                    TextView tv =
                            (TextView)((ViewGroup)v.getParent()).
                                findViewById(R.id.countdown_saved_timer_text);
                    this.mOnSavedCountDownTimerEvent.
                            onRequestDeleteSavedCountDownTimer(id, String.valueOf(tv.getText()));
                }
            } else if (v.getId() == R.id.countdown_saved_timer_text &&
                    v.getTag() != null &&
                    v.getTag() instanceof Long) {

                // Retrieve the timer
                long timer = ((Long)v.getTag()).longValue();
                this.mOnSavedCountDownTimerEvent.onSavedCountDownTimerClick(timer);
            }
        }
    }







    /**
     * A class that conforms with the ViewHolder pattern to performance
     * the list view rendering.
     */
    private static class ViewHolder {
        /**
         * @hide
         */
        public ViewHolder() {
            super();
        }
        ImageView mIcon;
        TextView mText;
    }

    /**
     * A class that holds the full data information.
     */
    private static class DataHolder {
        /**
         * @hide
         */
        public DataHolder() {
            super();
        }
        long mId;
        Drawable mIcon;
        String mContentDescription;
        String mText;
        long mTimer;
    }

    /**
     * An implementation of {@link ArrayAdapter} for display the saved countdown timers.<br/>
     * <br/>
     * The first item of the array always will be the "Add" register that is not
     * present in the database of timers
     */
    private class SavedCountDownTimersAdapter extends ArrayAdapter<CountDownTimer> {

        //The resource item layout
        private static final int RESOURCE_LAYOUT = R.layout.countdown_timers_item;

        //The resource of the item icon
        private static final int RESOURCE_ITEM_ICON = R.id.countdown_saved_timer_image;
        //The resource of the item text
        private static final int RESOURCE_ITEM_TEXT = R.id.countdown_saved_timer_text;

        private DataHolder[] mData;

        /**
         * Constructor of <code>SavedCountDownTimersAdapter</code>.
         *
         * @param context The current context
         * @param data The timers list
         */
        public SavedCountDownTimersAdapter(Context context, List<CountDownTimer> data) {
            super(context, RESOURCE_ITEM_TEXT, data);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void notifyDataSetChanged() {
            processData();
            super.notifyDataSetChanged();
        }

        /**
         * Method that process the data before use {@link #getView} method.
         */
        private void processData() {
            // Check that position 0 is a 0
            if (getCount() == 0 || getItem(0).getId() != -1) {
                CountDownTimer add = new CountDownTimer(-1, 0);
                insert(add, 0);  // Add
            }

            Resources res = getResources();
            this.mData = new DataHolder[getCount()+1];
            for (int i = 0; i < getCount(); i++) {
                //Timer info
                CountDownTimer timer = getItem(i);

                //Build the data holder
                this.mData[i] = new DataHolder();
                this.mData[i].mId = timer.getId();
                this.mData[i].mTimer = timer.getTimer();
                if (i == 0) {
                    // Add
                    this.mData[i].mIcon = res.getDrawable(android.R.drawable.ic_menu_add);
                    this.mData[i].mContentDescription =
                            res.getString(R.string.countdown_panel_add_text_cd);
                    this.mData[i].mText =
                            res.getString(R.string.countdown_panel_add_text);
                } else {
                    this.mData[i].mIcon = res.getDrawable(android.R.drawable.ic_menu_delete);
                    this.mData[i].mContentDescription =
                            res.getString(R.string.countdown_panel_remove_cd);
                    this.mData[i].mText =
                            TimerHelper.formatTime(
                                    timer.getTimer(), TimerHelper.TIMEFORMAT_FULL, false);
                }
            }
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            //Check to reuse view
            View v = convertView;
            if (v == null) {
                //Create the view holder
                LayoutInflater li =
                        (LayoutInflater)getContext().
                            getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                v = li.inflate(RESOURCE_LAYOUT, parent, false);
                ViewHolder viewHolder = new ViewHolder();
                viewHolder.mIcon = (ImageView)v.findViewById(RESOURCE_ITEM_ICON);
                viewHolder.mIcon.setOnClickListener(SavedCountDownTimers.this);
                viewHolder.mText = (TextView)v.findViewById(RESOURCE_ITEM_TEXT);
                viewHolder.mText.setOnClickListener(SavedCountDownTimers.this);
                v.setTag(viewHolder);
            }

            //Retrieve data holder
            final DataHolder dataHolder = this.mData[position];

            //Retrieve the view holder
            ViewHolder viewHolder = (ViewHolder)v.getTag();

            //Set the data
            Resources res = getContext().getResources();
            viewHolder.mIcon.setImageDrawable(dataHolder.mIcon);
            viewHolder.mIcon.setContentDescription(dataHolder.mContentDescription);
            viewHolder.mIcon.setTag(Long.valueOf(dataHolder.mId));
            viewHolder.mText.setText(dataHolder.mText);
            viewHolder.mText.setTag(Long.valueOf(dataHolder.mTimer));
            viewHolder.mText.setTextColor(
                    res.getColor(
                            dataHolder.mId == -1
                            ? android.R.color.secondary_text_dark
                            : android.R.color.primary_text_dark ));

            //Return the view
            return v;
        }
    }
}

