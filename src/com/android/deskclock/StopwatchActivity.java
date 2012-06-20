package com.android.deskclock;

import java.util.Vector;

import android.app.Activity;
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
	private static final int MSG_UPDATE_TIMER = 2;
	private static final int MSG_PAUSE_TIMER = 3;
	private static final int MSG_HIDE_TIMER = 4;

	private static final int REFRESH_RATE = 1;

	private Vector<String> line1 = new Vector<String>();
	private Vector<String> line2 = new Vector<String>();
	private StopWatch timer = new StopWatch();
	private boolean running = false;
	private TextView time1, time2;
	private ListView mListView;
	private long mTimeLong = 0;
	private Activity mContext;
	private long mTime = 0;

	Handler mHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
			case MSG_START_TIMER:
				timer.start();
				running = true;
				mHandler.sendEmptyMessage(MSG_UPDATE_TIMER);
				break;

			case MSG_UPDATE_TIMER:
				mTime = timer.getElapsedTime();
				time1.setText(getTimeStr(mTime));
				time2.setText(" " + addZeroLong(mTime));
				mHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIMER, REFRESH_RATE);
				break;

			case MSG_CLEAR_TIMER:
				mHandler.removeMessages(MSG_UPDATE_TIMER);
				time1.setText("00:00:00");
				time2.setText(" 000");
				timer.clear();
				break;

			case MSG_PAUSE_TIMER:
				mHandler.removeMessages(MSG_UPDATE_TIMER);
				running = false;
				timer.pause();
				break;

			case MSG_HIDE_TIMER:
				mHandler.removeMessages(MSG_UPDATE_TIMER);
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
		return x > 9 ? x + "" : "0" + x;
	}

	private String addZeroLong(long x) {
		x = x % 1000;
		if (x > 99)
			return "" + x;
		else if (x > 9)
			return "0" + x;
		else
			return "00" + x;

	}

	private String getTimeStrAdapter(long x) {
		return getTimeStr(x) + ":" + addZeroLong(x);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.stopwatch);
		mContext = this;

		final Button start = (Button) findViewById(R.id.start);
		final Button clear = (Button) findViewById(R.id.clear);
		mListView = (ListView) findViewById(R.id.list);
		time1 = (TextView) findViewById(R.id.time1);
		time2 = (TextView) findViewById(R.id.time2);
		time1.setText("00:00:00");
		time2.setText(" 000");
		start.setText(getString(R.string.stopwatch_start));
		clear.setText(getString(R.string.stopwatch_reset));

		time1.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!running) {
					mHandler.sendEmptyMessage(MSG_START_TIMER);
					start.setText(getString(R.string.stopwatch_pause));
					clear.setText(getString(R.string.stopwatch_lap));
				} else {
					mHandler.sendEmptyMessage(MSG_PAUSE_TIMER);
					start.setText(getString(R.string.stopwatch_start));
					clear.setText(getString(R.string.stopwatch_reset));
				}
			}
		});
		start.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!running) {
					mHandler.sendEmptyMessage(MSG_START_TIMER);
					start.setText(getString(R.string.stopwatch_pause));
					clear.setText(getString(R.string.stopwatch_lap));
				} else {
					mHandler.sendEmptyMessage(MSG_PAUSE_TIMER);
					start.setText(getString(R.string.stopwatch_start));
					clear.setText(getString(R.string.stopwatch_reset));
				}
			}
		});
		clear.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (!running) {
					mHandler.sendEmptyMessage(MSG_CLEAR_TIMER);
					line1 = new Vector<String>();
					line2 = new Vector<String>();
					mTimeLong = 0;
					setAdapter(range = 0);
				} else {
					mHandler.sendEmptyMessage(MSG_START_TIMER);
					line1.add(new String(getTimeStrAdapter(mTime)));
					line2.add(new String(getTimeStrAdapter(mTimeLong += mTime)));
					setAdapter(range += 1);
				}
			}
		});

	}

	private int range = 0;

	private void setAdapter(int x) {
		if (x > 0) {
			String a[] = {};
			String b[] = {};
			mListView.setAdapter(new StopwatchAdapter(mContext, line1
					.toArray(a), line2.toArray(b)));
		} else {
			mListView.setAdapter(null);
		}
	}

	protected void onResume() {
		super.onResume();
		long start = Long.parseLong(getSharedPreferences("mStartTime", MODE_PRIVATE).getString(
				"mStartTime", "#00000000"));
		if (start!=-1){
			StopWatch.init(start);
			mHandler.sendEmptyMessage(MSG_UPDATE_TIMER);			
		}
	}

	protected void onPause() {
		super.onPause();
		if (running) {
			mHandler.sendEmptyMessage(MSG_HIDE_TIMER);
			getSharedPreferences("mStartTime", MODE_PRIVATE).edit()
					.putString("mStartTime", StopWatch.getStartTime()).commit();
		} else {
			getSharedPreferences("mStartTime", MODE_PRIVATE).edit()
					.putString("mStartTime", "-1").commit();
		}
	}

	public static class StopWatch {
		private static long startTime = 0;
		private long pauseTime = 0;

		public void start() {
			StopWatch.startTime = System.currentTimeMillis()
					- (this.pauseTime == -1 ? 0 : this.pauseTime);
			this.pauseTime = 0;
		}

		public void pause() {
			if (this.pauseTime == 0)
				this.pauseTime = System.currentTimeMillis() - startTime;
		}

		public static void init(long time) {
			startTime = time;
		}

		static public String getStartTime() {
			return "" + startTime;
		}

		public void clear() {
			this.pauseTime = -1;
		}

		public long getElapsedTime() {
			return System.currentTimeMillis() - startTime;
		}
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