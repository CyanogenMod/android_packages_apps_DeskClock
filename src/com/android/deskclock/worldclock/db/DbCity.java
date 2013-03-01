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

import android.content.Context;
import android.database.Cursor;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;
import android.provider.BaseColumns;

import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;

public final class DbCity implements Parcelable {

    //////////////////////////////
    // Parcelable apis
    //////////////////////////////
    public static final Parcelable.Creator<DbCity> CREATOR
            = new Parcelable.Creator<DbCity>() {
                public DbCity createFromParcel(Parcel p) {
                    return new DbCity(p);
                }

                public DbCity[] newArray(int size) {
                    return new DbCity[size];
                }
            };

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel p, int flags) {
        p.writeInt(id);
        p.writeString(name);
        p.writeString(tz);
    }
    //////////////////////////////
    // end Parcelable apis
    //////////////////////////////

    //////////////////////////////
    // Column definitions
    //////////////////////////////
    public static class Columns implements BaseColumns {
        /**
         * The content:// style URL for this table
         */
        public static final Uri CONTENT_URI =
                Uri.parse("content://com.android.deskclock.worldclock.db/city");

        /**
         * The name of the city
         * <P>Type: TEXT</P>
         */
        public static final String NAME = "name";

        /**
         * The timezone of the city
         * <P>Type: TEXT</P>
         */
        public static final String TZ = "tz";

        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = _ID + " ASC";

        static final String[] CITY_QUERY_COLUMNS = { _ID, NAME, TZ };

        /**
         * These save calls to cursor.getColumnIndexOrThrow()
         * THEY MUST BE KEPT IN SYNC WITH ABOVE QUERY COLUMNS
         */
        public static final int CITY_ID_INDEX = 0;
        public static final int CITY_NAME_INDEX = 1;
        public static final int CITY_TZ_INDEX = 2;
    }
    //////////////////////////////
    // End column definitions
    //////////////////////////////

    // Public fields
    public int        id;
    public String     name;
    public String     tz;

    @Override
    public String toString() {
        return "DbCity{" +
                ", id=" + id +
                ", name='" + name + '\'' +
                ", tz='" + tz + '\'' +
                '}';
    }

    public DbCity(Cursor c) {
        id = c.getInt(Columns.CITY_ID_INDEX);
        name = c.getString(Columns.CITY_NAME_INDEX);
        tz = c.getString(Columns.CITY_TZ_INDEX);
    }

    public DbCity(Parcel p) {
        id = p.readInt();
        name = p.readString();
        tz = p.readString();
    }

    // Creates a default city.
    public DbCity() {
        id = -1;
        name = "";
        tz = "";
    }

    @Override
    public int hashCode() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof DbCity)) return false;
        final DbCity other = (DbCity) o;
        return id == other.id;
    }
}
