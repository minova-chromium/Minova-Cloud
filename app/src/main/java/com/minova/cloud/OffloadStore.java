package com.minova.cloud;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.net.Uri;
import java.io.IOException;
import java.util.*;

final class OffloadStore extends SQLiteOpenHelper {
    static final int MAX_ITEMS=20;
    static final class Batch {String id,user,state,note;Batch(String id,String user,String state,String note){this.id=id;this.user=user;this.state=state;this.note=note;}}
    static final class Item {MediaAsset asset;String hash,target;boolean uploaded;Item(MediaAsset asset,String hash,String target,boolean uploaded){this.asset=asset;this.hash=hash;this.target=target;this.uploaded=uploaded;}}
    OffloadStore(Context context){super(context,"phone-cleanup.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE batches (id TEXT PRIMARY KEY,user TEXT NOT NULL,state TEXT NOT NULL,note TEXT NOT NULL,created INTEGER NOT NULL)");db.execSQL("CREATE TABLE items (batch TEXT NOT NULL,uri TEXT NOT NULL,name TEXT NOT NULL,size INTEGER NOT NULL,modified INTEGER NOT NULL,added INTEGER NOT NULL,generation INTEGER NOT NULL,video INTEGER NOT NULL,hash TEXT,target TEXT,uploaded INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(batch,uri))");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    String create(String user,List<MediaAsset> assets)throws IOException{
        if(assets.isEmpty()||assets.size()>MAX_ITEMS)throw new IOException("Select between 1 and 20 photos or videos.");
        Set<String> unique=new HashSet<>();for(MediaAsset asset:assets){MediaAsset.requireLocal(asset.uri);if(!unique.add(asset.uri.toString()))throw new IOException("A media item was selected twice.");}
        String id=UUID.randomUUID().toString();SQLiteDatabase db=getWritableDatabase();db.beginTransaction();
        try{ContentValues batch=new ContentValues();batch.put("id",id);batch.put("user",user);batch.put("state","uploading");batch.put("note","Waiting to back up selected media");batch.put("created",System.currentTimeMillis());db.insertOrThrow("batches",null,batch);
            for(MediaAsset asset:assets){ContentValues values=new ContentValues();values.put("batch",id);values.put("uri",asset.uri.toString());values.put("name",asset.name);values.put("size",asset.size);values.put("modified",asset.modified);values.put("added",asset.added);values.put("generation",asset.generation);values.put("video",asset.video?1:0);db.insertOrThrow("items",null,values);}db.setTransactionSuccessful();
        }finally{db.endTransaction();}return id;
    }
    Batch get(String user,String id)throws IOException{try(Cursor cursor=getReadableDatabase().rawQuery("SELECT id,user,state,note FROM batches WHERE id=? AND user=?",new String[]{id,user})){if(!cursor.moveToFirst())throw new IOException("This cleanup belongs to another account or is no longer available.");return new Batch(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3));}}
    Batch pending(String user){try(Cursor cursor=getReadableDatabase().rawQuery("SELECT id,user,state,note FROM batches WHERE user=? AND state NOT IN ('done','cancelled') ORDER BY created ASC LIMIT 1",new String[]{user})){return cursor.moveToFirst()?new Batch(cursor.getString(0),cursor.getString(1),cursor.getString(2),cursor.getString(3)):null;}}
    List<Item> items(String user,String id)throws IOException{get(user,id);List<Item> result=new ArrayList<>();try(Cursor cursor=getReadableDatabase().rawQuery("SELECT uri,name,size,modified,added,generation,video,hash,target,uploaded FROM items WHERE batch=? ORDER BY rowid",new String[]{id})){while(cursor.moveToNext())result.add(new Item(new MediaAsset(Uri.parse(cursor.getString(0)),cursor.getString(1),cursor.getLong(2),cursor.getLong(3),cursor.getLong(4),cursor.getLong(5),cursor.getInt(6)!=0),cursor.getString(7),cursor.getString(8),cursor.getInt(9)!=0));}return result;}
    void state(String user,String id,String state,String note)throws IOException{get(user,id);ContentValues values=new ContentValues();values.put("state",state);values.put("note",note);getWritableDatabase().update("batches",values,"id=? AND user=?",new String[]{id,user});}
    void progress(String user,String id,String note){ContentValues values=new ContentValues();values.put("note",note);getWritableDatabase().update("batches",values,"id=? AND user=? AND state='uploading'",new String[]{id,user});}
    void uploaded(String user,String id,Uri uri,String hash,String target)throws IOException{get(user,id);ContentValues values=new ContentValues();values.put("hash",hash);values.put("target",target);values.put("uploaded",1);getWritableDatabase().update("items",values,"batch=? AND uri=?",new String[]{id,uri.toString()});}
}
