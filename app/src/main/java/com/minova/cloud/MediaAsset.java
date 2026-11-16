package com.minova.cloud;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.MediaStore;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

final class MediaAsset {
    final Uri uri;final String name;final long size,modified,added,generation;final boolean video;
    MediaAsset(Uri uri,String name,long size,long modified,long added,long generation,boolean video){this.uri=uri;this.name=name;this.size=size;this.modified=modified;this.added=added;this.generation=generation;this.video=video;}
    static void requireLocal(Uri uri)throws IOException{
        List<String> parts=uri.getPathSegments();
        if(!"content".equals(uri.getScheme())||!"media".equals(uri.getAuthority())||parts.size()!=4||!(parts.get(0).equals("external")||parts.get(0).equals("external_primary")||parts.get(0).matches("[a-fA-F0-9-]+"))||!(parts.get(1).equals("images")||parts.get(1).equals("video"))||!parts.get(2).equals("media"))throw new IOException("Select a local photo or video from On this phone.");
        try{if(ContentUris.parseId(uri)<=0)throw new NumberFormatException();}catch(Exception error){throw new IOException("Invalid phone media item.");}
    }
    static String[] columns(){return Build.VERSION.SDK_INT>=30?new String[]{"_id","_display_name","_size","date_modified","date_added","generation_modified"}:new String[]{"_id","_display_name","_size","date_modified","date_added"};}
    static MediaAsset read(Context context,Uri uri)throws IOException{
        requireLocal(uri);
        try(Cursor cursor=context.getContentResolver().query(uri,columns(),null,null,null)){
            if(cursor==null||!cursor.moveToFirst())throw new FileNotFoundException("This photo or video is no longer available on the phone.");
            return from(cursor,uri,uri.getPathSegments().get(1).equals("video"));
        }
    }
    static MediaAsset from(Cursor cursor,Uri uri,boolean video)throws IOException{long size=cursor.getLong(2);if(size<0||size>UploadEngine.MAX_SIZE)throw new IOException("This item exceeds the 20 GB limit.");return new MediaAsset(uri,cursor.getString(1),size,cursor.getLong(3),cursor.getLong(4),Build.VERSION.SDK_INT>=30?cursor.getLong(5):0,video);}
    static List<MediaAsset> list(Context context,boolean video,int limit)throws IOException{
        Uri collection=video?MediaStore.Video.Media.EXTERNAL_CONTENT_URI:MediaStore.Images.Media.EXTERNAL_CONTENT_URI;List<MediaAsset> result=new ArrayList<>();
        String selection=Build.VERSION.SDK_INT>=30?"is_pending=0 AND is_trashed=0":Build.VERSION.SDK_INT>=29?"is_pending=0":null;
        try(Cursor cursor=context.getContentResolver().query(collection,columns(),selection,null,"date_added DESC, _id DESC")){
            if(cursor!=null)while(cursor.moveToNext()&&result.size()<limit){try{result.add(from(cursor,ContentUris.withAppendedId(collection,cursor.getLong(0)),video));}catch(IOException ignored){}}
        }return result;
    }
    String assetKey(){return uri+"|"+size+"|"+modified;}
    boolean sameSource(MediaAsset other){return uri.equals(other.uri)&&size==other.size&&modified==other.modified&&generation==other.generation;}
    String folder(Context context,String user){android.content.SharedPreferences preferences=context.getSharedPreferences("backup-"+user,0);String device=preferences.getString("device",null);if(device==null){device=UUID.randomUUID().toString().substring(0,8);if(!preferences.edit().putString("device",device).commit())throw new IllegalStateException("Could not save this phone's backup identity.");}String month=new SimpleDateFormat("yyyy-MM",Locale.US).format(new Date(added*1000));return "Phone backups/Android-"+device+"/"+(video?"Videos":"Photos")+"/"+month;}
}
