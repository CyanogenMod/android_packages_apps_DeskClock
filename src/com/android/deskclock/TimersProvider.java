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

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.text.TextUtils;
import android.util.Log;

/**
 * A content provider for timers
 */
public class TimersProvider extends ContentProvider {

    private static final boolean DEBUG = false;

    private static final String LOG_TAG = "TimersProvider";  //$NON-NLS-1$

    private TimerDatabaseHelper mOpenHelper;

    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);

    /**
     * @hide
     */
    public static final String CONTENT_URI = "com.android.deskclock.timers"; //$NON-NLS-1$

    private static final int COUNTDOWN_TIMER = 1;
    private static final int COUNTDOWN_TIMER_ID = 2;
    static {
        sURLMatcher.addURI(
                CONTENT_URI,
                CountDownTimer.TABLE,
                COUNTDOWN_TIMER);
        sURLMatcher.addURI(
                CONTENT_URI,
                CountDownTimer.TABLE + "/#", //$NON-NLS-1$
                COUNTDOWN_TIMER_ID);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onCreate() {
        this.mOpenHelper = new TimerDatabaseHelper(getContext());
        return true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Cursor query(
            Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        // Generate the body of the query
        int match = sURLMatcher.match(uri);
        switch (match) {
            case COUNTDOWN_TIMER:
                qb.setTables(CountDownTimer.TABLE);
                break;
            case COUNTDOWN_TIMER_ID:
                qb.setTables(CountDownTimer.TABLE);
                qb.appendWhere(BaseColumns._ID);
                qb.appendWhere(uri.getPathSegments().get(1));
                break;
            default:
                throw new IllegalArgumentException("Unknown URL " + uri); //$NON-NLS-1$
        }

        // Extract the data from the database
        SQLiteDatabase db = this.mOpenHelper.getReadableDatabase();
        Cursor ret = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        if (ret == null) {
            Log.w(LOG_TAG, "TimersProvider.query: failed"); //$NON-NLS-1$
        } else {
            ret.setNotificationUri(getContext().getContentResolver(), uri);
        }
        return ret;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getType(Uri uri) {
        int match = sURLMatcher.match(uri);
        switch (match) {
            case COUNTDOWN_TIMER:
                return "vnd.android.cursor.dir/" + CountDownTimer.TABLE; //$NON-NLS-1$
            case COUNTDOWN_TIMER_ID:
                return "vnd.android.cursor.item/" + CountDownTimer.TABLE; //$NON-NLS-1$
            default:
                throw new IllegalArgumentException("Unknown URL"); //$NON-NLS-1$
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Uri insert(Uri uri, ContentValues values) {
        if (sURLMatcher.match(uri) != COUNTDOWN_TIMER) {
            throw new IllegalArgumentException("Cannot insert into URL: " + uri); //$NON-NLS-1$
        }
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        long rowId = db.insert(CountDownTimer.TABLE, null, values);
        if (rowId < 0) {
            throw new UnsupportedOperationException("Failed to insert row"); //$NON-NLS-1$
        }
        Log.w(LOG_TAG, "Added alarm rowId = " + rowId); //$NON-NLS-1$

        Uri newUrl = ContentUris.withAppendedId(CountDownTimer.Columns.CONTENT_URI, rowId);
        getContext().getContentResolver().notifyChange(newUrl, null);
        return newUrl;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int delete(Uri uri, final String selection, final String[] selectionArgs) {
        String where = selection;
        String[] whereArgs = selectionArgs;
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        int count;
        long rowId = 0;
        switch (sURLMatcher.match(uri)) {
            case COUNTDOWN_TIMER:
                count = db.delete(CountDownTimer.TABLE, where, whereArgs);
                break;
            case COUNTDOWN_TIMER_ID:
                String segment = uri.getPathSegments().get(1);
                rowId = Long.parseLong(segment);
                if (TextUtils.isEmpty(where)) {
                    where = BaseColumns._ID + "=" + rowId; //$NON-NLS-1$
                } else {
                    where =
                            BaseColumns._ID + "=" + rowId + " AND (" //$NON-NLS-1$ //$NON-NLS-2$
                            + where + ")";  //$NON-NLS-1$
                }
                count = db.delete(CountDownTimer.TABLE, where, whereArgs);
                break;
            default:
                throw new IllegalArgumentException("Cannot delete from URL: " + uri); //$NON-NLS-1$
        }

        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        int count;
        long rowId = 0;
        int match = sURLMatcher.match(uri);
        SQLiteDatabase db = this.mOpenHelper.getWritableDatabase();
        switch (match) {
            case COUNTDOWN_TIMER: {
                String segment = uri.getPathSegments().get(1);
                rowId = Long.parseLong(segment);
                count = db.update(
                        CountDownTimer.TABLE,
                        values, BaseColumns._ID + "=" + rowId, null); //$NON-NLS-1$
                break;
            }
            default: {
                throw new UnsupportedOperationException("Cannot update URL: " + uri); //$NON-NLS-1$
            }
        }
        if (DEBUG) Log.v(LOG_TAG,
                "*** notifyChange() rowId: " + rowId + " url " + uri); //$NON-NLS-1$ //$NON-NLS-2$
        getContext().getContentResolver().notifyChange(uri, null);
        return count;
    }

}

