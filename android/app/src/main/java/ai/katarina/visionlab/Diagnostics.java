package ai.katarina.visionlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.io.OutputStream;
import org.json.JSONObject;
import org.json.JSONArray;

/** Privacy-first diagnostic report with on-device opt-in and bounded automatic upload. */
public final class Diagnostics {
    private static final String PREFS="katarina_vision_diagnostics";
    private static final String LOG="events";
    private static final String PENDING="pending_error_codes";
    private static final String ENABLED="automatic_enabled";
    private static final String ENDPOINT="https://katarina-vision-lab.alfaaulia8.workers.dev/v1/telemetry";
    private static final int MAX=80;
    private final Context context;
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final AtomicBoolean uploadInProgress=new AtomicBoolean(false);
    public Diagnostics(Context context){this.context=context.getApplicationContext();}

    private SharedPreferences prefs(){return context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}
    public boolean isAutomaticEnabled(){return prefs().getBoolean(ENABLED,false);}
    public void setAutomaticEnabled(boolean enabled){
        prefs().edit().putBoolean(ENABLED,enabled).apply();
        if(enabled)flushAsync();
    }
    private boolean isError(String code){
        return code.startsWith("UNCAUGHT_")||code.endsWith("_FAILED")||
            code.endsWith("_ERROR")||code.startsWith("CLOUD_HTTP_4")||
            code.startsWith("CLOUD_HTTP_5")||code.startsWith("HEALTH_HTTP_4")||
            code.startsWith("HEALTH_HTTP_5")||
            code.equals("OFFICIAL_BROWSER_UNAVAILABLE")||
            code.equals("OFFICIAL_LINK_REJECTED")||
            code.equals("IMAGE_INVALID");
    }
    public synchronized void record(String code){
        if(code==null||!code.matches("[A-Z][A-Z0-9_]{1,55}"))return;
        SharedPreferences pref=prefs();
        String timestamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());
        String[] rows=(pref.getString(LOG,"")+timestamp+" "+code+"\n").split("\n");
        StringBuilder out=new StringBuilder();
        for(int i=Math.max(0,rows.length-MAX);i<rows.length;i++)
            if(!rows[i].isEmpty())out.append(rows[i]).append('\n');
        SharedPreferences.Editor editor=pref.edit().putString(LOG,out.toString());
        boolean err=isError(code);
        if(err){
            String[] queue=(pref.getString(PENDING,"")+code+"\n").split("\n");
            StringBuilder bounded=new StringBuilder();
            for(int i=Math.max(0,queue.length-40);i<queue.length;i++)
                if(!queue[i].isEmpty())bounded.append(queue[i]).append('\n');
            editor.putString(PENDING,bounded.toString());
        }
        // commit rather than apply so uncaught exception diagnostics survive a crash
        editor.commit();
        if(err&&isAutomaticEnabled())flushAsync();
    }
    public synchronized int pendingCount(){
        String str=prefs().getString(PENDING,"");
        return str.isEmpty()?0:str.split("\n").length;
    }
    public void flushAsync(){
        if(!isAutomaticEnabled()||!uploadInProgress.compareAndSet(false,true))return;
        executor.execute(()->{
            try{uploadPending();}
            finally{uploadInProgress.set(false);}
        });
    }
    private void uploadPending(){
        String secret;
        try{secret=new SecretStore(context).load();}catch(Exception e){return;}
        if(secret==null||secret.length()<24)return;
        final String captured;
        synchronized(this){captured=prefs().getString(PENDING,"");}
        if(captured.isEmpty())return;
        String[] all=captured.split("\n");
        JSONArray events=new JSONArray();
        for(int i=0;i<Math.min(20,all.length);i++)
            if(all[i].matches("[A-Z][A-Z0-9_]{1,55}"))events.put(all[i]);
        if(events.length()==0)return;
        HttpURLConnection connection=null;
        try{
            JSONObject payload=new JSONObject();
            payload.put("version",version());
            payload.put("android_api",Build.VERSION.SDK_INT);
            payload.put("events",events);
            byte[] bytes=payload.toString().getBytes(StandardCharsets.UTF_8);
            connection=(HttpURLConnection)new URL(ENDPOINT).openConnection();
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(9000);
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type","application/json");
            connection.setRequestProperty("X-Katarina-Key",secret);
            connection.setDoOutput(true);
            try(OutputStream output=connection.getOutputStream()){output.write(bytes);}
            if(connection.getResponseCode()!=200)return;
            synchronized(this){
                // Only delete the exact prefix already sent. Any newly queued errors remain.
                String current=prefs().getString(PENDING,"");
                String prefix=String.join("\n",java.util.Arrays.copyOfRange(all,0,Math.min(20,all.length)))+"\n";
                if(current.startsWith(prefix))prefs().edit().putString(PENDING,current.substring(prefix.length())).commit();
            }
        }catch(Exception ignored){
            // Keep queued error codes offline; never record the uploader's own errors.
        }finally{if(connection!=null)connection.disconnect();}
    }
    private String version(){
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            return info.versionName;
        }catch(Exception ignored){return "0.6.0";}
    }
    public String report(){
        String events=prefs().getString(LOG,"");
        return "Katarina Vision Lab / Diagnostic Report\n"+
          "App Version: "+version()+"\n"+
          "Android API: "+Build.VERSION.SDK_INT+"\n"+
          "Date: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date())+"\n"+
          "Automatic Diagnostics: "+(isAutomaticEnabled()?"ON":"OFF")+"\n"+
          "Pending offline error codes: "+pendingCount()+"\n\n"+
          "Events (codes only):\n"+(events.isEmpty()?"No events recorded.\n":events)+
          "\nPrivacy: no images, OCR text, URL, Client Key, Device Key, accounts, or device IDs.\n";
    }
    public void installCrashRecorder(){
        final Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            try{record("UNCAUGHT_"+error.getClass().getSimpleName().replaceAll("[^A-Za-z0-9]","").toUpperCase(Locale.US));}
            catch(Throwable ignored){}
            if(previous!=null)previous.uncaughtException(thread,error);
        });
    }
}
