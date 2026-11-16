package com.minova.cloud;

import android.content.Context;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class BackupStore extends SQLiteOpenHelper {
    BackupStore(Context context){super(context,"backup-receipts.db",null,1);}
    @Override public void onCreate(SQLiteDatabase db){db.execSQL("CREATE TABLE receipts (user TEXT NOT NULL, asset TEXT NOT NULL, target TEXT NOT NULL, PRIMARY KEY(user,asset))");}
    @Override public void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion){}
    boolean contains(String user,String asset){try(Cursor cursor=getReadableDatabase().rawQuery("SELECT 1 FROM receipts WHERE user=? AND asset=?",new String[]{user,asset})){return cursor.moveToFirst();}}
    void record(String user,String asset,String target){ContentValues values=new ContentValues();values.put("user",user);values.put("asset",asset);values.put("target",target);getWritableDatabase().insertWithOnConflict("receipts",null,values,SQLiteDatabase.CONFLICT_REPLACE);}
}
