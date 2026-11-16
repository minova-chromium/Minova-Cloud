package com.minova.cloud;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Drawable;
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
    private ScrollView libraryScroll;
    private String lastLibrarySignature="";
    private JSONObject session,share;
    private String view="files",path="",query="",invitation;
    private int serial=0,libraryLimit=100;
    private boolean loading=false,started=false,foreground=false;
    private AlertDialog dialog;
    private final Runnable refreshLoop=new Runnable(){@Override public void run(){if(foreground&&session!=null&&started){if(dialog==null||!dialog.isShowing())load(false);updateTransferStatus();}handler.postDelayed(this,10000);}};
    @Override public void onCreate(Bundle state){super.onCreate(state);if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::goBack);readInvitation(getIntent());session=SecureSession.load(this);showRoot();handler.post(refreshLoop);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);readInvitation(intent);if(invitation!=null){session=null;showRoot();}}
    private void readInvitation(Intent intent){Uri uri=intent.getData();if(uri!=null&&"https".equals(uri.getScheme())&&"cloud.project-minova.com".equals(uri.getHost())&&uri.getFragment()!=null)invitation=Uri.parse("https://cloud.project-minova.com/?"+uri.getFragment()).getQueryParameter("invite");}
    @Override protected void onResume(){super.onResume();foreground=true;if(started&&session!=null)load(false);}
    @Override protected void onPause(){foreground=false;super.onPause();}
    @Override protected void onDestroy(){handler.removeCallbacksAndMessages(null);if(dialog!=null)dialog.dismiss();io.shutdownNow();super.onDestroy();}
    private void goBack(){if(share!=null||!path.isEmpty()){if(!path.isEmpty()){int slash=path.lastIndexOf('/');path=slash<0?"":path.substring(0,slash);}else{share=null;view="shared";}query="";renderWorkspace();}else if(!view.equals("files")){navigate("files");}else finish();}
    @Override public boolean onKeyUp(int code,KeyEvent event){if(Build.VERSION.SDK_INT<33&&code==KeyEvent.KEYCODE_BACK){goBack();return true;}return super.onKeyUp(code,event);}
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+.5f);}
    private LinearLayout column(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private LinearLayout row(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.HORIZONTAL);layout.setGravity(Gravity.CENTER_VERTICAL);return layout;}
    private TextView text(String value,float size,int color){TextView label=new TextView(this);label.setText(value);label.setTextSize(size);label.setTextColor(color);label.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));label.setIncludeFontPadding(false);return label;}

    private GradientDrawable panel(int color){return MinovaUi.surface(this,color,LINE,8);}
    private TextView eyebrow(String value){TextView label=text(value,10,MUTED);label.setLetterSpacing(.14f);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return label;}
    private ImageView glyph(String kind,int color,int size){return MinovaUi.icon(this,kind,color,size);}
    private FrameLayout iconButton(String kind,boolean primary,String description,Runnable action){
        FrameLayout target=new FrameLayout(this);target.setBackground(MinovaUi.touch(this,primary?CYAN:SURFACE,primary?0:LINE,6));target.setContentDescription(description);target.setFocusable(true);
        ImageView image=glyph(kind,primary?NIGHT:MUTED,21);FrameLayout.LayoutParams params=new FrameLayout.LayoutParams(dp(21),dp(21),Gravity.CENTER);target.addView(image,params);target.setOnClickListener(v->action.run());return target;
    }
    private Button button(String value,boolean primary,Runnable action){
        Button button=new Button(this);button.setText(value.startsWith("+ ")?value.substring(2):value);button.setAllCaps(false);button.setTextSize(14);button.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));button.setTextColor(primary?NIGHT:WHITE);button.setBackground(MinovaUi.touch(this,primary?CYAN:SURFACE,primary?0:LINE,6));button.setStateListAnimator(null);button.setMinHeight(dp(48));button.setPadding(dp(16),dp(8),dp(16),dp(8));button.setOnClickListener(v->action.run());return button;
    }

    private void space(LinearLayout parent,int height){parent.addView(new View(this),new LinearLayout.LayoutParams(1,dp(height)));}
    private void add(LinearLayout parent,View child,int height){parent.addView(child,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    private void section(LinearLayout parent,String label,String description){TextView heading=text(label,24,WHITE);heading.setTypeface(null,Typeface.BOLD);add(parent,heading,-2);if(description!=null){space(parent,8);TextView detail=text(description,13,MUTED);detail.setLineSpacing(dp(3),1);add(parent,detail,-2);}space(parent,22);}

    private void base(){
        root=column();root.setBackgroundColor(NIGHT);setContentView(root);
        if(Build.VERSION.SDK_INT>=30){getWindow().setDecorFitsSystemWindows(false);root.setOnApplyWindowInsetsListener((v,insets)->{android.graphics.Insets inset=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(0,inset.top,0,inset.bottom);return insets;});}else root.setFitsSystemWindows(true);
        header=row();header.setPadding(dp(22),dp(12),dp(22),dp(12));
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.minova_lockup);logo.setScaleType(ImageView.ScaleType.FIT_CENTER);logo.setContentDescription("Minova");header.addView(logo,new LinearLayout.LayoutParams(dp(121),dp(34)));
        View divider=new View(this);divider.setBackgroundColor(LINE);LinearLayout.LayoutParams line=new LinearLayout.LayoutParams(dp(1),dp(22));line.setMargins(dp(15),0,dp(12),0);header.addView(divider,line);header.addView(text("Cloud",13,MUTED),new LinearLayout.LayoutParams(0,-2,1));add(root,header,70);
    }

    private void showRoot(){started=false;if(session==null)showLogin();else renderWorkspace();}
    private void showLogin(){
        serial++;base();ScrollView scroll=new ScrollView(this);LinearLayout form=column();form.setPadding(dp(28),dp(45),dp(28),dp(32));scroll.addView(form);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        add(form,eyebrow("YOUR PRIVATE CLOUD"),-2);space(form,18);TextView welcome=text("Your files.\nYour way.",44,WHITE);welcome.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));welcome.setLetterSpacing(-.04f);add(form,welcome,-2);space(form,17);TextView intro=text(invitation==null?"Welcome back. A space of your own is just one sign-in away.":"You’re invited. Create your account and make this space yours.",14,MUTED);intro.setLineSpacing(dp(4),1);add(form,intro,-2);space(form,30);
        EditText name=input("Your name",false),username=input("Username",false),password=input("Password",true);username.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);username.setSingleLine(true);
        if(invitation!=null){add(form,name,54);space(form,12);}add(form,username,54);space(form,12);add(form,password,54);space(form,18);
        TextView error=text("",13,Color.rgb(255,107,112));add(form,error,-2);space(form,10);
        final Button[] submit=new Button[1];submit[0]=button(invitation==null?"Sign in":"Accept invitation",true,()->{
            if(username.getText().toString().trim().isEmpty()||password.length()==0){error.setText("Enter your username and password.");return;}
            submit[0].setEnabled(false);error.setText("Connecting securely…");String pass=password.getText().toString(),user=username.getText().toString().trim(),display=name.getText().toString();
            io.execute(()->{try{JSONObject connected=new CloudApi(this,null).login(user,pass,display,invitation);runOnUiThread(()->{password.setText("");session=connected;invitation=null;view="files";path="";share=null;query="";renderWorkspace();});}catch(Exception exception){runOnUiThread(()->{password.setText("");error.setText(exception.getMessage());submit[0].setEnabled(true);});}});
        });add(form,submit[0],50);space(form,22);add(form,text("New here? Open your invitation email to create an account.\n\ncloud.project-minova.com",12,MUTED),-2);space(form,20);add(form,button("Open Minova home",false,()->openWebsite("https://project-minova.com/")),-2);
    }
    private EditText input(String hint,boolean password){EditText field=new EditText(this);field.setHint(hint);field.setTextColor(WHITE);field.setHintTextColor(MUTED);field.setTextSize(15);field.setSingleLine(true);field.setPadding(dp(14),dp(8),dp(14),dp(8));field.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));field.setBackground(MinovaUi.surface(this,SURFACE,LINE,6));if(password)field.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);return field;}
    private void navigate(String target){view=target;path="";share=null;query="";libraryLimit=100;renderWorkspace();}

    private void renderWorkspace(){
        if(session==null){showLogin();return;}serial++;loading=false;lastLibrarySignature="";base();started=true;
        String initials=session.optJSONObject("user").optString("name","M");TextView avatar=text(initials.substring(0,1).toUpperCase(Locale.ROOT),14,CYAN);avatar.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));avatar.setGravity(Gravity.CENTER);avatar.setBackground(MinovaUi.touch(this,0xff123040,0xff245266,6));avatar.setContentDescription("Your account");avatar.setOnClickListener(v->navigate("more"));header.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));
        body=column();body.setPadding(dp(22),dp(14),dp(22),0);root.addView(body,new LinearLayout.LayoutParams(-1,0,1));
        String heading=view.equals("files")?"Your files":view.equals("photos")?"Photos":view.equals("videos")?"Videos":view.equals("shared")?"Shared with me":view.equals("starred")?"Starred":view.equals("trash")?"Trash":"Your account";
        String subtitle=view.equals("photos")?"Your moments, kept close.":view.equals("videos")?"Make room for the replay.":view.equals("more")?"Make this space yours.":view.equals("shared")?"Good things, shared with you.":view.equals("trash")?"A second chance for your files.":view.equals("starred")?"The things you come back to.":"A place for everything you keep.";
        LinearLayout headingRow=row();LinearLayout titles=column();TextView title=text(heading,32,WHITE);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));title.setLetterSpacing(-.035f);add(titles,title,-2);space(titles,9);add(titles,text(subtitle,12,MUTED),-2);headingRow.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        if((view.equals("files")||isMedia())&&share==null){FrameLayout upload=iconButton("plus",true,"Upload "+(isMedia()?view:"files"),this::chooseUpload);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(46),dp(46));params.leftMargin=dp(10);headingRow.addView(upload,params);}
        add(body,headingRow,-2);space(body,20);
        syncStatus=text("Connecting…",10,MUTED);
        if(view.equals("more")){renderMore();addBottom();return;}
        LinearLayout tools=row();
        if(!view.equals("trash")){
            search=input("Search "+(isMedia()?view:"your files"),false);search.setText(query);search.setTextSize(13);search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);Drawable searchIcon=new MinovaUi.Icon("search",0xff718195);searchIcon.setBounds(0,0,dp(17),dp(17));search.setCompoundDrawables(searchIcon,null,null,null);search.setCompoundDrawablePadding(dp(10));tools.addView(search,new LinearLayout.LayoutParams(0,dp(48),1));
            FrameLayout go=iconButton("search",false,"Search",()->{query=search.getText().toString().trim();if(!isMedia()){view="files";path="";share=null;}libraryLimit=100;load(true);});LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(44),dp(48));params.leftMargin=dp(8);tools.addView(go,params);search.setOnEditorActionListener((v,action,event)->{if(action==EditorInfo.IME_ACTION_SEARCH){go.performClick();return true;}return false;});
        }
        FrameLayout refresh=iconButton("refresh",false,"Refresh files",()->load(true));LinearLayout.LayoutParams refreshParams=new LinearLayout.LayoutParams(dp(44),dp(48));refreshParams.leftMargin=dp(8);tools.addView(refresh,refreshParams);add(body,tools,-2);space(body,15);
        if(!path.isEmpty()||share!=null){LinearLayout crumb=row();crumb.addView(glyph("back",CYAN,15));TextView location=text("  "+(path.isEmpty()?"Shared folder":path),11,CYAN);location.setMaxLines(1);location.setEllipsize(TextUtils.TruncateAt.START);crumb.addView(location,new LinearLayout.LayoutParams(0,-2,1));crumb.setPadding(0,dp(4),0,dp(13));crumb.setOnClickListener(v->goBack());add(body,crumb,-2);}
        libraryScroll=new ScrollView(this);libraryScroll.setFillViewport(true);libraryScroll.setClipToPadding(false);content=column();content.setPadding(0,0,0,dp(24));libraryScroll.addView(content);body.addView(libraryScroll,new LinearLayout.LayoutParams(-1,0,1));addBottom();load(true);
    }

    private boolean isMedia(){return view.equals("photos")||view.equals("videos");}

    private void addBottom(){
        View topLine=new View(this);topLine.setBackgroundColor(LINE);add(root,topLine,1);bottom=row();bottom.setPadding(dp(12),dp(8),dp(12),dp(8));bottom.setBackgroundColor(0xff080c12);
        String[] keys={"files","photos","videos","more"},labels={"Files","Photos","Videos","More"},icons={"folder","photos","videos","menu"};
        for(int i=0;i<keys.length;i++){
            String key=keys[i];boolean active=view.equals(key);LinearLayout tab=column();tab.setGravity(Gravity.CENTER);tab.setPadding(0,dp(4),0,dp(4));tab.setBackground(MinovaUi.touch(this,active?0xff112633:0xff080c12,0,6));tab.setContentDescription(labels[i]+" tab");tab.setFocusable(true);
            tab.addView(glyph(icons[i],active?CYAN:0xff718195,21));space(tab,7);TextView label=text(labels[i],10,active?CYAN:MUTED);label.setGravity(Gravity.CENTER);label.setTypeface(Typeface.create(active?"sans-serif-medium":"sans-serif",Typeface.NORMAL));tab.addView(label);tab.setOnClickListener(v->navigate(key));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(57),1);params.setMargins(dp(3),0,dp(3),0);bottom.addView(tab,params);
        }add(root,bottom,-2);
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
            JSONObject response=result;runOnUiThread(()->{if(current!=serial||isFinishing())return;loading=false;String signature=response.toString();if(force||!signature.equals(lastLibrarySignature)){int scroll=libraryScroll==null?0:libraryScroll.getScrollY();renderItems(response,api);lastLibrarySignature=signature;if(!force&&libraryScroll!=null)libraryScroll.post(()->libraryScroll.scrollTo(0,scroll));}syncStatus.setText("Up to date");});
        }catch(Exception error){runOnUiThread(()->{if(current!=serial||isFinishing())return;loading=false;if(error instanceof CloudApi.ApiException&&((CloudApi.ApiException)error).status==401){SecureSession.clear(this);session=null;started=false;showLogin();message("Your device has expired or was disconnected. Sign in again.");}else{syncStatus.setText("Could not sync · "+error.getMessage());if(force){content.removeAllViews();add(content,text("Could not open this view. Check your connection and tap Refresh.",14,MUTED),-2);}}});}});
    }

    private void welcomePanel(){
        MinovaUi.OrbitPanel card=new MinovaUi.OrbitPanel(this);LinearLayout copy=column();copy.setPadding(dp(19),dp(18),dp(95),dp(18));TextView label=eyebrow("YOUR PRIVATE CLOUD");label.setTextColor(CYAN);add(copy,label,-2);space(copy,11);TextView message=text("A space for\neverything.",24,WHITE);message.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));message.setLetterSpacing(-.025f);add(copy,message,-2);space(copy,9);add(copy,text("Private by default. Shared by choice.",10,MUTED),-2);card.addView(copy,new FrameLayout.LayoutParams(-1,-2));ImageView mark=new ImageView(this);mark.setImageResource(R.drawable.minova_icon);FrameLayout.LayoutParams logo=new FrameLayout.LayoutParams(dp(64),dp(64),Gravity.RIGHT|Gravity.CENTER_VERTICAL);logo.rightMargin=dp(19);card.addView(mark,logo);add(content,card,-2);space(content,12);
        LinearLayout shortcuts=row();for(String kind:new String[]{"photos","videos"}){
            LinearLayout tile=row();tile.setPadding(dp(12),dp(14),dp(10),dp(14));tile.setBackground(MinovaUi.touch(this,SURFACE,LINE,8));tile.addView(glyph(kind,CYAN,24));LinearLayout names=column();names.setPadding(dp(10),0,0,0);add(names,text(kind.equals("photos")?"Photos":"Videos",13,WHITE),-2);space(names,4);add(names,text(kind.equals("photos")?"Your moments":"Your collection",9,MUTED),-2);tile.addView(names,new LinearLayout.LayoutParams(0,-2,1));tile.addView(glyph("chevron",0xff718195,13));tile.setContentDescription(kind.equals("photos")?"Open Photos":"Open Videos");tile.setOnClickListener(v->navigate(kind));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(68),1);if(kind.equals("videos"))params.leftMargin=dp(10);shortcuts.addView(tile,params);
        }add(content,shortcuts,-2);space(content,22);
    }
    private void renderItems(JSONObject result,CloudApi api){
        int scrollPosition=libraryScroll==null?0:libraryScroll.getScrollY();content.removeAllViews();JSONArray array=result.optJSONArray("items");List<JSONObject> items=new ArrayList<>();if(array!=null)for(int i=0;i<array.length();i++)items.add(array.optJSONObject(i));
        if(!isMedia())Collections.sort(items,(a,b)->{int directories=Boolean.compare(b.optBoolean("isDirectory"),a.optBoolean("isDirectory"));return directories!=0?directories:a.optString("name").compareToIgnoreCase(b.optString("name"));});
        if(view.equals("files")&&path.isEmpty()&&share==null&&query.isEmpty())welcomePanel();
        LinearLayout caption=row();String countLabel=items.size()+" "+(isMedia()?view:"items")+(result.optBoolean("hasMore")?" shown":"");TextView label=eyebrow(query.isEmpty()?(isMedia()?"YOUR COLLECTION":"ALL FILES"):"SEARCH RESULTS");caption.addView(label,new LinearLayout.LayoutParams(0,-2,1));caption.addView(text(countLabel,10,MUTED));
        if(view.equals("files")&&share==null&&query.isEmpty()){FrameLayout folder=iconButton("new-folder",false,"New folder",this::newFolder);folder.setBackground(MinovaUi.touch(this,NIGHT,0,6));LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(dp(42),dp(36));params.leftMargin=dp(6);caption.addView(folder,params);}add(content,caption,36);space(content,9);
        if(items.isEmpty()){
            LinearLayout empty=column();empty.setGravity(Gravity.CENTER);empty.setPadding(dp(22),dp(28),dp(22),dp(30));empty.setBackground(panel(SURFACE));ImageView icon=glyph(isMedia()?view:view.equals("trash")?"trash":"cloud",CYAN,40);empty.addView(icon);space(empty,17);TextView emptyTitle=text(!query.isEmpty()?"Nothing matched this time.":isMedia()?"Make room for your "+view+".":"Ready for what’s next.",20,WHITE);emptyTitle.setGravity(Gravity.CENTER);add(empty,emptyTitle,-2);space(empty,10);TextView help=text(!query.isEmpty()?"Try another name or search term.":isMedia()?"Add your favorites, or turn on phone backup in More.":view.equals("trash")?"Deleted items appear here. You can restore them whenever you need.":"Add files with the + button above. Every file starts private.",12,MUTED);help.setGravity(Gravity.CENTER);help.setLineSpacing(dp(3),1);add(empty,help,-2);add(content,empty,-2);
        }else if(isMedia()){
            for(int i=0;i<items.size();i+=2){LinearLayout line=row();line.setGravity(Gravity.TOP);for(int j=i;j<Math.min(i+2,items.size());j++){View card=mediaCard(items.get(j),api);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,dp(view.equals("photos")?216:190),1);if(j>i)params.leftMargin=dp(11);line.addView(card,params);}if(i+1>=items.size())line.addView(new View(this),new LinearLayout.LayoutParams(0,1,1));add(content,line,-2);space(content,11);}
        }else for(JSONObject item:items){add(content,fileRow(item),-2);space(content,8);}
        if(result.optBoolean("hasMore")){space(content,12);add(content,button("Load more",false,()->{libraryLimit+=100;load(true);}),48);}
        if(result.optBoolean("truncated"))add(content,text("Showing a limited set of results. Refine your search to find more.",12,MUTED),-2);
        space(content,16);LinearLayout status=row();status.addView(glyph("cloud",0xff48d597,12));syncStatus=text("Up to date",10,0xff718195);LinearLayout.LayoutParams statusParams=new LinearLayout.LayoutParams(0,-2,1);statusParams.leftMargin=dp(7);status.addView(syncStatus,statusParams);status.addView(text("MINOVA CLOUD",9,0xff718195),new LinearLayout.LayoutParams(-2,-2));add(content,status,-2);
        if(libraryScroll!=null&&scrollPosition>0)libraryScroll.post(()->libraryScroll.scrollTo(0,scrollPosition));
    }
    private String itemKind(JSONObject item){if(item.optBoolean("isDirectory"))return "folder";String name=item.optString("name").toLowerCase(Locale.ROOT);if(name.matches(".*\\.(jpg|jpeg|png|webp|gif|avif|heic|heif|bmp|dng)$"))return "photos";if(name.matches(".*\\.(mp4|webm|mov|mkv|m4v|avi)$"))return "videos";return "file";}
    private String shortDate(String value){try{return java.time.Instant.parse(value).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("d MMM",Locale.getDefault()));}catch(Exception ignored){return "";}}
    private View fileRow(JSONObject item){
        LinearLayout line=row();line.setPadding(dp(12),dp(12),dp(5),dp(12));line.setBackground(MinovaUi.touch(this,SURFACE,LINE,8));line.setOnClickListener(v->openItem(item));
        FrameLayout tile=new FrameLayout(this);tile.setBackground(MinovaUi.surface(this,item.optBoolean("isDirectory")?0xff142e43:0xff172833,0,6));ImageView fileIcon=glyph(itemKind(item),item.optBoolean("isDirectory")?0xff0798f2:CYAN,23);tile.addView(fileIcon,new FrameLayout.LayoutParams(dp(23),dp(23),Gravity.CENTER));line.addView(tile,new LinearLayout.LayoutParams(dp(45),dp(45)));
        LinearLayout labels=column();labels.setPadding(dp(12),0,dp(4),0);TextView name=text(item.optString("name"),14,WHITE);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);add(labels,name,-2);space(labels,7);String date=shortDate(item.optString("modified"));String detail=(item.optBoolean("isDirectory")?"Folder":formatBytes(item.optLong("size")))+(date.isEmpty()?"":"  ·  "+date);if(item.optBoolean("shared"))detail+="  ·  Shared";add(labels,text(detail,10,MUTED),-2);line.addView(labels,new LinearLayout.LayoutParams(0,-2,1));
        if(item.optBoolean("starred"))line.addView(glyph("star",CYAN,13));FrameLayout more=iconButton("more",false,"Actions for "+item.optString("name"),()->actions(item));more.setBackground(MinovaUi.touch(this,SURFACE,0,6));line.addView(more,new LinearLayout.LayoutParams(dp(40),dp(44)));return line;
    }
    private View mediaCard(JSONObject item,CloudApi api){
        FrameLayout card=new FrameLayout(this);card.setBackground(panel(SURFACE));card.setClipToOutline(true);card.setOnClickListener(v->openItem(item));
        MinovaUi.OrbitPanel artwork=new MinovaUi.OrbitPanel(this);card.addView(artwork,new FrameLayout.LayoutParams(-1,-1));ImageView symbol=glyph(view.equals("videos")?"play":"photos",0xff458495,36);FrameLayout.LayoutParams centered=new FrameLayout.LayoutParams(dp(36),dp(36),Gravity.CENTER);centered.bottomMargin=dp(25);artwork.addView(symbol,centered);
        ImageView image=new ImageView(this);image.setScaleType(ImageView.ScaleType.CENTER_CROP);card.addView(image,new FrameLayout.LayoutParams(-1,-1));View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x000d121a,0xe60d121a}));card.addView(shade,new FrameLayout.LayoutParams(-1,dp(100),Gravity.BOTTOM));
        if(view.equals("videos")){TextView type=text("VIDEO",8,WHITE);type.setLetterSpacing(.1f);type.setPadding(dp(7),dp(5),dp(7),dp(5));type.setBackground(MinovaUi.surface(this,0xa80d121a,0x4045768a,4));FrameLayout.LayoutParams badge=new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);badge.setMargins(dp(10),dp(10),0,0);card.addView(type,badge);}
        LinearLayout labels=column();labels.setPadding(dp(12),dp(12),dp(36),dp(13));TextView name=text(item.optString("name"),13,WHITE);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);add(labels,name,-2);space(labels,7);String date=shortDate(item.optString("modified"));add(labels,text(date.isEmpty()?formatBytes(item.optLong("size")):date+"  ·  "+formatBytes(item.optLong("size")),9,0xffb6c5d0),-2);card.addView(labels,new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM));
        FrameLayout more=iconButton("more",false,"Actions for "+item.optString("name"),()->actions(item));more.setBackground(MinovaUi.touch(this,0x00000000,0,6));FrameLayout.LayoutParams menu=new FrameLayout.LayoutParams(dp(36),dp(42),Gravity.BOTTOM|Gravity.RIGHT);menu.bottomMargin=dp(8);card.addView(more,menu);
        card.setLayoutParams(new LinearLayout.LayoutParams(0,dp(view.equals("photos")?216:190),1));
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
    private void chooseUpload(){new AlertDialog.Builder(this).setTitle("Upload to Minova Cloud").setItems(new String[]{"Choose from phone gallery","Browse phone files","Android file picker"},(d,which)->{if(which==0)startActivity(new Intent(this,PhoneGalleryActivity.class).putExtra("path",isMedia()?"":path).putExtra("video",view.equals("videos")));else if(which==1)startActivity(new Intent(this,PhoneFilesActivity.class).putExtra("path",isMedia()?"":path));else chooseDocument();}).show();}
    private void chooseDocument(){Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(view.equals("photos")?"image/*":view.equals("videos")?"video/*":"*/*");pick.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);pick.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(pick,201);}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(result!=RESULT_OK||data==null||session==null)return;
        if(request==201){ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int i=0;i<data.getClipData().getItemCount();i++)uris.add(data.getClipData().getItemAt(i).getUri());}else if(data.getData()!=null)uris.add(data.getData());String user=new CloudApi(this,session).userId();for(Uri uri:uris){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(SecurityException ignored){}TransferWork.upload(this,user,uri,isMedia()?"":path);}TransferWork.status(this,user,"Queued "+uris.size()+" upload"+(uris.size()==1?"":"s"),false);message("Uploads queued. You can keep using Cloud.");updateTransferStatus();}
        if(request==202&&data.getData()!=null&&pendingSaveItem!=null){Uri destination=data.getData();JSONObject item=pendingSaveItem,selectedShare=pendingSaveShare;pendingSaveItem=null;CloudApi api=new CloudApi(this,session);message("Saving to your phone…");io.execute(()->{try(OutputStream output=getContentResolver().openOutputStream(destination,"w")){if(output==null)throw new IOException("Could not open the chosen destination.");copyFile(api,item,selectedShare,output);runOnUiThread(()->message("Saved to phone"));}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});}
    }

    private void settingsRow(String icon,String title,String subtitle,Runnable action){
        LinearLayout entry=row();entry.setPadding(dp(14),dp(14),dp(14),dp(14));entry.setBackground(MinovaUi.touch(this,SURFACE,LINE,8));FrameLayout tile=new FrameLayout(this);tile.setBackground(MinovaUi.surface(this,0xff172c3b,0,6));tile.addView(glyph(icon,CYAN,20),new FrameLayout.LayoutParams(dp(20),dp(20),Gravity.CENTER));entry.addView(tile,new LinearLayout.LayoutParams(dp(39),dp(39)));LinearLayout copy=column();copy.setPadding(dp(12),0,dp(6),0);add(copy,text(title,14,WHITE),-2);if(subtitle!=null){space(copy,6);add(copy,text(subtitle,10,MUTED),-2);}entry.addView(copy,new LinearLayout.LayoutParams(0,-2,1));entry.addView(glyph("chevron",0xff718195,14));entry.setOnClickListener(v->action.run());entry.setContentDescription(title);add(content,entry,-2);space(content,8);
    }
    private void settingsLabel(String title){space(content,16);add(content,eyebrow(title),-2);space(content,12);}
    private void renderMore(){
        ScrollView scroll=new ScrollView(this);content=column();content.setPadding(0,0,0,dp(24));scroll.addView(content);body.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));JSONObject user=session.optJSONObject("user");
        MinovaUi.OrbitPanel profile=new MinovaUi.OrbitPanel(this);LinearLayout identity=row();identity.setPadding(dp(18),dp(20),dp(18),dp(20));TextView avatar=text(user.optString("name","M").substring(0,1).toUpperCase(Locale.ROOT),24,CYAN);avatar.setGravity(Gravity.CENTER);avatar.setBackground(MinovaUi.surface(this,0xff16384a,0xff2c5365,6));identity.addView(avatar,new LinearLayout.LayoutParams(dp(54),dp(54)));LinearLayout names=column();names.setPadding(dp(15),0,0,0);TextView name=text(user.optString("name"),22,WHITE);name.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));add(names,name,-2);space(names,7);add(names,text("@"+user.optString("username"),12,MUTED),-2);identity.addView(names,new LinearLayout.LayoutParams(0,-2,1));identity.addView(glyph("lock",CYAN,17));profile.addView(identity,new FrameLayout.LayoutParams(-1,-2));add(content,profile,-2);
        settingsLabel("YOUR MOMENTS");String userId=user.optString("id");boolean backup=getSharedPreferences("backup-"+userId,0).getBoolean("enabled",false);settingsRow("backup","Photo & video backup",backup?"On · scheduled by Android":"Off · keep your moments backed up",this::backupSettings);
        settingsRow("photos","On this phone","Choose photos and videos from your gallery",()->startActivity(new Intent(this,PhoneGalleryActivity.class)));
        settingsRow("cloud","Free up phone space","Back up, verify, then approve removal from this phone",()->startActivity(new Intent(this,PhoneGalleryActivity.class)));
        settingsRow("folder","Phone files","Browse shared storage and upload selected files",()->startActivity(new Intent(this,PhoneFilesActivity.class)));
        settingsRow("lock","Phone access","Manage full photo, video and shared-file access",this::phonePermissions);
        settingsLabel("YOUR LIBRARY");settingsRow("people","Shared with me","The files you’ve been invited into",()->navigate("shared"));settingsRow("star","Starred","Keep your favorites close",()->navigate("starred"));settingsRow("trash","Trash","Restore something you set aside",()->navigate("trash"));
        settingsLabel("YOUR ACCOUNT");settingsRow("device","Connected devices","See where you’re signed in",this::connectedDevices);
        if("owner".equals(user.optString("role")))settingsRow("mail","Invite someone","Make room for your people",()->editDialog("Invite by email","",email->mutation("/api/invites",newObject("email",email),"Invitation sent")));
        settingsLabel("TRANSFERS");LinearLayout transferCard=column();transferCard.setPadding(dp(16),dp(16),dp(16),dp(16));transferCard.setBackground(panel(SURFACE));TextView transfer=text("No transfers to show.",12,MUTED);transfer.setTag("transfer-status");add(transferCard,transfer,-2);add(content,transferCard,-2);updateTransferStatus();space(content,8);settingsRow("close","Cancel queued uploads","Completed files stay in your library",()->{WorkManager.getInstance(this).cancelUniqueWork("uploads-"+new CloudApi(this,session).userId());message("Upload queue cancelled. Completed files remain in Cloud.");});
        settingsLabel("EXPLORE");settingsRow("external","Open Cloud on the web",null,()->openWebsite(BuildConfig.CLOUD_ORIGIN+"/"));settingsRow("download","App updates",null,()->openWebsite(BuildConfig.CLOUD_ORIGIN+"/android"));settingsRow("logout","Sign out",null,this::signOut);
        space(content,17);TextView footer=text("MINOVA CLOUD  /  "+BuildConfig.VERSION_NAME+"\nShape your own path.",11,0xff718195);footer.setLineSpacing(dp(7),1);add(content,footer,-2);
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
    private void requestBackupPermissions(){PhoneAccess.media(this,203);}
    private void phonePermissions(){LinearLayout box=column();box.setPadding(dp(22),dp(10),dp(22),dp(16));add(box,text(PhoneAccess.fullMedia(this)?"Photos and videos: full access allowed.":"Photos and videos: access is limited or not allowed. Choose Allow all in Android's permission dialog to include your whole gallery.",13,MUTED),-2);space(box,14);add(box,button("Manage photo & video access",false,()->{if(dialog!=null)dialog.dismiss();PhoneAccess.media(this,206);}),48);space(box,12);add(box,text(PhoneAccess.allFiles()?"Phone files: shared storage access allowed.":"Full shared-storage access is optional. Android keeps other apps' private folders protected.",13,MUTED),-2);space(box,12);add(box,button("Manage phone file access",false,()->{if(dialog!=null)dialog.dismiss();PhoneAccess.files(this,206);}),48);space(box,15);add(box,text("Allowing access does not remove anything. Phone originals are removed only through Free up phone space after cloud verification and your Android confirmation.",12,MUTED),-2);dialog=new AlertDialog.Builder(this).setTitle("Phone access").setView(box).setNegativeButton("Close",null).show();}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] grants){super.onRequestPermissionsResult(request,permissions,grants);if(request==206&&session!=null){phonePermissions();return;}if(request==203&&session!=null){if(BackupWorker.permitted(this,false)||BackupWorker.permitted(this,true))message("Media access updated. Enable automatic backup when you are ready.");else message("Backup stays off until photo or video access is granted.");if(dialog!=null)dialog.dismiss();backupSettings();}}
    private void connectedDevices(){CloudApi api=new CloudApi(this,session);io.execute(()->{try{JSONArray devices=api.request("/api/devices","GET",null).getJSONArray("devices");String[] labels=new String[devices.length()];for(int i=0;i<labels.length;i++){JSONObject device=devices.getJSONObject(i);labels[i]=device.optString("name")+(device.optBoolean("current")?" (this phone)":"");}runOnUiThread(()->{dialog=new AlertDialog.Builder(this).setTitle("Remove a connected device").setItems(labels,(d,index)->{JSONObject device=devices.optJSONObject(index);dialog=new AlertDialog.Builder(this).setTitle("Disconnect "+device.optString("name")+"?").setMessage("This device will need to sign in again. Its cloud files remain.").setNegativeButton("Cancel",null).setPositiveButton("Disconnect",(confirm,which)->io.execute(()->{try{api.request("/api/devices","DELETE",newObject("id",device.optString("id")));runOnUiThread(()->{message("Device disconnected");if(device.optBoolean("current"))localSignOut(api.userId());});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}})).show();}).setNegativeButton("Close",null).show();});}catch(Exception error){runOnUiThread(()->message(error.getMessage()));}});}
    private void signOut(){CloudApi api=new CloudApi(this,session);dialog=new AlertDialog.Builder(this).setTitle("Sign out?").setMessage("Uploads and backup on this phone will stop. Your cloud files remain available on the website.").setNegativeButton("Cancel",null).setPositiveButton("Sign out",(d,w)->io.execute(()->{boolean revoked;try{api.request("/api/logout","POST",new JSONObject());revoked=true;}catch(Exception error){revoked=false;}boolean complete=revoked;runOnUiThread(()->{localSignOut(api.userId());if(!complete)message("Signed out locally. Remove this device on the website to revoke its server access.");});})).show();}
    private void localSignOut(String user){WorkManager.getInstance(this).cancelAllWorkByTag(TransferWork.tag(user));getSharedPreferences("backup-"+user,0).edit().putBoolean("enabled",false).apply();SecureSession.clear(this);thumbnails.evictAll();session=null;share=null;path="";query="";started=false;showLogin();}
    private void openWebsite(String url){try{Intent chooser=Intent.createChooser(new Intent(Intent.ACTION_VIEW,Uri.parse(url)),"Open in browser");chooser.putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS,new ComponentName[]{new ComponentName(this,MainActivity.class)});startActivity(chooser);}catch(Exception error){message("Open "+url+" in your browser.");}}
    private void message(String value){Toast.makeText(this,value==null?"Please try again.":value,Toast.LENGTH_LONG).show();}
    private static String formatBytes(long size){if(size<1024)return size+" B";if(size<1024*1024)return String.format(Locale.getDefault(),"%.1f KB",size/1024d);if(size<1024L*1024*1024)return String.format(Locale.getDefault(),"%.1f MB",size/(1024d*1024));return String.format(Locale.getDefault(),"%.1f GB",size/(1024d*1024*1024));}
}
