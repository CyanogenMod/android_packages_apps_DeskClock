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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.CursorLoader;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * The cities provider supplies info about a user-defined world clock city.
 */
public class DbCities {

    private static final String TAG = "Cities";

    final static int INVALID_CITY_ID = -1;

    /**
     * Creates a new city and fills in the given city's id.
     */
    public static long addCity(Context context, DbCity city) {
        ContentValues values = createContentValues(city);
        Uri uri = context.getContentResolver().insert(
                DbCity.Columns.CONTENT_URI, values);
        city.id = (int) ContentUris.parseId(uri);
        return city.id;
    }

    /**
     * Removes an existing City.
     */
    public static int deleteCity(Context context, int cityId) {
        if (cityId == INVALID_CITY_ID) return 0;

        ContentResolver contentResolver = context.getContentResolver();
        Uri uri = ContentUris.withAppendedId(DbCity.Columns.CONTENT_URI, cityId);
        return contentResolver.delete(uri, "", null);
    }

    public static CursorLoader getCitiesCursorLoader(Context context) {
        return new CursorLoader(context, DbCity.Columns.CONTENT_URI,
                DbCity.Columns.CITY_QUERY_COLUMNS, null, null, DbCity.Columns.DEFAULT_SORT_ORDER);
    }

    /**
     * Queries all cities
     * @return cursor over all cities
     */
    public static Cursor getCitiesCursor(ContentResolver contentResolver) {
        return contentResolver.query(
                DbCity.Columns.CONTENT_URI, DbCity.Columns.CITY_QUERY_COLUMNS,
                null, null, DbCity.Columns.DEFAULT_SORT_ORDER);
    }

    /**
     * Return a list will all the user-defined cities objects in the database.
     */
    public static List<DbCity> getCities(ContentResolver contentResolver) {
        Cursor cursor = contentResolver.query(
                DbCity.Columns.CONTENT_URI, DbCity.Columns.CITY_QUERY_COLUMNS,
                null, null, DbCity.Columns.DEFAULT_SORT_ORDER);
        List<DbCity> cities = new ArrayList<DbCity>();
        if (cursor != null) {
            while(cursor.moveToNext()) {
                cities.add(new DbCity(cursor));
            }
            cursor.close();
        }
        return cities;
    }

    /**
     * Return an city object representing the city id in the database.
     * Returns null if no city exists.
     */
    public static DbCity getCity(ContentResolver contentResolver, int cityId) {
        Cursor cursor = contentResolver.query(
                ContentUris.withAppendedId(DbCity.Columns.CONTENT_URI, cityId),
                DbCity.Columns.CITY_QUERY_COLUMNS,
                null, null, null);
        DbCity city = null;
        if (cursor != null) {
            if (cursor.moveToFirst()) {
                city = new DbCity(cursor);
            }
            cursor.close();
        }
        return city;
    }

    /**
     * A convenience method to set an city in the cities
     * content provider.
     * @return The id of city or < 1 if update failed.
     */
    public static long setCity(Context context, DbCity city) {
        ContentValues values = createContentValues(city);
        ContentResolver resolver = context.getContentResolver();
        long rowsUpdated = resolver.update(
                ContentUris.withAppendedId(DbCity.Columns.CONTENT_URI, city.id),
                values, null, null);
        if (rowsUpdated < 1) {
            Log.e(TAG, "Error updating city " + city);
            return rowsUpdated;
        }
        return city.id;
    }

    private static ContentValues createContentValues(DbCity city) {
        ContentValues values = new ContentValues(3);

        // -1 means generate new id.
        if (city.id != -1) {
            values.put(DbCity.Columns._ID, city.id);
        }
        values.put(DbCity.Columns.NAME, city.name);
        values.put(DbCity.Columns.TZ, city.tz);
        return values;
    }

}
