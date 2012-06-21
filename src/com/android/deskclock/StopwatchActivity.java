package com.android.deskclock;

import java.util.Vector;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class StopwatchActivity extends Activity {

	private static final int MSG_START_TIMER = 0;
	private static final int MSG_CLEAR_TIMER = 1;
	private static final int MSG_PAUSE_TIMER = 3;
	private static final int MSG_UPDATE_TIMER = 2;

	private static final int REFRESH_RATE = 3;

	private Vector<String> mPrimaryLine = new Vector<String>();
	private Vector<String> mSecondaryLine = new Vector<String>();
	private boolean mRunning, mSaveDate = false;
	private TextView time1, time2;
	private Button start, reset;
	private ListView mListView;
	private Activity mContext;

	private long mTime, mTimeLong = 0;
	private static long mStartTime = 0;
	private static long mPauseTime = 0;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case MSG_START_TIMER:
				StartStopwatch();
				mRunning = true;
				mHandler.sendEmptyMessage(MSG_UPDATE_TIMER);
				break;

			case MSG_UPDATE_TIMER:
				mTime = TimeStopwatch();
				time1.setText(getTimeStr(mTime));
				time2.setText(" " + addZeroLong(mTime));
				mHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIMER, REFRESH_RATE);
				break;

			case MSG_CLEAR_TIMER:
				mHandler.removeMessages(MSG_UPDATE_TIMER);
				time1.setText("00:00:00");
				time2.setText(" 000");
				ResetStopwatch();
				break;

			case MSG_PAUSE_TIMER:
				mHandler.removeMessages(MSG_UPDATE_TIMER);
				mRunning = false;
				PauseStopwatch();
				break;

			default:
				break;
			}
		}
	};

	private String getTimeStr(long time) {
		long s = (time - time % 1000) / 1000;
		long m = (s - s % 60) / 60;
		long h = (m - m % 60) / 60;
		return addZero(h) + ":" + addZero(m % 60) + ":" + addZero(s % 60);
	}

	private String addZero(long x) {
		return String.format("%02d", x);
	}

	private String addZeroLong(long x) {
		return String.format("%03d", x % 1000);
	}

	private String getTimeStrAdapter(long x) {
		return getTimeStr(x) + ":" + addZeroLong(x);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stopwatch);
		mContext = this;

		start = (Button) findViewById(R.id.start);
		reset = (Button) findViewById(R.id.clear);
		mListView = (ListView) findViewById(R.id.list);
		time1 = (TextView) findViewById(R.id.time1);
		time2 = (TextView) findViewById(R.id.time2);
		time1.setText("00:00:00");
		time2.setText(" 000");
		loadSavedData();
		if (mRunning)
			setButtonsRunningText();
		else
			setButtonsPausedText();

		time1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mSaveDate = true;
				if (mRunning) {
					setButtonsPausedText();
					mHandler.sendEmptyMessage(MSG_PAUSE_TIMER);
				} else {
					setButtonsRunningText();
					mHandler.sendEmptyMessage(MSG_START_TIMER);
				}
			}
		});
		start.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mSaveDate = true;
				if (mRunning) {
					setButtonsPausedText();
					mHandler.sendEmptyMessage(MSG_PAUSE_TIMER);
				} else {
					setButtonsRunningText();
					mHandler.sendEmptyMessage(MSG_START_TIMER);
				}
			}
		});
		reset.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				mSaveDate = true;
				if (!mRunning) {
					mHandler.sendEmptyMessage(MSG_CLEAR_TIMER);
					setAdapter(true);
				} else {
					mHandler.sendEmptyMessage(MSG_START_TIMER);
					mPrimaryLine.add(new String(getTimeStrAdapter(mTime)));
					mSecondaryLine.add(new String(
							getTimeStrAdapter(mTimeLong += mTime)));
					setAdapter(false);
				}
			}
		});
	}

	private void setButtonsRunningText() {
		start.setText(getString(R.string.stopwatch_pause));
		reset.setText(getString(R.string.stopwatch_lap));
	}

	private void setButtonsPausedText() {
		start.setText(getString(R.string.stopwatch_start));
		reset.setText(getString(R.string.stopwatch_reset));
	}

	private void setAdapter(boolean reset) {
		if (!reset) {
			String a[] = {};
			String b[] = {};
			mListView.setAdapter(new StopwatchAdapter(mContext, mPrimaryLine
					.toArray(a), mSecondaryLine.toArray(b)));
		} else {
			mPrimaryLine = new Vector<String>();
			mSecondaryLine = new Vector<String>();
			mTimeLong = 0;
			mListView.setAdapter(null);
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (mSaveDate) {
			String a[] = {};
			a = mPrimaryLine.toArray(a);
			String b[] = {};
			b = mSecondaryLine.toArray(b);
			SharedPreferences mSavedData = getSharedPreferences(
					"mStopwatchData", MODE_PRIVATE);
			mSavedData.edit().clear();
			mSavedData.edit().putLong("mStartTime", mStartTime).apply();
			mSavedData.edit().putLong("mPauseTime", mPauseTime).apply();
			mSavedData.edit().putInt("mLapLength", a.length).apply();
			for (int i = 0; i < a.length; i++) {
				mSavedData.edit().putString("mPLine=" + i, a[i]).apply();
				mSavedData.edit().putString("mSLine=" + i, b[i]).apply();
			}
		}
	}

	private void loadSavedData() {
		SharedPreferences mSavedData = getSharedPreferences("mStopwatchData",
				MODE_PRIVATE);
		if (mSavedData != null) {
			mStartTime = mSavedData.getLong("mStartTime", 0);
			mPauseTime = mSavedData.getLong("mPauseTime", 0);
			int length = mSavedData.getInt("mLapLength", 0);
			for (int i = 0; i < length; i++) {
				mPrimaryLine.add(new String(mSavedData.getString("mPLine=" + i,
						"Error data loading...")));
				mSecondaryLine.add(new String(mSavedData.getString("mSLine="
						+ i, "Error data loading...")));
			}
			if (length > 0)
				setAdapter(false);
			if (mStartTime != 0 && mPauseTime == 0) {
				mRunning = true;
				mSaveDate = true;
				mHandler.sendEmptyMessage(MSG_UPDATE_TIMER);
			} else {
				if (mPauseTime == -1) {
					time1.setText("00:00:00");
					time2.setText(" 000");
				} else {
					mStartTime = System.currentTimeMillis()
							- (mPauseTime == -1 ? 0 : mPauseTime);
					time1.setText(getTimeStr(TimeStopwatch()));
					time2.setText(" " + addZeroLong(TimeStopwatch()));
				}
			}
		}

	}

	public void StartStopwatch() {
		mStartTime = System.currentTimeMillis()
				- (mPauseTime == -1 ? 0 : mPauseTime);
		mPauseTime = 0;
	}

	public void PauseStopwatch() {
		if (mPauseTime == 0) {
			mPauseTime = TimeStopwatch();
			mStartTime = 0;
		}
	}

	public void ResetStopwatch() {
		mPauseTime = -1;
	}

	public long TimeStopwatch() {
		return System.currentTimeMillis() - mStartTime;
	}

	public class StopwatchAdapter extends ArrayAdapter<String> {
		private final Activity context;
		private final String[] line1;
		private final String[] line2;
		private final int length;

		public StopwatchAdapter(Activity context, String[] line1, String[] line2) {
			super(context, R.layout.stopwatch_adapter, line1);
			this.context = context;
			this.line1 = line1;
			this.line2 = line2;
			this.length = line1.length;
		}

		class ViewHolder {
			public TextView numView;
			public TextView line1View;
			public TextView line2View;
			public LinearLayout textView;
		}

		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			ViewHolder holder;
			View rowView = convertView;
			if (rowView == null) {
				LayoutInflater inflater = context.getLayoutInflater();
				rowView = inflater.inflate(R.layout.stopwatch_adapter, null,
						true);
				holder = new ViewHolder();
				holder.numView = (TextView) rowView.findViewById(R.id.number);
				holder.textView = (LinearLayout) rowView
						.findViewById(R.id.text);
				holder.line1View = (TextView) holder.textView
						.findViewById(R.id.line1);
				holder.line2View = (TextView) holder.textView
						.findViewById(R.id.line2);
				rowView.setTag(holder);
			} else {
				holder = (ViewHolder) rowView.getTag();
			}

			holder.numView.setText("" + (this.length - position));
			holder.line1View.setText(this.line1[this.length - position - 1]);
			holder.line2View.setText(this.line2[this.length - position - 1]);
			rowView.setEnabled(false);
			return rowView;
		}
	}
}