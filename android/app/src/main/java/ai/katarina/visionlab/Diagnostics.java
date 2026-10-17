package ai.katarina.visionlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Minimal opt-in diagnostic log. No OCR text, image bytes, URLs, keys,
 * cookies, network payloads or user/device identifiers are recorded.
 */
public final class Diagnostics {
    private static final String PREFS="katarina_vision_diagnostics";
    private static final String LOG="events";
    private static final int MAX=80;
    private final Context context;
    public Diagnostics(Context context){this.context=context.getApplicationContext();}

    public synchronized void record(String code){
        if(code==null||!code.matches("[A-Z][A-Z0-9_]{1,55}"))return;
        SharedPreferences pref=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String old=pref.getString(LOG,"");
        String timestamp=new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date());
        String[] rows=(old+timestamp+" "+code+"\n").split("\n");
        StringBuilder output=new StringBuilder();
        for(int i=Math.max(0,rows.length-MAX);i<rows.length;i++){
            if(!rows[i].isEmpty())output.append(rows[i]).append('\n');
        }
        pref.edit().putString(LOG,output.toString()).apply();
    }

    public String report(){
        String version="unknown";
        try{
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),0);
            version=info.versionName;
        }catch(Exception ignored){}
        String events=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).getString(LOG,"");
        return "Katarina Vision Lab / Diagnostic Report\n"+
          "App Version: "+version+"\n"+
          "Android API: "+Build.VERSION.SDK_INT+"\n"+
          "Date: "+new SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(new Date())+"\n\n"+
          "Events (codes only):\n"+(events.isEmpty()?"No events recorded.\n":events)+
          "\nPrivacy: no image, OCR text, URL, Client Key, Device Key, or account data included.\n";
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
