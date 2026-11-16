package com.minova.cloud;

import android.app.PendingIntent;
import android.content.*;
import android.database.Cursor;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.*;
import org.json.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class OffloadSafetyTest {
    @Test public void phoneGalleryAndFileBrowserLoadWithoutDeletingAnything()throws Exception{
        CloudApi api=login();android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();
        android.app.Activity gallery=instrumentation.startActivitySync(new Intent(context(),PhoneGalleryActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();assertFalse(gallery.isFinishing());assertNotNull(gallery.findViewById(android.R.id.content));instrumentation.runOnMainSync(gallery::finish);
        android.app.Activity files=instrumentation.startActivitySync(new Intent(context(),PhoneFilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();assertFalse(files.isFinishing());assertNotNull(files.findViewById(android.R.id.content));instrumentation.runOnMainSync(files::finish);api.request("/api/logout","POST",new JSONObject());
    }
    Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    @Test public void fullSharedStorageFileBrowserUploadsAndKeepsPhoneFiles()throws Exception{
        CloudApi api=login();assertTrue("Grant all-files access to the isolated debug app before running this check",PhoneAccess.allFiles());
        File directory=android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);assertTrue(directory.isDirectory()||directory.mkdirs());File source=new File(directory,"Minova synthetic document "+UUID.randomUUID()+".txt");byte[] content="Shared phone file upload; keep the original.".getBytes(java.nio.charset.StandardCharsets.UTF_8);try(OutputStream stream=new FileOutputStream(source)){stream.write(content);}
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();PhoneFilesActivity files=(PhoneFilesActivity)instrumentation.startActivitySync(new Intent(context(),PhoneFilesActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        try{
            long deadline=System.currentTimeMillis()+10000;while(files.entries.isEmpty()&&System.currentTimeMillis()<deadline)Thread.sleep(100);assertFalse("All-files access must show shared phone storage",files.entries.isEmpty());
            instrumentation.runOnMainSync(()->{files.selected.add(source.getAbsolutePath());files.upload();});
            JSONObject remote=null;deadline=System.currentTimeMillis()+30000;while(System.currentTimeMillis()<deadline){try{remote=api.request("/api/files?path="+CloudApi.encode(source.getName()),"GET",null).getJSONArray("items").getJSONObject(0);break;}catch(CloudApi.ApiException error){if(error.status!=404)throw error;Thread.sleep(100);}}assertNotNull(remote);assertTrue(source.isFile());assertEquals(CloudApi.sha256(content),api.request("/api/checksum?path="+CloudApi.encode(source.getName()),"GET",null).getString("sha256"));
        }finally{instrumentation.runOnMainSync(files::finish);assertTrue(source.delete());api.request("/api/logout","POST",new JSONObject());}
    }
    CloudApi login()throws Exception{Context context=context();context.getSharedPreferences("qa",0).edit().putString("origin","http://127.0.0.1:4178").commit();return new CloudApi(context,new CloudApi(context,null).login("preview","synthetic preview passphrase","",null));}
    MediaAsset photo()throws Exception{
        Context context=context();ContentValues values=new ContentValues();values.put(MediaStore.Images.Media.DISPLAY_NAME,"Cleanup synthetic "+UUID.randomUUID()+".png");values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.IS_PENDING,1);Uri uri=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        Bitmap bitmap=Bitmap.createBitmap(32,24,Bitmap.Config.ARGB_8888);bitmap.eraseColor(Color.CYAN);try(OutputStream stream=context.getContentResolver().openOutputStream(uri)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}bitmap.recycle();values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(uri,values,null,null);return MediaAsset.read(context,uri);
    }
    boolean exists(Uri uri){try(Cursor cursor=context().getContentResolver().query(uri,new String[]{"_id"},null,null,null)){return cursor!=null&&cursor.moveToFirst();}}
    interface Checked {void run()throws Exception;}
    void blocked(Checked run)throws Exception{try{run.run();fail("Unsafe phone removal was not blocked");}catch(IOException expected){}}
    @Test public void verifiedUploadNeedsAndroidConsentAndCloudSurvivesPhoneRemoval()throws Exception{
        CloudApi api=login();MediaAsset asset=photo();String user=api.userId();
        try(OffloadStore store=new OffloadStore(context())){
            String batch=store.create(user,Collections.singletonList(asset));blocked(()->SafeOffload.verify(api,store,batch,()->false));
            OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(OffloadWorker.class).setInputData(new Data.Builder().putString("user",user).putString("batch",batch).build()).build();WorkManager manager=WorkManager.getInstance(context());manager.enqueue(work).getResult().get(10,TimeUnit.SECONDS);
            WorkInfo.State state=WorkInfo.State.ENQUEUED;long deadline=System.currentTimeMillis()+60000;while(System.currentTimeMillis()<deadline){state=manager.getWorkInfoById(work.getId()).get(10,TimeUnit.SECONDS).getState();if(state.isFinished())break;Thread.sleep(100);}assertEquals(WorkInfo.State.SUCCEEDED,state);
            assertEquals("ready",store.get(user,batch).state);assertTrue("A background worker must never delete phone media",exists(asset.uri));
            SafeOffload.Proof proof=SafeOffload.verify(api,store,batch,()->false);PendingIntent request=SafeOffload.deletionRequest(context(),proof);assertNotNull(request);assertTrue("Creating a consent request must not delete anything",exists(asset.uri));
            OffloadStore.Item uploaded=store.items(user,batch).get(0);assertTrue(SafeOffload.matches(api,uploaded.target,uploaded.hash,asset.size));
            String duplicate=store.create(user,Collections.singletonList(asset));SafeOffload.prepare(api,store,duplicate,()->false,(n,a,b)->{});assertEquals(uploaded.target,store.items(user,duplicate).get(0).target);
            // Remove only this test-owned synthetic row to model successful OS confirmation.
            assertEquals(1,context().getContentResolver().delete(asset.uri,null,null));assertTrue("The cloud copy must remain after the phone copy is gone",SafeOffload.matches(api,uploaded.target,uploaded.hash,asset.size));
        }finally{if(exists(asset.uri))context().getContentResolver().delete(asset.uri,null,null);api.request("/api/logout","POST",new JSONObject());}
    }
    @Test public void missingOrModifiedCloudCopiesCannotAuthorizeRemoval()throws Exception{
        CloudApi api=login();MediaAsset asset=photo();String user=api.userId();
        try(OffloadStore store=new OffloadStore(context())){
            String batch=store.create(user,Collections.singletonList(asset));SafeOffload.prepare(api,store,batch,()->false,(n,a,b)->{});OffloadStore.Item uploaded=store.items(user,batch).get(0);
            JSONObject original=api.request("/api/files?path="+CloudApi.encode(uploaded.target),"GET",null).getJSONArray("items").getJSONObject(0);
            byte[] changed;try(InputStream input=context().getContentResolver().openInputStream(asset.uri)){changed=CloudApi.read(input,1024*1024);}changed[changed.length-1]^=1;
            JSONObject transfer=api.request("/api/uploads","POST",new JSONObject().put("path",uploaded.target).put("size",changed.length).put("sha256",CloudApi.sha256(changed)).put("replaceRevision",original.getString("revision")));api.chunk(transfer.getString("id"),0,changed,changed.length);api.request("/api/uploads/"+transfer.getString("id")+"/complete","POST",new JSONObject());
            blocked(()->SafeOffload.verify(api,store,batch,()->false));assertTrue(exists(asset.uri));api.request("/api/trash","POST",new JSONObject().put("path",uploaded.target));blocked(()->SafeOffload.verify(api,store,batch,()->false));assertTrue(exists(asset.uri));
        }finally{if(exists(asset.uri))context().getContentResolver().delete(asset.uri,null,null);api.request("/api/logout","POST",new JSONObject());}
    }
    @Test public void changedPhoneOriginalCancelledJobsAndOtherAccountsAreBlocked()throws Exception{
        CloudApi api=login();MediaAsset asset=photo();String user=api.userId();
        try(OffloadStore store=new OffloadStore(context())){
            String batch=store.create(user,Collections.singletonList(asset));SafeOffload.prepare(api,store,batch,()->false,(n,a,b)->{});
            blocked(()->store.items(UUID.randomUUID().toString(),batch));JSONObject other=new JSONObject(api.session.toString());other.getJSONObject("user").put("id",UUID.randomUUID().toString());CloudApi wrong=new CloudApi(context(),other);blocked(()->SafeOffload.verify(wrong,store,batch,()->false));
            String cancelled=store.create(user,Collections.singletonList(asset));SafeOffload.prepare(api,store,cancelled,()->false,(n,a,b)->{});SafeOffload.Proof obsolete=SafeOffload.verify(api,store,cancelled,()->false);store.state(user,cancelled,"cancelled","Cancelled after verification");blocked(()->SafeOffload.deletionRequest(context(),obsolete));assertTrue(exists(asset.uri));
            byte[] edited;try(InputStream input=context().getContentResolver().openInputStream(asset.uri)){edited=CloudApi.read(input,1024*1024);}edited[edited.length-1]^=1;try(OutputStream output=context().getContentResolver().openOutputStream(asset.uri,"w")){output.write(edited);}blocked(()->SafeOffload.verify(api,store,batch,()->false));assertTrue(exists(asset.uri));
            store.state(user,batch,"cancelled","Cancelled by test");blocked(()->SafeOffload.verify(api,store,batch,()->false));assertTrue(exists(asset.uri));
            blocked(()->MediaAsset.requireLocal(Uri.parse("content://other.provider/image/4")));blocked(()->MediaAsset.requireLocal(Uri.parse("file:///sdcard/Pictures/photo.png")));blocked(()->MediaAsset.requireLocal(Uri.parse("content://media/picker/1")));
        }finally{if(exists(asset.uri))context().getContentResolver().delete(asset.uri,null,null);api.request("/api/logout","POST",new JSONObject());}
    }
    @Test public void videosCanBeBackedUpAndVerifiedWithoutDeletingTheOriginal()throws Exception{
        CloudApi api=login();String user=api.userId();ContentValues values=new ContentValues();values.put(MediaStore.Video.Media.DISPLAY_NAME,"Cleanup video "+UUID.randomUUID()+".mp4");values.put(MediaStore.Video.Media.MIME_TYPE,"video/mp4");values.put(MediaStore.Video.Media.IS_PENDING,1);Uri uri=context().getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);
        try{
            try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("qa-video.mp4");OutputStream output=context().getContentResolver().openOutputStream(uri)){byte[] buffer=new byte[128*1024];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);}
            values.clear();values.put(MediaStore.Video.Media.IS_PENDING,0);context().getContentResolver().update(uri,values,null,null);MediaAsset asset=MediaAsset.read(context(),uri);assertTrue(asset.video);assertTrue(MediaAsset.list(context(),true,100).stream().anyMatch(item->item.uri.equals(uri)));
            try(OffloadStore store=new OffloadStore(context())){String batch=store.create(user,Collections.singletonList(asset));SafeOffload.prepare(api,store,batch,()->false,(n,a,b)->{});SafeOffload.Proof proof=SafeOffload.verify(api,store,batch,()->false);assertNotNull(SafeOffload.deletionRequest(context(),proof));assertTrue(exists(uri));assertTrue(store.items(user,batch).get(0).target.contains("/Videos/"));}
        }finally{if(exists(uri))context().getContentResolver().delete(uri,null,null);api.request("/api/logout","POST",new JSONObject());}
    }
}
