package com.minova.cloud;

import android.app.*;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

abstract class PhoneScreen extends Activity {
    LinearLayout page;android.widget.TextView status;CloudApi api;String user;
    final ExecutorService io=Executors.newFixedThreadPool(2);
    int dp(float n){return MinovaUi.dp(this,n);}
    TextView label(String value,int size,int color){TextView text=new TextView(this);text.setText(value);text.setTextSize(size);text.setTextColor(color);text.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));return text;}
    LinearLayout column(){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);return box;}
    void add(LinearLayout box,View child,int height){box.addView(child,new LinearLayout.LayoutParams(-1,height<0?height:dp(height)));}
    TextView action(String title,boolean primary,Runnable run){TextView view=label(title,14,primary?MinovaUi.NIGHT:MinovaUi.WHITE);view.setGravity(Gravity.CENTER);view.setPadding(dp(12),dp(10),dp(12),dp(10));view.setMinHeight(dp(46));view.setBackground(MinovaUi.touch(this,primary?MinovaUi.CYAN:MinovaUi.SURFACE,MinovaUi.LINE,8));view.setOnClickListener(v->run.run());view.setContentDescription(title);view.setFocusable(true);return view;}
    void message(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    void ui(Runnable run){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())run.run();});}
    @Override protected void onCreate(Bundle state){super.onCreate(state);api=new CloudApi(this);if(api.session==null){finish();return;}user=api.userId();getWindow().setStatusBarColor(MinovaUi.NIGHT);getWindow().setNavigationBarColor(MinovaUi.NIGHT);page=column();page.setBackgroundColor(MinovaUi.NIGHT);page.setPadding(dp(16),dp(18),dp(16),dp(16));setContentView(page);
        page.setOnApplyWindowInsetsListener((v,insets)->{int left,top,right,bottom;if(Build.VERSION.SDK_INT>=30){android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars());left=bars.left;top=bars.top;right=bars.right;bottom=bars.bottom;}else{left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();}v.setPadding(left+dp(16),top+dp(10),right+dp(16),bottom+dp(12));return insets;});
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::back);
    }
    void heading(String title){LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);TextView back=action("‹",false,this::back);back.setContentDescription("Back");row.addView(back,new LinearLayout.LayoutParams(dp(48),dp(48)));TextView caption=label(title,23,MinovaUi.WHITE);caption.setPadding(dp(14),0,0,0);row.addView(caption,new LinearLayout.LayoutParams(0,-2,1));add(page,row,58);}
    void description(String text){TextView caption=label(text,12,MinovaUi.MUTED);caption.setPadding(0,dp(7),0,dp(14));add(page,caption,-2);}
    void status(){status=label("",12,MinovaUi.MUTED);status.setPadding(0,dp(10),0,dp(10));add(page,status,-2);}
    void back(){finish();}
    @Override public boolean onKeyUp(int key,KeyEvent event){if(Build.VERSION.SDK_INT<33&&key==KeyEvent.KEYCODE_BACK){back();return true;}return super.onKeyUp(key,event);}
    @Override protected void onResume(){super.onResume();if(user!=null){try{SafeOffload.current(this,user);}catch(Exception error){finish();}}}
    @Override protected void onDestroy(){io.shutdownNow();super.onDestroy();}
}
