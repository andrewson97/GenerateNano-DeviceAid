package com.iterate.adreno.sdk;

import android.content.Context;

/**
 * Simple static holder for application context to access assets
 */
public final class AppContextHolder {
    private static Context appContext;

    public static void setContext(Context context) {
        appContext = context.getApplicationContext();
    }

    public static Context getContext() {
        return appContext;
    }
    
    public static Context get() {
        return appContext;
    }
}


