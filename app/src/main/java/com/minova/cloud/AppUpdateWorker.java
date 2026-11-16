package com.minova.cloud;
import android.content.Context;
import androidx.work.*;
public final class AppUpdateWorker extends Worker {
    public AppUpdateWorker(Context context,WorkerParameters parameters){super(context,parameters);}
    @Override public Result doWork(){try{AppUpdater.check(getApplicationContext(),false);return Result.success();}catch(Exception error){return Result.retry();}}
}
