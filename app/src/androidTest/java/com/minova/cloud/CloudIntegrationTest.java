package com.minova.cloud;

import android.content.*;
import android.graphics.*;
import android.net.Uri;
import android.provider.MediaStore;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.work.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class CloudIntegrationTest {
    @Test public void phonePhotoOrientationIsRespected()throws Exception{
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();File photo=new File(context.getCacheDir(),"orientation-test.jpg");
        Bitmap original=Bitmap.createBitmap(40,20,Bitmap.Config.ARGB_8888);original.eraseColor(Color.CYAN);try(OutputStream output=new FileOutputStream(photo)){original.compress(Bitmap.CompressFormat.JPEG,100,output);}original.recycle();
        androidx.exifinterface.media.ExifInterface metadata=new androidx.exifinterface.media.ExifInterface(photo);metadata.setAttribute(androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,Integer.toString(androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90));metadata.saveAttributes();
        Bitmap decoded=PhotoBitmaps.decode(CloudApi.read(new FileInputStream(photo),1024*1024),700);assertNotNull(decoded);assertEquals(20,decoded.getWidth());assertEquals(40,decoded.getHeight());decoded.recycle();
    }
    @Test public void androidUploadsResumeAndBackupMatchesTheWebsite()throws Exception {
        Context context=InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences("qa",0).edit().putString("origin","http://127.0.0.1:4178").commit();
        JSONObject session=new CloudApi(context,null).login("preview","synthetic preview passphrase","",null);
        CloudApi api=new CloudApi(context,session);String user=api.userId();
        assertEquals(session.getString("token"),SecureSession.load(context).getString("token"));
        assertFalse(context.getSharedPreferences("credentials",0).getString("session","").contains(session.getString("token")));
        String folder="Android integration "+UUID.randomUUID().toString().substring(0,8);api.ensureFolder(folder);
        byte[] bytes=new byte[3*1024*1024+37];new Random(7142).nextBytes(bytes);
        File source=new File(context.getFilesDir(),"synthetic-transfer.bin");try(OutputStream output=new FileOutputStream(source)){output.write(bytes);}
        Uri uri=Uri.fromFile(source);String hash=CloudApi.sha256(bytes),target=folder+"/resumable.bin";
        UploadEngine first=new UploadEngine(api,()->false);
        try{first.upload(uri,new UploadEngine.Info("resumable.bin",bytes.length),target,hash,false,(name,sent,total)->{if(sent<total)throw new InterruptedIOException("Synthetic interruption after the first chunk");});fail("Expected a synthetic interrupted transfer");}catch(InterruptedIOException expected){}
        UploadEngine second=new UploadEngine(api,()->false);second.upload(uri,new UploadEngine.Info("resumable.bin",bytes.length),target,hash,false,(name,sent,total)->{});
        HttpURLConnection download=api.connection("/api/download?path="+CloudApi.encode(target),"GET",true);
        try{assertEquals(200,download.getResponseCode());assertEquals(hash,CloudApi.sha256(CloudApi.read(download.getInputStream(),4*1024*1024)));}finally{download.disconnect();}
        api.request("/api/rename","POST",new JSONObject().put("path",target).put("name","renamed.bin"));
        assertEquals("renamed.bin",api.request("/api/files?path="+CloudApi.encode(folder),"GET",null).getJSONArray("items").getJSONObject(0).getString("name"));
        ContentValues values=new ContentValues();String photoName="Minova synthetic "+UUID.randomUUID().toString().substring(0,8)+".png";values.put(MediaStore.Images.Media.DISPLAY_NAME,photoName);values.put(MediaStore.Images.Media.MIME_TYPE,"image/png");values.put(MediaStore.Images.Media.IS_PENDING,1);
        Uri photo=context.getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,values);assertNotNull(photo);
        Bitmap bitmap=BitmapFactory.decodeResource(context.getResources(),R.drawable.minova_icon);try(OutputStream output=context.getContentResolver().openOutputStream(photo)){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,output));}bitmap.recycle();
        values.clear();values.put(MediaStore.Images.Media.IS_PENDING,0);context.getContentResolver().update(photo,values,null,null);
        context.getSharedPreferences("backup-"+user,0).edit().putBoolean("enabled",true).putBoolean("photos",true).putBoolean("videos",true).commit();
        Data input=new Data.Builder().putString("user",user).putLong("testMediaId",ContentUris.parseId(photo)).putBoolean("testVideo",false).build();
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(BackupWorker.class).setInputData(input).build();WorkManager manager=WorkManager.getInstance(context);manager.enqueue(work).getResult().get(10,TimeUnit.SECONDS);
        WorkInfo.State state=WorkInfo.State.ENQUEUED;long deadline=System.currentTimeMillis()+90000;
        while(System.currentTimeMillis()<deadline){state=manager.getWorkInfoById(work.getId()).get(10,TimeUnit.SECONDS).getState();if(state.isFinished())break;Thread.sleep(250);}
        assertEquals("Automatic backup must finish using only the synthetic photo",WorkInfo.State.SUCCEEDED,state);
        JSONArray photos=api.request("/api/library?kind=photos&q="+CloudApi.encode(photoName.substring(0,photoName.lastIndexOf('.'))),"GET",null).getJSONArray("items");
        assertEquals(1,photos.length());assertTrue(photos.getJSONObject(0).getString("path").startsWith("Phone backups/"));
        String backedUp=photos.getJSONObject(0).getString("path");
        assertTrue(backedUp.endsWith("/"+photoName));
        try(BackupStore receipts=new BackupStore(context)){assertEquals(1,receipts.getWritableDatabase().delete("receipts","user=? AND target=?",new String[]{user,backedUp}));}
        OneTimeWorkRequest repeated=new OneTimeWorkRequest.Builder(BackupWorker.class).setInputData(input).build();manager.enqueue(repeated).getResult().get(10,TimeUnit.SECONDS);deadline=System.currentTimeMillis()+30000;
        while(System.currentTimeMillis()<deadline){state=manager.getWorkInfoById(repeated.getId()).get(10,TimeUnit.SECONDS).getState();if(state.isFinished())break;Thread.sleep(250);}
        assertEquals(WorkInfo.State.SUCCEEDED,state);
        assertEquals(1,api.request("/api/library?kind=photos&q="+CloudApi.encode(photoName.substring(0,photoName.lastIndexOf('.'))),"GET",null).getJSONArray("items").length());
        context.getSharedPreferences("backup-"+user,0).edit().putBoolean("enabled",false).commit();
        api.request("/api/trash","POST",new JSONObject().put("path",backedUp));assertEquals(0,api.request("/api/library?kind=photos&q="+CloudApi.encode(photoName.substring(0,photoName.lastIndexOf('.'))),"GET",null).getJSONArray("items").length());
        JSONArray trash=api.request("/api/trash","GET",null).getJSONArray("items");String restoreId=null;for(int i=0;i<trash.length();i++)if(backedUp.equals(trash.getJSONObject(i).optString("path")))restoreId=trash.getJSONObject(i).getString("id");assertNotNull(restoreId);
        api.request("/api/restore","POST",new JSONObject().put("id",restoreId));
        assertEquals(1,api.request("/api/library?kind=photos&q="+CloudApi.encode(photoName.substring(0,photoName.lastIndexOf('.'))),"GET",null).getJSONArray("items").length());
        JSONObject deviceList=api.request("/api/devices","GET",null);assertTrue(deviceList.getJSONArray("devices").length()>0);
    }
}
