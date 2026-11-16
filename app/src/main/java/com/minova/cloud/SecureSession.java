package com.minova.cloud;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureSession {
    private static final String ALIAS="minova.cloud.device.v1";
    private static SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(store.containsAlias(ALIAS))return (SecretKey)store.getKey(ALIAS,null);
        KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }
    static synchronized void save(Context context,JSONObject session)throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key());
        String payload=Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)+":"+Base64.encodeToString(cipher.doFinal(session.toString().getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        if(!context.getSharedPreferences("credentials",0).edit().putString("session",payload).commit())throw new Exception("Could not save this device securely.");
    }
    static synchronized JSONObject load(Context context){
        try{
            String payload=context.getSharedPreferences("credentials",0).getString("session",null);if(payload==null)return null;
            String[] parts=payload.split(":",2);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
            return new JSONObject(new String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8));
        }catch(Exception ignored){clear(context);return null;}
    }
    static synchronized void clear(Context context){context.getSharedPreferences("credentials",0).edit().remove("session").commit();}
}
