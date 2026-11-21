package com.zionaube.bloatless.browser;

import android.app.Application;
import android.util.Log;

/**
 * Title: Zion Aube Bloatless Application Controller
 * Author: Zion Aube (Zion Aube)
 */
public class ZionAubeBloatlessApplication extends Application {
    public static final String ZION_AUBE_ENGINE_TITLE = "Zion Aube Chromium WebView Core";
    public static final String ZION_AUBE_AUTHOR_TAG = "Made by Zion Aube";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("ZionAubeBloatlessApp", "Initialized " + ZION_AUBE_ENGINE_TITLE + " - " + ZION_AUBE_AUTHOR_TAG);
    }
}
