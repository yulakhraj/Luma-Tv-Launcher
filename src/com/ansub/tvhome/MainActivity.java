package com.ansub.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.tv.*;
import android.app.AlertDialog;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.TypedValue;
import android.util.Log;
import android.view.*;
import android.widget.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.*;
import org.json.JSONObject;

/** A small, remote-first home screen. No autoplay, video backgrounds or continuous animations. */
public class MainActivity extends Activity {
    private static final String PREFS="config", APPLE="com.apple.atve.androidtv.appletv";
    private static final String HOTSTAR="in.startv.hotstar", YOUTUBE="com.google.android.youtube.tv";
    private static final String PLAY_STORE="com.android.vending";
    private static Typeface regular,semibold;
    private float scale;
    private FrameLayout root;
    private LinearLayout appRow,allAppsRow;
    private HorizontalScrollView allAppsScroll;
    private TextView heroTitle,heroDescription;
    private TextView greetingView,cityView,weatherTemp,weatherDetail;
    private WeatherIcon weatherIcon;
    private long weatherLastFetch;
    private boolean weatherLoading;
    private final Runnable updateGreeting=new Runnable(){@Override public void run(){
        if(greetingView!=null)greetingView.setText(greeting());
        handler.postDelayed(this,60000);
    }};
    private final Runnable updateWeather=new Runnable(){@Override public void run(){
        refreshWeather();handler.postDelayed(this,30*60*1000);
    }};
    private View firstApp;
    private String highlightedApp=YOUTUBE;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final BroadcastReceiver packagesChanged=new BroadcastReceiver(){
        @Override public void onReceive(Context c,Intent i){buildApps();}
    };
    @Override protected void onCreate(Bundle state){
        super.onCreate(state); loadFonts();
        scale=Math.min(getResources().getDisplayMetrics().widthPixels/1920f,
                getResources().getDisplayMetrics().heightPixels/1080f);
        root=new FrameLayout(this);root.setClipChildren(false);setContentView(root);
        ImageView wallpaper=new ImageView(this);wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        try(java.io.InputStream in=getAssets().open("luma_landscape.png")){wallpaper.setImageBitmap(BitmapFactory.decodeStream(in));}catch(Exception ignored){}
        root.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        View shade=new View(this);shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x3b07101d,0x1007101d,0x4207101d,0xe607101d}));root.addView(shade,new FrameLayout.LayoutParams(-1,-1));
        greetingView=label(root,greeting(),88,52,47,0xfff8f9fc,semibold);
        cityView=label(root,"Patna, Bihar",91,113,21,0xffe0e4ec,regular);
        addNavigation();
        addTopActions();
        label(root,"WELCOME HOME",94,292,19,0xffe0e4ec,semibold);
        heroTitle=label(root,"",94,345,78,0xfffafbff,semibold);
        heroTitle.setSingleLine();heroTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heroTitle.setLayoutParams(at(px(94),px(345),px(1180),px(106)));
        heroDescription=label(root,"",98,456,28,0xffe0e4ec,regular);
        label(root,"Favorites",94,515,25,0xfff8f8fa,semibold);
        TextView hint=label(root,"Select an app to watch",1450,519,19,0xffe0e4ec,regular);
        hint.setGravity(Gravity.RIGHT);hint.setLayoutParams(at(px(1370),px(519),px(468),px(34)));
        appRow=new LinearLayout(this);appRow.setClipChildren(false);appRow.setClipToPadding(false);
        appRow.setPadding(0,px(12),0,0);root.addView(appRow,at(px(94),px(560),px(1744),px(225)));
        label(root,"All apps",94,801,24,0xfff8f8fa,semibold);
        allAppsScroll=new HorizontalScrollView(this);allAppsScroll.setClipChildren(false);allAppsScroll.setClipToPadding(false);
        allAppsScroll.setHorizontalScrollBarEnabled(false);allAppsScroll.setSmoothScrollingEnabled(false);allAppsScroll.setFocusable(false);
        root.addView(allAppsScroll,at(px(94),px(842),px(1744),px(220)));
        allAppsRow=new LinearLayout(this);allAppsRow.setClipChildren(false);allAppsRow.setClipToPadding(false);
        allAppsRow.setPadding(0,px(10),px(14),0);allAppsScroll.addView(allAppsRow,new HorizontalScrollView.LayoutParams(-2,-1));
        buildApps();
        IntentFilter f=new IntentFilter(); f.addAction(Intent.ACTION_PACKAGE_ADDED);f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);f.addDataScheme("package");
        if(Build.VERSION.SDK_INT>=33)registerReceiver(packagesChanged,f,Context.RECEIVER_EXPORTED);else registerReceiver(packagesChanged,f);
        if(firstApp!=null)firstApp.requestFocus();
        android.content.SharedPreferences prefs=getSharedPreferences(PREFS,0);
        weatherTemp.setText(prefs.getString("weather_temp","--°"));
        weatherDetail.setText(prefs.getString("weather_detail","Weather loading"));
        weatherIcon.setCode(prefs.getInt("weather_code",1));
        handler.post(updateGreeting);handler.post(updateWeather);
    }
    private void addNavigation(){
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER);nav.setPadding(px(8),px(6),px(8),px(6));
        nav.setBackground(round(new int[]{0x552c374a,0x33303b4c},38));
        root.addView(nav,at(px(690),px(48),px(390),px(78)));
        addNavItem(nav,"Home",true,()->{if(firstApp!=null)firstApp.requestFocus();});
        addNavItem(nav,"Settings",false,()->openSettings());
    }
    private void addNavItem(LinearLayout nav,String name,boolean selected,Runnable action){
        TextView item=text(px(24),0xfff4f5f8,semibold);item.setText(name);item.setGravity(Gravity.CENTER);
        if(selected)item.setBackground(round(new int[]{0x66596579,0x55515d72},34));
        item.setFocusable(true);item.setClickable(true);item.setOnClickListener(v->action.run());
        item.setOnFocusChangeListener((v,has)->{if(has){v.setBackground(round(new int[]{0x7768758a,0x66525f73},34));v.setElevation(px(8));}else if(!selected)v.setBackgroundColor(Color.TRANSPARENT);});
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(name.equals("Settings")?205:165),-1);lp.setMargins(px(4),0,px(4),0);nav.addView(item,lp);
    }
    private String greeting(){
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour<12?"Good morning":hour<17?"Good afternoon":hour<21?"Good evening":"Good night";
    }
    private void addTopActions(){
        String[] labels={"Inputs","Wi-Fi"};
        int[] kinds={TopActionIcon.INPUT,TopActionIcon.WIFI};
        int[] widths={170,155};int gap=16;
        int x=1105;
        for(int i=0;i<labels.length;i++){
            final int kind=kinds[i];String caption=labels[i];
            ActionChip chip=new ActionChip(this,caption,kind);
            chip.setOnClickListener(v->{if(kind==TopActionIcon.INPUT)showInputs();
                else if(kind==TopActionIcon.WIFI)openWifiSettings();else openSettings();});
            chip.setOnFocusChangeListener((v,has)->((ActionChip)v).setFocused(has));
            root.addView(chip,at(px(x),px(53),px(widths[i]),px(70)));
            x+=widths[i]+gap;
        }
        weatherIcon=new WeatherIcon(this);root.addView(weatherIcon,at(px(1547),px(54),px(48),px(48)));
        weatherTemp=label(root,"--°",1606,50,32,0xfff8f9fc,semibold);
        weatherTemp.setGravity(Gravity.RIGHT);weatherTemp.setLayoutParams(at(px(1606),px(50),px(226),px(47)));
        weatherDetail=label(root,"Weather loading",1530,96,19,0xffe0e4ec,regular);
        weatherDetail.setGravity(Gravity.RIGHT);weatherDetail.setSingleLine();
        weatherDetail.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherDetail.setLayoutParams(at(px(1490),px(95),px(342),px(32)));
        TextClock time=clock("h:mm a","HH:mm",px(48),0xfff8f9fc,semibold);
        time.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        root.addView(time,at(px(1515),px(129),px(317),px(70)));
    }
    private void refreshWeather(){
        if(weatherLoading||(weatherLastFetch!=0&&SystemClock.elapsedRealtime()-weatherLastFetch<25*60*1000))return;
        weatherLoading=true;
        worker.execute(()->{
            try{
                String address="https://api.open-meteo.com/v1/forecast?latitude=25.5941&longitude=85.1356"
                        +"&current=temperature_2m,weather_code,is_day&timezone=auto";
                JSONObject weather=readJson(address).getJSONObject("current");
                int code=weather.getInt("weather_code");
                int temperature=(int)Math.round(weather.getDouble("temperature_2m"));
                String detail=condition(code)+" · Patna";
                getSharedPreferences(PREFS,0).edit().putString("weather_temp",temperature+"°C")
                        .putString("weather_detail",detail).putInt("weather_code",code).apply();
                handler.post(()->{if(isDestroyed())return;
                    weatherTemp.setText(temperature+"°C");weatherDetail.setText(detail);weatherIcon.setCode(code);
                    weatherLastFetch=SystemClock.elapsedRealtime();weatherLoading=false;});
            }catch(Exception error){Log.w("LumaWeather","Could not update weather",error);handler.post(()->{if(isDestroyed())return;
                if("Weather loading".contentEquals(weatherDetail.getText()))weatherDetail.setText("Weather unavailable");
                weatherLastFetch=SystemClock.elapsedRealtime();weatherLoading=false;});}
        });
    }
    private JSONObject readJson(String address)throws Exception{
        HttpURLConnection connection=(HttpURLConnection)new URL(address).openConnection();
        connection.setConnectTimeout(7000);connection.setReadTimeout(7000);
        connection.setRequestProperty("Accept","application/json");
        connection.setRequestProperty("User-Agent","LumaHome/2.4 (Android TV weather)");
        try{
            if(connection.getResponseCode()!=200)throw new Exception("HTTP "+connection.getResponseCode());
            StringBuilder body=new StringBuilder();
            try(BufferedReader input=new BufferedReader(new InputStreamReader(connection.getInputStream(),"UTF-8"))){
                String line;while((line=input.readLine())!=null)body.append(line);
            }
            return new JSONObject(body.toString());
        }finally{connection.disconnect();}
    }
    private static String condition(int code){
        if(code==0)return "Clear";if(code<=3)return "Partly cloudy";
        if(code==45||code==48)return "Fog";
        if(code>=51&&code<=57)return "Drizzle";
        if(code>=61&&code<=67)return "Rain";
        if(code>=71&&code<=77)return "Snow";
        if(code>=80&&code<=82)return "Showers";
        if(code>=85&&code<=86)return "Snow showers";
        if(code>=95)return "Thunderstorm";
        return "Cloudy";
    }
    private static final class WeatherIcon extends View{
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        private int code=1;
        WeatherIcon(Context context){super(context);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        void setCode(int value){code=value;invalidate();}
        @Override protected void onDraw(Canvas canvas){
            float scale=Math.min(getWidth(),getHeight())/48f;canvas.save();canvas.scale(scale,scale);
            paint.setStyle(Paint.Style.FILL);paint.setColor(0xffffd68a);
            canvas.drawCircle(19,18,10,paint);
            if(code==0){paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.5f);
                for(int a=0;a<360;a+=45){double r=Math.toRadians(a);canvas.drawLine(19+(float)Math.cos(r)*14,18+(float)Math.sin(r)*14,19+(float)Math.cos(r)*19,18+(float)Math.sin(r)*19,paint);}}
            if(code!=0){paint.setStyle(Paint.Style.FILL);paint.setColor(0xffe8edf5);
                canvas.drawCircle(19,29,10,paint);canvas.drawCircle(29,26,12,paint);canvas.drawRoundRect(9,28,40,37,5,5,paint);
                if(code>=51){paint.setColor(0xff93c8fa);paint.setStrokeWidth(2.5f);paint.setStrokeCap(Paint.Cap.ROUND);
                    canvas.drawLine(17,40,15,45,paint);canvas.drawLine(28,40,26,45,paint);}}
            canvas.restore();
        }
    }
    private void showInputs(){
        List<TvInputInfo> inputs=availableInputs();
        if(inputs.isEmpty()){
            Toast.makeText(this,"No TV inputs are available.",Toast.LENGTH_SHORT).show();return;
        }
        String[] names=new String[inputs.size()];for(int i=0;i<inputs.size();i++)names[i]=inputLabel(inputs.get(i));
        new AlertDialog.Builder(this).setTitle("Choose an input").setItems(names,(dialog,which)->openInput(inputs.get(which).getId()))
                .setNegativeButton("Cancel",null).show();
    }
    private void openWifiSettings(){
        try{startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));}
        catch(Exception e){startActivity(new Intent(Settings.ACTION_SETTINGS));}
    }
    private void openSettings(){startActivity(new Intent(Settings.ACTION_SETTINGS));}
    private List<TvInputInfo> availableInputs(){
        List<TvInputInfo> out=new ArrayList<>();TvInputManager manager=(TvInputManager)getSystemService(Context.TV_INPUT_SERVICE);
        if(manager==null)return out;
        for(TvInputInfo input:manager.getTvInputList())try{if(input.isPassthroughInput()&&!input.isHidden(this))out.add(input);}catch(Exception ignored){}
        Collections.sort(out,(a,b)->a.getId().compareTo(b.getId()));return out;
    }
    private final class ActionChip extends FrameLayout{
        private final GradientDrawable bg=new GradientDrawable();
        ActionChip(Context context,String title,int iconKind){super(context);setFocusable(true);setClickable(true);
            bg.setCornerRadius(px(40));bg.setColor(0x332f3545);bg.setStroke(px(2),0x44e0e6f0);setBackground(bg);
            TopActionIcon icon=new TopActionIcon(context,iconKind);addView(icon,at(px(24),px(21),px(34),px(34)));
            TextView label=text(px(23),0xfff1f2f5,semibold);label.setText(title);label.setGravity(Gravity.CENTER_VERTICAL);
            addView(label,at(px(72),0,-1,-1));setContentDescription(title);
        }
        void setFocused(boolean focused){bg.setStroke(px(focused?3:2),focused?0xffefcf98:0x44e0e6f0);
            bg.setColor(focused?0x66545b6e:0x332f3545);setElevation(px(focused?8:0));}
    }
    private static final class TopActionIcon extends View{
        static final int INPUT=1,WIFI=2,SETTINGS=3;private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final int kind;
        TopActionIcon(Context context,int k){super(context);kind=k;setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);}
        @Override protected void onDraw(Canvas canvas){float s=Math.min(getWidth(),getHeight())/36f;canvas.save();canvas.scale(s,s);
            paint.setColor(0xffedf0f5);paint.setStrokeWidth(2.8f);paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeJoin(Paint.Join.ROUND);
            if(kind==WIFI){paint.setStyle(Paint.Style.STROKE);for(int r=15;r>=7;r-=4){RectF oval=new RectF(18-r,15-r,18+r,15+r);canvas.drawArc(oval,225,90,false,paint);canvas.drawArc(oval,225,90,false,paint);}
                Path arc=new Path();arc.moveTo(5,12);arc.quadTo(18,-1,31,12);canvas.drawPath(arc,paint);canvas.drawCircle(18,27,2.5f,paint);}
            else if(kind==INPUT){paint.setStyle(Paint.Style.STROKE);canvas.drawRoundRect(4,6,32,25,4,4,paint);canvas.drawLine(12,30,24,30,paint);canvas.drawLine(18,25,18,30,paint);paint.setStyle(Paint.Style.FILL);canvas.drawRoundRect(13,11,23,17,2,2,paint);}
            else {paint.setStyle(Paint.Style.STROKE);canvas.drawCircle(18,18,6,paint);for(int a=0;a<360;a+=45){double rad=Math.toRadians(a);float x1=(float)(18+8*Math.cos(rad)),y1=(float)(18+8*Math.sin(rad));float x2=(float)(18+12*Math.cos(rad)),y2=(float)(18+12*Math.sin(rad));canvas.drawLine(x1,y1,x2,y2,paint);}paint.setStyle(Paint.Style.FILL);canvas.drawCircle(18,18,2,paint);}
            canvas.restore();
        }
    }
    @Override protected void onNewIntent(Intent i){super.onNewIntent(i);setIntent(i);buildApps();}
    @Override protected void onResume(){super.onResume();buildApps();refreshWeather();}
    @Override public void onBackPressed(){if(firstApp!=null)firstApp.requestFocus();}
    @Override protected void onDestroy(){
        worker.shutdownNow();handler.removeCallbacksAndMessages(null);
        unregisterReceiver(packagesChanged);super.onDestroy();
    }
    private boolean installed(String pkg){try{getPackageManager().getApplicationInfo(pkg,0);return true;}catch(Exception e){return false;}}
    private List<String> appList(){
        return Arrays.asList(YOUTUBE,HOTSTAR,APPLE);
    }
    private LinkedHashSet<String> launchablePackages(){
        LinkedHashSet<String> out=new LinkedHashSet<>();
        for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
            Intent query=new Intent(Intent.ACTION_MAIN);query.addCategory(category);
            for(ResolveInfo app:getPackageManager().queryIntentActivities(query,0))
                if(app.activityInfo!=null)out.add(app.activityInfo.packageName);
        }
        return out;
    }
    private List<String> allLaunchableApps(){
        List<String> apps=new ArrayList<>();
        for(String pkg:launchablePackages())if(!pkg.equals(getPackageName())
                &&!pkg.equals("com.ansub.tvhome")
                &&!pkg.equals("com.android.tv"))apps.add(pkg);
        Collections.sort(apps,(a,b)->appName(a).compareToIgnoreCase(appName(b)));
        return apps;
    }
    private String appName(String pkg){
        if(HOTSTAR.equals(pkg))return "JioHotstar";if(YOUTUBE.equals(pkg))return "YouTube";if(APPLE.equals(pkg))return "Apple TV";if(PLAY_STORE.equals(pkg))return "Play Store";
        try{return getPackageManager().getApplicationLabel(getPackageManager().getApplicationInfo(pkg,0)).toString();}catch(Exception e){return "App";}
    }
    private void buildApps(){
        if(appRow==null||allAppsRow==null)return;
        List<String> allApps=allLaunchableApps();
        if(!appList().contains(highlightedApp)&&!allApps.contains(highlightedApp))highlightedApp=YOUTUBE;
        View current=getCurrentFocus();
        boolean restoreFavorites=current==null||isInside(current,appRow);
        boolean restoreAll=isInside(current,allAppsRow);
        appRow.removeAllViews();allAppsRow.removeAllViews();firstApp=null;
        View selectedFavorite=null,selectedAll=null;
        for(String pkg:appList()){
            View card=makeAppCard(pkg,400,160,23);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(400),px(215));lp.rightMargin=px(18);appRow.addView(card,lp);
            if(firstApp==null)firstApp=card;
            if(pkg.equals(highlightedApp))selectedFavorite=card;
        }
        for(String pkg:allApps){
            View card=makeAppCard(pkg,260,120,20);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(px(260),px(188));lp.rightMargin=px(18);allAppsRow.addView(card,lp);
            if(pkg.equals(highlightedApp))selectedAll=card;
        }
        if(restoreAll&&selectedAll!=null)selectedAll.requestFocus();
        else if(restoreAll&&firstApp!=null)firstApp.requestFocus();
        else if(restoreFavorites&&firstApp!=null)(selectedFavorite!=null?selectedFavorite:firstApp).requestFocus();
        if(heroTitle!=null)showAppHero(highlightedApp);
    }
    private View makeAppCard(String pkg,int width,int faceHeight,int captionSize){
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setGravity(Gravity.CENTER);
        card.setClipChildren(false);card.setFocusable(true);card.setClickable(true);
        int radius=width>=400?22:18;
        FrameLayout face=new FrameLayout(this);face.setBackground(round(favoriteColors(pkg),radius));face.setClipToOutline(true);
        Drawable banner=null;try{if(installed(pkg))banner=getPackageManager().getApplicationBanner(pkg);}catch(Exception ignored){}
        if(banner!=null){
            ImageView artworkView=new ImageView(this);artworkView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            artworkView.setImageDrawable(fit(banner,px(width),px(faceHeight)));
            face.addView(artworkView,new FrameLayout.LayoutParams(-1,-1));
        }else{
            ImageView icon=new ImageView(this);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            try{if(installed(pkg))icon.setImageDrawable(getPackageManager().getApplicationIcon(pkg));}catch(Exception ignored){}
            int iconSize=Math.min(width/3,faceHeight-25);
            if(icon.getDrawable()!=null)face.addView(icon,at(px((width-iconSize)/2),px((faceHeight-iconSize)/2),px(iconSize),px(iconSize)));
            else {TextView mark=text(px(width>=400?81:48),Color.WHITE,semibold);mark.setGravity(Gravity.CENTER);mark.setText(APPLE.equals(pkg)?"tv":"+");face.addView(mark,new FrameLayout.LayoutParams(-1,-1));}
        }
        GradientDrawable focusOutline=new GradientDrawable();focusOutline.setColor(Color.TRANSPARENT);
        focusOutline.setCornerRadius(px(radius));focusOutline.setStroke(px(1),0x66ffffff);
        View outline=new View(this);outline.setBackground(focusOutline);
        face.addView(outline,new FrameLayout.LayoutParams(-1,-1));
        card.addView(face,new LinearLayout.LayoutParams(-1,px(faceHeight)));
        TextView caption=text(px(captionSize),0xffd9dce4,semibold);caption.setText(appName(pkg)+(installed(pkg)?"":"  +"));caption.setGravity(Gravity.CENTER);
        caption.setSingleLine();caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(caption,new LinearLayout.LayoutParams(-1,px(42)));
        card.setContentDescription(installed(pkg)?"Open "+appName(pkg):"Install "+appName(pkg)+" from Google Play");
        card.setOnClickListener(v->openApp(pkg));
        card.setOnFocusChangeListener((v,has)->{
            focusOutline.setStroke(px(has?4:1),has?0xfff5f6ff:0x66ffffff);
            face.setScaleX(has?1.055f:1f);face.setScaleY(has?1.055f:1f);
            face.setTranslationY(has?-px(7):0);face.setElevation(px(has?16:0));
            caption.setTextColor(has?Color.WHITE:0xffd9dce4);
            if(has)showAppHero(pkg);
        });
        return card;
    }
    private int[] favoriteColors(String pkg){
        if(HOTSTAR.equals(pkg))return new int[]{0xff1739a5,0xff6222a5};
        if(YOUTUBE.equals(pkg))return new int[]{0xfffafbfd,0xffe8edf5};
        if(PLAY_STORE.equals(pkg))return new int[]{0xfff3f5fa,0xffdce2ed};
        return new int[]{0xff353d48,0xff11161e};
    }
    private void showAppHero(String pkg){
        highlightedApp=pkg;
        if(heroTitle==null||heroDescription==null)return;
        heroTitle.setText(appName(pkg));
        String detail=HOTSTAR.equals(pkg)?"Movies, series and live entertainment"
                :YOUTUBE.equals(pkg)?"Videos, music and creators"
                :APPLE.equals(pkg)?(installed(pkg)?"Apple Originals and your library":"Install Apple TV from Google Play to watch")
                :PLAY_STORE.equals(pkg)?"Find and install more apps"
                :"Press OK to open "+appName(pkg);
        heroDescription.setText(detail);
    }
    private boolean isInside(View v,View parent){while(v!=null){if(v==parent)return true;v=v.getParent() instanceof View?(View)v.getParent():null;}return false;}
    private void openApp(String pkg){
        Intent i=launchIntent(getPackageManager(),pkg);
        if(i==null)i=new Intent(Intent.ACTION_VIEW,Uri.parse("market://details?id="+pkg)).setPackage(PLAY_STORE);
        try{startActivity(i);}catch(Exception e){Toast.makeText(this,"This app is not available on this TV.",Toast.LENGTH_LONG).show();}
    }
    private void openInput(String key){
        Intent i=new Intent(Intent.ACTION_VIEW,TvContract.buildChannelUriForPassthroughInput(key));
        Intent live=new Intent(i).setClassName("com.android.tv","com.android.tv.MainActivity");
        try{startActivity(live.resolveActivity(getPackageManager())!=null?live:i);}catch(Exception ignored){}
    }
    private GradientDrawable round(int[] colors,int radius){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,colors);d.setCornerRadius(px(radius));return d;
    }
    private TextView label(FrameLayout parent,String value,int x,int y,int size,int color,Typeface face){
        TextView t=text(px(size),color,face);t.setText(value);parent.addView(t,at(px(x),px(y),-2,-2));return t;
    }
    // The name set in Settings > Inputs (like "Bedroom"), else the TV's own name (like "HDMI 1").
    private String inputLabel(TvInputInfo in) {
        CharSequence custom = in.loadCustomLabel(this);
        if (custom != null && custom.length() > 0) return custom.toString();
        CharSequence label = in.loadLabel(this);
        return label != null && label.length() > 0 ? label.toString() : "HDMI";
    }

    private static Intent launchIntent(PackageManager pm, String pkg) {
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    // Xiaomi TVs ship MiLan Pro but don't register it by name; every other TV gets Roboto.
    private static void loadFonts() {
        if (regular != null) return;
        regular = fromFile("/system/fonts/MILanPro_Normal.ttf", Typeface.create("sans-serif", Typeface.NORMAL));
        semibold = fromFile("/system/fonts/MILanPro_SemiBold.ttf", Typeface.create("sans-serif-medium", Typeface.NORMAL));
    }

    private static Typeface fromFile(String path, Typeface fallback) {
        try { return new File(path).exists() ? Typeface.createFromFile(path) : fallback; }
        catch (Exception e) { return fallback; }
    }

    // Draws a banner once into a tile-sized bitmap (center-cropped). Some apps ship huge banners
    // that Android 9 refuses to draw ("Canvas: trying to draw too large bitmap"); this also saves RAM.
    private Drawable fit(Drawable d, int w, int h) {
        try {
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            int iw = d.getIntrinsicWidth(), ih = d.getIntrinsicHeight();
            if (iw <= 0 || ih <= 0) { iw = w; ih = h; }
            float s = Math.max((float) w / iw, (float) h / ih);
            int dw = Math.round(iw * s), dh = Math.round(ih * s);
            d.setBounds((w - dw) / 2, (h - dh) / 2, (w - dw) / 2 + dw, (h - dh) / 2 + dh);
            d.draw(new Canvas(b));
            return new BitmapDrawable(getResources(), b);
        } catch (Throwable t) {
            return d;
        }
    }

    private int px(int v) {
        return Math.round(v * scale);
    }

    private TextClock clock(String f12, String f24, int px, int color, Typeface tf) {
        TextClock c = new TextClock(this);
        c.setFormat12Hour(f12);
        c.setFormat24Hour(f24);
        c.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        c.setTextColor(color);
        c.setTypeface(tf);
        return c;
    }

    private TextView text(int px, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_PX, px);
        t.setTextColor(color);
        if (tf != null) t.setTypeface(tf);
        return t;
    }

    private static FrameLayout.LayoutParams at(int x, int y, int w, int h) {
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(w, h);
        lp.leftMargin = x;
        lp.topMargin = y;
        return lp;
    }
}
