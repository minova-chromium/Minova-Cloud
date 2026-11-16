package com.minova.cloud;

import android.content.Context;
import android.net.Uri;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Semaphore;

final class TransferWork {
    static ForegroundInfo foreground(Context context){
        android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);
        manager.createNotificationChannel(new android.app.NotificationChannel("cloud-transfers","Cloud uploads and backup",android.app.NotificationManager.IMPORTANCE_LOW));
        android.content.Intent intent=new android.content.Intent(context,MainActivity.class);
        android.app.PendingIntent open=android.app.PendingIntent.getActivity(context,0,intent,android.app.PendingIntent.FLAG_IMMUTABLE|android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification notification=new android.app.Notification.Builder(context,"cloud-transfers").setSmallIcon(R.drawable.minova_cloud_notification).setContentTitle("Minova Cloud transfer").setContentText("Uploading files to your private cloud").setVisibility(android.app.Notification.VISIBILITY_PRIVATE).setOngoing(true).setContentIntent(open).build();
        return android.os.Build.VERSION.SDK_INT>=29?new ForegroundInfo(1042,notification,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC):new ForegroundInfo(1042,notification);
    }
    static final Semaphore LOCK=new Semaphore(1);
    static String tag(String user){return "minova-"+user;}
    static void status(Context context,String user,String message,boolean error){context.getSharedPreferences("transfer-"+user,0).edit().putString("status",message).putBoolean("error",error).putLong("updated",System.currentTimeMillis()).apply();}
    static Constraints constraints(boolean wifi){return new Constraints.Builder().setRequiredNetworkType(wifi?NetworkType.UNMETERED:NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build();}
    static void upload(Context context,String user,Uri uri,String path){
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(UploadWorker.class).setInputData(new Data.Builder().putString("user",user).putString("uri",uri.toString()).putString("path",path).build()).setConstraints(constraints(false)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag(tag(user)).build();
        WorkManager.getInstance(context).enqueueUniqueWork("uploads-"+user,ExistingWorkPolicy.APPEND_OR_REPLACE,request);
    }
    static void schedule(Context context,String user,boolean enabled,boolean wifi){
        // Scheduling backup never schedules or approves phone deletion.
        WorkManager manager=WorkManager.getInstance(context);String name="backup-"+user;
        if(!enabled){manager.cancelUniqueWork(name);manager.cancelUniqueWork("backup-now-"+user);return;}
        Data data=new Data.Builder().putString("user",user).build();
        PeriodicWorkRequest work=new PeriodicWorkRequest.Builder(BackupWorker.class,15,TimeUnit.MINUTES).setInputData(data).setConstraints(constraints(wifi)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag(tag(user)).build();
        manager.enqueueUniquePeriodicWork(name,ExistingPeriodicWorkPolicy.UPDATE,work);
    }
    static void offload(Context context,String user,String batch){
        OneTimeWorkRequest request=new OneTimeWorkRequest.Builder(OffloadWorker.class).setInputData(new Data.Builder().putString("user",user).putString("batch",batch).build()).setConstraints(constraints(false)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,20,TimeUnit.SECONDS).addTag(tag(user)).build();
        WorkManager.getInstance(context).enqueueUniqueWork("phone-cleanup-"+batch,ExistingWorkPolicy.KEEP,request);
    }
    static void backupNow(Context context,String user,boolean wifi){
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(BackupWorker.class).setInputData(new Data.Builder().putString("user",user).build()).setConstraints(constraints(wifi)).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,15,TimeUnit.SECONDS).addTag(tag(user)).build();
        WorkManager.getInstance(context).enqueueUniqueWork("backup-now-"+user,ExistingWorkPolicy.KEEP,work);
    }
}
