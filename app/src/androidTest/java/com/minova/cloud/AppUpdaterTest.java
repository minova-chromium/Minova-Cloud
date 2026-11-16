package com.minova.cloud;
import android.content.*;
import android.net.Uri;
import androidx.core.content.FileProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.*;
import java.net.URL;
import static org.junit.Assert.*;
@RunWith(AndroidJUnit4.class)
public class AppUpdaterTest {
    static final String CERT="de8c98c42644ccb7339047b6a4b6bd73656fda55219944beba57c35a97636430";
    Context context(){return InstrumentationRegistry.getInstrumentation().getTargetContext();}
    interface Checked {void run()throws Exception;}
    void blocked(Checked action)throws Exception{try{action.run();fail("Unsafe update accepted");}catch(IOException expected){}}
    JSONObject metadata(String hash)throws Exception{return new JSONObject().put("version","1.3.0").put("versionCode",4).put("package","com.minova.cloud").put("file","Minova-Cloud.apk").put("sha256",hash).put("certificateSha256",CERT);}
    @Test public void metadataPinsPackageCertificateAndImmutableRelease()throws Exception{
        JSONObject value=metadata(new String(new char[64]).replace('\0','a'));AppUpdater.Release release=AppUpdater.parse(value,CERT);assertEquals("https://github.com/minova-chromium/Minova-Cloud/releases/download/v1.3.0/Minova-Cloud.apk",release.url());
        blocked(()->AppUpdater.parse(metadata(release.hash).put("version","../../evil"),CERT));
        blocked(()->AppUpdater.parse(metadata(release.hash).put("package","another.app"),CERT));
        blocked(()->AppUpdater.parse(metadata(release.hash).put("versionCode",-1),CERT));
        blocked(()->AppUpdater.parse(metadata(release.hash).put("certificateSha256",CERT.replace('d','f')),CERT));
        blocked(()->AppUpdater.parse(metadata("invalid"),CERT));
    }
    @Test public void downloadsRejectUntrustedHostsAndTransport()throws Exception{
        assertTrue(AppUpdater.safeUrl(new URL("https://github.com/minova-chromium/Minova-Cloud")));
        for(String url:new String[]{"http://github.com/a","https://github.com.evil.example/a","https://github.com:444/a","https://user@github.com/a","https://example.com/a"})assertFalse(url,AppUpdater.safeUrl(new URL(url)));
    }
    @Test public void apkRequiresMatchingHashPackageVersionAndRealSigningCertificate()throws Exception{
        File file=new File(context().getCacheDir(),"qa-update.apk");
        try(InputStream input=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open("qa-update.apk");OutputStream output=new FileOutputStream(file)){byte[] buffer=new byte[65536];int count;while((count=input.read(buffer))!=-1)output.write(buffer,0,count);}
        try {
            String hash=AppUpdater.hash(file);AppUpdater.Release release=AppUpdater.parse(metadata(hash),CERT);AppUpdater.verifyArchive(context(),file,release,CERT);
            blocked(()->AppUpdater.verifyArchive(context(),file,new AppUpdater.Release("1.3.0",5,hash,CERT),CERT));
            blocked(()->AppUpdater.verifyArchive(context(),file,new AppUpdater.Release("9.0.0",4,hash,CERT),CERT));
            blocked(()->AppUpdater.verifyArchive(context(),file,release,CERT.replace('d','f')));
            try(OutputStream output=new FileOutputStream(file,true)){output.write(1);}blocked(()->AppUpdater.verifyArchive(context(),file,release,CERT));
            try(OutputStream output=new FileOutputStream(file)){output.write(new byte[]{1,2,3});}blocked(()->AppUpdater.verifyArchive(context(),file,new AppUpdater.Release("1.3.0",4,AppUpdater.hash(file),CERT),CERT));
        } finally {assertTrue(file.delete());}
    }
    @Test public void updateScreenLoadsWithoutMinovaLoginAndApkUriIsScoped()throws Exception{
        android.app.Instrumentation instrumentation=InstrumentationRegistry.getInstrumentation();android.app.Activity screen=instrumentation.startActivitySync(new Intent(context(),AppUpdateActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));instrumentation.waitForIdleSync();assertFalse(screen.isFinishing());instrumentation.runOnMainSync(screen::finish);
        File directory=new File(context().getFilesDir(),"updates");assertTrue(directory.isDirectory()||directory.mkdirs());File apk=new File(directory,"scope-test.apk");try{assertTrue(apk.createNewFile());Uri uri=FileProvider.getUriForFile(context(),context().getPackageName()+".files",apk);assertEquals("content",uri.getScheme());assertEquals(context().getPackageName()+".files",uri.getAuthority());}finally{assertTrue(apk.delete());}
        try{FileProvider.getUriForFile(context(),context().getPackageName()+".files",new File(context().getFilesDir(),"account.dpapi"));fail("Private profile exposed");}catch(IllegalArgumentException expected){}
    }
}
