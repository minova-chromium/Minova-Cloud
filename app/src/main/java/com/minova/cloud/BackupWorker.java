package com.minova.cloud;

import android.Manifest;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import java.text.SimpleDateFormat;
import java.util.*;

public final class BackupWorker extends Worker {
    public BackupWorker(Context context,WorkerParameters parameters){super(context,parameters);}
    static boolean permitted(Context context,boolean video){
        if(Build.VERSION.SDK_INT<33)return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
        if(context.checkSelfPermission(video?Manifest.permission.READ_MEDIA_VIDEO:Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED)return true;
        return Build.VERSION.SDK_INT>=34&&context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED;
    }
    @Override public Result doWork(){
        Context context=getApplicationContext();CloudApi api=new CloudApi(context);String user=getInputData().getString("user");
        if(api.session==null||!api.userId().equals(user))return Result.failure();
        SharedPreferences settings=context.getSharedPreferences("backup-"+user,0);
        if(!settings.getBoolean("enabled",false))return Result.success();
        if(!TransferWork.LOCK.tryAcquire())return Result.retry();
        int uploaded=0;
        try(BackupStore store=new BackupStore(context)){
            if(!permitted(context,false)&&!permitted(context,true)){TransferWork.status(context,user,"Allow photo and video access in Backup settings to continue.",true);return Result.failure();}
            String device=settings.getString("device",null);if(device==null){device=UUID.randomUUID().toString().substring(0,8);settings.edit().putString("device",device).commit();}
            UploadEngine engine=new UploadEngine(api,()->isStopped()||!settings.getBoolean("enabled",false));
            for(boolean video:new boolean[]{false,true}){
                if(BuildConfig.DEBUG && getInputData().getLong("testMediaId",-1)>=0 && video!=getInputData().getBoolean("testVideo",false))continue;
                if(!settings.getBoolean(video?"videos":"photos",true)||!permitted(context,video))continue;
                Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection={MediaStore.MediaColumns._ID,MediaStore.MediaColumns.DISPLAY_NAME,MediaStore.MediaColumns.SIZE,MediaStore.MediaColumns.DATE_MODIFIED,MediaStore.MediaColumns.DATE_ADDED};
                String selection=null;String[] arguments=null;
                if(BuildConfig.DEBUG && getInputData().getLong("testMediaId",-1)>=0){selection=MediaStore.MediaColumns._ID+"=?";arguments=new String[]{Long.toString(getInputData().getLong("testMediaId",-1))};}
                try(Cursor cursor=context.getContentResolver().query(collection,projection,selection,arguments,MediaStore.MediaColumns.DATE_ADDED+" ASC")){
                    if(cursor==null)continue;
                    while(cursor.moveToNext()){
                        engine.check();long id=cursor.getLong(0),size=cursor.getLong(2),modified=cursor.getLong(3);
                        if(size<0||size>UploadEngine.MAX_SIZE)continue;
                        Uri uri=ContentUris.withAppendedId(collection,id);String asset=uri+"|"+size+"|"+modified;
                        if(store.contains(user,asset))continue;
                        if(size>256L*1024*1024){setForegroundAsync(TransferWork.foreground(context)).get();engine.allowLongTransfer();}
                        String name=UploadEngine.cleanName(cursor.getString(1));
                        TransferWork.status(context,user,"Preparing backup · "+name,false);
                        String hash=engine.checksum(uri);String month=new SimpleDateFormat("yyyy-MM",Locale.US).format(new Date(cursor.getLong(4)*1000));
                        String folder="Phone backups/Android-"+device+"/"+(video?"Videos":"Photos")+"/"+month;
                        String target=folder+"/"+name;
                        api.ensureFolder(folder);
                        target=engine.upload(uri,new UploadEngine.Info(name,size),target,hash,true,(filename,sent,total)->TransferWork.status(context,user,"Backing up "+filename+" · "+(total==0?100:sent*100/total)+"%",false));
                        store.record(user,asset,target);uploaded++;
                        if(uploaded>=50){TransferWork.status(context,user,"50 files backed up. Continuing shortly…",false);return Result.retry();}
                    }
                }
            }
            settings.edit().putLong("lastSuccess",System.currentTimeMillis()).apply();
            TransferWork.status(context,user,uploaded==0?"Backup up to date":"Backed up "+uploaded+" file"+(uploaded==1?"":"s"),false);return Result.success();
        }catch(Exception error){
            boolean retry=!(error instanceof SecurityException)&&(!(error instanceof CloudApi.ApiException)||((CloudApi.ApiException)error).status>=500||((CloudApi.ApiException)error).status==429||((CloudApi.ApiException)error).status==409);
            TransferWork.status(context,user,(retry?"Backup paused; will retry. ":"")+error.getMessage(),true);return retry?Result.retry():Result.failure();
        }finally{TransferWork.LOCK.release();}
    }
}
