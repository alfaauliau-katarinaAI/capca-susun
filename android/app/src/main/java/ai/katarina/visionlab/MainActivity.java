package ai.katarina.visionlab;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.text.InputType;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** User-selected image OCR only; not a 2Captcha worker client. */
public final class MainActivity extends Activity {
    private static final int PICK_IMAGE=2026;
    private static final String WORKER_ENDPOINT="https://katarina-vision-lab.alfaaulia8.workers.dev";
    private static final String CAPTCHA_LOGIN="https://2captcha.com/auth/login";
    private static final String CAPTCHA_WORKER="https://2captcha.com/cabinet";
    private static final String CAPTCHA_ANDROID_APP="https://play.google.com/store/apps/details?id=com.rucaptcha.bot";
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Bitmap bitmap;
    private boolean demoRunning=false;
    private Diagnostics diagnostics;
    private PairingHelper pairing;
    private TextView pairingCodeText;
    private ImageView preview;
    private TextView status, result;
    private EditText urlInput, keyInput, expectedInput;
    private final int fg=Color.rgb(29,53,57), accent=Color.rgb(20,104,96);
    private int dp(int n){ return (int)(getResources().getDisplayMetrics().density*n+.5f); }
    private GradientDrawable panel(int color){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12)); return d;
    }
    private TextView label(String value,int size){
        TextView t=new TextView(this);t.setText(value);t.setTextColor(fg);t.setTextSize(size);
        t.setPadding(0,dp(7),0,dp(7));return t;
    }
    private EditText field(LinearLayout root,String hint){
        EditText e=new EditText(this);e.setSingleLine(true);e.setTextSize(14);
        e.setHint(hint);e.setBackground(panel(Color.WHITE));e.setPadding(dp(12),dp(12),dp(12),dp(12));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(8);
        root.addView(e,p);return e;
    }
    private Button button(LinearLayout root,String title,Runnable action){
        Button b=new Button(this);b.setText(title);b.setTextSize(13);b.setAllCaps(false);b.setTextColor(Color.WHITE);
        b.setBackground(panel(accent));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(48));p.bottomMargin=dp(8);
        root.addView(b,p);b.setOnClickListener(v->action.run());return b;
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        diagnostics=new Diagnostics(this);
        pairing=new PairingHelper(this);
        diagnostics.installCrashRecorder();
        diagnostics.record("APP_OPEN");
        diagnostics.flushAsync();
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,248,248));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(24),dp(18),dp(24));scroll.addView(root);
        TextView title=label("KATARINA / VISION LAB",23);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(label("Android offline OCR + optional Cloudflare AI (v0.8)",13));
        root.addView(label("Uji hanya gambar yang kamu miliki atau punya izin. Integrasi ini tidak mengakses akun Worker tanpa kamu login langsung di situs resmi.",13));
        root.addView(label("Uji OCR teks / gambar sederhana",19));
        root.addView(label("Tanpa login, tanpa Device Key, dan tanpa mengunggah gambar.",12));
        button(root,"Jalankan demo OCR offline",this::runDemoOcr);
        button(root,"Pilih gambar",()->{
            Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT);
            pick.addCategory(Intent.CATEGORY_OPENABLE);pick.setType("image/*");
            startActivityForResult(pick,PICK_IMAGE);
        });
        preview=new ImageView(this);preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.setBackground(panel(Color.WHITE));
        LinearLayout.LayoutParams pp=new LinearLayout.LayoutParams(-1,dp(180));pp.bottomMargin=dp(12);
        root.addView(preview,pp);
        button(root,"Baca teks lokal (offline)",()->runOcr(false));
        button(root,"Hybrid (lokal lalu AI bila perlu)",()->runOcr(true));
        button(root,"Coba AI Cloudflare",this::cloudOcr);
        button(root,"Salin hasil OCR",this::copyOcrText);
        status=label("Status: pilih gambar",13);root.addView(status);
        result=label("Hasil OCR tampil di sini",17);
        result.setBackground(panel(Color.WHITE));result.setPadding(dp(12),dp(12),dp(12),dp(12));root.addView(result);
        root.addView(label("Hubungkan Cloudflare tanpa Device Key",19));
        pairingCodeText=label("Kode pairing: "+(pairing.pendingCode().isEmpty()?"belum dibuat":pairing.pendingCode()),18);
        pairingCodeText.setTypeface(null,Typeface.BOLD);
        root.addView(pairingCodeText);
        root.addView(label("1. Tekan Buat Kode. 2. Salin kode dan kirim ke chat Katarina agar disetujui. 3. Tekan Selesaikan Koneksi. Tidak perlu tahu Device Key lama.",12));
        button(root,"Buat kode pairing",this::beginPairing);
        button(root,"Salin kode pairing",this::copyPairCode);
        button(root,"Selesaikan koneksi",this::finishPairing);
        root.addView(label("Pengaturan Cloudflare",19));
        urlInput=field(root,"https://your-worker.workers.dev");
        keyInput=field(root,"Opsional: Device Key lama (tidak diperlukan jika pairing)");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        expectedInput=field(root,"Panjang teks yang diharapkan, 0 = bebas");
        expectedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        urlInput.setText(WORKER_ENDPOINT);
        urlInput.setEnabled(false);
        expectedInput.setText(getPreferences(MODE_PRIVATE).getString("expected","0"));
        button(root,"Simpan pengaturan",this::saveSettings);
        button(root,"Tes koneksi Cloudflare",this::checkBackend);
        root.addView(label("Pemantauan kesalahan",19));
        CheckBox automaticReporting=new CheckBox(this);
        automaticReporting.setText("Kirim otomatis kode error anonim ke Cloudflare");
        automaticReporting.setTextSize(14);
        automaticReporting.setTextColor(fg);
        automaticReporting.setChecked(diagnostics.isAutomaticEnabled());
        automaticReporting.setOnCheckedChangeListener((view,enabled)->{
            diagnostics.setAutomaticEnabled(enabled);
            if(status!=null)message(enabled?"Laporan error otomatis aktif":"Laporan error otomatis dimatikan");
        });
        root.addView(automaticReporting);
        root.addView(label("Hanya kode error, versi aplikasi, dan versi Android. Tanpa gambar, isi OCR, password, Client Key atau identitas HP. Data disimpan maksimum 7 hari di Cloudflare. Laporan crash dikirim saat aplikasi dibuka lagi. Kamu dapat mematikan fitur kapan saja.",12));
        button(root,"Kirim laporan error tertunda",()->{diagnostics.flushAsync();message("Pengiriman error tertunda dicoba, jika fitur aktif dan Device Key tersimpan");});
        button(root,"Bagikan laporan diagnostik",this::shareReport);
        root.addView(label("2Captcha — akses akun resmi",19));
        button(root,"Masuk akun 2Captcha (resmi)",()->openOfficial2Captcha(CAPTCHA_LOGIN,"WORKER_LOGIN_OPENED"));
        button(root,"Buka dashboard Worker",()->openOfficial2Captcha(CAPTCHA_WORKER,"WORKER_DASHBOARD_OPENED"));
        button(root,"Aplikasi 2Captcha Bot resmi",()->openOfficial2Captcha(CAPTCHA_ANDROID_APP,"WORKER_OFFICIAL_APP_OPENED"));
        root.addView(label("Login resmi saja. Rilis ini belum mengambil atau mengirim pekerjaan Worker berbayar secara otomatis.",12));

        root.addView(label("OCR lokal tidak mengunggah gambar. Cloud AI hanya dipanggil lewat tombol Cloudflare atau fallback hybrid.",12));
        root.addView(label("Laporan dapat dibaca di ringkasan Cloudflare berizin, bukan akses jarak jauh ke layar HP.",12));
        setContentView(scroll);
        if(getIntent()!=null&&getIntent().getBooleanExtra("katarina_demo_ocr",false))runDemoOcr();
    }
    private void message(String msg){status.setText("Status: "+msg);}
    private void saveSettings(){
        String endpoint=WORKER_ENDPOINT;
        if(!endpoint.isEmpty()&&!endpoint.matches("https://[a-zA-Z0-9.-]+(?::443)?")){
            message("URL Cloudflare harus HTTPS tanpa path");return;
        }
        String newKey=keyInput.getText().toString().trim();
        if(!newKey.isEmpty()&&newKey.length()<24){message("Device Key minimal 24 karakter");return;}
        try{if(!newKey.isEmpty())new SecretStore(this).save(newKey);}
        catch(Exception e){message("Gagal menyimpan Device Key");return;}
        getPreferences(MODE_PRIVATE).edit().putString("endpoint",endpoint)
          .putString("expected",expectedInput.getText().toString().trim()).apply();
        keyInput.setText("");diagnostics.record("SETTINGS_SAVED");diagnostics.flushAsync();message("Tersimpan; Device Key terenkripsi");
    }
    @Override protected void onActivityResult(int code,int rc,Intent data){
        super.onActivityResult(code,rc,data);
        if(code!=PICK_IMAGE||rc!=RESULT_OK||data==null)return;
        Uri uri=data.getData();if(uri==null)return;
        try{
            BitmapFactory.Options opts=new BitmapFactory.Options();
            opts.inJustDecodeBounds=true;
            try(InputStream in=getContentResolver().openInputStream(uri)){ BitmapFactory.decodeStream(in,null,opts); }
            if(opts.outWidth<=0||opts.outHeight<=0)throw new IllegalArgumentException("invalid image");
            int sample=1;
            while(Math.max(opts.outWidth,opts.outHeight)/sample>1600&&sample<128)sample*=2;
            BitmapFactory.Options scaled=new BitmapFactory.Options();
            scaled.inSampleSize=sample;
            scaled.inPreferredConfig=Bitmap.Config.RGB_565;
            Bitmap raw;
            try(InputStream in=getContentResolver().openInputStream(uri)){ raw=BitmapFactory.decodeStream(in,null,scaled); }
            if(raw==null){diagnostics.record("IMAGE_INVALID");message("Gambar tidak terbaca");return;}
            int max=Math.max(raw.getWidth(),raw.getHeight());
            float scale=Math.min(1f,1024f/max);
            bitmap=scale<1f?Bitmap.createScaledBitmap(raw,
              Math.max(1,Math.round(raw.getWidth()*scale)),
              Math.max(1,Math.round(raw.getHeight()*scale)),true):raw;
            preview.setImageBitmap(bitmap);result.setText("");diagnostics.record("IMAGE_SELECTED");message("Gambar siap");
        }catch(Exception e){diagnostics.record("IMAGE_OPEN_ERROR");message("Gagal membuka gambar");}
    }
    private void runDemoOcr(){
        try{
            // Deterministic, synthetic example: never a real platform CAPTCHA.
            Bitmap demo=Bitmap.createBitmap(800,300,Bitmap.Config.ARGB_8888);
            Canvas canvas=new Canvas(demo);
            canvas.drawColor(Color.WHITE);
            Paint p=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.SUBPIXEL_TEXT_FLAG);
            p.setColor(Color.BLACK);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(205f);
            canvas.drawText("TEST",75f,220f,p);
            bitmap=demo;
            preview.setImageBitmap(bitmap);
            result.setText("");
            demoRunning=true;
            diagnostics.record("OCR_DEMO_START");
            runOcr(false);
        }catch(Exception e){
            demoRunning=false;
            diagnostics.record("OCR_DEMO_FAILED");
            Log.e("KatarinaOCR","OCR_DEMO_FAILED "+e.getClass().getSimpleName());
            message("Demo offline gagal. Periksa laporan diagnostik.");
        }
    }
    private void copyOcrText(){
        String value=result.getText().toString().trim();
        if(value.isEmpty()){message("Belum ada hasil OCR untuk disalin.");return;}
        ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager!=null){
            manager.setPrimaryClip(ClipData.newPlainText("Katarina OCR",value));
            diagnostics.record("OCR_TEXT_COPIED");
            message("Hasil OCR tersalin.");
        }
    }
    private int expectedLength(){
        try{return Math.max(0,Math.min(50,Integer.parseInt(expectedInput.getText().toString().trim())));}
        catch(Exception ignored){return 0;}
    }
    private void runOcr(boolean hybrid){
        if(bitmap==null){message("Pilih gambar dahulu");return;}
        diagnostics.record(hybrid?"OCR_HYBRID_START":"OCR_LOCAL_START");
        message("Memproses OCR lokal...");
        final var recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(t->{
            String words=t.getText().trim(),compact=words.replaceAll("[^a-zA-Z0-9]","");
            result.setText("OCR lokal:\n"+(words.isEmpty()?"(kosong)":words));
            diagnostics.record("OCR_LOCAL_SUCCESS");
            if(demoRunning){
                demoRunning=false;
                boolean exact="TEST".equals(compact.toUpperCase(java.util.Locale.US));
                diagnostics.record(exact?"OCR_DEMO_PASS":"OCR_DEMO_MISMATCH");
                Log.i("KatarinaOCR",exact?"OCR_DEMO_PASS":"OCR_DEMO_MISMATCH");
            }
            message("OCR lokal selesai");
            int expected=expectedLength();
            if(hybrid&&(compact.isEmpty()||(expected>0&&compact.length()!=expected)))cloudOcr();
        }).addOnFailureListener(e->{diagnostics.record("OCR_LOCAL_FAILED");if(demoRunning){demoRunning=false;diagnostics.record("OCR_DEMO_FAILED");Log.e("KatarinaOCR","OCR_DEMO_FAILED");}message("OCR lokal gagal");if(hybrid)cloudOcr();})
          .addOnCompleteListener(t->recognizer.close());
    }
    private void cloudOcr(){
        if(bitmap==null){message("Pilih gambar dahulu");return;}
        diagnostics.record("CLOUD_REQUEST_START");
        String endpoint=WORKER_ENDPOINT;
        if(!endpoint.matches("https://[a-zA-Z0-9.-]+(?::443)?")){diagnostics.record("CLOUD_NO_URL");message("Isi URL Cloudflare HTTPS");return;}
        String key=keyInput.getText().toString().trim();
        if(key.isEmpty())try{key=new SecretStore(this).load();}catch(Exception e){message("Kunci tidak tersedia");return;}
        if(key.length()<24){diagnostics.record("CLOUD_NO_KEY");message("Device Key belum tersimpan");return;}
        final String secret=key;final Bitmap image=bitmap;
        message("Memanggil Cloudflare AI...");
        executor.execute(()->{
            HttpURLConnection c=null;
            try{
                ByteArrayOutputStream bytes=new ByteArrayOutputStream();
                Bitmap working=image;int quality=85;
                for(int i=0;i<16;i++){
                    bytes.reset();working.compress(Bitmap.CompressFormat.JPEG,quality,bytes);
                    if(bytes.size()<=350000)break;
                    if(quality>40)quality-=15;
                    else working=Bitmap.createScaledBitmap(working,
                      Math.max(1,working.getWidth()*3/4),Math.max(1,working.getHeight()*3/4),true);
                }
                if(bytes.size()>350000)throw new IllegalArgumentException("image_too_large");
                JSONObject payload=new JSONObject();
                payload.put("image","data:image/jpeg;base64,"+Base64.encodeToString(bytes.toByteArray(),Base64.NO_WRAP));
                c=(HttpURLConnection)new URL(endpoint+"/v1/ocr").openConnection();
                c.setConnectTimeout(12000);c.setReadTimeout(30000);
                c.setRequestMethod("POST");c.setDoOutput(true);
                c.setRequestProperty("Content-Type","application/json");
                c.setRequestProperty("X-Katarina-Key",secret);
                try(OutputStream os=c.getOutputStream()){os.write(payload.toString().getBytes(StandardCharsets.UTF_8));}
                int code=c.getResponseCode();
                diagnostics.record("CLOUD_HTTP_"+code);
                try(InputStream in=code<400?c.getInputStream():c.getErrorStream()){
                    ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int n;
                    while((n=in.read(chunk))!=-1&&b.size()<100000)b.write(chunk,0,n);
                    JSONObject resp=new JSONObject(b.toString("UTF-8"));
                    String text=resp.optString("text",""),error=resp.optString("error","");
                    runOnUiThread(()->{
                        if(code>=400){
                            if(code==502)message("AI Cloudflare belum siap (lisensi/model/kuota). OCR lokal tetap tersedia.");
                            else if(code==401)message("Device Key tidak cocok.");
                            else message("AI Cloudflare: "+error+" ("+code+")");
                        }
                        else {result.setText(result.getText()+"\nAI Cloudflare:\n"+text);message("Selesai; verifikasi teks secara manual");}
                    });
                }
            }catch(Exception e){diagnostics.record("CLOUD_NETWORK_ERROR");runOnUiThread(()->message("Koneksi Cloudflare gagal: "+e.getClass().getSimpleName()));}
            finally{if(c!=null)c.disconnect();}
        });
    }
    private void checkBackend(){
        String endpoint=WORKER_ENDPOINT;
        if(!endpoint.matches("https://[a-zA-Z0-9.-]+(?::443)?")){
            diagnostics.record("HEALTH_NO_URL");
            message("Masukkan URL Cloudflare HTTPS terlebih dahulu");
            return;
        }
        diagnostics.record("HEALTH_START");
        message("Mengecek /health...");
        executor.execute(()->{
            HttpURLConnection connection=null;
            try{
                connection=(HttpURLConnection)new URL(endpoint+"/health").openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(7000);
                connection.setRequestMethod("GET");
                final int statusCode=connection.getResponseCode();
                diagnostics.record("HEALTH_HTTP_"+statusCode);
                runOnUiThread(()->message(statusCode==200
                    ?"Cloudflare endpoint dapat diakses (model AI belum diuji)"
                    :"Cloudflare /health: HTTP "+statusCode));
            }catch(Exception e){
                diagnostics.record("HEALTH_NETWORK_ERROR");
                runOnUiThread(()->message("Tidak dapat menghubungi Cloudflare: "+e.getClass().getSimpleName()));
            }finally{if(connection!=null)connection.disconnect();}
        });
    }

    /** Open only hard-coded HTTPS destinations on the official 2Captcha or Google Play domains. */
    private void openOfficial2Captcha(String address,String diagnosticCode){
        Uri uri=Uri.parse(address);
        String host=uri.getHost();
        if(!"https".equals(uri.getScheme())||
            !("2captcha.com".equals(host)||"play.google.com".equals(host))){
            diagnostics.record("OFFICIAL_LINK_REJECTED");
            message("Alamat 2Captcha tidak valid");
            return;
        }
        try{
            Intent browser=new Intent(Intent.ACTION_VIEW,uri);
            browser.addCategory(Intent.CATEGORY_BROWSABLE);
            startActivity(browser);
            diagnostics.record(diagnosticCode);
            message("Halaman resmi dibuka di browser. Login di sana, bukan di Katarina.");
        }catch(android.content.ActivityNotFoundException e){
            diagnostics.record("OFFICIAL_BROWSER_UNAVAILABLE");
            message("Tidak ditemukan browser. Instal Chrome atau browser terpercaya.");
        }
    }

    private void beginPairing(){
        message("Membuat pairing terenkripsi...");
        executor.execute(()->{
            try{
                String code=pairing.begin();
                diagnostics.record("PAIR_START_SUCCESS");
                runOnUiThread(()->{
                    pairingCodeText.setText("Kode pairing: "+code);
                    message("Kirim kode "+code+" ke chat Katarina untuk disetujui.");
                });
            }catch(Exception e){
                diagnostics.record("PAIR_START_FAILED");
                runOnUiThread(()->message("Pairing gagal: "+e.getClass().getSimpleName()));
            }
        });
    }
    private void copyPairCode(){
        String code=pairing.pendingCode();
        if(code.isEmpty()){message("Buat kode pairing terlebih dahulu");return;}
        ClipboardManager manager=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
        if(manager!=null){
            manager.setPrimaryClip(ClipData.newPlainText("Katarina pairing code",code));
            message("Kode pairing tersalin. Kirim ke chat untuk persetujuan.");
        }
    }
    private void finishPairing(){
        message("Memeriksa persetujuan pairing...");
        executor.execute(()->{
            try{
                String info=pairing.finish();
                diagnostics.record("PAIR_CHECK_SUCCESS");
                runOnUiThread(()->{
                    pairingCodeText.setText("Kode pairing: "+(pairing.pendingCode().isEmpty()?"tersambung":pairing.pendingCode()));
                    message(info);
                });
                diagnostics.flushAsync();
            }catch(Exception e){
                diagnostics.record("PAIR_CHECK_FAILED");
                runOnUiThread(()->message("Pairing belum selesai: "+e.getClass().getSimpleName()));
            }
        });
    }
    private void shareReport(){
        diagnostics.record("REPORT_SHARED_BY_USER");
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_SUBJECT,"Katarina Vision Lab - Diagnostic Report");
        send.putExtra(Intent.EXTRA_TEXT,diagnostics.report());
        startActivity(Intent.createChooser(send,"Bagikan laporan diagnostik"));
    }

    @Override protected void onDestroy(){
        executor.shutdownNow();
        super.onDestroy();
    }
}