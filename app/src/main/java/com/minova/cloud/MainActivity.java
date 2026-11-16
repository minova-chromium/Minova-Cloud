package com.minova.cloud;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import android.util.LruCache;
import androidx.core.content.FileProvider;
import androidx.work.WorkManager;
import org.json.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.*;

public final class MainActivity extends Activity {
    private static final int NIGHT=Color.rgb(13,18,26),SURFACE=Color.rgb(17,24,35),LINE=Color.rgb(37,50,66),CYAN=Color.rgb(22,216,228),WHITE=Color.rgb(247,250,252),MUTED=Color.rgb(167,181,197);
    private final ExecutorService io=Executors.newFixedThreadPool(4);
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final LruCache<String,Bitmap> thumbnails=new LruCache<>(20*1024){@Override protected int sizeOf(String key,Bitmap value){return value.getByteCount()/1024;}};
    private LinearLayout root,body,header,content,bottom;
    private TextView syncStatus;
    private EditText search;
    private JSONObject session,share;
    private String view="files",path="",query="",invitation;
    private int serial=0,libraryLimit=100;
    private boolean loading=false,started=false,foreground=false;
    private AlertDialog dialog;
    private final Runnable refreshLoop=new Runnable(){@Override public void run(){if(foreground&&session!=null&&started){if(dialog==null||!dialog.isShowing())load(false);updateTransferStatus();}handler.postDelayed(this,10000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);readInvitation(getIntent());session=SecureSession.load(this);showRoot();handler.post(refreshLoop);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readInvitation(intent);if(invitation!=null){session=null;showRoot();}}
    private void readInvitation(Intent intent){Uri uri=intent.getData();if(uri!=null&&"https".equals(uri.getScheme())&&"cloud.project-minova.com".equals(uri.getHost())&&uri.getFragment()!=null)invitation=Uri.parse("https://cloud.project-minova.com/?"+uri.getFragment()).getQueryParameter("invite");}
    @Override protected void onResume(){super.onResume();foreground=true;if(started&&session!=null)load(false);}
    @Override protected void onPause(){foreground=false;super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
    @Override public void onBackPressed(){if(share!=null||!path.isEmpty()){if(!path.isEmpty()){int slash=path.lastIndexOf('/');path=slash<0?"":path.substring(0,slash);}else{share=null;view="shared";}query="";renderWorkspace();}else if(!view.equals("files")){navigate("files");}else super.onBackPressed();}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private TextView text(String value,float size,int color){TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);return label;}
    private GradientDrawable panel(int color){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(8));shape.setStroke(dp(1),LINE);return shape;}
    private Button button(String value,boolean primary,Runnable action){Button button=new Button(this);button.setText(value);button.setAllCaps(false);button.setTextSize(13);button.setTextColor(primary?NIGHT:WHITE);button.setBackground(panel(primary?CYAN:SURFACE));button.setMinHeight(dp(46));button.setPadding(dp(16),dp(6),dp(16),dp(6));button.setOnClickListener(v->action.run());return button;}
    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void add(LinearLayout parent,View child,int height){parent.addView(child,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    private void section(LinearLayout parent,String label,String description){TextView heading=text(label,24,WHITE);heading.setTypeface(null,Typeface.BOLD);add(parent,heading,-2);if(description!=null){space(parent,8);TextView detail=text(description,13,MUTED);detail.setLineSpacing(dp(3),1);add(parent,detail,-2);}space(parent,22);}
    private void base(){
        root=column();root.setBackgroundColor(NIGHT);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets inset=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(0,inset.top,0,inset.bottom);return insets;});}else root.setFitsSystemWindows(true);
        header=row();header.setPadding(dp(20),dp(10),dp(20),dp(10));ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.minova_icon);header.addView(logo,new LinearLayout.LayoutParams(dp(38),dp(38)));TextView brand=text("MINOVA  /  Cloud",17,WHITE);brand.setTypeface(null,Typeface.BOLD);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.leftMargin=dp(12);header.addView(brand,params);add(root,header,64);
    }
    private void showRoot(){started=false;if(session==null)showLogin();else renderWorkspace();}
    private void showLogin(){
        serial++;base();ScrollView scroll=new ScrollView(this);LinearLayout form=column();form.setPadding(dp(28),dp(45),dp(28),dp(32));scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add(form,text("YOUR PRIVATE SPACE",11,CYAN),-2);space(form,16);section(form,"Your files.\nYour way.",invitation==null?"Sign in with the same Minova account you use on the website.":"You’re invited. Create your Minova account to get started.");
        EditText name=input("Your name",false),username=input("Username",false),password=input("Password",true);username.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);username.setSingleLine(true);
        if(invitation!=null){add(form,name,54);space(form,12);}add(form,username,54);space(form,12);add(form,password,54);space(form,18);
        TextView error=text("",13,Color.rgb(255,107,112));add(form,error,-2);space(form,10);
        final Button[] submit=new Button[1];submit[0]=button(invitation==null?"Sign in":"Accept invitation",true,()->{
            if(username.getText().toString().trim().isEmpty()||password.length()==0){error.setText("Enter your username and password.");return;}
            submit[0].setEnabled(false);error.setText("Connecting securely…");String pass=password.getText().toString(),user=username.getText().toString().trim(),display=name.getText().toString();
            io.execute(()->{try{JSONObject connected=new CloudApi(this,null).login(user,pass,display,invitation);runOnUiThread(()->{password.setText("");session=connected;invitation=null;view="files";path="";share=null;query="";renderWorkspace();});}catch(Exception exception){runOnUiThread(()->{password.setText("");error.setText(exception.getMessage());submit[0].setEnabled(true);});}});
        });add(form,submit[0],50);space(form,22);add(form,text("New here? Open your invitation email to create an account.\n\ncloud.project-minova.com",12,MUTED),-2);space(form,20);add(form,button("Open Minova home",false,()->openWebsite("https://project-minova.com/")),-2);
    }
    private EditText input(String hint,boolean password){EditText field=new EditText(this);field.setHint(hint);field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(15);field.setSingleLine(true);field.setPadding(dp(14),dp(8),dp(14),dp(8));field.setBackground(panel(SURFACE));if(password)field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private void navigate(String target){view=target;path="";share=null;query="";libraryLimit=100;renderWorkspace();}
    private void renderWorkspace(){
        if(session==null){showLogin();return;}serial++;loading=false;base();started=true;
        header.addView(button("Account",false,()->navigate("more")),new LinearLayout.LayoutParams(-2,dp(40)));
        body=column();body.setPadding(dp(20),dp(17),dp(20),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        String heading=view.equals("files")?"Files":view.equals("photos")?"Photos":view.equals("videos")?"Videos":view.equals("shared")?"Shared with me":view.equals("starred")?"Starred":view.equals("trash")?"Trash":"Your account";
        TextView title=text(heading,30,WHITE);title.setTypeface(null,Typeface.BOLD);add(body,title,-2);space(body,5);
        syncStatus=text("Connected to your Minova Cloud",11,MUTED);add(body,syncStatus,-2);space(body,17);
        if(view.equals("more")){renderMore();addBottom();return;}
        if(!view.equals("trash")){
            LinearLayout searchRow=row();search=input("Search "+heading.toLowerCase(Locale.ROOT),false);search.setText(query);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchRow.addView(search,new LinearLayout.LayoutParams(0,dp(46),1));Button go=button("Find",false,()->{query=search.getText().toString().trim();if(!isMedia()){view="files";path="";share=null;}libraryLimit=100;load(true);});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-2,dp(46));params.leftMargin=dp(8);searchRow.addView(go,params);add(body,searchRow,-2);search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){go.performClick();return true;}return false;});space(body,12);
        }
        LinearLayout actions=row();boolean writable=(view.equals("files")||isMedia())&&share==null;
        if(writable){actions.addView(button("+ Upload",true,this::chooseUpload),new LinearLayout.LayoutParams(0,dp(44),1));if(view.equals("files")){Button folder=button("New folder",false,this::newFolder);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(44),1);params.leftMargin=dp(8);actions.addView(folder,params);}}
        Button refresh=button("↻",false,()->load(true));refresh.setContentDescription("Refresh files");LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(48),dp(44));params.leftMargin=dp(8);actions.addView(refresh,params);add(body,actions,-2);space(body,12);
        TextView location=text(path.isEmpty()?(share==null?"PRIVATE LIBRARY":"SHARED · READ ONLY"):"‹  "+path,11,CYAN);location.setPadding(0,dp(4),0,dp(12));location.setOnClickListener(v->{if(!path.isEmpty()||share!=null)onBackPressed();});add(body,location,-2);
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(0,0,0,dp(16));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));addBottom();load(true);
    }
    private boolean isMedia(){return view.equals("photos")||view.equals("videos");}
    private void addBottom(){
        bottom=row();bottom.setPadding(dp(8),dp(9),dp(8),dp(9));bottom.setBackgroundColor(Color.rgb(8,12,18));String[] keys={"files","photos","videos","more"},labels={"Files","Photos","Videos","More"};
        for(int i=0;i<keys.length;i++){String key=keys[i];TextView tab=text(labels[i],13,view.equals(key)?CYAN:MUTED);tab.setGravity(Gravity.CENTER);tab.setTypeface(null,view.equals(key)?Typeface.BOLD:Typeface.NORMAL);tab.setContentDescription(labels[i]+" tab");tab.setOnClickListener(v->navigate(key));bottom.addView(tab,new LinearLayout.LayoutParams(0,dp(42),1));}add(root,bottom,-2);
    }
    private void load(boolean force){
        if(session==null||view.equals("more")||content==null||loading&&!force)return;
        int current=++serial;loading=true;String currentView=view,currentPath=path,currentQuery=query;JSONObject currentShare=share;CloudApi api=new CloudApi(this,session);
        if(force){content.removeAllViews();add(content,text("Opening your space…",14,MUTED),-2);}
        io.execute(()->{try{
            JSONObject result;
            if(currentView.equals("photos")||currentView.equals("videos")){
                JSONArray all=new JSONArray();boolean more=false;for(int offset=0;offset<libraryLimit;offset+=100){JSONObject page=api.request("/api/library?kind="+currentView+"&q="+CloudApi.encode(currentQuery)+"&offset="+offset+"&limit=100","GET",null);JSONArray items=page.getJSONArray("items");for(int i=0;i<items.length();i++)all.put(items.get(i));more=page.optBoolean("hasMore");if(!more)break;}result=new JSONObject().put("items",all).put("hasMore",more);
            }else if(currentView.equals("trash"))result=api.request("/api/trash","GET",null);
            else if(currentView.equals("shared")&&currentShare==null){result=api.request("/api/shares","GET",null);JSONArray incoming=new JSONArray(),items=result.getJSONArray("items");for(int i=0;i<items.length();i++){JSONObject item=items.getJSONObject(i);if(item.optBoolean("incoming"))incoming.put(item.put("_share",new JSONObject().put("id",item.getString("id")).put("owner",item.getJSONObject("owner"))));}result.put("items",incoming);}
            else if(!currentQuery.isEmpty()||currentView.equals("starred"))result=api.request("/api/search?q="+CloudApi.encode(currentQuery)+(currentView.equals("starred")?"&filter=starred":""),"GET",null);
            else result=api.request("/api/files?path="+CloudApi.encode(currentPath)+(currentShare==null?"":"&share="+CloudApi.encode(currentShare.optString("id"))),"GET",null);
            JSONObject response=result;runOnUiThread(()->{if(current!=serial||isFinishing())return;loading=false;renderItems(response,api);syncStatus.setText("Updated just now · same files as the website");});
        }catch(Exception error){runOnUiThread(()->{if(current!=serial||isFinishing())return;loading=false;if(error instanceof CloudApi.ApiException&&((CloudApi.ApiException)error).status==401){SecureSession.clear(this);session=null;started=false;showLogin();message("Your device has expired or was disconnected. Sign in again.");}else{syncStatus.setText("Could not sync · "+error.getMessage());if(force){content.removeAllViews();add(content,text("Could not open this view. Check your connection and tap Refresh.",14,MUTED),-2);}}});}});
    }
    private void renderItems(JSONObject result,CloudApi api){
        content.removeAllViews();JSONArray array=result.optJSONArray("items");List<JSONObject> items=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)items.add(array.optJSONObject(i));
        if(!isMedia())Collections.sort(items,(a,b)->{int directories=Boolean.compare(b.optBoolean("isDirectory"),a.optBoolean("isDirectory"));return directories!=0?directories:a.optString("name").compareToIgnoreCase(b.optString("name"));});
        if(items.isEmpty()){space(content,35);section(content,isMedia()?"Your "+view+" belong here.":"Nothing here yet.",isMedia()?"Upload from this phone, add files on the website, or turn on automatic backup in More.":"Your private files will appear here. Shared items and the trash stay in their own views.");return;}
        if(isMedia()){
            for(int i=0;i<items.size();i+=2){LinearLayout line=row();line.setGravity(Gravity.TOP);for(int j=i;j<Math.min(i+2,items.size());j++){View card=mediaCard(items.get(j),api);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);if(j>i)params.leftMargin=dp(10);line.addView(card,params);}if(i+1>=items.size())line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));add(content,line,-2);space(content,10);}
        }else for(JSONObject item:items){add(content,fileRow(item),-2);space(content,9);}
        if(result.optBoolean("hasMore")){space(content,12);add(content,button("Load more",false,()->{libraryLimit+=100;load(true);}),48);}
        if(result.optBoolean("truncated"))add(content,text("This is a limited set of results. Refine the search to find more.",12,MUTED),-2);
    }
    private View fileRow(JSONObject item){
        LinearLayout line=row();line.setPadding(dp(14),dp(12),dp(8),dp(12));line.setBackground(panel(SURFACE));TextView icon=text(item.optBoolean("isDirectory")?"▣":item.optString("name").toLowerCase(Locale.ROOT).matches(".*\\.(mp4|webm|mov|mkv)$")?"▷":"▤",25,CYAN);line.addView(icon,new LinearLayout.LayoutParams(dp(38),dp(42)));
        LinearLayout labels=column();TextView name=text(item.optString("name"),14,WHITE);name.setMaxLines(2);add(labels,name,-2);add(labels,text(item.optBoolean("isDirectory")?"Folder":formatBytes(item.optLong("size")),11,MUTED),-2);line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));labels.setPadding(dp(3),0,dp(8),0);labels.setOnClickListener(v->openItem(item));icon.setOnClickListener(v->openItem(item));
        Button actions=button("⋮",false,()->actions(item));actions.setContentDescription("Actions for "+item.optString("name"));line.addView(actions,new LinearLayout.LayoutParams(dp(43),dp(43)));return line;
    }
    private View mediaCard(JSONObject item,CloudApi api){
        LinearLayout card=column();card.setBackground(panel(SURFACE));card.setClipToOutline(true);FrameLayout visual=new FrameLayout(this);visual.setBackgroundColor(Color.rgb(20,38,52));TextView symbol=text(view.equals("videos")?"▷":"▧",38,CYAN);symbol.setGravity(Gravity.CENTER);visual.addView(symbol,new FrameLayout.LayoutParams(-1,-1));ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);visual.addView(image,new FrameLayout.LayoutParams(-1,-1));add(card,visual,130);visual.setOnClickListener(v->openItem(item));
        LinearLayout labels=column();labels.setPadding(dp(11),dp(11),dp(11),dp(10));TextView name=text(item.optString("name"),13,WHITE);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);add(labels,name,38);name.setOnClickListener(v->openItem(item));LinearLayout actions=row();actions.addView(text(formatBytes(item.optLong("size")),10,MUTED),new LinearLayout.LayoutParams(0,-2,1));Button more=button("⋮",false,()->actions(item));more.setContentDescription("Actions for "+item.optString("name"));actions.addView(more,new LinearLayout.LayoutParams(dp(38),dp(38)));add(labels,actions,-2);add(card,labels,-2);
        if(view.equals("photos")&&item.optString("name").toLowerCase(Locale.ROOT).matches(".*\\.(jpg|jpeg|png|webp|gif|avif)$"))thumbnail(image,item,api);return card;
    }
    private void thumbnail(ImageView image,JSONObject item,CloudApi api){
        String key=api.userId()+"/"+item.optString("path")+item.optString("modified");Bitmap cached=thumbnails.get(key);if(cached!=null){image.setImageBitmap(cached);return;}
        io.execute(()->{try{HttpURLConnection connection=api.connection(api.fileRoute(item,null,true),"GET",true);byte[] bytes;try{if(connection.getResponseCode()!=200)return;bytes=CloudApi.read(connection.getInputStream(),12*1024*1024);}finally{connection.disconnect();}Bitmap bitmap=PhotoBitmaps.decode(bytes,700);if(bitmap!=null){thumbnails.put(key,bitmap);runOnUiThread(()->{if(image.isAttachedToWindow())image.setImageBitmap(bitmap);});}}catch(Exception ignored){}});
    }
    private JSONObject resolved(JSONObject item)throws Exception{JSONObject result=new JSONObject(item.toString());if(result.has("_share"))result.put("path","");return result;}
    private JSONObject itemShare(JSONObject item){return item.optJSONObject("_share")!=null?item.optJSONObject("_share"):share;}
    private void openItem(JSONObject item){
        if(view.equals("trash")){actions(item);return;}
        if(item.optBoolean("isDirectory")){JSONObject incoming=item.optJSONObject("_share");if(incoming!=null){share=incoming;path="";view="shared";}else path=item.optString("path");query="";renderWorkspace();return;}
        String name=item.optString("name").toLowerCase(Locale.ROOT);
        if(name.matches(".*\\.(jpg|jpeg|png|webp|gif|avif|heic|heif|bmp)$")){photoPreview(item);return;}
        if(name.matches(".*\\.(mp4|webm|mov|m4v|3gp)$")){try{CloudApi api=new CloudApi(this,session);VideoView player=new VideoView(this);MediaController controller=new MediaController(this);controller.setAnchorView(player);player.setMediaController(controller);player.setVideoURI(Uri.parse(api.origin()+api.fileRoute(resolved(item),itemShare(item),true)),api.mediaHeaders());LinearLayout box=column();box.addView(player,new LinearLayout.LayoutParams(-1,dp(320)));dialog=new AlertDialog.Builder(this).setTitle(item.optString("name")).setView(box).setPositiveButton("Close",(d,w)->player.stopPlayback()).setNeutralButton("Open with…",(d,w)->{player.stopPlayback();download(item,false);}).create();dialog.setOnDismissListener(d->player.stopPlayback());player.setOnPreparedListener(media->player.start());player.setOnErrorListener((media,what,extra)->{message("This video format needs another player. Use Open with…");return true;});dialog.show();}catch(Exception error){message(error.getMessage());}return;}
        download(item,false);
    }
    private void photoPreview(JSONObject item){
        CloudApi api=new CloudApi(this,session);JSONObject selected,selectedShare=itemShare(item);
        try{selected=resolved(item);}catch(Exception error){message(error.getMessage());return;}
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);LinearLayout box=column();box.addView(image,new LinearLayout.LayoutParams(-1,dp(360)));
        dialog=new AlertDialog.Builder(this).setTitle(item.optString("name")).setView(box).setPositiveButton("Close",null).setNeutralButton("Save to phone",(d,w)->download(item,true)).setNegativeButton("Open with…",(d,w)->download(item,false)).show();
        io.execute(()->{try{HttpURLConnection connection=api.connection(api.fileRoute(selected,selectedShare,false),"GET",true);byte[] bytes;try{if(connection.getResponseCode()!=200)throw new IOException("The photo is no longer available.");bytes=CloudApi.read(connection.getInputStream(),32*1024*1024);}finally{connection.disconnect();}Bitmap bitmap=PhotoBitmaps.decode(bytes,2000);if(bitmap==null)throw new IOException("This photo needs another viewer. Use Open with…");runOnUiThread(()->image.setImageBitmap(bitmap));}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});
    }
    private void actions(JSONObject item){
        boolean incoming=itemShare(item)!=null;ArrayList<String> labels=new ArrayList<>();if(view.equals("trash"))labels.add("Restore");else{if(!item.optBoolean("isDirectory")){labels.add("Open");labels.add("Save to phone");}if(!incoming){labels.add("Share with a member");labels.add(item.optBoolean("starred")?"Remove star":"Star");labels.add("Rename");labels.add("Move to trash");}}
        dialog=new AlertDialog.Builder(this).setTitle(item.optString("name")).setItems(labels.toArray(new String[0]),(d,index)->{
            String action=labels.get(index);switch(action){case "Open":openItem(item);break;case "Save to phone":download(item,true);break;case "Share with a member":shareItem(item);break;case "Star":case "Remove star":mutation("/api/star",newObject("path",item.optString("path")),"Star updated");break;case "Rename":editDialog("Rename",item.optString("name"),value->mutation("/api/rename",newObject("path",item.optString("path"),"name",value),"Name updated"));break;case "Move to trash":dialog=new AlertDialog.Builder(this).setTitle("Move to trash?").setMessage("You can restore this item later. Its existing shares will be removed.").setNegativeButton("Cancel",null).setPositiveButton("Move to trash",(confirm,which)->mutation("/api/trash",newObject("path",item.optString("path")),"Moved to trash")).show();break;case "Restore":mutation("/api/restore",newObject("id",item.optString("id")),"Item restored");break;}
        }).setNegativeButton("Close",null).show();
    }
    private static JSONObject newObject(String... pairs){JSONObject value=new JSONObject();try{for(int i=0;i<pairs.length;i+=2)value.put(pairs[i],pairs[i+1]);}catch(JSONException ignored){}return value;}
    private interface TextAction{void run(String value);}
    private void editDialog(String label,String initial,TextAction action){EditText field=input(label,false);field.setText(initial);LinearLayout box=column();box.setPadding(dp(20),dp(12),dp(20),0);add(box,field,54);dialog=new AlertDialog.Builder(this).setTitle(label).setView(box).setNegativeButton("Cancel",null).setPositiveButton("Continue",(d,w)->{String value=field.getText().toString().trim();if(!value.isEmpty())action.run(value);}).show();}
    private void newFolder(){editDialog("New folder","",name->mutation("/api/folders",newObject("path",path.isEmpty()?name:path+"/"+name),"Folder created"));}
    private void mutation(String route,JSONObject body,String success){CloudApi api=new CloudApi(this,session);io.execute(()->{try{api.request(route,"POST",body);runOnUiThread(()->{message(success);load(true);});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});}
    private void shareItem(JSONObject item){
        CloudApi api=new CloudApi(this,session);io.execute(()->{try{JSONArray members=api.request("/api/members","GET",null).getJSONArray("users"),shares=api.request("/api/shares","GET",null).getJSONArray("items");ArrayList<String> labels=new ArrayList<>();ArrayList<Runnable> actions=new ArrayList<>();
            for(int i=0;i<shares.length();i++){JSONObject existing=shares.getJSONObject(i);if(!existing.optBoolean("incoming")&&existing.optString("path").equals(item.optString("path"))){labels.add("Remove access: "+existing.getJSONObject("recipient").optString("name"));actions.add(()->io.execute(()->{try{api.request("/api/shares","DELETE",newObject("id",existing.optString("id")));runOnUiThread(()->message("Access removed"));}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}}));}}
            for(int i=0;i<members.length();i++){JSONObject member=members.getJSONObject(i);if(member.optString("id").equals(api.userId()))continue;labels.add("Share with "+member.optString("name"));actions.add(()->mutation("/api/shares",newObject("path",item.optString("path"),"recipientId",member.optString("id")),"Shared with "+member.optString("name")));}
            runOnUiThread(()->{if(labels.isEmpty()){message("Invite someone on the website first. Members appear here after joining.");return;}dialog=new AlertDialog.Builder(this).setTitle("Share · view and download").setItems(labels.toArray(new String[0]),(d,index)->actions.get(index).run()).setNegativeButton("Close",null).show();});
        }catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});
    }
    private JSONObject pendingSaveItem,pendingSaveShare;
    private void download(JSONObject item,boolean save){
        if(save){try{pendingSaveItem=resolved(item);pendingSaveShare=itemShare(item);Intent create=new Intent(Intent.ACTION_CREATE_DOCUMENT);create.addCategory(Intent.CATEGORY_OPENABLE);create.setType(mime(item.optString("name")));create.putExtra(Intent.EXTRA_TITLE,item.optString("name"));startActivityForResult(create,202);}catch(Exception error){message(error.getMessage());}return;}
        CloudApi api=new CloudApi(this,session);JSONObject selected,selectedShare=itemShare(item);try{selected=resolved(item);}catch(Exception error){message(error.getMessage());return;}message("Downloading for preview…");
        io.execute(()->{try{File directory=new File(getCacheDir(),"downloads");if(!directory.exists()&&!directory.mkdirs())throw new IOException("Could not open the preview cache.");File target=new File(directory,UUID.randomUUID()+"-"+UploadEngine.cleanName(selected.optString("name")));try(OutputStream output=new FileOutputStream(target)){copyFile(api,selected,selectedShare,output);}Uri uri=FileProvider.getUriForFile(this,getPackageName()+".files",target);runOnUiThread(()->{try{Intent open=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,mime(selected.optString("name"))).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(open,"Open "+selected.optString("name")));}catch(Exception error){message("No compatible viewer is installed. Use Save to phone instead.");}});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});
    }
    private void copyFile(CloudApi api,JSONObject item,JSONObject selectedShare,OutputStream output)throws Exception{
        HttpURLConnection connection=api.connection(api.fileRoute(item,selectedShare,false),"GET",true);try{int status=connection.getResponseCode();if(status!=200)throw new IOException("The file is no longer available or your device needs to sign in again.");try(InputStream stream=connection.getInputStream()){byte[] bytes=new byte[128*1024];int count;long total=0;while((count=stream.read(bytes))!=-1){total+=count;if(total>UploadEngine.MAX_SIZE)throw new IOException("File exceeds the 20 GB limit.");output.write(bytes,0,count);}}}finally{connection.disconnect();}
    }
    private static String mime(String name){String extension=name.contains(".")?name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT):"";String type=android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);return type==null?"application/octet-stream":type;}
    private void chooseUpload(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(view.equals("photos")?"image/*":view.equals("videos")?"video/*":"*/*");pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,201);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||session==null)return;
        if(request==201){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());String user=new CloudApi(this,session).userId();for(Uri uri:uris){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}TransferWork.upload(this,user,uri,isMedia()?"":path);}TransferWork.status(this,user,"Queued "+uris.size()+" upload"+(uris.size()==1?"":"s"),false);message("Uploads queued. You can keep using Cloud.");updateTransferStatus();}
        if(request==202&&data.getData()!=null&&pendingSaveItem!=null){Uri destination=data.getData();JSONObject item=pendingSaveItem,selectedShare=pendingSaveShare;pendingSaveItem=null;CloudApi api=new CloudApi(this,session);message("Saving to your phone…");io.execute(()->{try(OutputStream output=getContentResolver().openOutputStream(destination,"w")){if(output==null)throw new IOException("Could not open the chosen destination.");copyFile(api,item,selectedShare,output);runOnUiThread(()->message("Saved to phone"));}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});}
    }
    private void renderMore(){
        ScrollView scroll=new ScrollView(this);content=column();scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));JSONObject user=session.optJSONObject("user");space(content,5);section(content,user.optString("name"),"@"+user.optString("username")+" · same account as the website");
        add(content,button("Automatic photo & video backup",true,this::backupSettings),48);space(content,10);add(content,button("Shared with me",false,()->navigate("shared")),48);space(content,10);add(content,button("Starred",false,()->navigate("starred")),48);space(content,10);add(content,button("Trash & restore",false,()->navigate("trash")),48);space(content,10);add(content,button("Connected devices",false,this::connectedDevices),48);
        if("owner".equals(user.optString("role"))){space(content,10);add(content,button("Invite by email",false,()->editDialog("Invite by email","",email->mutation("/api/invites",newObject("email",email),"Invitation sent"))),48);}
        space(content,20);TextView transfer=text("",12,MUTED);transfer.setTag("transfer-status");add(content,transfer,-2);updateTransferStatus();space(content,12);add(content,button("Cancel queued uploads",false,()->{WorkManager.getInstance(this).cancelUniqueWork("uploads-"+new CloudApi(this,session).userId());message("Upload queue cancelled. Completed files remain in Cloud.");}),46);space(content,18);add(content,button("Open website",false,()->openWebsite(BuildConfig.CLOUD_ORIGIN+"/")),46);space(content,10);add(content,button("Updates & installation help",false,()->openWebsite(BuildConfig.CLOUD_ORIGIN+"/android")),46);space(content,10);add(content,button("Sign out",false,this::signOut),46);space(content,16);add(content,text("Minova Cloud "+BuildConfig.VERSION_NAME+"\nYour files stay on your own cloud storage.",11,MUTED),-2);space(content,20);
    }
    private void updateTransferStatus(){if(session==null||syncStatus==null)return;String user=new CloudApi(this,session).userId();String status=getSharedPreferences("transfer-"+user,0).getString("status","");if(!status.isEmpty()){if(content!=null){TextView label=content.findViewWithTag("transfer-status");if(label!=null)label.setText(status);}if(!view.equals("more"))syncStatus.setText(status);}}
    private void backupSettings(){
        String user=new CloudApi(this,session).userId();SharedPreferences settings=getSharedPreferences("backup-"+user,0);LinearLayout box=column();box.setPadding(dp(22),dp(8),dp(22),dp(14));
        TextView help=text("Back up allowed photos and videos to your private Phone backups folder. The first backup includes existing media. Cloud deletions never delete phone originals.\n\nAndroid schedules backups about every 15 minutes when the network and battery allow it; exact timing varies.",13,MUTED);help.setLineSpacing(dp(3),1);add(box,help,-2);space(box,14);
        Switch enabled=new Switch(this);enabled.setText("Automatic backup");enabled.setTextColor(WHITE);enabled.setChecked(settings.getBoolean("enabled",false));add(box,enabled,50);
        CheckBox photos=new CheckBox(this);photos.setText("Photos");photos.setTextColor(WHITE);photos.setChecked(settings.getBoolean("photos",true));add(box,photos,44);CheckBox videos=new CheckBox(this);videos.setText("Videos");videos.setTextColor(WHITE);videos.setChecked(settings.getBoolean("videos",true));add(box,videos,44);CheckBox wifi=new CheckBox(this);wifi.setText("Wi-Fi only");wifi.setTextColor(WHITE);wifi.setChecked(settings.getBoolean("wifi",true));add(box,wifi,44);
        enabled.setOnCheckedChangeListener((button,checked)->{if(checked&&!BackupWorker.permitted(this,false)&&!BackupWorker.permitted(this,true)){requestBackupPermissions();enabled.setChecked(false);return;}settings.edit().putBoolean("enabled",checked).apply();TransferWork.schedule(this,user,checked,wifi.isChecked());});
        CompoundButton.OnCheckedChangeListener update=(button,checked)->{settings.edit().putBoolean("photos",photos.isChecked()).putBoolean("videos",videos.isChecked()).putBoolean("wifi",wifi.isChecked()).apply();TransferWork.schedule(this,user,settings.getBoolean("enabled",false),wifi.isChecked());};photos.setOnCheckedChangeListener(update);videos.setOnCheckedChangeListener(update);wifi.setOnCheckedChangeListener(update);
        boolean limited=Build.VERSION.SDK_INT>=34&&checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)==PackageManager.PERMISSION_GRANTED&&(checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)!=PackageManager.PERMISSION_GRANTED);
        add(box,button(limited?"Change selected photos & videos":"Manage photo & video access",false,this::requestBackupPermissions),46);space(box,10);long last=settings.getLong("lastSuccess",0);add(box,text((limited?"Access is limited to the media you selected.\n":"")+(last==0?"No completed backup yet.":"Last completed: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(new Date(last))),11,MUTED),-2);
        dialog=new AlertDialog.Builder(this).setTitle("Photo & video backup").setView(box).setNegativeButton("Close",null).setPositiveButton("Back up now",(d,w)->{if(!settings.getBoolean("enabled",false)){message("Enable automatic backup and allow media access first.");return;}TransferWork.backupNow(this,user,wifi.isChecked());message("Backup queued. Check the transfer status in More.");}).show();
    }
    private void requestBackupPermissions(){String[] permissions=Build.VERSION.SDK_INT>=34?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO,Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED}:Build.VERSION.SDK_INT>=33?new String[]{Manifest.permission.READ_MEDIA_IMAGES,Manifest.permission.READ_MEDIA_VIDEO}:new String[]{Manifest.permission.READ_EXTERNAL_STORAGE};requestPermissions(permissions,203);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==203&&session!=null){if(BackupWorker.permitted(this,false)||BackupWorker.permitted(this,true))message("Media access updated. Enable automatic backup when you are ready.");else message("Backup stays off until photo or video access is granted.");if(dialog!=null)dialog.dismiss();backupSettings();}}
    private void connectedDevices(){CloudApi api=new CloudApi(this,session);io.execute(()->{try{JSONArray devices=api.request("/api/devices","GET",null).getJSONArray("devices");String[] labels=new String[devices.length()];for(int i=0;i<labels.length;i++){JSONObject device=devices.getJSONObject(i);labels[i]=device.optString("name")+(device.optBoolean("current")?" (this phone)":"");}runOnUiThread(()->{dialog=new AlertDialog.Builder(this).setTitle("Remove a connected device").setItems(labels,(d,index)->{JSONObject device=devices.optJSONObject(index);dialog=new AlertDialog.Builder(this).setTitle("Disconnect "+device.optString("name")+"?").setMessage("This device will need to sign in again. Its cloud files remain.").setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(confirm,which)->io.execute(()->{try{api.request("/api/devices","DELETE",newObject("id",device.optString("id")));runOnUiThread(()->{message("Device disconnected");if(device.optBoolean("current"))localSignOut(api.userId());});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}})).show();}).setNegativeButton("Close",null).show();});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});}
    private void signOut(){CloudApi api=new CloudApi(this,session);dialog=new AlertDialog.Builder(this).setTitle("Sign out?").setMessage("Uploads and backup on this phone will stop. Your cloud files remain available on the website.").setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,w)->io.execute(()->{boolean revoked;try{api.request("/api/logout","POST",new JSONObject());revoked=true;}catch(Exception error){revoked=false;}boolean complete=revoked;runOnUiThread(()->{localSignOut(api.userId());if(!complete)message("Signed out locally. Remove this device on the website to revoke its server access.");});})).show();}
    private void localSignOut(String user){WorkManager.getInstance(this).cancelAllWorkByTag(TransferWork.tag(user));getSharedPreferences("backup-"+user,0).edit().putBoolean("enabled",false).apply();SecureSession.clear(this);thumbnails.evictAll();session=null;share=null;path="";query="";started=false;showLogin();}
    private void openWebsite(String url){try{Intent chooser=Intent.createChooser(new Intent(Intent.ACTION_VIEW,Uri.parse(url)),"Open in browser");chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{new ComponentName(this,MainActivity.class)});startActivity(chooser);}catch(Exception error){message("Open "+url+" in your browser.");}}
    private void message(String value){Toast.makeText(this,value==null?"Please try again.":value,Toast.LENGTH_LONG).show();}
    private static String formatBytes(long size){if(size<1024)return size+" B";if(size<1024*1024)return String.format(Locale.getDefault(),"%.1f KB",size/1024d);if(size<1024L*1024*1024)return String.format(Locale.getDefault(),"%.1f MB",size/(1024d*1024));return String.format(Locale.getDefault(),"%.1f GB",size/(1024d*1024*1024));}
}
