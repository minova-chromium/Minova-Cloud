package com.minova.cloud;

import android.content.Context;
import android.net.Uri;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class UploadWorker extends Worker {
    public UploadWorker(Context context,WorkerParameters parameters){super(context,parameters);}
    @Override public Result doWork(){
        CloudApi api=new CloudApi(getApplicationContext());String user=getInputData().getString("user");
        if(api.session==null||!api.userId().equals(user))return Result.failure();
        if(!TransferWork.LOCK.tryAcquire())return Result.retry();
        try{
            Uri uri=Uri.parse(getInputData().getString("uri"));String folder=getInputData().getString("path");if(folder==null)folder="";
            UploadEngine engine=new UploadEngine(api,this::isStopped);UploadEngine.Info info=engine.info(uri);
            if(info.size>256L*1024*1024){setForegroundAsync(TransferWork.foreground(getApplicationContext())).get();engine.allowLongTransfer();}
            TransferWork.status(getApplicationContext(),user,"Preparing "+info.name,false);
            String hash=engine.checksum(uri);if(!folder.isEmpty())api.ensureFolder(folder);
            engine.upload(uri,info,(folder.isEmpty()?"":folder+"/")+info.name,hash,false,(name,sent,size)->TransferWork.status(getApplicationContext(),user,"Uploading "+name+" · "+(size==0?100:sent*100/size)+"%",false));
            TransferWork.status(getApplicationContext(),user,"Uploaded "+info.name,false);return Result.success();
        }catch(Exception error){
            boolean retry=!(error instanceof CloudApi.ApiException)||((CloudApi.ApiException)error).status>=500||((CloudApi.ApiException)error).status==429;
            TransferWork.status(getApplicationContext(),user,(retry?"Waiting to retry: ":"")+error.getMessage(),true);return retry?Result.retry():Result.failure();
        }finally{TransferWork.LOCK.release();}
    }
}
