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

import android.database.Cursor;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;

/**
 * A class that represent a <code>CountDown</code> timer
 */
public class CountDownTimer implements Parcelable {

    /**
     * @hide
     */
    public static final String TABLE = "coundown_timers"; //$NON-NLS-1$

    /**
     * A class that defines the columns of the {@link CountDownTimer}
     */
    public static class Columns implements BaseColumns {
        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
                Uri.parse("content://" + //$NON-NLS-1$
                            TimersProvider.CONTENT_URI + "/" + TABLE); //$NON-NLS-1$

        /**
         * The time of the timer in milliseconds from 0
         * <P>Type: NUMERIC</P>
         */
        public static final String TIMER = "timer"; //$NON-NLS-1$

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER =
                TIMER + " ASC"; //$NON-NLS-1$

        static final String[] COUNTDOWN_TIMER_QUERY_COLUMNS = { _ID, TIMER };

        /**
         * These save calls to cursor.getColumnIndexOrThrow()
         * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
         */
        /**
         * @hide
         */
        public static final int COUNTDOWN_TIMER_ID_INDEX = 0;
        /**
         * @hide
         */
        public static final int COUNTDOWN_TIMER_TIMER_INDEX = 1;
    }

    private long mId;
    private long mTimer;

    /**
     * Constructor of <code>CountDownTimer</code>
     *
     * @param cursor The cursor from which load the class
     */
    public CountDownTimer(Cursor cursor) {
        super();
        this.mId = cursor.getLong(Columns.COUNTDOWN_TIMER_ID_INDEX);
        this.mTimer = cursor.getLong(Columns.COUNTDOWN_TIMER_TIMER_INDEX);
    }

    /**
     * Constructor of <code>CountDownTimer</code>
     *
     * @param parcel The parcel from which load the class
     */
    public CountDownTimer(Parcel parcel) {
        super();
        this.mId = parcel.readLong();
        this.mTimer = parcel.readLong();
    }

    /**
     * Constructor of <code>CountDownTimer</code>
     *
     * @param id The identifier of the CountDownTimer
     * @param timer The timer of the CountDownTimer
     */
    public CountDownTimer(long id, long timer) {
        super();
        this.mId = id;
        this.mTimer = timer;
    }


    /**
     * Method that returns the identifier of timer
     *
     * @return long The identifier of timer
     */
    public long getId() {
        return this.mId;
    }

    /**
     * Method that returns the identifier of timer
     *
     * @param id the identifier of timer
     */
    public void setId(long id) {
        this.mId = id;
    }

    /**
     * Method that returns the time of timer
     *
     * @return long the time of timer
     */
    public long getTimer() {
        return this.mTimer;
    }

    /**
     * Method that sets the time of timer
     *
     * @param timer The time of timer
     */
    public void setTimer(long timer) {
        this.mTimer = timer;
    }

    /**
     * @hide
     */
    public static final Parcelable.Creator<CountDownTimer> CREATOR
                = new Parcelable.Creator<CountDownTimer>() {
                    public CountDownTimer createFromParcel(Parcel p) {
                        return new CountDownTimer(p);
                    }

                    public CountDownTimer[] newArray(int size) {
                        return new CountDownTimer[size];
                    }
                };

    /**
     * {@inheritDoc}
     */
    public int describeContents() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeLong(this.mId);
        dest.writeLong(this.mTimer);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (this.mId ^ (this.mId >>> 32));
        result = prime * result + (int) (this.mTimer ^ (this.mTimer >>> 32));
        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CountDownTimer other = (CountDownTimer) obj;
        if (this.mId != other.mId)
            return false;
        if (this.mTimer != other.mTimer)
            return false;
        return true;
    }

}

