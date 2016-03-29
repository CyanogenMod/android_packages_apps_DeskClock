package com.android.alarmmusic;

import android.content.ComponentName;
import android.os.RemoteException;
import android.util.Log;
import com.cyanogen.ambient.alarmmusic.AlarmMusicResult;
import com.cyanogen.ambient.alarmmusic.AlarmMusicRequest;
import com.cyanogen.ambient.alarmmusic.IAlarmMusicListener;

public class AlarmMusicListenerImpl extends IAlarmMusicListener.Stub {
    private static final String TAG = "AlarmMusicListener";
    private ComponentName mComponentName;

    public static AlarmMusicListenerImpl getInstance(ComponentName componentName) {
        return new AlarmMusicListenerImpl(componentName);
    }

    private AlarmMusicListenerImpl(ComponentName cn) {
        mComponentName = cn;
    }

    public String mUri;

    @Override
    public void done(AlarmMusicResult result)
            throws RemoteException {
        Log.d(TAG, "alarmMusicReady: " + mComponentName + " Uri: " + result.mUriString);
        switch(result.mStatus) {
            case AlarmMusicResult.RESULT_URI_OK:
                // successfully retrieved URI to play - play it for 3 seconds
                ComponentName component = AlarmMusicHelper.getComponent(0);
                AlarmMusicRequest request = new AlarmMusicRequest();
                AlarmMusicHelper.setUri(result.mUriString);
                request.mUriString = result.mUriString;
                request.mDurationSec = 5;   // play for 5 seconds
                AlarmMusicHelper.play(
                        component,
                        request,
                        AlarmMusicListenerImpl.getInstance(component));
                break;
            case AlarmMusicResult.RESULT_URI_INVALID:
                // unknown error in retrieving URI to play - display a Toast
                break;
            case AlarmMusicResult.RESULT_URI_NOT_AUTHENTICATED:
                // not authenticated during URI retrieval, start Authentication Flow
                break;
            case AlarmMusicResult.RESULT_PLAY_OK:
                // Play request was successful
                break;
            case AlarmMusicResult.RESULT_PLAY_INVALID:
                // Play request failed
                // generate status bar notification and play a built in ringtone
                break;
            case AlarmMusicResult.RESULT_PLAY_NOT_AUTHENTICATED:
                // Not Authenticated
                // generate status bar notification and play a built in ringtone
                break;
        }
    }
}