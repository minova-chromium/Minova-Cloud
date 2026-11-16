package com.minova.cloud;

import android.content.Context;
import android.os.Build;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

final class CloudApi {
    static final class ApiException extends IOException {final int status;ApiException(int status,String message){super(message);this.status=status;}}
    final Context context;
    final JSONObject session;
    CloudApi(Context context){this(context,SecureSession.load(context));}
    CloudApi(Context context,JSONObject session){this.context=context.getApplicationContext();this.session=session;}
    String userId(){return session==null?"":session.optJSONObject("user").optString("id");}
    String origin(){return BuildConfig.DEBUG?context.getSharedPreferences("qa",0).getString("origin",BuildConfig.CLOUD_ORIGIN):BuildConfig.CLOUD_ORIGIN;}
    static String encode(String value){return android.net.Uri.encode(value);}
    static String sha256(byte[] value)throws Exception{return hex(MessageDigest.getInstance("SHA-256").digest(value));}
    static String hex(byte[] value){StringBuilder result=new StringBuilder();for(byte b:value)result.append(String.format(Locale.US,"%02x",b&255));return result.toString();}
    HttpURLConnection connection(String route,String method,boolean authenticate)throws Exception{
        if(!route.startsWith("/api/"))throw new IOException("Unsupported Cloud request.");
        HttpURLConnection connection=(HttpURLConnection)new URL(origin()+route).openConnection();
        connection.setInstanceFollowRedirects(false);connection.setConnectTimeout(20000);connection.setReadTimeout(route.startsWith("/api/checksum?")?10*60*1000:120000);connection.setRequestMethod(method);
        connection.setRequestProperty("Accept","application/json");
        if(authenticate&&session!=null){connection.setRequestProperty("Authorization","Bearer "+session.getString("token"));connection.setRequestProperty("X-Minova-CSRF",session.getString("csrf"));}
        return connection;
    }
    static byte[] read(InputStream stream,long maximum)throws IOException{
        if(stream==null)return new byte[0];
        try(InputStream input=stream;ByteArrayOutputStream output=new ByteArrayOutputStream()){
            byte[] buffer=new byte[32768];long total=0;int count;
            while((count=input.read(buffer))!=-1){total+=count;if(total>maximum)throw new IOException("This preview is too large. Use Open or Download instead.");output.write(buffer,0,count);}return output.toByteArray();
        }
    }
    JSONObject response(HttpURLConnection connection)throws Exception{
        int status=connection.getResponseCode();byte[] bytes=read(status>=200&&status<300?connection.getInputStream():connection.getErrorStream(),8*1024*1024);
        JSONObject value;try{value=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}catch(Exception error){throw new ApiException(status,"Minova Cloud could not answer. Try again shortly.");}
        if(status<200||status>=300)throw new ApiException(status,value.optString("error","Cloud request failed."));return value;
    }
    JSONObject request(String route,String method,JSONObject body)throws Exception{return request(route,method,body,true,null);}
    JSONObject request(String route,String method,JSONObject body,boolean authenticate,String cookie)throws Exception{
        HttpURLConnection connection=connection(route,method,authenticate);
        try{
            if(cookie!=null)connection.setRequestProperty("Cookie",cookie);
            if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.length);try(OutputStream output=connection.getOutputStream()){output.write(bytes);}}
            return response(connection);
        }finally{connection.disconnect();}
    }
    JSONObject login(String username,String password,String name,String invite)throws Exception{
        JSONObject body=new JSONObject().put("username",username).put("password",password);
        if(invite!=null){body.put("name",name).put("token",invite);}
        HttpURLConnection login=connection(invite==null?"/api/login":"/api/join","POST",false);
        String cookie;String csrf;
        try{
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);login.setDoOutput(true);login.setRequestProperty("Content-Type","application/json");login.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream output=login.getOutputStream()){output.write(bytes);}
            JSONObject result=response(login);csrf=result.getString("csrf");cookie=login.getHeaderField("Set-Cookie");if(cookie==null)throw new IOException("Could not create a sign-in session.");cookie=cookie.split(";",2)[0];
        }finally{login.disconnect();}
        HttpURLConnection connect=connection("/api/devices","POST",false);
        try{
            connect.setRequestProperty("Cookie",cookie);connect.setRequestProperty("X-Minova-CSRF",csrf);connect.setRequestProperty("Content-Type","application/json");connect.setDoOutput(true);
            byte[] bytes=new JSONObject().put("name",Build.MANUFACTURER+" "+Build.MODEL).toString().getBytes(StandardCharsets.UTF_8);connect.setFixedLengthStreamingMode(bytes.length);
            try(OutputStream output=connect.getOutputStream()){output.write(bytes);}
            JSONObject result=response(connect);SecureSession.save(context,result);return result;
        }finally{
            connect.disconnect();HttpURLConnection logout=connection("/api/logout","POST",false);
            try{logout.setRequestProperty("Cookie",cookie);logout.setRequestProperty("X-Minova-CSRF",csrf);logout.getResponseCode();}catch(Exception ignored){}finally{logout.disconnect();}
        }
    }
    JSONObject chunk(String id,long offset,byte[] bytes,int length)throws Exception{
        HttpURLConnection connection=connection("/api/uploads/"+id+"?offset="+offset,"PUT",true);
        try{connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/octet-stream");connection.setFixedLengthStreamingMode(length);try(OutputStream output=connection.getOutputStream()){output.write(bytes,0,length);}return response(connection);}finally{connection.disconnect();}
    }
    String fileRoute(JSONObject item,JSONObject share,boolean inline){return "/api/download?path="+encode(item.optString("path"))+(share==null?"":"&share="+encode(share.optString("id")))+(inline?"&inline=1":"");}
    Map<String,String> mediaHeaders(){Map<String,String> headers=new HashMap<>();headers.put("Authorization","Bearer "+session.optString("token"));return headers;}
    void ensureFolder(String path)throws Exception{
        String built="";for(String part:path.split("/")){if(part.isEmpty())continue;built=built.isEmpty()?part:built+"/"+part;
            try{request("/api/files?path="+encode(built),"GET",null);}catch(ApiException error){if(error.status!=404)throw error;try{request("/api/folders","POST",new JSONObject().put("path",built));}catch(ApiException exists){if(exists.status!=409)throw exists;}}}
    }
}
