package com.minova.cloud;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public final class PhoneFilesActivity extends PhoneScreen {
    File root,current;List<File> entries=new ArrayList<>();ListView list;TextView location,upload;final LinkedHashSet<String> selected=new LinkedHashSet<>();int generation;
    @Override public void onCreate(Bundle state){super.onCreate(state);if(api==null||api.session==null)return;heading("Files on this phone");description("Choose files to upload to Minova Cloud. Phone originals are kept.");root=Environment.getExternalStorageDirectory();current=root;
        LinearLayout tools=new LinearLayout(this);tools.addView(action("Allow file access",false,()->PhoneAccess.files(this,211)),new LinearLayout.LayoutParams(0,dp(46),1));TextView picker=action("Android file picker",false,this::documents);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(46),1);params.leftMargin=dp(8);tools.addView(picker,params);add(page,tools,46);
        location=label("Phone storage",12,MinovaUi.CYAN);location.setPadding(0,dp(12),0,dp(12));add(page,location,-2);list=new ListView(this);list.setCacheColorHint(MinovaUi.NIGHT);list.setDividerHeight(dp(1));page.addView(list,new LinearLayout.LayoutParams(-1,0,1));list.setOnItemClickListener((p,v,position,id)->choose(position));status();upload=action("Upload selected files",true,this::upload);add(page,upload,48);refresh();
    }
    @Override protected void onResume(){super.onResume();if(list!=null)refresh();}
    void documents(){Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(intent,212);}
    boolean allowed(File file)throws IOException{String canonical=file.getCanonicalPath(),base=root.getCanonicalPath();return file.getAbsolutePath().equals(canonical)&&(canonical.equals(base)||canonical.startsWith(base+File.separator));}
    void refresh(){if(current==null)return;location.setText(current.equals(root)?"Phone storage":current.getAbsolutePath());int serial=++generation;
        if(Build.VERSION.SDK_INT>=29&&!PhoneAccess.allFiles()){entries.clear();list.setAdapter(new FileAdapter());status.setText("Allow all-files access to browse shared phone storage, or use Android's file picker.");return;}
        io.execute(()->{try{if(!allowed(current))throw new IOException("Linked folders are not browsed.");File[] files=current.listFiles();if(files==null)throw new IOException("Android does not allow access to this folder.");List<File> result=new ArrayList<>();Arrays.sort(files,(a,b)->a.isDirectory()!=b.isDirectory()?(a.isDirectory()?-1:1):a.getName().compareToIgnoreCase(b.getName()));for(File file:files){if(result.size()>=2000)break;if(allowed(file))result.add(file);}ui(()->{if(serial!=generation)return;entries=result;list.setAdapter(new FileAdapter());update();});}catch(Exception error){ui(()->{entries.clear();list.setAdapter(new FileAdapter());status.setText(error.getMessage());});}});
    }
    void choose(int index){File file=entries.get(index);if(file.isDirectory()){current=file;refresh();return;}String path=file.getAbsolutePath();if(!selected.remove(path)){if(selected.size()>=100){message("Select up to 100 files at a time.");return;}if(file.length()>UploadEngine.MAX_SIZE){message("The limit is 20 GB per file.");return;}selected.add(path);}((BaseAdapter)list.getAdapter()).notifyDataSetChanged();update();}
    void update(){status.setText(selected.size()+" files selected · "+entries.size()+" items shown");upload.setText(selected.isEmpty()?"Upload selected files":"Upload "+selected.size()+" files");}
    void upload(){if(selected.isEmpty()){message("Select a file first.");return;}try{SafeOffload.current(this,user);String folder=getIntent().getStringExtra("path");if(folder==null)folder="";for(String path:selected){File file=new File(path);if(!allowed(file)||!file.isFile())throw new IOException("A selected file is no longer available.");TransferWork.upload(this,user,Uri.fromFile(file),folder);}selected.clear();message("Uploads queued. Your phone originals are kept.");refresh();}catch(Exception error){message(error.getMessage());}}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=212||result!=RESULT_OK||data==null)return;try{SafeOffload.current(this,user);List<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<Math.min(100,data.getClipData().getItemCount());i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());String folder=getIntent().getStringExtra("path");for(Uri uri:uris){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}TransferWork.upload(this,user,uri,folder==null?"":folder);}message("Queued "+uris.size()+" files. Phone originals are kept.");}catch(Exception error){message(error.getMessage());}}
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants){super.onRequestPermissionsResult(code,permissions,grants);refresh();}
    @Override void back(){if(current!=null&&!current.equals(root)){current=current.getParentFile();refresh();}else super.back();}
    final class FileAdapter extends BaseAdapter {
        public int getCount(){return entries.size();}public Object getItem(int p){return entries.get(p);}public long getItemId(int p){return p;}
        public View getView(int p,View old,ViewGroup parent){File file=entries.get(p);LinearLayout row=new LinearLayout(PhoneFilesActivity.this);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(10),dp(13),dp(10),dp(13));row.addView(MinovaUi.icon(PhoneFilesActivity.this,file.isDirectory()?"folder":"file",MinovaUi.CYAN,24));TextView name=label(file.getName(),14,MinovaUi.WHITE);name.setPadding(dp(12),0,dp(10),0);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));row.addView(label(file.isDirectory()?"›":selected.contains(file.getAbsolutePath())?"✓":"○",20,MinovaUi.CYAN));return row;}
    }
}
