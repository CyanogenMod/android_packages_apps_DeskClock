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

package com.android.deskclock.preferences;

import android.app.AlertDialog.Builder;
import android.content.Context;
import android.content.res.TypedArray;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.NumberPicker;
import android.widget.NumberPicker.OnValueChangeListener;

import com.android.deskclock.R;

/**
 * Method that display a dialog for allow user choose between
 * the sectors options.
 */
public class SectorsDialogPreference extends DialogPreference {

    private CheckBox mEnabled;
    private NumberPicker mSectorsPicker;

    private int mValue;
    private int mMin;
    private int mMax;

    /**
     * Constructor of <code>SectorsDialogPreference</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public SectorsDialogPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.StopWatchPanel);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Constructor of <code>SectorsDialogPreference</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public SectorsDialogPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.SectorsDialogPreference, defStyle, 0);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Method that initialized the layout
     *
     * @param tarray The TypedArray
     */
    private void init(TypedArray tarray) {
        this.mMin = tarray.getInt(R.styleable.SectorsDialogPreference_min, 1);
        this.mMin = Math.max(1, this.mMin);
        this.mMax = tarray.getInt(R.styleable.SectorsDialogPreference_max, 3);
        this.mMax = Math.max(this.mMax, this.mMin);
    }

    /**
     * Method that returns the value
     *
     * @return value The value to set
     */
    public int getValue() {
        return this.mValue;
    }

    /**
     * Method that sets the value
     *
     * @param value The value to set
     */
    public void setValue(int value) {
        this.mValue = value;
        if (isPersistent()) {
            persistInt(value);
        }
        if (this.mSectorsPicker != null) {
            this.mEnabled.setChecked(this.mValue > 0);
            this.mSectorsPicker.setEnabled(this.mValue > 0);
            this.mSectorsPicker.setValue(Math.min(Math.max(this.mValue, this.mMin), this.mMax));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onPrepareDialogBuilder(Builder builder) {
        super.onPrepareDialogBuilder(builder);

        LayoutInflater inflater =
                (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = inflater.inflate(R.layout.sector_picker_dialog, null);
        builder.setView(view);

        this.mEnabled = (CheckBox) view.findViewById(R.id.sector_enabled);
        this.mSectorsPicker = (NumberPicker) view.findViewById(R.id.sector_picker);

        // initialize state
        this.mEnabled.setChecked(this.mValue > 0);
        this.mEnabled.setOnClickListener(new View.OnClickListener() {
            @SuppressWarnings("synthetic-access")
            public void onClick(View v) {
                boolean enabled = ((CheckBox)v).isChecked();
                setValue( enabled ? SectorsDialogPreference.this.mSectorsPicker.getValue() : 0 );
                SectorsDialogPreference.this.mSectorsPicker.setEnabled(enabled);
            }
        });
        this.mSectorsPicker.setEnabled(this.mValue > 0);
        this.mSectorsPicker.setMinValue(this.mMin);
        this.mSectorsPicker.setMaxValue(this.mMax);
        this.mSectorsPicker.setValue(Math.min(Math.max(this.mValue, this.mMin), this.mMax));
        // make the repeat rate three times as fast
        // as normal since the range is so large.
        // don't wrap from min->max
        this.mSectorsPicker.setOnLongPressUpdateInterval(100);
        this.mSectorsPicker.setWrapSelectorWheel(false);
        this.mSectorsPicker.setOnValueChangedListener(new OnValueChangeListener() {
            public void onValueChange(NumberPicker picker, int oldVal, int newVal) {
                setValue(newVal);
            }
        });
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        if (positiveResult) {
            int value = this.mValue;
            if (callChangeListener(Integer.valueOf(value))) {
                setValue(value);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        return Integer.valueOf(a.getInt(index, 0));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onSetInitialValue(boolean restoreValue, Object defaultValue) {
        setValue(restoreValue ?
                    getPersistedInt(this.mValue) :
                    ((Integer)defaultValue).intValue());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Parcelable onSaveInstanceState() {
        final Parcelable superState = super.onSaveInstanceState();
        if (isPersistent()) {
            // No need to save instance state since it's persistent
            return superState;
        }

        final SavedState myState = new SavedState(superState);
        myState.value = getValue();
        return myState;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (state == null || !state.getClass().equals(SavedState.class)) {
            // Didn't save state for us in onSaveInstanceState
            super.onRestoreInstanceState(state);
            return;
        }

        SavedState myState = (SavedState) state;
        super.onRestoreInstanceState(myState.getSuperState());
        setValue(myState.value);
    }

    /**
     * Class for serialize/deserialize state
     * @hide
     */
    private static class SavedState extends BaseSavedState {

        int value;

        /**
         * Constructor of <code>SavedState</code>
         *
         * @param source The source parcelable object
         * @hide
         */
        public SavedState(Parcel source) {
            super(source);
            this.value = source.readInt();
        }

        /**
         * Constructor of <code>SavedState</code>
         *
         * @param superState A parcelable reference
         * @hide
         */
        public SavedState(Parcelable superState) {
            super(superState);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            dest.writeInt(this.value);
        }

        /**
         * @hide
         */
        @SuppressWarnings({ "unused", "hiding" })
        public static final Parcelable.Creator<SavedState> CREATOR =
                new Parcelable.Creator<SavedState>() {
            public SavedState createFromParcel(Parcel in) {
                return new SavedState(in);
            }

            public SavedState[] newArray(int size) {
                return new SavedState[size];
            }
        };
    }

}

