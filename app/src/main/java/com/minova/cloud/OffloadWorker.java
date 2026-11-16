package com.minova.cloud;

import android.content.Context;
import androidx.work.*;

public final class OffloadWorker extends Worker {
    public OffloadWorker(Context context,WorkerParameters parameters){super(context,parameters);}
    @Override public Result doWork(){
        Context context=getApplicationContext();CloudApi api=new CloudApi(context);String user=getInputData().getString("user"),batch=getInputData().getString("batch");
        if(api.session==null||!api.userId().equals(user)||batch==null)return Result.failure();
        if(!TransferWork.LOCK.tryAcquire())return Result.retry();
        try(OffloadStore store=new OffloadStore(context)){
            OffloadStore.Batch job=store.get(user,batch);if(job.state.equals("cancelled")||job.state.equals("done"))return Result.success();
            store.state(user,batch,"uploading","Backing up selected phone media");
            long remaining=0;for(OffloadStore.Item item:store.items(user,batch)){if(!item.uploaded)remaining+=item.asset.size;if(remaining>256L*1024*1024){setForegroundAsync(TransferWork.foreground(context)).get();break;}}
            long[] lastProgress={0};SafeOffload.prepare(api,store,batch,()->isStopped(),(name,sent,size)->{String note="Backing up "+name+" · "+(size==0?100:sent*100/size)+"%";TransferWork.status(context,user,note,false);long now=android.os.SystemClock.elapsedRealtime();if(now-lastProgress[0]>=1000||sent==size){store.progress(user,batch,note);lastProgress[0]=now;}});
            TransferWork.status(context,user,"Phone backup ready. Open Free up phone space to review removal.",false);return Result.success();
        }catch(Exception error){
            boolean retry=!(error instanceof SafeOffload.Blocked)&&!(error instanceof SecurityException)&&(!(error instanceof CloudApi.ApiException)||((CloudApi.ApiException)error).status>=500||((CloudApi.ApiException)error).status==429);
            try(OffloadStore store=new OffloadStore(context)){OffloadStore.Batch job=store.get(user,batch);if(!job.state.equals("cancelled"))store.state(user,batch,retry?"uploading":"error",error.getMessage()==null?"Backup could not finish":error.getMessage());}catch(Exception ignored){}
            TransferWork.status(context,user,"Phone originals kept. "+error.getMessage(),true);return retry?Result.retry():Result.failure();
        }finally{TransferWork.LOCK.release();}
    }
}
