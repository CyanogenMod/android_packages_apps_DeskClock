package com.android.alarmmusic;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import com.cyanogen.ambient.alarmmusic.AlarmMusicServices;
import com.cyanogen.ambient.common.ConnectionResult;
import com.cyanogen.ambient.common.api.AmbientApiClient;

/**
 * Holds Ambient Client for all PIM apps
 */
public class AmbientConnection {

    public static final SingletonHolder<AmbientApiClient, Context> CLIENT =
            new SingletonHolder<AmbientApiClient, Context>() {
                private static final String TAG = "Dialer.AmbientSingletonHolder";

                @Override
                protected AmbientApiClient create(Context context) {
                    AmbientApiClient client = new AmbientApiClient.Builder(context)
                            .addApi(AlarmMusicServices.API)
                            .build();

                    client.registerConnectionFailedListener(
                            new AmbientApiClient.OnConnectionFailedListener() {
                                @Override
                                public void onConnectionFailed(ConnectionResult result) {
                                    Log.w(TAG, "Ambient connection failed: " + result);
                                }
                            });
                    client.registerDisconnectionListener(
                            new AmbientApiClient.OnDisconnectionListener() {
                                @Override
                                public void onDisconnection() {
                                    Log.d(TAG, "Ambient connection disconnected");
                                }
                            });
                    client.registerConnectionCallbacks(
                            new AmbientApiClient.ConnectionCallbacks() {
                                @Override
                                public void onConnected(Bundle connectionHint) {
                                    Log.d(TAG, "Ambient connection established");
                                }

                                @Override
                                public void onConnectionSuspended(int cause) {
                                    Log.d(TAG, "Ambient connection suspended");
                                }
                            });
                    client.connect();
                    return client;
                }
            };


}
