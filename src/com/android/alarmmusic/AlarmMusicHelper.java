package com.android.alarmmusic;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;

import android.os.Handler;
import android.util.Log;
import com.cyanogen.ambient.common.api.AmbientApiClient;
import com.cyanogen.ambient.common.api.ResultCallback;
import com.cyanogen.ambient.alarmmusic.AlarmMusicApi;
import com.cyanogen.ambient.alarmmusic.AlarmMusicServices;
import com.cyanogen.ambient.alarmmusic.InstalledPluginsResult;
import com.cyanogen.ambient.alarmmusic.PendingIntentResult;
import com.cyanogen.ambient.alarmmusic.AlarmMusicResult;
import com.cyanogen.ambient.alarmmusic.AlarmMusicResultResult;
import com.cyanogen.ambient.alarmmusic.AlarmMusicRequest;
import com.cyanogen.ambient.alarmmusic.IAlarmMusicListener;




import java.util.HashMap;
import java.util.List;

/**
 * Call Method Helper - in charge of keeping track of AlarmMusicProviders currently installed
 */
public class AlarmMusicHelper {

    private static AlarmMusicHelper sInstance;
    private static final String TAG = AlarmMusicHelper.class.getSimpleName();

    AmbientApiClient mClient;
    Context mContext;
    AlarmMusicApi mAlarmMusicApi;
    Handler mMainHandler;
    private static List<ComponentName> mInstalledPlugins;
    private static HashMap<ComponentName, AlarmMusicInfos> mAlarmMusicInfos = new HashMap<>();

    /**
     * Get a single instance of the helper
     */
    private static synchronized AlarmMusicHelper getInstance() {
        if (sInstance == null) {
            sInstance = new AlarmMusicHelper();
        }
        return sInstance;
    }

    /**
     * Start the Helper and kick off ModCore queries
     */
    public static void init(Context context) {
        AlarmMusicHelper helper = getInstance();
        helper.mContext = context;
        helper.mClient = AmbientConnection.CLIENT.get(context);
        helper.mAlarmMusicApi = AlarmMusicServices.getInstance();
        helper.mMainHandler = new Handler(context.getMainLooper());
        updateAlarmMusicPlugins();
    }

    /**
     * prepare to query ModCore for installed plugins
     */
    private static void updateAlarmMusicPlugins() {
        getInstance().mAlarmMusicApi.getInstalledPlugins(getInstance().mClient)
                .setResultCallback(new ResultCallback<InstalledPluginsResult>() {
            @Override
            public void onResult(InstalledPluginsResult installedPluginsResult) {
                mInstalledPlugins = installedPluginsResult.components;
                mAlarmMusicInfos.clear();
                for (ComponentName cn : mInstalledPlugins) {
                    mAlarmMusicInfos.put(cn, new AlarmMusicInfos());
                }
            }
        });
        return;
    }

    public static int getAlarmMusicPluginCount() {
        if (mInstalledPlugins != null) {
            return mInstalledPlugins.size();
        }
        return 0;
    }

    public static AlarmMusicInfos getAlarmMusicIfExists(ComponentName cn) {
        if (mAlarmMusicInfos.containsKey(cn)) {
            return mAlarmMusicInfos.get(cn);
        } else {
            return null;
        }
    }

    public static ComponentName getComponent(int index) {
        if (mInstalledPlugins == null) {
            return null;
        } else if (index >= mInstalledPlugins.size()) {
            return null;
        } else {
            return mInstalledPlugins.get(index);
        }
    }

    public static void getUri(final ComponentName cn, final IAlarmMusicListener listener) {
        getInstance().mAlarmMusicApi.getUri(getInstance().mClient, cn, listener);
    }

    public static void play(final ComponentName cn, final AlarmMusicRequest request,
                                 final IAlarmMusicListener listener) {
        getInstance().mAlarmMusicApi.play(getInstance().mClient, cn, request, listener);
    }

    public static void stop(final ComponentName cn, final AlarmMusicRequest request,
                                 final IAlarmMusicListener listener) {
        getInstance().mAlarmMusicApi.stop(getInstance().mClient, cn, request, listener);
    }

    private String mUri;
    public static void setUri(String uri) {
        getInstance().mUri = uri;
    }
    public static String getUri() {
        return getInstance().mUri;
    }

}
