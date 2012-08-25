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

import java.io.Serializable;

/**
 * This class represent an stopwatch lap result.
 */
@SuppressWarnings("hiding")
public class StopWatchLap implements Serializable, Cloneable, Comparable<StopWatchLap> {

    private static final long serialVersionUID = 1L;

    /**
     * This class represent an stopwatch sector result.
     */
    public class StopWatchSector implements Serializable, Cloneable, Comparable<StopWatchSector> {

        private static final long serialVersionUID = 1L;

        private final int mSector;
        private Long mTime;

        /**
         * Constructor of <code>StopWatchSector</code>
         *
         * @param sector The sector number
         */
        public StopWatchSector(int sector) {
            this(sector, null);
        }

        /**
         * Constructor of <code>StopWatchSector</code>
         *
         * @param sector sector lap number
         * @param time The elapsed time of the sector
         */
        public StopWatchSector(int sector, Long time) {
            super();
            this.mSector = sector;
            this.mTime = time;
        }

        /**
         * Method that returns the sector number
         *
         * @return int The sector number
         */
        public int getSector() {
            return this.mSector;
        }

        /**
         * Method that returns the elapsed time of the sector
         *
         * @return Long The elapsed time of the sector
         */
        public Long getTime() {
            return this.mTime;
        }

        /**
         * Method that sets the elapsed time of the sector
         *
         * @param time The elapsed time of the sector
         */
        public void setTime(Long time) {
            this.mTime = time;
        }

        /**
         * {@inheritDoc}
         */
        public int compareTo(StopWatchSector another) {
            return Integer.valueOf(this.mSector).compareTo(Integer.valueOf(another.mSector));
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected Object clone() {
            StopWatchSector o = new StopWatchSector(this.mSector);
            o.mTime = this.mTime;
            return o;
        }
    }



    private int mLap;
    private String mTitle;
    private Long mTime;
    private Long mDiff;
    private final StopWatchSector[] mSectors;

    /**
     * Constructor of <code>StopWatchLap</code>
     *
     * @param lap The lap number
     * @param sectors The number of sectors
     */
    public StopWatchLap(int lap, int sectors) {
        this(lap, sectors, null, null);
    }

    /**
     * Constructor of <code>StopWatchLap</code>
     *
     * @param lap The lap number
     * @param sectors The number of sectors
     * @param time The elapsed time of the lap
     * @param diff The amount of time respect the best lap
     */
    public StopWatchLap(int lap, int sectors, Long time, Long diff) {
        super();
        this.mLap = lap;
        this.mTime = time;
        this.mDiff = diff;
        this.mTitle = null;
        this.mSectors = new StopWatchSector[sectors];
        initializeSectors();
    }

    /**
     * Method that initializes the sector information
     */
    private void initializeSectors() {
        for (int i = 0; i < this.mSectors.length; i++) {
            this.mSectors[i] = new StopWatchSector(i+1);
        }
    }

    /**
     * Method that returns the lap number
     *
     * @return int The lap number
     */
    public int getLap() {
        return this.mLap;
    }

    /**
     * Method that sets the lap number
     *
     * @param lap The lap number
     */
    public void setLap(int lap) {
        this.mLap = lap;
    }

    /**
     * Method that returns the title of the lap
     *
     * @return String The title of the lap
     */
    public String getTitle() {
        return this.mTitle;
    }

    /**
     * Method that sets title of the lap
     *
     * @param title The title of the lap
     */
    public void setTitle(String title) {
        this.mTitle = title;
    }

    /**
     * Method that returns the elapsed time of the lap
     *
     * @return Long The elapsed time of the lap
     */
    public Long getTime() {
        return this.mTime;
    }

    /**
     * Method that sets the elapsed time of the lap
     *
     * @param time The elapsed time of the lap
     */
    public void setTime(Long time) {
        this.mTime = time;
    }

    /**
     * Method that returns the amount of time respect the best lap
     *
     * @return Long The amount of time respect the best lap
     */
    public Long getDiff() {
        return this.mDiff;
    }

    /**
     * Method that sets the amount of time respect the best lap
     *
     * @param diff The amount of time respect the best lap
     */
    public void setDiff(Long diff) {
        this.mDiff = diff;
    }

    /**
     * Method that returns the number of sectors of the lap
     *
     * @return int The number of sectors of the lap
     */
    public int getSectorsCount() {
        return this.mSectors.length;
    }

    /**
     * Method that returns the sector information
     *
     * @param sector The sector to retrieve
     * @return StopWatchSector The sector information
     */
    public StopWatchSector getSector(int sector) {
        return this.mSectors[sector];
    }

    /**
     * Method that sets the sector information
     *
     * @param sector The sector to set
     * @param time The elapsed time of the sector
     */
    public void setSector(int sector, Long time) {
        this.mSectors[sector].setTime(time);
    }

    /**
     * {@inheritDoc}
     */
    public int compareTo(StopWatchLap another) {
        return Integer.valueOf(this.mLap).compareTo(Integer.valueOf(another.mLap));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object clone() {
        StopWatchLap o = new StopWatchLap(this.mLap, this.mSectors.length);
        o.mTime = this.mTime;
        o.mTitle = this.mTitle;
        o.mDiff = this.mDiff;
        for (int i = 0; i < this.mSectors.length; i++ ) {
            o.mSectors[i] = (StopWatchSector)this.mSectors[i].clone();
        }
        return o;
    }
}

