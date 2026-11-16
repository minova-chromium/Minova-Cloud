package com.minova.cloud;

import android.app.PendingIntent;
import android.content.Context;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import org.json.*;
import java.io.*;
import java.util.*;

/** Workers can prepare uploads. Only a fresh foreground proof can request phone deletion. */
final class SafeOffload {
    static final class Blocked extends IOException {Blocked(String message){super(message);}}
    static final class Proof {
        final String user,batch;final List<MediaAsset> assets;final long checkedAt;
        private Proof(String user,String batch,List<MediaAsset> assets){this.user=user;this.batch=batch;this.assets=Collections.unmodifiableList(assets);checkedAt=SystemClock.elapsedRealtime();}
    }
    static void current(Context context,String user)throws IOException{JSONObject session=SecureSession.load(context);if(session==null||!user.equals(new CloudApi(context,session).userId()))throw new Blocked("Sign in to the same Minova account before continuing.");}
    static void source(Context context,MediaAsset original)throws IOException{MediaAsset actual;try{actual=MediaAsset.read(context,original.uri);}catch(FileNotFoundException error){throw new Blocked("The phone original is no longer available. Nothing will be removed.");}if(!original.sameSource(actual))throw new Blocked("The phone original changed: "+original.name+". Nothing will be removed. Select it again to back up the current version.");}
    static boolean matches(CloudApi api,String target,String hash,long size)throws Exception{
        JSONObject item=api.request("/api/files?path="+CloudApi.encode(target),"GET",null).getJSONArray("items").getJSONObject(0);
        return item.optString("path").isEmpty()&&!item.optBoolean("isDirectory")&&item.getLong("size")==size&&hash.equals(api.request("/api/checksum?path="+CloudApi.encode(target),"GET",null).getString("sha256"));
    }
    static void prepare(CloudApi api,OffloadStore store,String batch,UploadEngine.Stop stop,UploadEngine.Progress progress)throws Exception{
        Context context=api.context;String user=api.userId();current(context,user);OffloadStore.Batch job=store.get(user,batch);
        if(job.state.equals("cancelled")||job.state.equals("done"))throw new Blocked("This phone cleanup is no longer active.");
        UploadEngine engine=new UploadEngine(api,()->{try{return stop.stopped()||store.get(user,batch).state.equals("cancelled");}catch(IOException error){return true;}});engine.allowLongTransfer();
        try(BackupStore receipts=new BackupStore(context)){
            for(OffloadStore.Item item:store.items(user,batch)){
                current(context,user);engine.check();if(item.uploaded)continue;MediaAsset asset=item.asset;source(context,asset);
                String hash=engine.checksum(asset.uri);source(context,asset);String target=receipts.target(user,asset.assetKey());
                if(target!=null){try{if(!matches(api,target,hash,asset.size))target=null;}catch(CloudApi.ApiException error){if(error.status!=404)throw error;target=null;}}
                if(target==null){String folder=asset.folder(context,user);api.ensureFolder(folder);target=engine.upload(asset.uri,new UploadEngine.Info(UploadEngine.cleanName(asset.name),asset.size),folder+"/"+UploadEngine.cleanName(asset.name),hash,true,progress);}
                source(context,asset);current(context,user);receipts.record(user,asset.assetKey(),target);store.uploaded(user,batch,asset.uri,hash,target);
            }
        }
        current(context,user);if(store.get(user,batch).state.equals("cancelled"))throw new Blocked("Cleanup cancelled. Phone originals are kept.");
        store.state(user,batch,"ready","Backed up. Review the cloud copies before removing anything from this phone.");
    }
    static Proof verify(CloudApi api,OffloadStore store,String batch,UploadEngine.Stop stop)throws Exception{
        Context context=api.context;String user=api.userId();current(context,user);
        if(!store.get(user,batch).state.equals("ready"))throw new IOException("Wait for every selected item to finish uploading first.");
        List<OffloadStore.Item> items=store.items(user,batch);if(items.isEmpty()||items.size()>OffloadStore.MAX_ITEMS)throw new IOException("Invalid phone cleanup selection.");
        List<MediaAsset> checked=new ArrayList<>();UploadEngine engine=new UploadEngine(api,stop);engine.allowLongTransfer();
        for(OffloadStore.Item item:items){engine.check();current(context,user);source(context,item.asset);
            if(!item.uploaded||item.target==null||item.hash==null)throw new IOException("This item has not finished uploading.");
            if(!item.hash.equals(engine.checksum(item.asset.uri)))throw new IOException("The phone original changed. Nothing will be removed.");
            if(!matches(api,item.target,item.hash,item.asset.size))throw new IOException("The cloud copy could not be verified: "+item.asset.name+". Nothing will be removed.");
            source(context,item.asset);checked.add(item.asset);
        }
        current(context,user);if(!store.get(user,batch).state.equals("ready"))throw new Blocked("This phone cleanup was cancelled. Phone originals are kept.");return new Proof(user,batch,checked);
    }
    static PendingIntent deletionRequest(Context context,Proof proof)throws Exception{
        if(Build.VERSION.SDK_INT<30)throw new IOException("Protected phone cleanup requires Android 11 or newer. Uploads remain available on this phone.");
        current(context,proof.user);if(SystemClock.elapsedRealtime()-proof.checkedAt>60000)throw new IOException("The verification expired. Check the cloud copies again before removing phone originals.");
        try(OffloadStore store=new OffloadStore(context)){if(!store.get(proof.user,proof.batch).state.equals("ready"))throw new Blocked("This cleanup was cancelled. Phone originals are kept.");}
        List<Uri> uris=new ArrayList<>();for(MediaAsset asset:proof.assets){source(context,asset);uris.add(asset.uri);}
        // Android performs deletion only after the user accepts its own confirmation dialog.
        return MediaStore.createDeleteRequest(context.getContentResolver(),uris);
    }
}
