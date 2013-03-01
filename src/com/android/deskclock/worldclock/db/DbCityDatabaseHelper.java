/*
 * Copyright (C) 2013 The CyanogenMod Project
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

package com.android.deskclock.worldclock.db;

import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;
import android.util.Log;

/**
 * Helper class for opening the used-defined cities database from multiple providers.
 * Also provides some common functionality.
 */
class DbCityDatabaseHelper extends SQLiteOpenHelper {

    private static final String TAG = "DbCityDatabaseHelper";

    private static final boolean DEBUG = true;

    private static final String DATABASE_NAME = "cities.db";
    private static final int DATABASE_VERSION = 1;

    public DbCityDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE cities (" +
                   "_id INTEGER PRIMARY KEY," +
                   "name TEXT, " +
                   "tz TEXT);");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
        if (DEBUG) Log.v(TAG, "Upgrading cities database from version " + oldVersion + " to "
                + currentVersion);

        if (DEBUG) Log.v(TAG, "Cities database upgrade done.");
    }

    Uri commonInsert(ContentValues values) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        long rowId = -1;
        try {
            // Check if we are trying to re-use an existing id.
            Object value = values.get(DbCity.Columns._ID);
            if (value != null) {
                int id = (Integer) value;
                if (id > -1) {
                    final Cursor cursor = db
                            .query("cities", new String[]{DbCity.Columns._ID}, "_id = ?",
                                    new String[]{id + ""}, null, null, null);
                    if (cursor.moveToFirst()) {
                        // Record exists. Remove the id so sqlite can generate a new one.
                        values.putNull(DbCity.Columns._ID);
                    }
                }
            }

            rowId = db.insert("cities", DbCity.Columns.NAME, values);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (rowId < 0) {
            throw new SQLException("Failed to insert row");
        }
        if (DEBUG) Log.v(TAG, "Added city rowId = " + rowId);

        return ContentUris.withAppendedId(DbCity.Columns.CONTENT_URI, rowId);
    }
}
