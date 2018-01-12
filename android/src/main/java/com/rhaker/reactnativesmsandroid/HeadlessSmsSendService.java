package com.rhaker.reactnativesmsandroid;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

/**
 * Created by azghibarta on 1/11/18.
 */

public class HeadlessSmsSendService extends Service {

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        // TODO: Override if needed
        return null;
    }
}
