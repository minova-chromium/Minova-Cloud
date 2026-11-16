package com.minova.cloud;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

final class UploadEngine {
    interface Progress {void update(String name,long sent,long size)throws Exception;}
    interface Stop {boolean stopped();}
    static final long MAX_SIZE=20L*1024*1024*1024;
    static final class Info {
        String name;long size;
        Info(String name,long size){this.name=name;this.size=size;}
    }
    final CloudApi api;final Context context;final Stop stop;long deadline;
    UploadEngine(CloudApi api,Stop stop){this.api=api;this.context=api.context;this.stop=stop;this.deadline=System.currentTimeMillis()+8*60*1000;}
    void allowLongTransfer(){deadline=System.currentTimeMillis()+5*60*60*1000L;}
    void check()throws IOException{if(stop.stopped()||System.currentTimeMillis()>deadline)throw new InterruptedIOException("Upload paused. Android will resume it automatically.");}
    Info info(Uri uri)throws Exception{
        if("file".equals(uri.getScheme())){File file=new File(uri.getPath());if(!file.isFile())throw new IOException("This file is no longer available.");return new Info(cleanName(file.getName()),file.length());}
        try(Cursor cursor=context.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE},null,null,null)){
            if(cursor!=null&&cursor.moveToFirst()){
                String name=cursor.getString(0);long size=cursor.isNull(1)?-1:cursor.getLong(1);
                if(size>=0)return new Info(cleanName(name),size);
            }
        }
        throw new IOException("This provider did not report a file size. Save the file on your phone first, then upload it.");
    }
    static String cleanName(String name){
        String safe=(name==null?"Upload":name).replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("[. ]+$","");
        if(safe.isEmpty())safe="Upload";
        if(safe.matches("(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])(?:\\..*)?$"))safe="_"+safe;
        return safe.length()>170?safe.substring(0,170):safe;
    }
    InputStream open(Uri uri)throws IOException{InputStream stream=context.getContentResolver().openInputStream(uri);if(stream==null)throw new IOException("This file is no longer available on the phone.");return stream;}
    String checksum(Uri uri)throws Exception{
        MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream stream=open(uri)){byte[] bytes=new byte[256*1024];int count;while((count=stream.read(bytes))!=-1){check();hash.update(bytes,0,count);}}return CloudApi.hex(hash.digest());
    }
    String upload(Uri uri,Info info,String target,String hash,boolean backup,Progress progress)throws Exception{
        if(info.size<0||info.size>MAX_SIZE)throw new IOException("The limit is 20 GB per file.");
        String fingerprint=CloudApi.sha256((api.userId()+"|"+uri+"|"+hash+"|"+target).getBytes(StandardCharsets.UTF_8));
        SharedPreferences pending=context.getSharedPreferences("pending-"+api.userId(),0);
        String id=null;long offset=0;
        String previous=pending.getString(fingerprint,null);
        if(previous!=null){try{JSONObject status=api.request("/api/uploads/"+previous,"GET",null);if(status.getLong("size")==info.size){id=previous;offset=status.getLong("received");target=status.getString("path");}}catch(CloudApi.ApiException error){if(error.status!=404)throw error;}}
        if(id==null){
            if(backup){
                String original=target;boolean available=false;
                for(int suffix=0;suffix<100;suffix++){
                    if(suffix>0){int dot=original.lastIndexOf('.'),slash=original.lastIndexOf('/');target=dot>slash?original.substring(0,dot)+" ("+(suffix+1)+")"+original.substring(dot):original+" ("+(suffix+1)+")";}
                    try{JSONObject existing=api.request("/api/files?path="+CloudApi.encode(target),"GET",null).getJSONArray("items").getJSONObject(0);
                        if(!existing.optBoolean("isDirectory")&&existing.getLong("size")==info.size&&hash.equals(api.request("/api/checksum?path="+CloudApi.encode(target),"GET",null).getString("sha256"))){progress.update(info.name,info.size,info.size);pending.edit().remove(fingerprint).apply();return target;}
                    }catch(CloudApi.ApiException error){if(error.status!=404)throw error;available=true;break;}
                }
                if(!available)throw new IOException("Too many files share this name. Rename the original on your phone and try again.");
            }
            JSONObject created=api.request("/api/uploads","POST",new JSONObject().put("path",target).put("size",info.size).put("sha256",hash));
            id=created.getString("id");if(!pending.edit().putString(fingerprint,id).commit())throw new IOException("Could not save upload progress.");
        }
        try(InputStream stream=open(uri)){
            long skipped=0;while(skipped<offset){check();long moved=stream.skip(offset-skipped);if(moved<=0){if(stream.read()==-1)throw new IOException("The source file changed during upload.");moved=1;}skipped+=moved;}
            byte[] bytes=new byte[2*1024*1024];
            while(offset<info.size){check();int wanted=(int)Math.min(bytes.length,info.size-offset),count=0;while(count<wanted){int read=stream.read(bytes,count,wanted-count);if(read==-1)throw new IOException("The source file changed during upload.");count+=read;}
                offset=api.chunk(id,offset,bytes,count).getLong("received");progress.update(info.name,offset,info.size);
            }
        }
        check();
        try{api.request("/api/uploads/"+id+"/complete","POST",new JSONObject());pending.edit().remove(fingerprint).apply();progress.update(info.name,info.size,info.size);return target;}
        catch(CloudApi.ApiException error){if(error.status==422){try{api.request("/api/uploads/"+id,"DELETE",null);}catch(Exception ignored){}pending.edit().remove(fingerprint).apply();}throw error;}
    }
}
