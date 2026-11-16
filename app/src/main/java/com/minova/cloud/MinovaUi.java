package com.minova.cloud;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

/** Small native components using the Minova palette and six/eight dp corners. */
final class MinovaUi {
    static final int NIGHT=0xff0d121a,SURFACE=0xff111823,RAISED=0xff17202c,LINE=0xff253242,CYAN=0xff16d8e4,WHITE=0xfff7fafc,MUTED=0xffa7b5c5;
    static int dp(Context context,float value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    static GradientDrawable surface(Context context,int color,int border,int radius){GradientDrawable drawable=new GradientDrawable();drawable.setColor(color);drawable.setCornerRadius(dp(context,radius));if(border!=0)drawable.setStroke(dp(context,1),border);return drawable;}
    static Drawable touch(Context context,int color,int border,int radius){return new RippleDrawable(ColorStateList.valueOf(0x2816d8e4),surface(context,color,border,radius),surface(context,WHITE,0,radius));}
    static ImageView icon(Context context,String name,int color,int size){ImageView image=new ImageView(context);Icon drawable=new Icon(name,color);image.setImageDrawable(drawable);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setLayoutParams(new LinearLayout.LayoutParams(dp(context,size),dp(context,size)));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);return image;}
    static final class Icon extends Drawable {
        final String name;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);int color;
        Icon(String name,int color){this.name=name;this.color=color;}
        private void line(Canvas canvas,float... points){Path path=new Path();path.moveTo(points[0],points[1]);for(int i=2;i<points.length;i+=2)path.lineTo(points[i],points[i+1]);canvas.drawPath(path,paint);}
        private void box(Canvas canvas,float left,float top,float right,float bottom,float radius){canvas.drawRoundRect(left,top,right,bottom,radius,radius,paint);}
        @Override public void draw(Canvas canvas){
            canvas.save();Rect bounds=getBounds();canvas.translate(bounds.left,bounds.top);canvas.scale(bounds.width()/24f,bounds.height()/24f);paint.setColor(color);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(1.6f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            switch(name){
                case "files":box(canvas,3,3,10,10,1);box(canvas,14,3,21,10,1);box(canvas,3,14,10,21,1);box(canvas,14,14,21,21,1);break;
                case "folder":case "new-folder":line(canvas,3,8,3,5,9,5,12,8,21,8,21,20,3,20,3,8,21,8);if(name.equals("new-folder")){line(canvas,9,14,15,14);line(canvas,12,11,12,17);}break;
                case "photos":box(canvas,3,3,21,21,2);canvas.drawCircle(8,8,1.5f,paint);line(canvas,3,17,9,12,13,16,17,10,21,15);break;
                case "videos":box(canvas,3,5,21,19,2);line(canvas,10,9,16,12,10,15,10,9);break;
                case "play":line(canvas,9,5,19,12,9,19,9,5);break;
                case "upload":line(canvas,12,16,12,3,7,8);line(canvas,12,3,17,8);line(canvas,4,15,4,21,20,21,20,15);break;
                case "download":line(canvas,12,3,12,16,7,11);line(canvas,12,16,17,11);line(canvas,4,17,4,21,20,21,20,17);break;
                case "search":canvas.drawCircle(10,10,6,paint);line(canvas,15,15,21,21);break;
                case "refresh":canvas.drawArc(4,4,20,20,30,290,false,paint);line(canvas,20,4,20,10,14,10);break;
                case "share":canvas.drawCircle(6,12,3,paint);canvas.drawCircle(18,5,3,paint);canvas.drawCircle(18,19,3,paint);line(canvas,9,10,15,6);line(canvas,9,14,15,18);break;
                case "star":line(canvas,12,3,14.8f,8.7f,21,9.6f,16.5f,14,17.6f,20.2f,12,17.3f,6.4f,20.2f,7.5f,14,3,9.6f,9.2f,8.7f,12,3);break;
                case "trash":line(canvas,4,6,20,6);line(canvas,8,6,8,3,16,3,16,6);line(canvas,6,6,7,21,17,21,18,6);line(canvas,10,10,10,17);line(canvas,14,10,14,17);break;
                case "cloud":case "backup":Path cloud=new Path();cloud.moveTo(7,18);cloud.cubicTo(0,18,1,7,8,8);cloud.cubicTo(9,-1,22,2,20,11);cloud.cubicTo(25,14,22,19,18,18);canvas.drawPath(cloud,paint);if(name.equals("backup")){line(canvas,12,21,12,11,8,15);line(canvas,12,11,16,15);}break;
                case "device":box(canvas,6,2,18,22,2);line(canvas,10,18,14,18);break;
                case "lock":box(canvas,5,10,19,22,2);canvas.drawArc(8,2,16,16,180,180,false,paint);line(canvas,12,15,12,17);break;
                case "user":canvas.drawCircle(12,7,4,paint);canvas.drawArc(4,13,20,27,180,180,false,paint);break;
                case "people":canvas.drawCircle(9,7,3,paint);canvas.drawArc(2,12,16,26,180,180,false,paint);canvas.drawArc(14,4,21,11,260,210,false,paint);canvas.drawArc(15,12,23,24,250,100,false,paint);break;
                case "more":paint.setStyle(Paint.Style.FILL);canvas.drawCircle(12,5,1.5f,paint);canvas.drawCircle(12,12,1.5f,paint);canvas.drawCircle(12,19,1.5f,paint);break;
                case "menu":line(canvas,4,6,20,6);line(canvas,4,12,20,12);line(canvas,4,18,15,18);break;
                case "arrow":line(canvas,4,12,20,12,14,6);line(canvas,20,12,14,18);break;
                case "chevron":line(canvas,9,6,15,12,9,18);break;
                case "back":line(canvas,18,12,5,12,11,6);line(canvas,5,12,11,18);break;
                case "plus":line(canvas,12,5,12,19);line(canvas,5,12,19,12);break;
                case "external":line(canvas,14,3,21,3,21,10);line(canvas,21,3,11,13);line(canvas,10,4,4,4,4,20,20,20,20,14);break;
                case "logout":line(canvas,10,3,4,3,4,21,10,21);line(canvas,9,12,22,12,17,7);line(canvas,22,12,17,17);break;
                case "mail":box(canvas,3,5,21,19,2);line(canvas,3,6,12,13,21,6);break;
                case "close":line(canvas,6,6,18,18);line(canvas,18,6,6,18);break;
                default:line(canvas,14,2,5,2,5,22,19,22,19,7,14,2,14,7,19,7);line(canvas,9,12,15,12);line(canvas,9,16,15,16);break;
            }
            canvas.restore();
        }
        @Override public void setAlpha(int alpha){paint.setAlpha(alpha);invalidateSelf();}
        @Override public void setColorFilter(ColorFilter filter){paint.setColorFilter(filter);invalidateSelf();}
        @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
        @Override public int getIntrinsicWidth(){return 24;}
        @Override public int getIntrinsicHeight(){return 24;}
    }
    static final class OrbitPanel extends FrameLayout {
        final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        OrbitPanel(Context context){super(context);setWillNotDraw(false);setClipToOutline(true);GradientDrawable background=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xff152b40,0xff101a28});background.setCornerRadius(dp(context,8));background.setStroke(dp(context,1),0xff294154);setBackground(background);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(getContext(),1));paint.setColor(0x1830d8e4);float width=getWidth(),height=getHeight();for(int i=0;i<4;i++)canvas.drawOval(width*.5f-i*45,-height*.85f-i*5,width*1.6f+i*40,height*1.1f+i*18,paint);paint.setColor(0x1b1769ff);canvas.drawLine(width*.3f,height,width,height*.15f,paint);}
    }
}
