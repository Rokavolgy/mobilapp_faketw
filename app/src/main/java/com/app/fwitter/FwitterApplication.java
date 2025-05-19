package com.app.fwitter;

import android.app.Application;
import android.os.Build;

import com.app.fwitter.notification.PostNotificationManager;
import com.app.fwitter.services.DailyFeedRefreshManager;

public class FwitterApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        //PostNotificationManager.initialize(this); //mashol tolti be feedactivity

        DailyFeedRefreshManager.initialize(this);
    }
}