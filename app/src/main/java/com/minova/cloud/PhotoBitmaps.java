package com.minova.cloud;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import androidx.exifinterface.media.ExifInterface;
import java.io.ByteArrayInputStream;

final class PhotoBitmaps {
    static Bitmap decode(byte[] bytes,int maximum){
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);options.inSampleSize=1;
        while(Math.max(options.outWidth,options.outHeight)/options.inSampleSize>maximum)options.inSampleSize*=2;
        options.inJustDecodeBounds=false;Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);
        if(bitmap==null)return null;
        try{
            ExifInterface metadata=new ExifInterface(new ByteArrayInputStream(bytes));Matrix matrix=new Matrix();
            if(metadata.isFlipped())matrix.postScale(-1,1);matrix.postRotate(metadata.getRotationDegrees());
            if(!matrix.isIdentity()){Bitmap oriented=Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),matrix,true);if(oriented!=bitmap)bitmap.recycle();return oriented;}
        }catch(Exception ignored){}
        return bitmap;
    }
}
