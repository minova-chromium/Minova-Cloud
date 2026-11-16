package com.minova.cloud;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.Build;
import android.provider.Settings;
import androidx.core.content.FileProvider;
import androidx.work.*;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;

/** Updates come only from the official stable release and must retain the installed signing identity. */
final class AppUpdater {
    static final String REPOSITORY="minova-chromium/Minova-Cloud",PACKAGE="com.minova.cloud";
    static final String RELEASES="https://github.com/"+REPOSITORY+"/releases/";
    static final Semaphore LOCK=new Semaphore(1);
    static final long MAX_APK=128L*1024*1024;
    static final class Release {
        final String version,hash,certificate;final int code;
        Release(String version,int code,String hash,String certificate){this.version=version;this.code=code;this.hash=hash;this.certificate=certificate;}
        String url(){return RELEASES+"download/v"+version+"/Minova-Cloud.apk";}
    }
    static SharedPreferences settings(Context context){return context.getSharedPreferences("app-updates",0);}
    static void schedule(Context context){
        if(BuildConfig.DEBUG)return;
        Constraints network=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest work=new PeriodicWorkRequest.Builder(AppUpdateWorker.class,12,TimeUnit.HOURS).setConstraints(network).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("minova-app-updates",ExistingPeriodicWorkPolicy.UPDATE,work);
        long last=settings(context).getLong("checked",0);
        if(System.currentTimeMillis()-last>6*60*60*1000L)checkSoon(context);
    }
    static void checkSoon(Context context){if(BuildConfig.DEBUG)return;OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(AppUpdateWorker.class).setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();WorkManager.getInstance(context).enqueueUniqueWork("minova-check-update",ExistingWorkPolicy.KEEP,work);}
    static boolean safeUrl(URL url){return "https".equals(url.getProtocol())&&(url.getPort()==-1||url.getPort()==443)&&url.getUserInfo()==null&&Arrays.asList("github.com","release-assets.githubusercontent.com","objects.githubusercontent.com").contains(url.getHost());}
    static HttpURLConnection open(String value)throws IOException{
        URL url=new URL(value);
        for(int redirects=0;redirects<6;redirects++){
            if(!safeUrl(url))throw new IOException("Update download left the trusted release service.");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(120000);connection.setRequestProperty("User-Agent","Minova-Cloud-Updater/"+BuildConfig.VERSION_NAME);
            int status=connection.getResponseCode();if(status>=300&&status<400){String location=connection.getHeaderField("Location");connection.disconnect();if(location==null)throw new IOException("Update redirect was incomplete.");url=new URL(url,location);continue;}
            if(status!=200){connection.disconnect();throw new IOException("Update service is unavailable ("+status+"). Your current app is kept.");}return connection;
        }throw new IOException("Too many update redirects.");
    }
    static String normalize(String certificate){return certificate.replace(":","").toLowerCase(Locale.ROOT);}
    static String installedCertificate(Context context)throws Exception{return certificate(context.getPackageManager().getPackageInfo(context.getPackageName(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES));}
    static String certificate(PackageInfo info)throws Exception{
        Signature[] signatures=Build.VERSION.SDK_INT>=28?(info.signingInfo==null?null:info.signingInfo.getApkContentsSigners()):info.signatures;
        if(signatures==null||signatures.length!=1)throw new IOException("This app's signing identity could not be verified.");return CloudApi.sha256(signatures[0].toByteArray());
    }
    static Release parse(JSONObject value,String trustedCertificate)throws Exception{
        String version=value.getString("version"),hash=value.getString("sha256"),cert=normalize(value.getString("certificateSha256"));int code=value.getInt("versionCode");
        if(!version.matches("[0-9]{1,6}\\.[0-9]{1,6}\\.[0-9]{1,6}")||code<1||!PACKAGE.equals(value.getString("package"))||!"Minova-Cloud.apk".equals(value.getString("file"))||!hash.matches("[a-f0-9]{64}")||!cert.equals(normalize(trustedCertificate)))throw new IOException("The update identity could not be verified. Your current app is kept.");return new Release(version,code,hash,cert);
    }
    static Release latest(Context context)throws Exception{
        HttpURLConnection connection=open(RELEASES+"latest/download/release.json");try{byte[] bytes=CloudApi.read(connection.getInputStream(),65536);return parse(new JSONObject(new String(bytes,StandardCharsets.UTF_8)),installedCertificate(context));}finally{connection.disconnect();}
    }
    static File file(Context context,Release release){return new File(new File(context.getFilesDir(),"updates"),"minova-cloud-"+release.code+".apk");}
    static String hash(File file)throws Exception{MessageDigest hash=MessageDigest.getInstance("SHA-256");try(InputStream input=new FileInputStream(file)){byte[] bytes=new byte[128*1024];int count;while((count=input.read(bytes))!=-1)hash.update(bytes,0,count);}return CloudApi.hex(hash.digest());}
    static void verifyArchive(Context context,File file,Release release,String trustedCertificate)throws Exception{
        if(!file.isFile()||file.length()<1||file.length()>MAX_APK||!release.hash.equals(hash(file)))throw new IOException("The downloaded update failed its checksum check.");
        PackageInfo info=context.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(),Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES);
        if(info==null||!PACKAGE.equals(info.packageName)||(Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode)!=release.code||!release.version.equals(info.versionName)||!normalize(trustedCertificate).equals(certificate(info)))throw new IOException("This APK is not a compatible signed Minova Cloud update.");
    }
    static void download(Context context,Release release)throws Exception{
        File target=file(context,release);File directory=target.getParentFile();if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("Could not create the update folder.");
        if(target.isFile()){try{verifyArchive(context,target,release,installedCertificate(context));return;}catch(Exception invalid){if(!target.delete())throw new IOException("Could not replace an incomplete update.");}}
        File partial=new File(directory,target.getName()+".part");HttpURLConnection connection=open(release.url());long received=0;
        try{if(connection.getContentLengthLong()>MAX_APK)throw new IOException("The update exceeds the supported download size.");
            try(InputStream input=connection.getInputStream();OutputStream output=new FileOutputStream(partial)){byte[] bytes=new byte[128*1024];int count;while((count=input.read(bytes))!=-1){if(Thread.currentThread().isInterrupted())throw new InterruptedIOException("Update download paused.");received+=count;if(received>MAX_APK)throw new IOException("The update exceeds the supported download size.");output.write(bytes,0,count);}}
            verifyArchive(context,partial,release,installedCertificate(context));if(!partial.renameTo(target))throw new IOException("Could not finish saving the update.");
        }finally{connection.disconnect();if(partial.exists())partial.delete();}
    }
    static void check(Context context,boolean manualDownload)throws Exception{
        if(manualDownload)LOCK.acquire();else if(!LOCK.tryAcquire())return;
        SharedPreferences preferences=settings(context);
        try{
            if(BuildConfig.DEBUG){preferences.edit().putString("status","Automatic updates are disabled in debug builds.").apply();return;}
            preferences.edit().putString("status","Checking for updates…").apply();Release release=latest(context);preferences.edit().putLong("checked",System.currentTimeMillis()).apply();
            if(release.code<=BuildConfig.VERSION_CODE){preferences.edit().remove("available").remove("ready").putString("status","You're up to date · "+BuildConfig.VERSION_NAME).apply();return;}
            JSONObject saved=new JSONObject().put("version",release.version).put("versionCode",release.code).put("sha256",release.hash).put("certificateSha256",release.certificate).put("package",PACKAGE).put("file","Minova-Cloud.apk");preferences.edit().putString("available",saved.toString()).apply();
            boolean metered=context.getSystemService(ConnectivityManager.class).isActiveNetworkMetered();
            if(manualDownload||!preferences.getBoolean("wifiOnly",true)||!metered){preferences.edit().putString("status","Downloading Minova Cloud "+release.version+"…").apply();download(context,release);preferences.edit().putInt("ready",release.code).putString("status","Version "+release.version+" is ready to install.").apply();}
            else preferences.edit().putString("status","Version "+release.version+" is available. Automatic download is waiting for Wi-Fi.").apply();
            notifyUpdate(context,release);
        }catch(Exception error){preferences.edit().putString("status",error.getMessage()==null?"Update check failed. Your current app is kept.":error.getMessage()).apply();throw error;}
        finally{LOCK.release();}
    }
    static Release available(Context context){try{String value=settings(context).getString("available",null);if(value==null)return null;Release release=parse(new JSONObject(value),installedCertificate(context));return release.code>BuildConfig.VERSION_CODE?release:null;}catch(Exception error){return null;}}
    static void notifyUpdate(Context context,Release release){SharedPreferences preferences=settings(context);if(preferences.getInt("notified",0)==release.code)return;NotificationManager manager=context.getSystemService(NotificationManager.class);if(!manager.areNotificationsEnabled())return;
        manager.createNotificationChannel(new NotificationChannel("cloud-updates","Minova Cloud updates",NotificationManager.IMPORTANCE_DEFAULT));PendingIntent open=PendingIntent.getActivity(context,73,new Intent(context,AppUpdateActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        manager.notify(73,new Notification.Builder(context,"cloud-updates").setSmallIcon(R.drawable.minova_cloud_notification).setContentTitle("Minova Cloud "+release.version+" is available").setContentText("Tap to update inside Minova Cloud").setContentIntent(open).setAutoCancel(true).build());preferences.edit().putInt("notified",release.code).apply();
    }
    static void prompt(Activity activity){if(BuildConfig.DEBUG||activity.isFinishing())return;Release release=available(activity);if(release==null)return;SharedPreferences preferences=settings(activity);long now=System.currentTimeMillis();if(preferences.getInt("prompted",0)==release.code&&now-preferences.getLong("promptedAt",0)<24*60*60*1000L)return;preferences.edit().putInt("prompted",release.code).putLong("promptedAt",now).apply();
        new AlertDialog.Builder(activity).setTitle("Minova Cloud update").setMessage("Version "+release.version+" is available. Update here without visiting the website. Your sign-in and files are kept.").setNegativeButton("Later",null).setPositiveButton("Update",(d,w)->activity.startActivity(new Intent(activity,AppUpdateActivity.class).putExtra("install",true))).show();
    }
    static void install(Activity activity,Release release)throws Exception{
        if(release.code<=BuildConfig.VERSION_CODE)throw new IOException("This update is already installed.");
        verifyArchive(activity,file(activity,release),release,installedCertificate(activity));
        if(!activity.getPackageManager().canRequestPackageInstalls()){activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));return;}
        Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".files",file(activity,release));activity.startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    }
}
