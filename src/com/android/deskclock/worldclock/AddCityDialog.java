/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.deskclock.worldclock;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;

import com.android.deskclock.R;

public class AddCityDialog implements OnClickListener, OnItemSelectedListener, TextWatcher {

    private static final int HOURS_1 = 60 * 60000;

    public interface OnCitySelected {
        public void onCitySelected(String city, String tz);
    }

    final AsyncTask<Void, Void, Boolean> tzLoadTask = new AsyncTask<Void, Void, Boolean>() {

        private String[] mZones;

        @Override
        protected Boolean doInBackground(Void... params) {
            final Collator collator = Collator.getInstance();
            List<CityTimeZone> zones = loadTimezones();
            Collections.sort(zones, new Comparator<CityTimeZone>() {
                public int compare(CityTimeZone lhs, CityTimeZone rhs) {
                    int lhour = lhs.mHours * lhs.mSign;
                    int rhour = rhs.mHours * rhs.mSign;
                    if (lhour != rhour) {
                        return lhour < rhour ? -1 : 1;
                    }
                    return collator.compare(lhs.mId, rhs.mId);
                }
            });

            List<String> tzLabels = new ArrayList<String>();
            for (CityTimeZone zone : zones)  {
                tzLabels.add(zone.toString());
            }

            mZones = tzLabels.toArray(new String[tzLabels.size()]);
            mDefaultTimeZoneId =
                    Arrays.binarySearch(mZones, String.valueOf(mDefaultTimeZoneLabel));
            return Boolean.TRUE;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            setTimeZoneData(mZones, mDefaultTimeZoneId, true);
        }
    };

    static class CityTimeZone {
        int mSign;
        int mHours;
        int mMinutes;
        String mId;

        @Override
        public String toString() {
            return String.format("GMT%s%02d:%02d - %s",
                                  (mSign == -1 ? "-" : "+"),
                                  mHours,
                                  mMinutes,
                                  mId);
        }
    }

    private final Context mContext;
    private final OnCitySelected mListener;
    private final AlertDialog mDialog;
    private final EditText mCityName;
    private final Spinner mTimeZones;
    private Button mButton;

    private int mDefaultTimeZoneId;
    private CityTimeZone mDefaultTimeZoneLabel;

    public AddCityDialog(
            Context ctx, LayoutInflater inflater, OnCitySelected listener) {
        mContext = ctx;
        mListener = listener;
        mDefaultTimeZoneId = 0;
        mDefaultTimeZoneLabel = null;

        // Initialize dialog
        View dlgView = inflater.inflate(R.layout.city_add, null);
        mCityName = (EditText)dlgView.findViewById(R.id.add_city_name);
        mCityName.addTextChangedListener(this);
        mTimeZones = (Spinner)dlgView.findViewById(R.id.add_city_tz);
        setTimeZoneData(new String[]{ctx.getString(R.string.cities_add_loading)}, 0, false);
        mTimeZones.setEnabled(false);
        mTimeZones.setOnItemSelectedListener(this);

        // Create the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(ctx);
        builder.setTitle(R.string.cities_add_city_title);
        builder.setView(dlgView);
        builder.setPositiveButton(ctx.getString(android.R.string.ok), this);
        builder.setNegativeButton(ctx.getString(android.R.string.cancel), null);
        mDialog = builder.create();
    }

    void showDialog() {
        mDialog.show();
        tzLoadTask.execute();
        mButton = mDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        mButton.setEnabled(false);
    }

    void setTimeZoneData(String[] data, int selected, boolean enabled) {
        ArrayAdapter<String> adapter =
                new ArrayAdapter<String>(
                        mContext,
                        android.R.layout.simple_spinner_item,
                        data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mTimeZones.setAdapter(adapter);
        mTimeZones.setSelection(selected);
        mTimeZones.setEnabled(enabled);
        if (mButton != null) {
            checkSelectionStatus();
        }
    }

    List<CityTimeZone> loadTimezones() {
        Map<String, CityTimeZone> timeZones = new HashMap<String, CityTimeZone>();
        Resources res = mContext.getResources();
        final long date = Calendar.getInstance().getTimeInMillis();
        mDefaultTimeZoneLabel = buildCityTimeZone(TimeZone.getDefault().getID(), date);
        String[] ids = res.getStringArray(R.array.cities_tz);
        for (String id : ids) {
            if (!timeZones.containsKey(id)) {
                timeZones.put( id, buildCityTimeZone(id, date) );
            }
        }
        return new ArrayList<CityTimeZone>(timeZones.values());
    }

    private CityTimeZone buildCityTimeZone(String id, long date) {
        final TimeZone tz = TimeZone.getTimeZone(id);
        final int offset = tz.getOffset(date);
        final int p = Math.abs(offset);

        CityTimeZone timeZone = new CityTimeZone();
        timeZone.mId = id;
        timeZone.mSign = offset < 0 ? -1 : 1;
        timeZone.mHours = p / (HOURS_1);
        timeZone.mMinutes = (p / 60000) % 60;
        return timeZone;
    }

    private void checkSelectionStatus() {
        String name = mCityName.getText().toString().toLowerCase();
        String tz = mTimeZones.getSelectedItem().toString();
        boolean enabled =
                !TextUtils.isEmpty(name) && !TextUtils.isEmpty(tz) && mTimeZones.isEnabled();
        mButton.setEnabled(enabled);
    }

    public void onClick(DialogInterface dialog, int which) {
        String name = mCityName.getText().toString();
        String tz = mTimeZones.getSelectedItem().toString();
        if (mListener != null) {
            mListener.onCitySelected(name, tz.substring(tz.indexOf(" - ") + 3));
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        checkSelectionStatus();
    }

    public void onNothingSelected(AdapterView<?> parent) {
        checkSelectionStatus();
    }

    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    public void afterTextChanged(Editable s) {
        checkSelectionStatus();
    }

}