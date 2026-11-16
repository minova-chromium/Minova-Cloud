package com.minova.cloud;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;

final class PhoneAccess {
    static boolean allFiles(){return Build.VERSION.SDK_INT>=30&&Environment.isExternalStorageManager();}
    static boolean fullMedia(Activity activity){return allFiles()||(Build.VERSION.SDK_INT>=33?activity.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)==PackageManager.PERMISSION_GRANTED&&activity.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)==PackageManager.PERMISSION_GRANTED:activity.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED);}
    static void media(Activity activity,int request){String[] permissions=Build.VERSION.SDK_INT>=34?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}:Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO}:new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};activity.requestPermissions(permissions,request);}
    static void files(Activity activity,int request){
        if(Build.VERSION.SDK_INT<30){media(activity,request);return;}
        try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,Uri.parse("package:"+activity.getPackageName())));}
        catch(ActivityNotFoundException error){activity.startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));}
    }
}
