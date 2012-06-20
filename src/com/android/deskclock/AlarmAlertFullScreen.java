/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.Toast;

import java.util.Calendar;

/**
 * Alarm Clock alarm alert: pops visible indicator and plays alarm tone. This
 * activity is the full screen version which shows over the lock screen with the
 * wallpaper as the background.
 */
public class AlarmAlertFullScreen extends Activity {

	// These defaults must match the values in res/xml/settings.xml
	private static final String DEFAULT_SNOOZE = "10";
	private static final String DEFAULT_VOLUME_BEHAVIOR = "2";
	private static final String DEFAULT_FLIP_ACTION = "0";
	private static final String DEFAULT_SHAKE_ACTION = "1";
	private static final boolean DEFAULT_MATH_VALUE = false;
	protected static final String SCREEN_OFF = "screen_off";

	protected Alarm mAlarm;
	private int mVolumeBehavior;
	boolean mFullscreenStyle;
	private boolean mMathModule;
	private int mFlipAction;
	private int mShakeAction;
	SensorEventListener mOrientationListener;
	SensorEventListener mShakeListener;

	// Receives the ALARM_KILLED action from the AlarmKlaxon,
	// and also ALARM_SNOOZE_ACTION / ALARM_DISMISS_ACTION from other
	// applications
	private BroadcastReceiver mReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (action.equals(Alarms.ALARM_SNOOZE_ACTION)) {
				snooze();
			} else if (action.equals(Alarms.ALARM_DISMISS_ACTION)) {
				dismiss(false);
			} else {
				Alarm alarm = intent
						.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
				if (alarm != null && mAlarm.id == alarm.id) {
					dismiss(true);
				}
			}
		}
	};

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		mAlarm = getIntent().getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

		// Get the volume/camera button behavior setting
		final String vol = PreferenceManager.getDefaultSharedPreferences(this)
				.getString(SettingsActivity.KEY_VOLUME_BEHAVIOR,
						DEFAULT_VOLUME_BEHAVIOR);
		mVolumeBehavior = Integer.parseInt(vol);

		final String flipAction = PreferenceManager
				.getDefaultSharedPreferences(this).getString(
						SettingsActivity.KEY_FLIP_ACTION, DEFAULT_FLIP_ACTION);
		Log.v("flipaction = " + flipAction);
		mFlipAction = Integer.parseInt(flipAction);

		final String shakeAction = PreferenceManager
				.getDefaultSharedPreferences(this)
				.getString(SettingsActivity.KEY_SHAKE_ACTION,
						DEFAULT_SHAKE_ACTION);
		mShakeAction = Integer.parseInt(shakeAction);
		
		mMathModule = PreferenceManager
		.getDefaultSharedPreferences(this)
				.getBoolean(SettingsActivity.KEY_MATH_MODULE,
						DEFAULT_MATH_VALUE);

		final Window win = getWindow();
		win.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
		// Turn on the screen unless we are being launched from the AlarmAlert
		// subclass as a result of the screen turning off.
		if (!getIntent().getBooleanExtra(SCREEN_OFF, false)) {
			win.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
					| WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
					| WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
		}

		updateLayout();

		// Register to get the alarm killed/snooze/dismiss intent.
		IntentFilter filter = new IntentFilter(Alarms.ALARM_KILLED);
		filter.addAction(Alarms.ALARM_SNOOZE_ACTION);
		filter.addAction(Alarms.ALARM_DISMISS_ACTION);
		registerReceiver(mReceiver, filter);
	}

	private void setTitle() {
		final String titleText = mAlarm.getLabelOrDefault(this);

		setTitle(titleText);
	}

	protected int getLayoutResId() {
		return R.layout.alarm_alert_fullscreen;
	}

	private void updateLayout() {
		LayoutInflater inflater = LayoutInflater.from(this);

		setContentView(inflater.inflate(getLayoutResId(), null));

		/*
		 * snooze behavior: pop a snooze confirmation view, kick alarm manager.
		 */
		Button snooze = (Button) findViewById(R.id.snooze);
		snooze.requestFocus();
		snooze.setOnClickListener(new Button.OnClickListener() {
			public void onClick(View v) {
				snooze();
			}
		});

		/* dismiss button: close notification */
		findViewById(R.id.dismiss).setOnClickListener(
				new Button.OnClickListener() {
					public void onClick(View v) {
						dismiss(false);
					}
				});

		/* Set the title from the passed in alarm */
		setTitle();
	}

	// Attempt to snooze this alert.
	private void snooze() {
		// Do not snooze if the snooze button is disabled.
		if (!findViewById(R.id.snooze).isEnabled()) {
			dismiss(false);
			return;
		}
		final String snooze = PreferenceManager.getDefaultSharedPreferences(
				this).getString(SettingsActivity.KEY_ALARM_SNOOZE,
				DEFAULT_SNOOZE);
		int snoozeMinutes = Integer.parseInt(snooze);

		final long snoozeTime = System.currentTimeMillis()
				+ (1000 * 60 * snoozeMinutes);
		Alarms.saveSnoozeAlert(AlarmAlertFullScreen.this, mAlarm.id, snoozeTime);

		// Get the display time for the snooze and update the notification.
		final Calendar c = Calendar.getInstance();
		c.setTimeInMillis(snoozeTime);

		// Append (snoozed) to the label.
		String label = mAlarm.getLabelOrDefault(this);
		label = getString(R.string.alarm_notify_snooze_label, label);

		// Notify the user that the alarm has been snoozed.
		Intent cancelSnooze = new Intent(this, AlarmReceiver.class);
		cancelSnooze.setAction(Alarms.CANCEL_SNOOZE);
		cancelSnooze.putExtra(Alarms.ALARM_INTENT_EXTRA, mAlarm);
		PendingIntent broadcast = PendingIntent.getBroadcast(this, mAlarm.id,
				cancelSnooze, 0);
		NotificationManager nm = getNotificationManager();
		Notification n = new Notification(R.drawable.stat_notify_alarm, label,
				0);
		n.setLatestEventInfo(
				this,
				label,
				getString(R.string.alarm_notify_snooze_text,
						Alarms.formatTime(this, c)), broadcast);
		n.flags |= Notification.FLAG_AUTO_CANCEL
				| Notification.FLAG_ONGOING_EVENT;
		nm.notify(mAlarm.id, n);

		String displayTime = getString(R.string.alarm_alert_snooze_set,
				snoozeMinutes);
		// Intentionally log the snooze time for debugging.
		Log.v(displayTime);

		// Display the snooze minutes in a toast.
		Toast.makeText(AlarmAlertFullScreen.this, displayTime,
				Toast.LENGTH_LONG).show();
		stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
		finish();
	}

	private NotificationManager getNotificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private SensorManager getSensorManager() {
		return (SensorManager) getSystemService(Context.SENSOR_SERVICE);
	}

	// Dismiss the alarm.
	private void dismiss(boolean killed) {
		Log.i(killed ? "Alarm killed" : "Alarm dismissed by user");
		// The service told us that the alarm has been killed, do not modify
		// the notification or stop the service.
		if (!mMathModule) {
			if (!killed) {
				// Cancel the notification and stop playing the alarm
				NotificationManager nm = getNotificationManager();
				nm.cancel(mAlarm.id);
				stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
			}
		} else {
			Intent i = new Intent(this, AlarmMath.class);
			i.putExtra(Alarms.ALARM_INTENT_EXTRA, mAlarm);
			i.putExtra(SCREEN_OFF, true);
			startActivity(i);
		}
		finish();
	}

	private void attachOrientationListener() {
		if (mFlipAction != 0) {
			mOrientationListener = new SensorEventListener() {
				private static final int FACE_UP_LOWER_LIMIT = -45;
				private static final int FACE_UP_UPPER_LIMIT = 45;
				private static final int FACE_DOWN_UPPER_LIMIT = 135;
				private static final int FACE_DOWN_LOWER_LIMIT = -135;
				private static final int TILT_UPPER_LIMIT = 45;
				private static final int TILT_LOWER_LIMIT = -45;
				private static final int SENSOR_SAMPLES = 3;

				private boolean mWasFaceUp;
				private boolean[] mSamples = new boolean[SENSOR_SAMPLES];
				private int mSampleIndex;

				@Override
				public void onAccuracyChanged(Sensor sensor, int acc) {
				}

				@Override
				public void onSensorChanged(SensorEvent event) {
					// Add a sample overwriting the oldest one. Several samples
					// are used
					// to avoid the erroneous values the sensor sometimes
					// returns.
					float y = event.values[1];
					float z = event.values[2];

					if (!mWasFaceUp) {
						// Check if its face up enough.
						mSamples[mSampleIndex] = y > FACE_UP_LOWER_LIMIT
								&& y < FACE_UP_UPPER_LIMIT
								&& z > TILT_LOWER_LIMIT && z < TILT_UPPER_LIMIT;

						// The device first needs to be face up.
						boolean faceUp = true;
						for (boolean sample : mSamples) {
							faceUp = faceUp && sample;
						}
						if (faceUp) {
							mWasFaceUp = true;
							for (int i = 0; i < SENSOR_SAMPLES; i++)
								mSamples[i] = false;
						}
					} else {
						// Check if its face down enough. Note that wanted
						// values go from FACE_DOWN_UPPER_LIMIT to 180
						// and from -180 to FACE_DOWN_LOWER_LIMIT
						mSamples[mSampleIndex] = (y > FACE_DOWN_UPPER_LIMIT || y < FACE_DOWN_LOWER_LIMIT)
								&& z > TILT_LOWER_LIMIT && z < TILT_UPPER_LIMIT;

						boolean faceDown = true;
						for (boolean sample : mSamples) {
							faceDown = faceDown && sample;
						}
						if (faceDown) {
							switch (mFlipAction) {
							case 1:
								snooze();
								break;

							case 2:
								dismiss(false);
								break;

							default:
								break;
							}
						}
					}

					mSampleIndex = ((mSampleIndex + 1) % SENSOR_SAMPLES);
				}
			};

			// Register the sensor listener and start to get values
			getSensorManager().registerListener(
					mOrientationListener,
					getSensorManager()
							.getDefaultSensor(Sensor.TYPE_ORIENTATION),
					SensorManager.SENSOR_DELAY_NORMAL);
		}
	}

	private void attachShakeListener() {
		if (mShakeAction != 0) {
			mShakeListener = new SensorEventListener() {
				private static final float SENSITIVITY = 16;
				private static final int BUFFER = 5;
				private float[] gravity = new float[3];
				private float average = 0;
				private int i = 0;

				@Override
				public void onAccuracyChanged(Sensor sensor, int acc) {
				}

				public void onSensorChanged(SensorEvent event) {

					final float alpha = (float) 0.8;

					float x = event.values[0]
							- (gravity[0] = alpha * gravity[0] + (1 - alpha)
									* event.values[0]);
					float y = event.values[1]
							- (gravity[1] = alpha * gravity[1] + (1 - alpha)
									* event.values[1]);
					float z = event.values[2]
							- (gravity[2] = alpha * gravity[2] + (1 - alpha)
									* event.values[2]);
					if (i <= BUFFER) {
						average += Math.abs(x) + Math.abs(y) + Math.abs(z);
						i += 1;
					} else {
						if (average / BUFFER >= SENSITIVITY)
							switch (mShakeAction) {
							case 1:
								snooze();
								break;

							case 2:
								dismiss(false);
								break;

							default:
								break;
							}
						;
						average = 0;
						i = 0;
					}
				}
			};
			getSensorManager().registerListener(
					mShakeListener,
					getSensorManager().getDefaultSensor(
							Sensor.TYPE_ACCELEROMETER),
					SensorManager.SENSOR_DELAY_GAME);
		}
	}

	private void detachListeners() {
		if (mOrientationListener != null) {
			getSensorManager().unregisterListener(mOrientationListener);
			mOrientationListener = null;
		}
		if (mShakeListener != null) {
			getSensorManager().unregisterListener(mShakeListener);
			mShakeListener = null;
		}
	}

	/**
	 * this is called when a second alarm is triggered while a previous alert
	 * window is still active.
	 */
	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		if (Log.LOGV)
			Log.v("AlarmAlert.OnNewIntent()");

		mAlarm = intent.getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);

		setTitle();

		detachListeners();
	}

	@Override
	protected void onResume() {
		super.onResume();
		// If the alarm was deleted at some point, disable snooze.
		if (Alarms.getAlarm(getContentResolver(), mAlarm.id) == null) {
			Button snooze = (Button) findViewById(R.id.snooze);
			snooze.setEnabled(false);
		}

		attachOrientationListener();
		attachShakeListener();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (Log.LOGV)
			Log.v("AlarmAlert.onDestroy()");
		// No longer care about the alarm being killed.
		unregisterReceiver(mReceiver);
	}

	@Override
	public void onPause() {
		super.onPause();
		detachListeners();
	}

	@Override
	public boolean dispatchKeyEvent(KeyEvent event) {
		// Do this on key down to handle a few of the system keys.
		boolean up = event.getAction() == KeyEvent.ACTION_UP;
		switch (event.getKeyCode()) {
		// Volume keys and camera keys dismiss the alarm
		case KeyEvent.KEYCODE_VOLUME_UP:
		case KeyEvent.KEYCODE_VOLUME_DOWN:
		case KeyEvent.KEYCODE_VOLUME_MUTE:
		case KeyEvent.KEYCODE_CAMERA:
		case KeyEvent.KEYCODE_FOCUS:
			if (up) {
				switch (mVolumeBehavior) {
				case 1:
					snooze();
					break;

				case 2:
					dismiss(false);
					break;

				default:
					break;
				}
			}
			return true;
		default:
			break;
		}
		return super.dispatchKeyEvent(event);
	}

	@Override
	public void onBackPressed() {
		// Don't allow back to dismiss. This method is overriden by AlarmAlert
		// so that the dialog is dismissed.
		return;
	}
}
