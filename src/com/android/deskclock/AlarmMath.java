package com.android.deskclock;

import java.util.Random;

import com.android.deskclock.Alarms;
import com.android.deskclock.Log;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.widget.Button;
import android.widget.TextView;

public class AlarmMath extends Activity {

	private static final int NUM_CHARS = 14; 

	protected Alarm mAlarm;
	private String userMath = "";
	private String strMath = "";
	private int intMath = 0;
	
	private TextView text;
	private Button math_done;

	@Override
	protected void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.alarm_math);
		mAlarm = getIntent().getParcelableExtra(Alarms.ALARM_INTENT_EXTRA);
		text = (TextView) findViewById(R.id.math_text);
		calculateMath();

		math_done = (Button) findViewById(R.id.math_done);
		math_done.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (userMath.length() != 0)
					if (Long.parseLong(userMath) != intMath)
						userMath = "";
				calculateMath();
				updateText();
			}
		});
		updateText();
		Button button = (Button) findViewById(R.id.math_1);
		button.setText("1");
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("1");
				updateText();
			}
		});
		button = (Button) findViewById(R.id.math_2);
		button.setText("2");
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("2");
				updateText();
			}
		});
		button = (Button) findViewById(R.id.math_3);
		button.setText("3");
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("3");
				updateText();
			}
		});
		button = (Button) findViewById(R.id.math_4);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("4");
				updateText();
			}
		});
		button.setText("4");
		button = (Button) findViewById(R.id.math_5);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("5");
				updateText();
			}
		});
		button.setText("5");
		button = (Button) findViewById(R.id.math_6);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("6");
				updateText();
			}
		});
		button.setText("6");
		button = (Button) findViewById(R.id.math_7);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("7");
				updateText();
			}
		});
		button.setText("7");
		button = (Button) findViewById(R.id.math_8);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("8");
				updateText();
			}
		});
		button.setText("8");
		button = (Button) findViewById(R.id.math_9);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("9");
				updateText();
			}
		});
		button.setText("9");
		button = (Button) findViewById(R.id.math_0);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				addString("0");
				updateText();
			}
		});
		button.setText("0");
		button = (Button) findViewById(R.id.math_reset);
		button.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				if (userMath.length() > 0) {
					userMath = userMath.substring(0, userMath.length() - 1);
					updateText();
				}
			}
		});
		button.setOnLongClickListener(new OnLongClickListener() {
			@Override
			public boolean onLongClick(View v) {
				userMath = "";
				updateText();
				return false;
			}
		});
		button.setText("\u2190");
	}

	private void dismiss(boolean killed) {
		Log.i(killed ? "Alarm killed" : "Alarm dismissed by user");
		if (!killed & mAlarm != null) {
			NotificationManager nm = getNotificationManager();
			nm.cancel(mAlarm.id);
			stopService(new Intent(Alarms.ALARM_ALERT_ACTION));
		}
		finish();
	}

	private NotificationManager getNotificationManager() {
		return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
	}

	private int getRandom(int start, int end) {
		return new Random().nextInt(end - start + 1) + start;
	}

	private void calculateMath() {
		int num = getRandom(1, 7);
		switch (num) {
		case (1): { // A*B
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			strMath = a + "*" + b;
			intMath = a * b;
			break;
		}
		case (2): { // A*B+C
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			int c = getRandom(1, 9);
			strMath = a + "*" + b + "+" + c;
			intMath = a * b + c;
			break;
		}
		case (3): { // A^2
			int a = getRandom(0, 9);
			strMath = a + "\u00B2";
			intMath = a * a;
		}
		case (4): { // A*B+CC
			int a = getRandom(0, 9);
			int b = getRandom(1, 9);
			int c = getRandom(10, 99);
			strMath = a + "*" + b + "+" + c;
			intMath = a * b + c;
			break;
		}
		case (5): { // AA+BB
			int a = getRandom(10, 99);
			int b = getRandom(10, 99);
			strMath = a + "+" + b;
			intMath = a + b;
			break;
		}
		case (6): { // AA*B
			int a = getRandom(10, 99);
			int b = getRandom(0, 9);
			strMath = a + "*" + b;
			intMath = a + b;
			break;
		}
		case (7): { // A^2+B
			int a = getRandom(0, 9);
			int b = getRandom(10, 99);
			strMath = a + "\u00B2+" + b;
			intMath = a * a + b;
		}
		}

	}
	
	private void addString(String str){
		if (userMath.length()+strMath.length()<=NUM_CHARS)
			userMath += str;		
	}

	private void updateText() {
		if (userMath.length() != 0) {
			math_done.setText("OK");
			if (Long.parseLong(userMath) == intMath)
				dismiss(false);
		} else {
			math_done.setText("NEXT");
		}
		text.setText(strMath + "=" + userMath);
	}
}