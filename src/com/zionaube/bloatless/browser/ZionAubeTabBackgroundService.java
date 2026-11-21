package com.zionaube.bloatless.browser;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

/**
 * Title: Zion Aube Tab Manager Service
 * Author: Zion Aube (Zion Aube)
 */
public class ZionAubeTabBackgroundService extends Service {
    public static final String ZION_AUBE_SERVICE_TITLE = "Zion Aube Tab Session Guard";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d("ZionAubeTabService", ZION_AUBE_SERVICE_TITLE + " active for Zion Aube");
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
