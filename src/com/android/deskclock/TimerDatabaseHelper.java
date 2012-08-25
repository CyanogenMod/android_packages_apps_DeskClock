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

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Helper class for opening the database of timers.
 */
public class TimerDatabaseHelper extends SQLiteOpenHelper {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "TimerDatabaseHelper";  //$NON-NLS-1$

    private static final String DATABASE_NAME = "timers.db"; //$NON-NLS-1$
    private static final int DATABASE_VERSION = 1;

    /**
     * Constructor of <code>TimerDatabaseHelper</code>
     *
     * @param context The current context
     */
    public TimerDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE coundown_timers (" +  //$NON-NLS-1$
                "_id INTEGER PRIMARY KEY," +  //$NON-NLS-1$
                "timer NUMERIC);");  //$NON-NLS-1$
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (DEBUG) Log.v(
                LOG_TAG,
                String.format("Upgrading timers database from version %d to %d, " +  //$NON-NLS-1$
                              "which will destroy all old data",  //$NON-NLS-1$
                              Integer.valueOf(oldVersion),
                              Integer.valueOf(newVersion)
                              ));
        db.execSQL("DROP TABLE IF EXISTS coundown_timers");  //$NON-NLS-1$
        onCreate(db);
    }

}

