package com.android.alarmmusic;

import android.app.PendingIntent;
import android.content.ComponentName;
import android.os.UserHandle;

import java.util.Objects;


/**
 * Created by gkipnis on 3/7/16.
 */
public class AlarmMusicInfos {
    public static final String TAG = AlarmMusicInfos.class.getSimpleName();
    public UserHandle mUserHandle;
    public ComponentName mComponent;
    public PendingIntent mLoginIntent;


    @Override
    public int hashCode() {
        return Objects.hash(mUserHandle, mComponent, mLoginIntent);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object instanceof AlarmMusicInfos) {
            AlarmMusicInfos info = (AlarmMusicInfos)object;
            return Objects.equals(mUserHandle, info.mUserHandle)
                    && Objects.equals(mComponent, info.mComponent)
                    && Objects.equals(mLoginIntent, info.mLoginIntent)
                    ;
        }
        return false;
    }
}
