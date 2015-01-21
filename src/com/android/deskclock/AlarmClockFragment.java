/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.Profile;
import android.app.ProfileManager;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Loader;
import android.content.UriPermission;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.DataSetObserver;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Vibrator;
import android.transition.AutoTransition;
import android.transition.Fade;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import com.android.deskclock.alarms.AlarmStateManager;
import com.android.deskclock.provider.Alarm;
import com.android.deskclock.provider.AlarmInstance;
import com.android.deskclock.provider.DaysOfWeek;
import com.android.deskclock.widget.ActionableToastBar;
import com.android.deskclock.widget.TextTime;

import java.io.File;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AlarmClock application.
 */
public class AlarmClockFragment extends DeskClockFragment implements
        LoaderManager.LoaderCallbacks<Cursor>, OnTimeSetListener, View.OnTouchListener {
    private static final float EXPAND_DECELERATION = 1f;
    private static final float COLLAPSE_DECELERATION = 0.7f;

    private static final int ANIMATION_DURATION = 300;
    private static final int EXPAND_DURATION = 300;
    private static final int COLLAPSE_DURATION = 250;

    private static final int ROTATE_180_DEGREE = 180;
    private static final float ALARM_ELEVATION = 8f;
    private static final float TINTED_LEVEL = 0.09f;

    private static final String KEY_EXPANDED_ID = "expandedId";
    private static final String KEY_REPEAT_CHECKED_IDS = "repeatCheckedIds";
    private static final String KEY_RINGTONE_TITLE_CACHE = "ringtoneTitleCache";
    private static final String KEY_SELECTED_ALARMS = "selectedAlarms";
    private static final String KEY_DELETED_ALARM = "deletedAlarm";
    private static final String KEY_UNDO_SHOWING = "undoShowing";
    private static final String KEY_PREVIOUS_DAY_MAP = "previousDayMap";
    private static final String KEY_SELECTED_ALARM = "selectedAlarm";
    private static final DeskClockExtensions sDeskClockExtensions = ExtensionsFactory
            .getDeskClockExtensions();
    private static final String KEY_DELETE_CONFIRMATION = "deleteConfirmation";
    private static final String KEY_SELECT_SOURCE = "selectedSource";

    private static final int REQUEST_CODE_RINGTONE = 1;
    private static final int REQUEST_CODE_EXTERN_AUDIO = 2;
    private static final int REQUEST_CODE_PROFILE = 3;
    private static final long INVALID_ID = -1;

    // This extra is used when receiving an intent to create an alarm, but no alarm details
    // have been passed in, so the alarm page should start the process of creating a new alarm.
    public static final String ALARM_CREATE_NEW_INTENT_EXTRA = "deskclock.create.new";

    // This extra is used when receiving an intent to scroll to specific alarm. If alarm
    // can not be found, and toast message will pop up that the alarm has be deleted.
    public static final String SCROLL_TO_ALARM_INTENT_EXTRA = "deskclock.scroll.to.alarm";

    private FrameLayout mMainLayout;

    private ProfileManager mProfileManager;
    private ProfilesObserver mProfileObserver;
    private AudioManager mAudioManager;

    private final Uri PROFILES_SETTINGS_URI =
            Settings.System.getUriFor(Settings.System.SYSTEM_PROFILES_ENABLED);

    private static final int MSG_PROFILE_STATUS_CHANGE = 1000;

    private ListView mAlarmsList;
    private AlarmItemAdapter mAdapter;
    private View mEmptyView;
    private View mFooterView;

    private String mDisplayName;

    private Bundle mRingtoneTitleCache; // Key: ringtone uri, value: ringtone title
    private ActionableToastBar mUndoBar;
    private View mUndoFrame;

    private Alarm mSelectedAlarm;
    private static final String SEL_AUDIO_SRC = "audio/*";
    private static final int SEL_SRC_RINGTONE = 0;
    private static final int SEL_SRC_EXTERNAL = 1;
    private int mSelectSource = SEL_SRC_RINGTONE;
    private long mScrollToAlarmId = INVALID_ID;

    private Loader mCursorLoader = null;

    // Saved states for undo
    private Alarm mDeletedAlarm;
    private Alarm mAddedAlarm;
    private boolean mUndoShowing;

    private Interpolator mExpandInterpolator;
    private Interpolator mCollapseInterpolator;

    private Transition mAddRemoveTransition;
    private Transition mRepeatTransition;
    private Transition mEmptyViewTransition;

    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);

            switch (msg.what) {
                case MSG_PROFILE_STATUS_CHANGE:
                    updateProfilesStatus();
                    break;
            }
        }
    };

    public AlarmClockFragment() {
        // Basic provider required by Fragment.java
    }

    @Override
    public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mCursorLoader = getLoaderManager().initLoader(0, null, this);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedState) {
        // Inflate the layout for this fragment
        final View v = inflater.inflate(R.layout.alarm_clock, container, false);

        long expandedId = INVALID_ID;
        long[] repeatCheckedIds = null;
        long[] selectedAlarms = null;
        Bundle previousDayMap = null;
        if (savedState != null) {
            expandedId = savedState.getLong(KEY_EXPANDED_ID);
            repeatCheckedIds = savedState.getLongArray(KEY_REPEAT_CHECKED_IDS);
            mRingtoneTitleCache = savedState.getBundle(KEY_RINGTONE_TITLE_CACHE);
            mDeletedAlarm = savedState.getParcelable(KEY_DELETED_ALARM);
            mUndoShowing = savedState.getBoolean(KEY_UNDO_SHOWING);
            selectedAlarms = savedState.getLongArray(KEY_SELECTED_ALARMS);
            previousDayMap = savedState.getBundle(KEY_PREVIOUS_DAY_MAP);
            mSelectedAlarm = savedState.getParcelable(KEY_SELECTED_ALARM);
            mSelectSource = savedState.getInt(KEY_SELECT_SOURCE);
        }

        // Register profiles status
        mProfileManager = (ProfileManager) getActivity().getSystemService(Context.PROFILE_SERVICE);
        mProfileObserver = new ProfilesObserver(mHandler);

        mExpandInterpolator = new DecelerateInterpolator(EXPAND_DECELERATION);
        mCollapseInterpolator = new DecelerateInterpolator(COLLAPSE_DECELERATION);

        mAddRemoveTransition = new AutoTransition();
        mAddRemoveTransition.setDuration(ANIMATION_DURATION);

        mRepeatTransition = new AutoTransition();
        mRepeatTransition.setDuration(ANIMATION_DURATION / 2);
        mRepeatTransition.setInterpolator(new AccelerateDecelerateInterpolator());

        mEmptyViewTransition = new TransitionSet()
                .setOrdering(TransitionSet.ORDERING_SEQUENTIAL)
                .addTransition(new Fade(Fade.OUT))
                .addTransition(new Fade(Fade.IN))
                .setDuration(ANIMATION_DURATION);

        boolean isLandscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        View menuButton = v.findViewById(R.id.menu_button);
        if (menuButton != null) {
            if (isLandscape) {
                menuButton.setVisibility(View.GONE);
            } else {
                menuButton.setVisibility(View.VISIBLE);
                setupFakeOverflowMenuButton(menuButton);
            }
        }

        mEmptyView = v.findViewById(R.id.alarms_empty_view);

        mMainLayout = (FrameLayout) v.findViewById(R.id.main);
        mAlarmsList = (ListView) v.findViewById(R.id.alarms_list);

        mUndoBar = (ActionableToastBar) v.findViewById(R.id.undo_bar);
        mUndoFrame = v.findViewById(R.id.undo_frame);
        mUndoFrame.setOnTouchListener(this);

        mFooterView = v.findViewById(R.id.alarms_footer_view);
        mFooterView.setOnTouchListener(this);

        mAdapter = new AlarmItemAdapter(getActivity(),
                expandedId, repeatCheckedIds, selectedAlarms, previousDayMap, mAlarmsList);
        mAdapter.registerDataSetObserver(new DataSetObserver() {

            private int prevAdapterCount = -1;

            @Override
            public void onChanged() {

                final int count = mAdapter.getCount();
                if (mDeletedAlarm != null && prevAdapterCount > count) {
                    showUndoBar();
                }

                if ((count == 0 && prevAdapterCount > 0) ||  /* should fade in */
                        (count > 0 && prevAdapterCount == 0) /* should fade out */) {
                    TransitionManager.beginDelayedTransition(mMainLayout, mEmptyViewTransition);
                }
                mEmptyView.setVisibility(count == 0 ? View.VISIBLE : View.GONE);

                // Cache this adapter's count for when the adapter changes.
                prevAdapterCount = count;
                super.onChanged();
            }
        });

        if (mRingtoneTitleCache == null) {
            mRingtoneTitleCache = new Bundle();
        }

        mAlarmsList.setAdapter(mAdapter);
        mAlarmsList.setVerticalScrollBarEnabled(true);
        mAlarmsList.setOnCreateContextMenuListener(this);

        mAudioManager = (AudioManager)getActivity().getSystemService(Context.AUDIO_SERVICE);

        if (mUndoShowing) {
            showUndoBar();
        }
        return v;
    }

    private void setUndoBarRightMargin(int margin) {
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) mUndoBar.getLayoutParams();
        ((FrameLayout.LayoutParams) mUndoBar.getLayoutParams())
                .setMargins(params.leftMargin, params.topMargin, margin, params.bottomMargin);
        mUndoBar.requestLayout();
    }

    @Override
    public void onResume() {
        super.onResume();

        final DeskClock activity = (DeskClock) getActivity();
        if (activity.getSelectedTab() == DeskClock.ALARM_TAB_INDEX) {
            setFabAppearance();
            setLeftRightButtonAppearance();
        }

        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
        // Check if another app asked us to create a blank new alarm.
        final Intent intent = getActivity().getIntent();
        if (intent.hasExtra(ALARM_CREATE_NEW_INTENT_EXTRA)) {
            if (intent.getBooleanExtra(ALARM_CREATE_NEW_INTENT_EXTRA, false)) {
                // An external app asked us to create a blank alarm.
                startCreatingAlarm();
            }

            // Remove the CREATE_NEW extra now that we've processed it.
            intent.removeExtra(ALARM_CREATE_NEW_INTENT_EXTRA);
        } else if (intent.hasExtra(SCROLL_TO_ALARM_INTENT_EXTRA)) {
            long alarmId = intent.getLongExtra(SCROLL_TO_ALARM_INTENT_EXTRA, Alarm.INVALID_ID);
            if (alarmId != Alarm.INVALID_ID) {
                mScrollToAlarmId = alarmId;
                if (mCursorLoader != null && mCursorLoader.isStarted()) {
                    // We need to force a reload here to make sure we have the latest view
                    // of the data to scroll to.
                    mCursorLoader.forceLoad();
                }
            }

            // Remove the SCROLL_TO_ALARM extra now that we've processed it.
            intent.removeExtra(SCROLL_TO_ALARM_INTENT_EXTRA);
        } else {
            // If alarm stream volume is 0, show a warning
            if (mAudioManager.getStreamVolume(AudioManager.STREAM_ALARM) == 0) {
                showSilentWarningBar();
            }

        }

        // Update the profile status and register the profile observer
        getActivity().getContentResolver().registerContentObserver(
                PROFILES_SETTINGS_URI, false, mProfileObserver);
        updateProfilesStatus();
    }

    private void hideUndoBar(boolean animate, MotionEvent event) {
        if (mUndoBar != null) {
            mUndoFrame.setVisibility(View.GONE);
            if (event != null && mUndoBar.isEventInToastBar(event)) {
                // Avoid touches inside the undo bar.
                return;
            }
            mUndoBar.hide(animate);
        }
        mDeletedAlarm = null;
        mUndoShowing = false;
    }

    private void showUndoBar() {
        final Alarm deletedAlarm = mDeletedAlarm;
        mUndoFrame.setVisibility(View.VISIBLE);
        mUndoBar.show(new ActionableToastBar.ActionClickedListener() {
            @Override
            public void onActionClicked() {
                mAddedAlarm = deletedAlarm;
                mDeletedAlarm = null;
                mUndoShowing = false;

                asyncAddAlarm(deletedAlarm);
            }
        }, 0, getResources().getString(R.string.alarm_deleted), true, 0, R.string.alarm_undo, true);
    }

    private void showSilentWarningBar() {
        mUndoFrame.setVisibility(View.VISIBLE);
        mUndoBar.show(new ActionableToastBar.ActionClickedListener() {
            @Override
            public void onActionClicked() {
                mAudioManager.adjustStreamVolume(AudioManager.STREAM_ALARM,
                        AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI);
                mUndoShowing = false;
            }
        }, 0, getResources().getString(R.string.warn_silent_alarm_title), true,
                R.drawable.ic_alarm_off, 0, true);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_EXPANDED_ID, mAdapter.getExpandedId());
        outState.putLongArray(KEY_REPEAT_CHECKED_IDS, mAdapter.getRepeatArray());
        outState.putLongArray(KEY_SELECTED_ALARMS, mAdapter.getSelectedAlarmsArray());
        outState.putBundle(KEY_RINGTONE_TITLE_CACHE, mRingtoneTitleCache);
        outState.putParcelable(KEY_DELETED_ALARM, mDeletedAlarm);
        outState.putBoolean(KEY_UNDO_SHOWING, mUndoShowing);
        outState.putBundle(KEY_PREVIOUS_DAY_MAP, mAdapter.getPreviousDaysOfWeekMap());
        outState.putParcelable(KEY_SELECTED_ALARM, mSelectedAlarm);
        outState.putInt(KEY_SELECT_SOURCE, mSelectSource);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        ToastMaster.cancelToast();
    }

    @Override
    public void onPause() {
        super.onPause();
        // When the user places the app in the background by pressing "home",
        // dismiss the toast bar. However, since there is no way to determine if
        // home was pressed, just dismiss any existing toast bar when restarting
        // the app.
        hideUndoBar(false, null);

        // Unregister the profile observer
        getActivity().getContentResolver().unregisterContentObserver(mProfileObserver);
    }

    // Callback used by TimePickerDialog
    @Override
    public void onTimeSet(TimePicker timePicker, int hourOfDay, int minute) {
        if (mSelectedAlarm == null) {
            // If mSelectedAlarm is null then we're creating a new alarm.
            Alarm a = new Alarm();
            a.alert = RingtoneManager.getActualDefaultRingtoneUri(getActivity(),
                    RingtoneManager.TYPE_ALARM);
            if (a.alert == null) {
                a.alert = Uri.parse("content://settings/system/alarm_alert");
            }
            a.hour = hourOfDay;
            a.minutes = minute;
            a.enabled = true;
            mAddedAlarm = a;
            asyncAddAlarm(a);
        } else {
            mSelectedAlarm.hour = hourOfDay;
            mSelectedAlarm.minutes = minute;
            mSelectedAlarm.enabled = true;
            mScrollToAlarmId = mSelectedAlarm.id;
            asyncUpdateAlarm(mSelectedAlarm, true);
            mSelectedAlarm = null;
        }
    }

    private void showLabelDialog(final Alarm alarm) {
        final FragmentTransaction ft = getFragmentManager().beginTransaction();
        final Fragment prev = getFragmentManager().findFragmentByTag("label_dialog");
        if (prev != null) {
            ft.remove(prev);
        }
        ft.addToBackStack(null);

        // Create and show the dialog.
        final LabelDialogFragment newFragment =
                LabelDialogFragment.newInstance(alarm, alarm.label, getTag());
        newFragment.show(ft, "label_dialog");
    }

    public void setLabel(Alarm alarm, String label) {
        alarm.label = label;
        asyncUpdateAlarm(alarm, false);
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        return Alarm.getAlarmsCursorLoader(getActivity());
    }

    @Override
    public void onLoadFinished(Loader<Cursor> cursorLoader, final Cursor data) {
        mAdapter.swapCursor(data);
        if (mScrollToAlarmId != INVALID_ID) {
            scrollToAlarm(mScrollToAlarmId);
            mScrollToAlarmId = INVALID_ID;
        }
    }

    /**
     * Scroll to alarm with given alarm id.
     *
     * @param alarmId The alarm id to scroll to.
     */
    private void scrollToAlarm(long alarmId) {
        int alarmPosition = -1;
        for (int i = 0; i < mAdapter.getCount(); i++) {
            long id = mAdapter.getItemId(i);
            if (id == alarmId) {
                alarmPosition = i;
                break;
            }
        }

        if (alarmPosition >= 0) {
            mAdapter.setNewAlarm(alarmId);
            mAlarmsList.smoothScrollToPositionFromTop(alarmPosition, 0);
        } else {
            // Trying to display a deleted alarm should only happen from a missed notification for
            // an alarm that has been marked deleted after use.
            Context context = getActivity().getApplicationContext();
            Toast toast = Toast.makeText(context, R.string.missed_alarm_has_been_deleted,
                    Toast.LENGTH_LONG);
            ToastMaster.setToast(toast);
            toast.show();
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> cursorLoader) {
        mAdapter.swapCursor(null);
    }

    private void sendPickIntent() {
        if (mSelectSource == SEL_SRC_RINGTONE) {
            Uri oldRingtone = Alarm.NO_RINGTONE_URI.equals(
                    AlarmClockFragment.this.mSelectedAlarm.alert) ? null : mSelectedAlarm.alert;
            final Intent intent = new Intent(RingtoneManager.ACTION_RINGTONE_PICKER);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    oldRingtone);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                    RingtoneManager.TYPE_ALARM);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT,
                    false);
            AlarmClockFragment.this.startActivityForResult(intent, REQUEST_CODE_RINGTONE);
        } else {
            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                    AlarmClockFragment.this.mSelectedAlarm.alert);
            intent.setType(SEL_AUDIO_SRC);
            AlarmClockFragment.this.startActivityForResult(intent, REQUEST_CODE_EXTERN_AUDIO);
        }
    }

    private class RingTonePickerDialogListener implements DialogInterface.OnClickListener {
        private AlarmClockFragment alarm;

        public RingTonePickerDialogListener(AlarmClockFragment clock) {
            alarm = clock;
        }

        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case SEL_SRC_RINGTONE:
                case SEL_SRC_EXTERNAL:
                    alarm.mSelectSource = which;
                    break;
                case DialogInterface.BUTTON_POSITIVE:
                    alarm.sendPickIntent();
                case DialogInterface.BUTTON_NEGATIVE:
                default:
                    dialog.dismiss();
                    break;
            }
        }
    }

    private void launchRingTonePicker(final Alarm alarm) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setTitle(R.string.alarm_picker_title).setItems(
                R.array.ringtone_picker_entries,

                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                launchSingleRingTonePicker(alarm);
                                break;
                            case 1:
                                launchSinglePlaylistPicker(alarm);
                                break;
                            case 2:
                                alarm.alert = AlarmMultiPlayer.RANDOM_URI;
                                asyncUpdateAlarm(alarm, false);
                                break;
                        }
                    }
                });
        AlertDialog d = builder.create();
        d.show();
    }

    private void launchSinglePlaylistPicker(final Alarm alarm) {
        final Context context = getActivity();

        final String[] projection
                = new String[]{MediaStore.Audio.Playlists._ID, MediaStore.Audio.Playlists.NAME};
        Cursor c = context.getContentResolver().query(
                MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                projection, null, null, null);

        final CursorAdapter cursorAdapter
                = new SimpleCursorAdapter(context, android.R.layout.simple_list_item_1, c,
                new String[] {MediaStore.Audio.Playlists.NAME}, new int[]{android.R.id.text1}, 0);

        new AlertDialog.Builder(context).setSingleChoiceItems(cursorAdapter, 0,

                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        Cursor c = (Cursor) cursorAdapter.getItem(which);
                        if (c != null) {
                            alarm.alert = Uri.withAppendedPath(
                                    MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI,
                                    String.valueOf(c.getLong(0)));
                            asyncUpdateAlarm(alarm, false);
                        }
                        dialog.dismiss();
                        cursorAdapter.changeCursor(null);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void launchSingleRingTonePicker(Alarm alarm) {
        mSelectedAlarm = alarm;
        RingTonePickerDialogListener listener = new RingTonePickerDialogListener(this);
        new AlertDialog.Builder(getActivity())
                .setTitle(getResources().getString(R.string.alarm_select))
                .setSingleChoiceItems(
                        new String[] {
                                getString(R.string.alarm_select_ringtone),
                                getString(R.string.alarm_select_external) },
                        mSelectSource, listener)
                .setPositiveButton(getString(android.R.string.ok), listener)
                .setNegativeButton(getString(android.R.string.cancel), listener)
                .show();
    }

    private void launchProfilePicker(Alarm alarm) {
        mSelectedAlarm = alarm;
        final Intent intent = new Intent(ProfileManager.ACTION_PROFILE_PICKER);

        intent.putExtra(ProfileManager.EXTRA_PROFILE_EXISTING_UUID, alarm.profile.toString());
        intent.putExtra(ProfileManager.EXTRA_PROFILE_SHOW_NONE, true);
        startActivityForResult(intent, REQUEST_CODE_PROFILE);
    }

    private void releaseRingtoneUri(Uri uri) {
        final ContentResolver cr = getActivity().getContentResolver();
        if (uri == null || mAdapter == null) {
            return;
        }

        // Check that the uri is currently persisted
        boolean containsUri = false;
        for (UriPermission uriPermission : cr.getPersistedUriPermissions()) {
            if (uriPermission.getUri().compareTo(uri) == 0) {
                containsUri = true;
                break;
            }
        }
        if (!containsUri) {
            return;
        }

        // Check that only one uri is in use
        int found = 0;
        int count = mAdapter.getCount();
        for (int i = 0; i < count; i++) {
            Alarm alarm = new Alarm((Cursor) mAdapter.getItem(i));
            if (alarm.alert != null && uri.compareTo(alarm.alert) == 0) {
                found++;
                if (found > 1) {
                    break;
                }
            }
        }
        if (found == 1) {
            // Release current granted uri
            try {
                if (uri != null) {
                    getActivity().getContentResolver().releasePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
            } catch (SecurityException e) {
                // Ignore
            }
        }
    }

    private Uri getRingtoneUri(Intent intent) {
        boolean releaseRingtoneUri = true;

        Uri uri;
        if (mSelectSource == SEL_SRC_RINGTONE) {
            uri = intent.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
        } else {
            uri = intent.getData();
            if (uri != null) {
                // if the alarms are the same, don't revoke the permissions
                releaseRingtoneUri = !uri.equals(mSelectedAlarm.alert);

                try {
                    getActivity().getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ex) {
                    LogUtils.e("Unable to take persistent grant permission for uri " + uri, ex);
                    uri = null;
                    final int msgId = R.string.take_persistent_grant_uri_permission_failed_msg;
                    Toast.makeText(getActivity(), msgId, Toast.LENGTH_SHORT).show();
                }
            }
        }
        if (uri == null) {
            uri = Alarm.NO_RINGTONE_URI;
        }

        if (releaseRingtoneUri) {
            // Release the current ringtone uri
            releaseRingtoneUri(mSelectedAlarm.alert);
        }

        return uri;
    }

    private void saveRingtoneUri(Intent intent) {

        Uri uri =  getRingtoneUri(intent);
        mSelectedAlarm.alert = uri;

        // Save the last selected ringtone as the default for new alarms
        if (!Alarm.NO_RINGTONE_URI.equals(uri)) {
            RingtoneManager.setActualDefaultRingtoneUri(
                    getActivity(), RingtoneManager.TYPE_ALARM, uri);
        }
        asyncUpdateAlarm(mSelectedAlarm, false);
    }

    private void saveProfile(Intent intent) {
        final String uuid = intent.getStringExtra(ProfileManager.EXTRA_PROFILE_PICKED_UUID);
        if (uuid != null) {
            try {
                mSelectedAlarm.profile = UUID.fromString(uuid);
            } catch (IllegalArgumentException ex) {
                mSelectedAlarm.profile = ProfileManager.NO_PROFILE;
            }
        } else {
            mSelectedAlarm.profile = ProfileManager.NO_PROFILE;
        }
        asyncUpdateAlarm(mSelectedAlarm, false);
    }

    private boolean isProfilesEnabled() {
        return Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.SYSTEM_PROFILES_ENABLED, 1) == 1;
    }

    private String getProfileName(Alarm alarm) {
        if (!isProfilesEnabled() || alarm.profile.equals(ProfileManager.NO_PROFILE)) {
            return getString(R.string.profile_no_selected);
        }
        Profile profile = mProfileManager.getProfile(alarm.profile);
        if (profile == null) {
            return getString(R.string.profile_no_selected);
        }
        return profile.getName();
    }

    private void updateProfilesStatus() {
        // Need to refresh the data
        if (mAdapter != null) {
            mAdapter.notifyDataSetChanged();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_CODE_RINGTONE:
                case REQUEST_CODE_EXTERN_AUDIO:
                    saveRingtoneUri(data);
                    break;
                case REQUEST_CODE_PROFILE:
                    saveProfile(data);
                default:
                    LogUtils.w("Unhandled request code in onActivityResult: " + requestCode);
            }
        }
    }

    private class ProfilesObserver extends ContentObserver {
        public ProfilesObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            onChange(selfChange, null);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri) {
            if (uri == null) return;
            if (PROFILES_SETTINGS_URI.equals(uri)) {
                mHandler.removeMessages(MSG_PROFILE_STATUS_CHANGE);
                mHandler.sendEmptyMessage(MSG_PROFILE_STATUS_CHANGE);
            }
        }
    }

    public class AlarmItemAdapter extends CursorAdapter {
        private final Context mContext;
        private final LayoutInflater mFactory;
        private final String[] mShortWeekDayStrings;
        private final String[] mLongWeekDayStrings;
        private final int mColorLit;
        private final int mColorDim;
        private final Typeface mRobotoNormal;
        private final ListView mList;

        private long mExpandedId;
        private ItemHolder mExpandedItemHolder;
        private final HashSet<Long> mRepeatChecked = new HashSet<Long>();
        private final HashSet<Long> mSelectedAlarms = new HashSet<Long>();
        private Bundle mPreviousDaysOfWeekMap = new Bundle();

        private final boolean mHasVibrator;
        private final int mCollapseExpandHeight;

        // This determines the order in which it is shown and processed in the UI.
        // The array is filled when the adapter is created
        private final int[] DAY_ORDER = new int[7];

        public class ItemHolder {

            // views for optimization
            LinearLayout alarmItem;
            TextTime clock;
            TextView tomorrowLabel;
            Switch onoff;
            TextView daysOfWeek;
            TextView label;
            ImageButton delete;
            View expandArea;
            View summary;
            TextView clickableLabel;
            CheckBox repeat;
            LinearLayout repeatDays;
            Button[] dayButtons = new Button[7];
            CheckBox vibrate;
            CheckBox increasingVolume;
            TextView ringtone;
            TextView profile;
            View hairLine;
            View arrow;
            View collapseExpandArea;

            // Other states
            Alarm alarm;
        }

        // Used for scrolling an expanded item in the list to make sure it is fully visible.
        private long mScrollAlarmId = AlarmClockFragment.INVALID_ID;
        private final Runnable mScrollRunnable = new Runnable() {
            @Override
            public void run() {
                if (mScrollAlarmId != AlarmClockFragment.INVALID_ID) {
                    View v = getViewById(mScrollAlarmId);
                    if (v != null) {
                        Rect rect = new Rect(v.getLeft(), v.getTop(), v.getRight(), v.getBottom());
                        mList.requestChildRectangleOnScreen(v, rect, false);
                    }
                    mScrollAlarmId = AlarmClockFragment.INVALID_ID;
                }
            }
        };

        public AlarmItemAdapter(Context context, long expandedId, long[] repeatCheckedIds,
                                long[] selectedAlarms, Bundle previousDaysOfWeekMap, ListView list) {
            super(context, null, 0);
            mContext = context;
            mFactory = LayoutInflater.from(context);
            mList = list;

            DateFormatSymbols dfs = new DateFormatSymbols();
            mShortWeekDayStrings = Utils.getShortWeekdays();
            mLongWeekDayStrings = dfs.getWeekdays();
            int firstDayOfWeek = Calendar.getInstance(Locale.getDefault()).getFirstDayOfWeek();
            int j = 0;
            for (int i = firstDayOfWeek; i <= DAY_ORDER.length; i++, j++) {
                DAY_ORDER[j] = i;
            }
            for (int i = Calendar.SUNDAY; i < firstDayOfWeek; i++, j++) {
                DAY_ORDER[j] = i;
            }

            Resources res = mContext.getResources();
            mColorLit = res.getColor(R.color.clock_white);
            mColorDim = res.getColor(R.color.clock_gray);

            mRobotoNormal = Typeface.create("sans-serif", Typeface.NORMAL);

            mExpandedId = expandedId;
            if (repeatCheckedIds != null) {
                buildHashSetFromArray(repeatCheckedIds, mRepeatChecked);
            }
            if (previousDaysOfWeekMap != null) {
                mPreviousDaysOfWeekMap = previousDaysOfWeekMap;
            }
            if (selectedAlarms != null) {
                buildHashSetFromArray(selectedAlarms, mSelectedAlarms);
            }

            mHasVibrator = ((Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE))
                    .hasVibrator();

            mCollapseExpandHeight = (int) res.getDimension(R.dimen.collapse_expand_height);
        }

        public void removeSelectedId(int id) {
            mSelectedAlarms.remove(id);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (!getCursor().moveToPosition(position)) {
                // May happen if the last alarm was deleted and the cursor refreshed while the
                // list is updated.
                LogUtils.v("couldn't move cursor to position " + position);
                return null;
            }
            View v;
            if (convertView == null) {
                v = newView(mContext, getCursor(), parent);
            } else {
                v = convertView;
            }
            bindView(v, mContext, getCursor());
            return v;
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            final View view = mFactory.inflate(R.layout.alarm_time, parent, false);
            setNewHolder(view);
            return view;
        }

        /**
         * In addition to changing the data set for the alarm list, swapCursor is now also
         * responsible for preparing the transition for any added/removed items.
         */
        @Override
        public synchronized Cursor swapCursor(Cursor cursor) {
            if (mAddedAlarm != null || mDeletedAlarm != null) {
                TransitionManager.beginDelayedTransition(mAlarmsList, mAddRemoveTransition);
            }

            final Cursor c = super.swapCursor(cursor);

            mAddedAlarm = null;
            mDeletedAlarm = null;

            return c;
        }

        private void setNewHolder(View view) {
            // standard view holder optimization
            final ItemHolder holder = new ItemHolder();
            holder.alarmItem = (LinearLayout) view.findViewById(R.id.alarm_item);
            holder.tomorrowLabel = (TextView) view.findViewById(R.id.tomorrowLabel);
            holder.clock = (TextTime) view.findViewById(R.id.digital_clock);
            holder.onoff = (Switch) view.findViewById(R.id.onoff);
            holder.onoff.setTypeface(mRobotoNormal);
            holder.daysOfWeek = (TextView) view.findViewById(R.id.daysOfWeek);
            holder.label = (TextView) view.findViewById(R.id.label);
            holder.delete = (ImageButton) view.findViewById(R.id.delete);
            holder.summary = view.findViewById(R.id.summary);
            holder.expandArea = view.findViewById(R.id.expand_area);
            holder.hairLine = view.findViewById(R.id.hairline);
            holder.arrow = view.findViewById(R.id.arrow);
            holder.repeat = (CheckBox) view.findViewById(R.id.repeat_onoff);
            holder.clickableLabel = (TextView) view.findViewById(R.id.edit_label);
            holder.repeatDays = (LinearLayout) view.findViewById(R.id.repeat_days);
            holder.collapseExpandArea = view.findViewById(R.id.collapse_expand);

            // Build button for each day.
            for (int i = 0; i < 7; i++) {
                final Button dayButton = (Button) mFactory.inflate(
                        R.layout.day_button, holder.repeatDays, false /* attachToRoot */);
                dayButton.setText(mShortWeekDayStrings[DAY_ORDER[i]]);
                dayButton.setContentDescription(mLongWeekDayStrings[DAY_ORDER[i]]);
                holder.repeatDays.addView(dayButton);
                holder.dayButtons[i] = dayButton;
            }
            holder.vibrate = (CheckBox) view.findViewById(R.id.vibrate_onoff);
            holder.increasingVolume = (CheckBox) view.findViewById(R.id.increasing_volume_onoff);
            holder.ringtone = (TextView) view.findViewById(R.id.choose_ringtone);
            holder.profile = (TextView) view.findViewById(R.id.choose_profile);

            view.setTag(holder);
        }

        @Override
        public void bindView(final View view, Context context, final Cursor cursor) {
            final Alarm alarm = new Alarm(cursor);
            Object tag = view.getTag();
            if (tag == null) {
                // The view was converted but somehow lost its tag.
                setNewHolder(view);
            }
            if (!Utils.isRingToneUriValid(mContext, alarm.alert)) {
                alarm.alert = RingtoneManager.getActualDefaultRingtoneUri(context,
                        RingtoneManager.TYPE_ALARM);

                if (!Utils.isRingToneUriValid(mContext, alarm.alert)) {
                    System.out.println("RAJ - ring tone is not valid!: " + alarm.alert);
                    Uri uri = Utils.getSystemDefaultAlarm(mContext);
                    alarm.alert = uri;
                    System.out.println("RAJ - resetting to default: " + Utils.isRingToneUriValid(mContext, alarm.alert));

                    RingtoneManager.setActualDefaultRingtoneUri(
                            getActivity(), RingtoneManager.TYPE_ALARM, uri);
                }

                asyncUpdateAlarm(alarm, false);
            }
            final ItemHolder itemHolder = (ItemHolder) tag;
            itemHolder.alarm = alarm;

            // We must unset the listener first because this maybe a recycled view so changing the
            // state would affect the wrong alarm.
            itemHolder.onoff.setOnCheckedChangeListener(null);
            itemHolder.onoff.setChecked(alarm.enabled);

            if (mSelectedAlarms.contains(itemHolder.alarm.id)) {
                setAlarmItemBackgroundAndElevation(itemHolder.alarmItem, true /* expanded */);
                setDigitalTimeAlpha(itemHolder, true);
                itemHolder.onoff.setEnabled(false);
            } else {
                itemHolder.onoff.setEnabled(true);
                setAlarmItemBackgroundAndElevation(itemHolder.alarmItem, false /* expanded */);
                setDigitalTimeAlpha(itemHolder, itemHolder.onoff.isChecked());
            }
            itemHolder.clock.setFormat(
                    (int)mContext.getResources().getDimension(R.dimen.alarm_label_size));
            itemHolder.clock.setTime(alarm.hour, alarm.minutes);
            itemHolder.clock.setClickable(true);
            itemHolder.clock.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    mSelectedAlarm = itemHolder.alarm;
                    AlarmUtils.showTimeEditDialog(AlarmClockFragment.this, alarm);
                    expandAlarm(itemHolder, true);
                    itemHolder.alarmItem.post(mScrollRunnable);
                }
            });

            final CompoundButton.OnCheckedChangeListener onOffListener =
                    new CompoundButton.OnCheckedChangeListener() {
                        @Override
                        public void onCheckedChanged(CompoundButton compoundButton,
                                                     boolean checked) {
                            if (checked != alarm.enabled) {
                                setDigitalTimeAlpha(itemHolder, checked);
                                alarm.enabled = checked;
                                asyncUpdateAlarm(alarm, alarm.enabled);
                            }
                        }
                    };

            if (mRepeatChecked.contains(alarm.id) || itemHolder.alarm.daysOfWeek.isRepeating()) {
                itemHolder.tomorrowLabel.setVisibility(View.GONE);
            } else {
                itemHolder.tomorrowLabel.setVisibility(View.VISIBLE);
                final Resources resources = getResources();
                final String labelText = isTomorrow(alarm) ?
                        resources.getString(R.string.alarm_tomorrow) :
                        resources.getString(R.string.alarm_today);
                itemHolder.tomorrowLabel.setText(labelText);
            }
            itemHolder.onoff.setOnCheckedChangeListener(onOffListener);

            boolean expanded = isAlarmExpanded(alarm);
            if (expanded) {
                mExpandedItemHolder = itemHolder;
            }
            itemHolder.expandArea.setVisibility(expanded? View.VISIBLE : View.GONE);
            itemHolder.delete.setVisibility(expanded ? View.VISIBLE : View.GONE);
            itemHolder.summary.setVisibility(expanded? View.GONE : View.VISIBLE);
            itemHolder.hairLine.setVisibility(expanded ? View.GONE : View.VISIBLE);
            itemHolder.arrow.setRotation(expanded ? ROTATE_180_DEGREE : 0);

            // Set the repeat text or leave it blank if it does not repeat.
            final String daysOfWeekStr =
                    alarm.daysOfWeek.toString(AlarmClockFragment.this.getActivity(), false);
            if (daysOfWeekStr != null && daysOfWeekStr.length() != 0) {
                itemHolder.daysOfWeek.setText(daysOfWeekStr);
                itemHolder.daysOfWeek.setContentDescription(alarm.daysOfWeek.toAccessibilityString(
                        AlarmClockFragment.this.getActivity()));
                itemHolder.daysOfWeek.setVisibility(View.VISIBLE);
                itemHolder.daysOfWeek.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        expandAlarm(itemHolder, true);
                        itemHolder.alarmItem.post(mScrollRunnable);
                    }
                });

            } else {
                itemHolder.daysOfWeek.setVisibility(View.GONE);
            }

            if (alarm.label != null && alarm.label.length() != 0) {
                itemHolder.label.setText(alarm.label + "  ");
                itemHolder.label.setVisibility(View.VISIBLE);
                itemHolder.label.setContentDescription(
                        mContext.getResources().getString(R.string.label_description) + " "
                                + alarm.label);
                itemHolder.label.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        expandAlarm(itemHolder, true);
                        itemHolder.alarmItem.post(mScrollRunnable);
                    }
                });
            } else {
                itemHolder.label.setVisibility(View.GONE);
            }

            itemHolder.delete.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    mDeletedAlarm = alarm;
                    mRepeatChecked.remove(alarm.id);
                    asyncDeleteAlarm(alarm);
                }
            });

            if (expanded) {
                expandAlarm(itemHolder, false);
            }

            itemHolder.alarmItem.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    if (isAlarmExpanded(alarm)) {
                        collapseAlarm(itemHolder, true);
                    } else {
                        expandAlarm(itemHolder, true);
                    }
                }
            });

            itemHolder.profile.setVisibility(isProfilesEnabled() ? View.VISIBLE : View.GONE);
        }

        private void setAlarmItemBackgroundAndElevation(LinearLayout layout, boolean expanded) {
            if (expanded) {
                layout.setBackgroundColor(getTintedBackgroundColor());
                layout.setElevation(ALARM_ELEVATION);
            } else {
                layout.setBackgroundResource(R.drawable.alarm_background_normal);
                layout.setElevation(0);
            }
        }

        private int getTintedBackgroundColor() {
            final int c = Utils.getCurrentHourColor();
            final int red = Color.red(c) + (int) (TINTED_LEVEL * (255 - Color.red(c)));
            final int green = Color.green(c) + (int) (TINTED_LEVEL * (255 - Color.green(c)));
            final int blue = Color.blue(c) + (int) (TINTED_LEVEL * (255 - Color.blue(c)));
            return Color.rgb(red, green, blue);
        }

        private boolean isTomorrow(Alarm alarm) {
            final Calendar now = Calendar.getInstance();
            final int alarmHour = alarm.hour;
            final int currHour = now.get(Calendar.HOUR_OF_DAY);
            return alarmHour < currHour ||
                    (alarmHour == currHour && alarm.minutes < now.get(Calendar.MINUTE));
        }

        private void bindExpandArea(final ItemHolder itemHolder, final Alarm alarm) {
            // Views in here are not bound until the item is expanded.

            if (alarm.label != null && alarm.label.length() > 0) {
                itemHolder.clickableLabel.setText(alarm.label);
            } else {
                itemHolder.clickableLabel.setText(R.string.label);
            }

            itemHolder.clickableLabel.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    showLabelDialog(alarm);
                }
            });

            if (mRepeatChecked.contains(alarm.id) || itemHolder.alarm.daysOfWeek.isRepeating()) {
                itemHolder.repeat.setChecked(true);
                itemHolder.repeatDays.setVisibility(View.VISIBLE);
            } else {
                itemHolder.repeat.setChecked(false);
                itemHolder.repeatDays.setVisibility(View.GONE);
            }
            itemHolder.repeat.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    // Animate the resulting layout changes.
                    TransitionManager.beginDelayedTransition(mList, mRepeatTransition);

                    final boolean checked = ((CheckBox) view).isChecked();
                    if (checked) {
                        // Show days
                        itemHolder.repeatDays.setVisibility(View.VISIBLE);
                        mRepeatChecked.add(alarm.id);

                        // Set all previously set days
                        // or
                        // Set all days if no previous.
                        final int bitSet = mPreviousDaysOfWeekMap.getInt("" + alarm.id);
                        alarm.daysOfWeek.setBitSet(bitSet);
                        if (!alarm.daysOfWeek.isRepeating()) {
                            alarm.daysOfWeek.setDaysOfWeek(true, DAY_ORDER);
                        }
                        updateDaysOfWeekButtons(itemHolder, alarm.daysOfWeek);
                    } else {
                        // Hide days
                        itemHolder.repeatDays.setVisibility(View.GONE);
                        mRepeatChecked.remove(alarm.id);

                        // Remember the set days in case the user wants it back.
                        final int bitSet = alarm.daysOfWeek.getBitSet();
                        mPreviousDaysOfWeekMap.putInt("" + alarm.id, bitSet);

                        // Remove all repeat days
                        alarm.daysOfWeek.clearAllDays();
                    }

                    asyncUpdateAlarm(alarm, false);
                }
            });

            updateDaysOfWeekButtons(itemHolder, alarm.daysOfWeek);
            for (int i = 0; i < 7; i++) {
                final int buttonIndex = i;

                itemHolder.dayButtons[i].setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final boolean isActivated =
                                itemHolder.dayButtons[buttonIndex].isActivated();
                        alarm.daysOfWeek.setDaysOfWeek(!isActivated, DAY_ORDER[buttonIndex]);
                        if (!isActivated) {
                            turnOnDayOfWeek(itemHolder, buttonIndex);
                        } else {
                            turnOffDayOfWeek(itemHolder, buttonIndex);

                            // See if this was the last day, if so, un-check the repeat box.
                            if (!alarm.daysOfWeek.isRepeating()) {
                                // Animate the resulting layout changes.
                                TransitionManager.beginDelayedTransition(mList, mRepeatTransition);

                                itemHolder.repeat.setChecked(false);
                                itemHolder.repeatDays.setVisibility(View.GONE);
                                mRepeatChecked.remove(alarm.id);

                                // Set history to no days, so it will be everyday when repeat is
                                // turned back on
                                mPreviousDaysOfWeekMap.putInt("" + alarm.id,
                                        DaysOfWeek.NO_DAYS_SET);
                            }
                        }
                        asyncUpdateAlarm(alarm, false);
                    }
                });
            }

            if (!mHasVibrator) {
                itemHolder.vibrate.setVisibility(View.INVISIBLE);
            } else {
                itemHolder.vibrate.setVisibility(View.VISIBLE);
                if (!alarm.vibrate) {
                    itemHolder.vibrate.setChecked(false);
                } else {
                    itemHolder.vibrate.setChecked(true);
                }
            }

            itemHolder.vibrate.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean checked = ((CheckBox) v).isChecked();
                    alarm.vibrate = checked;
                    asyncUpdateAlarm(alarm, false);
                }
            });

            final String ringtone;
            final String ringtitle;
            if (Alarm.NO_RINGTONE_URI.equals(alarm.alert)) {
                ringtone = mContext.getResources().getString(R.string.silent_alarm_summary);
            } else {
                ringtitle = getRingToneTitle(alarm);
                if (ringtitle != null) {
                    ringtone = ringtitle;
                } else {
                    ringtone = mContext.getResources().getString(R.string.silent_alarm_summary);
                }
            }
            itemHolder.ringtone.setText(ringtone);
            itemHolder.ringtone.setContentDescription(
                    mContext.getResources().getString(R.string.ringtone_description) + " "
                            + ringtone);
            itemHolder.ringtone.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchRingTonePicker(alarm);
                }
            });


            itemHolder.increasingVolume.setVisibility(View.VISIBLE);
            itemHolder.increasingVolume.setChecked(alarm.increasingVolume);
            itemHolder.increasingVolume.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final boolean checked = ((CheckBox) v).isChecked();
                    //When action mode is on - simulate long click
                    alarm.increasingVolume = checked;
                    asyncUpdateAlarm(alarm, false);
                }
            });

            final String profile = getProfileName(alarm);
            itemHolder.profile.setText(profile);
            itemHolder.profile.setVisibility(isProfilesEnabled() ? View.VISIBLE : View.GONE);
            itemHolder.profile.setContentDescription(
                    mContext.getResources().getString(R.string.profile_description, profile));
            itemHolder.profile.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    launchProfilePicker(alarm);
                }
            });
        }

        // Sets the alpha of the digital time display. This gives a visual effect
        // for enabled/disabled alarm while leaving the on/off switch more visible
        private void setDigitalTimeAlpha(ItemHolder holder, boolean enabled) {
            float alpha = enabled ? 1f : 0.69f;
            holder.clock.setAlpha(alpha);
        }

        private void updateDaysOfWeekButtons(ItemHolder holder, DaysOfWeek daysOfWeek) {
            HashSet<Integer> setDays = daysOfWeek.getSetDays();
            for (int i = 0; i < 7; i++) {
                if (setDays.contains(DAY_ORDER[i])) {
                    turnOnDayOfWeek(holder, i);
                } else {
                    turnOffDayOfWeek(holder, i);
                }
            }
        }

        public void toggleSelectState(View v) {
            // long press could be on the parent view or one of its childs, so find the parent view
            v = getTopParent(v);
            if (v != null) {
                long id = ((ItemHolder)v.getTag()).alarm.id;
                if (mSelectedAlarms.contains(id)) {
                    mSelectedAlarms.remove(id);
                } else {
                    mSelectedAlarms.add(id);
                }
            }
        }

        private View getTopParent(View v) {
            while (v != null && v.getId() != R.id.alarm_item) {
                v = (View) v.getParent();
            }
            return v;
        }

        public int getSelectedItemsNum() {
            return mSelectedAlarms.size();
        }

        private void turnOffDayOfWeek(ItemHolder holder, int dayIndex) {
            final Button dayButton = holder.dayButtons[dayIndex];
            dayButton.setActivated(false);
            dayButton.setTextColor(getResources().getColor(R.color.clock_white));
        }

        private void turnOnDayOfWeek(ItemHolder holder, int dayIndex) {
            final Button dayButton = holder.dayButtons[dayIndex];
            dayButton.setActivated(true);
            dayButton.setTextColor(Utils.getCurrentHourColor());
        }


        /**
         * Does a read-through cache for ringtone titles.
         *
         * @param Alarm The alarm to get the ringtone title from.
         * @return The ringtone title. {@literal null} if no matching ringtone found.
         */
        private String getRingToneTitle(Alarm alarm) {
            Uri uri = alarm.alert;
            // Try the cache first
            String title = mRingtoneTitleCache.getString(uri.toString());
            if (title == null) {
                if (uri.equals(AlarmMultiPlayer.RANDOM_URI)) {
                    title = mContext.getResources().getString(R.string.alarm_type_random);
                } else {
                    if (Utils.isRingToneUriValid(mContext, uri)) {
                        if (uri.getAuthority().equals(Utils.DOC_AUTHORITY)
                                || uri.getAuthority().equals(Utils.DOC_DOWNLOAD)
                                || uri.getAuthority().equals(Utils.DOC_EXTERNAL)) {
                            title = getDisplayNameFromDatabase(mContext,uri);
                        } else if (uri.isPathPrefixMatch(MediaStore.Audio.Playlists.EXTERNAL_CONTENT_URI)) {
                            Cursor c = getActivity().getContentResolver().query(uri, new String[] {MediaStore.Audio.Playlists.NAME}, null, null, null);
                            if (c.moveToFirst()) {
                                title = c.getString(0);
                            }
                        } else {
                            Ringtone ringTone = RingtoneManager.getRingtone(mContext, uri);
                            if (ringTone != null) {
                                title = ringTone.getTitle(mContext);
                            }
                        }
                    }
                }
                if (title != null) {
                    mRingtoneTitleCache.putString(uri.toString(), title);
                }
            }
            return title;
        }

        private String getDisplayNameFromDatabase(Context context,Uri uri) {
            String selection = null;
            String[] selectionArgs = null;
            String title = mContext.getString(R.string.ringtone_default);
            // If restart Alarm,there is no permission to get the title from the uri.
            // No matter in which database,the music has the same id.
            // So we can only get the info of the music from other database by id in uri.
            if (uri.getAuthority().equals(Utils.DOC_DOWNLOAD)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (uri.getAuthority().equals(Utils.DOC_AUTHORITY)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                selection = "_id=?";
                selectionArgs = new String[] {
                    split[1]
                };
            }
            Cursor cursor = null;
            try {
                cursor = context.getContentResolver().query(uri,
                        new String[] {
                                Utils.getTitleColumnNameForUri(uri),
                        }, selection, selectionArgs, null);
                if (cursor != null && cursor.getCount() > 0) {
                        cursor.moveToFirst();
                    title = cursor.getString(0);
                }
            } catch (Exception e) {
                LogUtils.e("Get ringtone uri Exception: e.toString=" + e.toString());
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            return title;
        }

        public void setNewAlarm(long alarmId) {
            mExpandedId = alarmId;
        }

        /**
         * Expands the alarm for editing.
         *
         * @param itemHolder The item holder instance.
         */
        private void expandAlarm(final ItemHolder itemHolder, boolean animate) {
            // Skip animation later if item is already expanded
            animate &= mExpandedId != itemHolder.alarm.id;

            if (mExpandedItemHolder != null
                    && mExpandedItemHolder != itemHolder
                    && mExpandedId != itemHolder.alarm.id) {
                // Only allow one alarm to expand at a time.
                collapseAlarm(mExpandedItemHolder, animate);
            }

            bindExpandArea(itemHolder, itemHolder.alarm);

            mExpandedId = itemHolder.alarm.id;
            mExpandedItemHolder = itemHolder;

            // Scroll the view to make sure it is fully viewed
            mScrollAlarmId = itemHolder.alarm.id;

            // Save the starting height so we can animate from this value.
            final int startingHeight = itemHolder.alarmItem.getHeight();

            // Set the expand area to visible so we can measure the height to animate to.
            setAlarmItemBackgroundAndElevation(itemHolder.alarmItem, true /* expanded */);
            itemHolder.expandArea.setVisibility(View.VISIBLE);
            itemHolder.delete.setVisibility(View.VISIBLE);

            if (!animate) {
                // Set the "end" layout and don't do the animation.
                itemHolder.arrow.setRotation(ROTATE_180_DEGREE);
                return;
            }

            // Add an onPreDrawListener, which gets called after measurement but before the draw.
            // This way we can check the height we need to animate to before any drawing.
            // Note the series of events:
            //  * expandArea is set to VISIBLE, which causes a layout pass
            //  * the view is measured, and our onPreDrawListener is called
            //  * we set up the animation using the start and end values.
            //  * the height is set back to the starting point so it can be animated down.
            //  * request another layout pass.
            //  * return false so that onDraw() is not called for the single frame before
            //    the animations have started.
            final ViewTreeObserver observer = mAlarmsList.getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    // We don't want to continue getting called for every listview drawing.
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }
                    // Calculate some values to help with the animation.
                    final int endingHeight = itemHolder.alarmItem.getHeight();
                    final int distance = endingHeight - startingHeight;
                    final int collapseHeight = itemHolder.collapseExpandArea.getHeight();

                    // Set the height back to the start state of the animation.
                    itemHolder.alarmItem.getLayoutParams().height = startingHeight;
                    // To allow the expandArea to glide in with the expansion animation, set a
                    // negative top margin, which will animate down to a margin of 0 as the height
                    // is increased.
                    // Note that we need to maintain the bottom margin as a fixed value (instead of
                    // just using a listview, to allow for a flatter hierarchy) to fit the bottom
                    // bar underneath.
                    FrameLayout.LayoutParams expandParams = (FrameLayout.LayoutParams)
                            itemHolder.expandArea.getLayoutParams();
                    expandParams.setMargins(0, -distance, 0, collapseHeight);
                    itemHolder.alarmItem.requestLayout();

                    // Set up the animator to animate the expansion.
                    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f)
                            .setDuration(EXPAND_DURATION);
                    animator.setInterpolator(mExpandInterpolator);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animator) {
                            Float value = (Float) animator.getAnimatedValue();

                            // For each value from 0 to 1, animate the various parts of the layout.
                            itemHolder.alarmItem.getLayoutParams().height =
                                    (int) (value * distance + startingHeight);
                            FrameLayout.LayoutParams expandParams = (FrameLayout.LayoutParams)
                                    itemHolder.expandArea.getLayoutParams();
                            expandParams.setMargins(
                                    0, (int) -((1 - value) * distance), 0, collapseHeight);
                            itemHolder.arrow.setRotation(ROTATE_180_DEGREE * value);
                            itemHolder.summary.setAlpha(1 - value);
                            itemHolder.hairLine.setAlpha(1 - value);

                            itemHolder.alarmItem.requestLayout();
                        }
                    });
                    // Set everything to their final values when the animation's done.
                    animator.addListener(new AnimatorListener() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Set it back to wrap content since we'd explicitly set the height.
                            itemHolder.alarmItem.getLayoutParams().height =
                                    LayoutParams.WRAP_CONTENT;
                            itemHolder.arrow.setRotation(ROTATE_180_DEGREE);
                            itemHolder.summary.setVisibility(View.GONE);
                            itemHolder.hairLine.setVisibility(View.GONE);
                            itemHolder.delete.setVisibility(View.VISIBLE);
                        }

                        @Override
                        public void onAnimationCancel(Animator animation) {
                            // TODO we may have to deal with cancelations of the animation.
                        }

                        @Override
                        public void onAnimationRepeat(Animator animation) { }
                        @Override
                        public void onAnimationStart(Animator animation) { }
                    });
                    animator.start();

                    // Return false so this draw does not occur to prevent the final frame from
                    // being drawn for the single frame before the animations start.
                    return false;
                }
            });
        }

        private boolean isAlarmExpanded(Alarm alarm) {
            return mExpandedId == alarm.id;
        }

        private void collapseAlarm(final ItemHolder itemHolder, boolean animate) {
            mExpandedId = AlarmClockFragment.INVALID_ID;
            mExpandedItemHolder = null;

            // Save the starting height so we can animate from this value.
            final int startingHeight = itemHolder.alarmItem.getHeight();

            // Set the expand area to gone so we can measure the height to animate to.
            setAlarmItemBackgroundAndElevation(itemHolder.alarmItem, false /* expanded */);
            itemHolder.expandArea.setVisibility(View.GONE);

            if (!animate) {
                // Set the "end" layout and don't do the animation.
                itemHolder.arrow.setRotation(0);
                itemHolder.hairLine.setTranslationY(0);
                return;
            }

            // Add an onPreDrawListener, which gets called after measurement but before the draw.
            // This way we can check the height we need to animate to before any drawing.
            // Note the series of events:
            //  * expandArea is set to GONE, which causes a layout pass
            //  * the view is measured, and our onPreDrawListener is called
            //  * we set up the animation using the start and end values.
            //  * expandArea is set to VISIBLE again so it can be shown animating.
            //  * request another layout pass.
            //  * return false so that onDraw() is not called for the single frame before
            //    the animations have started.
            final ViewTreeObserver observer = mAlarmsList.getViewTreeObserver();
            observer.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
                @Override
                public boolean onPreDraw() {
                    if (observer.isAlive()) {
                        observer.removeOnPreDrawListener(this);
                    }

                    // Calculate some values to help with the animation.
                    final int endingHeight = itemHolder.alarmItem.getHeight();
                    final int distance = endingHeight - startingHeight;

                    // Re-set the visibilities for the start state of the animation.
                    itemHolder.expandArea.setVisibility(View.VISIBLE);
                    itemHolder.delete.setVisibility(View.GONE);
                    itemHolder.summary.setVisibility(View.VISIBLE);
                    itemHolder.hairLine.setVisibility(View.VISIBLE);
                    itemHolder.summary.setAlpha(1);

                    // Set up the animator to animate the expansion.
                    ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f)
                            .setDuration(COLLAPSE_DURATION);
                    animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                        @Override
                        public void onAnimationUpdate(ValueAnimator animator) {
                            Float value = (Float) animator.getAnimatedValue();

                            // For each value from 0 to 1, animate the various parts of the layout.
                            itemHolder.alarmItem.getLayoutParams().height =
                                    (int) (value * distance + startingHeight);
                            FrameLayout.LayoutParams expandParams = (FrameLayout.LayoutParams)
                                    itemHolder.expandArea.getLayoutParams();
                            expandParams.setMargins(
                                    0, (int) (value * distance), 0, mCollapseExpandHeight);
                            itemHolder.arrow.setRotation(ROTATE_180_DEGREE * (1 - value));
                            itemHolder.delete.setAlpha(value);
                            itemHolder.summary.setAlpha(value);
                            itemHolder.hairLine.setAlpha(value);

                            itemHolder.alarmItem.requestLayout();
                        }
                    });
                    animator.setInterpolator(mCollapseInterpolator);
                    // Set everything to their final values when the animation's done.
                    animator.addListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            // Set it back to wrap content since we'd explicitly set the height.
                            itemHolder.alarmItem.getLayoutParams().height =
                                    LayoutParams.WRAP_CONTENT;

                            FrameLayout.LayoutParams expandParams = (FrameLayout.LayoutParams)
                                    itemHolder.expandArea.getLayoutParams();
                            expandParams.setMargins(0, 0, 0, mCollapseExpandHeight);

                            itemHolder.expandArea.setVisibility(View.GONE);
                            itemHolder.arrow.setRotation(0);
                        }
                    });
                    animator.start();

                    return false;
                }
            });
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        private View getViewById(long id) {
            for (int i = 0; i < mList.getCount(); i++) {
                View v = mList.getChildAt(i);
                if (v != null) {
                    ItemHolder h = (ItemHolder)(v.getTag());
                    if (h != null && h.alarm.id == id) {
                        return v;
                    }
                }
            }
            return null;
        }

        public long getExpandedId() {
            return mExpandedId;
        }

        public long[] getSelectedAlarmsArray() {
            int index = 0;
            long[] ids = new long[mSelectedAlarms.size()];
            for (long id : mSelectedAlarms) {
                ids[index] = id;
                index++;
            }
            return ids;
        }

        public long[] getRepeatArray() {
            int index = 0;
            long[] ids = new long[mRepeatChecked.size()];
            for (long id : mRepeatChecked) {
                ids[index] = id;
                index++;
            }
            return ids;
        }

        public Bundle getPreviousDaysOfWeekMap() {
            return mPreviousDaysOfWeekMap;
        }

        private void buildHashSetFromArray(long[] ids, HashSet<Long> set) {
            for (long id : ids) {
                set.add(id);
            }
        }
    }

    private void startCreatingAlarm() {
        // Set the "selected" alarm as null, and we'll create the new one when the timepicker
        // comes back.
        mSelectedAlarm = null;
        AlarmUtils.showTimeEditDialog(this, null);
    }

    private static AlarmInstance setupAlarmInstance(Context context, Alarm alarm) {
        ContentResolver cr = context.getContentResolver();
        AlarmInstance newInstance = alarm.createInstanceAfter(Calendar.getInstance());
        newInstance = AlarmInstance.addInstance(cr, newInstance);
        // Register instance to state manager
        AlarmStateManager.registerInstance(context, newInstance, true);
        return newInstance;
    }

    private void asyncDeleteAlarm(final Alarm alarm) {
        final Context context = AlarmClockFragment.this.getActivity().getApplicationContext();
        final AsyncTask<Void, Void, Void> deleteTask = new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... parameters) {
                // Activity may be closed at this point , make sure data is still valid
                if (context != null && alarm != null) {
                    // Release the alarm ringtone uri
                    releaseRingtoneUri(alarm.alert);

                    ContentResolver cr = context.getContentResolver();
                    AlarmStateManager.deleteAllInstances(context, alarm.id);
                    Alarm.deleteAlarm(cr, alarm.id);
                    sDeskClockExtensions.deleteAlarm(
                            AlarmClockFragment.this.getActivity().getApplicationContext(), alarm.id);
                }
                return null;
            }
        };
        mUndoShowing = true;
        deleteTask.execute();
    }

    private void asyncAddAlarm(final Alarm alarm) {
        final Context context = AlarmClockFragment.this.getActivity().getApplicationContext();
        final AsyncTask<Void, Void, AlarmInstance> updateTask =
                new AsyncTask<Void, Void, AlarmInstance>() {
                    @Override
                    protected AlarmInstance doInBackground(Void... parameters) {
                        if (context != null && alarm != null) {
                            ContentResolver cr = context.getContentResolver();

                            // Add alarm to db
                            Alarm newAlarm = Alarm.addAlarm(cr, alarm);
                            mScrollToAlarmId = newAlarm.id;

                            // Create and add instance to db
                            if (newAlarm.enabled) {
                                sDeskClockExtensions.addAlarm(
                                        AlarmClockFragment.this.getActivity().getApplicationContext(),
                                        newAlarm);
                                return setupAlarmInstance(context, newAlarm);
                            }
                        }
                        return null;
                    }

                    @Override
                    protected void onPostExecute(AlarmInstance instance) {
                        if (instance != null) {
                            AlarmUtils.popAlarmSetToast(context, instance.getAlarmTime().getTimeInMillis());
                        }
                    }
                };
        updateTask.execute();
    }

    private void asyncUpdateAlarm(final Alarm alarm, final boolean popToast) {
        System.out.println("RAJ - asyncUpdateAlarm: " + Log.getStackTraceString(new Exception()));
        final Context context = AlarmClockFragment.this.getActivity().getApplicationContext();
        final AsyncTask<Void, Void, AlarmInstance> updateTask =
                new AsyncTask<Void, Void, AlarmInstance>() {
                    @Override
                    protected AlarmInstance doInBackground(Void ... parameters) {
                        ContentResolver cr = context.getContentResolver();

                        // Dismiss all old instances
                        AlarmStateManager.deleteAllInstances(context, alarm.id);
                        // Register/Update the ringtone uri
                        if (alarm.alert != null) {
                            try {
                                getActivity().getContentResolver().takePersistableUriPermission(
                                        alarm.alert, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (SecurityException ex) {
                                // Ignore
                            }
                        }

                        // Dismiss all old instances
                        AlarmStateManager.deleteAllInstances(context, alarm.id);

                        // Update alarm
                        Alarm.updateAlarm(cr, alarm);
                        if (alarm.enabled) {
                            return setupAlarmInstance(context, alarm);
                        }

                        return null;
                    }

                    @Override
                    protected void onPostExecute(AlarmInstance instance) {
                        if (popToast && instance != null) {
                            AlarmUtils.popAlarmSetToast(context, instance.getAlarmTime().getTimeInMillis());
                        }
                    }
                };
        updateTask.execute();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        hideUndoBar(true, event);
        return false;
    }

    @Override
    public void onFabClick(View view){
        hideUndoBar(true, null);
        startCreatingAlarm();
    }

    @Override
    public void setFabAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mFab == null || activity.getSelectedTab() != DeskClock.ALARM_TAB_INDEX) {
            return;
        }
        mFab.setVisibility(View.VISIBLE);
        mFab.setImageResource(R.drawable.ic_fab_plus);
        mFab.setContentDescription(getString(R.string.button_alarms));
    }

    @Override
    public void setLeftRightButtonAppearance() {
        final DeskClock activity = (DeskClock) getActivity();
        if (mLeftButton == null || mRightButton == null ||
                activity.getSelectedTab() != DeskClock.ALARM_TAB_INDEX) {
            return;
        }
        mLeftButton.setVisibility(View.INVISIBLE);
        mRightButton.setVisibility(View.INVISIBLE);
    }
}
