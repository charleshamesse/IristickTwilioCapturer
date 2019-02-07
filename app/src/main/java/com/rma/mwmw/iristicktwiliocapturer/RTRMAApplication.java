package com.rma.mwmw.iristicktwiliocapturer;

import android.app.Application;

import com.iristick.smartglass.support.app.IristickApp;

public class RTRMAApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        IristickApp.init(this);
    }
}
