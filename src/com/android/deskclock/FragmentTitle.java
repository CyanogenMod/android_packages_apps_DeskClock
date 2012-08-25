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

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.provider.Settings;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.FloatMath;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * A {@link ViewGroup} for displaying a Holo theme title for
 * fragments that need to display a titlebar
 */
public class FragmentTitle extends RelativeLayout {

    private static final int DEFAULT_DPI_STATUS_BAR_HEIGHT = 25;

    /**
     * Constructor of <code>FragmentTitle</code>.
     *
     * @param context The current context
     */
    public FragmentTitle(Context context) {
        super(context);
        init(null);
    }

    /**
     * Constructor of <code>FragmentTitle</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     */
    public FragmentTitle(Context context, AttributeSet attrs) {
        super(context, attrs);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FragmentTitle);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Constructor of <code>FragmentTitle</code>.
     *
     * @param context The current context
     * @param attrs The attributes of the XML tag that is inflating the view.
     * @param defStyle The default style to apply to this view. If 0, no style
     *        will be applied (beyond what is included in the theme). This may
     *        either be an attribute resource, whose value will be retrieved
     *        from the current theme, or an explicit style resource.
     */
    public FragmentTitle(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.FragmentTitle, defStyle, 0);
        try {
            init(a);
        } finally {
            a.recycle();
        }
    }

    /**
     * Method that initializes the widget.
     *
     * @param tarray The TypedArray
     */
    private void init(TypedArray tarray) {
        addView(inflate(getContext(), R.layout.fragment_titlebar, null));

        final Activity activity = (Activity)getContext();

        // Set the icon and title
        ImageView icon = (ImageView)findViewById(R.id.fragment_titlebar_icon);
        TextView title = (TextView)findViewById(R.id.fragment_titlebar_title);
        if (tarray != null) {
            icon.setImageDrawable(tarray.getDrawable(R.styleable.FragmentTitle_icon));
            title.setText(tarray.getString(R.styleable.FragmentTitle_title));
        }

        // Configure option menu
        ImageButton overflow = (ImageButton)findViewById(R.id.fragment_titlebar_overflow);
        if (overflow.getContentDescription() != null) {
            overflow.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    // Show content description
                    Toast.makeText(
                            getContext(),
                            v.getContentDescription(),
                            Toast.LENGTH_SHORT).show();
                    return true;
                }
            });
        }
        overflow.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Show menu
                activity.openContextMenu(v);
            }
        });
        activity.registerForContextMenu(overflow);

        // Show overflow button?
        if (showOptionsMenu()) {
            overflow.setVisibility(View.VISIBLE);
        }

        // Is necessary to determine the system status bar position?
        final View sb = findViewById(R.id.fragment_statusbar);
        sb.setLayoutParams(
                new RelativeLayout.LayoutParams(
                        sb.getMeasuredWidth(), getStatusBarHeight()));
    }

    /**
     * Method that returns the height of the system status bar
     *
     * @return int The height of the system status bar
     */
    private int getStatusBarHeight() {
        Activity activity = ((Activity)getContext());
        DisplayMetrics displayMetrics = new DisplayMetrics();
        ((WindowManager) activity.
               getSystemService(Context.WINDOW_SERVICE)).
                   getDefaultDisplay().getMetrics(displayMetrics);
        return (int)(FloatMath.ceil(DEFAULT_DPI_STATUS_BAR_HEIGHT * displayMetrics.density));
    }

    /**
     * Method that returns if an option menu has to be displayed
     *
     * @return boolean If an option menu has to be displayed
     */
    private boolean showOptionsMenu() {
        // Show overflow button?
        final Activity activity = (Activity)getContext();
        boolean forceShowOverflow =
                Settings.System.getInt(
                        activity.getApplicationContext().getContentResolver(),
                        Settings.System.UI_FORCE_OVERFLOW_BUTTON, 0) == 1;
        return (!ViewConfiguration.get(getContext()).hasPermanentMenuKey() || forceShowOverflow);
    }
}
