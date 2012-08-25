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

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.PagerAdapter;
import android.util.SparseArray;
import android.view.ViewGroup;

/**
 * A {@link PagerAdapter} class for swiping between DeskClock, StopWatch and CountDown
 * fragments.<br/>
 * <br/>
 * This page adapter implements the next logical views:<br/>
 * <br/>
 * <pre>DeskClock :: StopWatch :: CountDown</pre>
 * <br/>
 * The principal view is DeskClock that holds the digital desktop clock.
 * As secondary applications StopWatch and CountDown allow access to the
 * clock timer functionality
 */
public class DeskClockPagerAdapter extends FragmentPagerAdapter {

    /**
     * An enumeration of all the fragments supported
     */
    public enum DeskClockFragments {
        /**
         * The deskclock fragment
         */
        DESKCLOCK(DeskClockFragment.class),
        /**
         * The stopwatch fragment
         */
        STOPWATCH(StopWatchFragment.class),
        /**
         * The countdown fragment
         */
        COUNTDOWN(CountDownFragment.class);

        private Class<? extends Fragment> mFragmentClass;

        /**
         * Constructor of <code>DeskClockFragments</code>
         *
         * @param fragmentClass The fragment class
         */
        private DeskClockFragments(Class<? extends Fragment> fragmentClass) {
            this.mFragmentClass = fragmentClass;
        }

        /**
         * Method that returns the fragment class
         *
         * @return Class<? extends Fragment> The fragment class
         */
        public Class<? extends Fragment> getFragmentClass() {
            return this.mFragmentClass;
        }
    }

    /**
     * A private class with information about fragment initialization
     */
    private class Holder {
        String mClassName;
        Bundle mParams;
    }

    private final SparseArray<WeakReference<Fragment>> mFragmentArray
                                            = new SparseArray<WeakReference<Fragment>>();
    private final List<Holder> mHolderList = new ArrayList<Holder>();
    private final FragmentActivity mFragmentActivity;
    private int mCurrentPage;

    /**
     * Constructor of <code>DeskClockPagerAdapter<code>
     *
     * @param fragmentActivity The activity of the fragment
     */
    public DeskClockPagerAdapter(FragmentActivity fragmentActivity) {
        super(fragmentActivity.getSupportFragmentManager());
        this.mFragmentActivity = fragmentActivity;
    }

    /**
     * Method that adds a new fragment class to the viewer (the fragment is
     * internally instantiate)
     *
     * @param className The full qualified name of fragment class
     * @param params The instantiate params
     */
    @SuppressWarnings("synthetic-access")
    public void add(Class<? extends Fragment> className, Bundle params) {
        Holder holder = new Holder();
        holder.mClassName = className.getName();
        holder.mParams = params;

        int position = this.mHolderList.size();
        this.mHolderList.add(position, holder);
        notifyDataSetChanged();
    }

    /**
     * Method that returns the fragment in the argument position
     *
     * @param position The position of the fragment to return
     * @return Fragment The fragment in the argument position
     */
    public Fragment getFragment(int position) {
        WeakReference<Fragment> weakFragment = this.mFragmentArray.get(position);
        if (weakFragment != null && weakFragment.get() != null) {
            return weakFragment.get();
        }
        return getItem(position);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Object instantiateItem(ViewGroup container, int position) {
        Fragment fragment = (Fragment)super.instantiateItem(container, position);
        WeakReference<Fragment> weakFragment = this.mFragmentArray.get(position);
        if (weakFragment != null) {
            weakFragment.clear();
        }
        this.mFragmentArray.put(position, new WeakReference<Fragment>(fragment));
        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Fragment getItem(int position) {
        Holder currentHolder = this.mHolderList.get(position);
        Fragment fragment =
                Fragment.instantiate(
                        this.mFragmentActivity, currentHolder.mClassName, currentHolder.mParams);
        return fragment;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyItem(ViewGroup container, int position, Object object) {
        super.destroyItem(container, position, object);
        WeakReference<Fragment> weakFragment = this.mFragmentArray.get(position);
        if (weakFragment != null) {
            weakFragment.clear();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getCount() {
        return this.mHolderList.size();
    }

    /**
     * Method that returns the current page position
     *
     * @return int The current page
     */
    public int getCurrentPage() {
        return this.mCurrentPage;
    }

    /**
     * Method that sets the current page position
     *
     * @param currentPage The current page
     */
    protected void setCurrentPage(int currentPage) {
        this.mCurrentPage = currentPage;
    }
}
