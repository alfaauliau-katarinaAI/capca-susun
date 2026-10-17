package ai.katarina.visionlab;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.util.Base64;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.widget.Button;
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
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private Bitmap bitmap;
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
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(Color.rgb(245,248,248));
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(24),dp(18),dp(24));scroll.addView(root);
        TextView title=label("KATARINA / VISION LAB",23);title.setTypeface(null,Typeface.BOLD);root.addView(title);
        root.addView(label("Android offline OCR + optional Cloudflare AI",13));
        root.addView(label("Uji hanya gambar yang kamu miliki atau punya izin. Ini bukan bot Worker 2Captcha.",13));
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
        root.addView(label("Pengaturan Cloudflare",19));
        urlInput=field(root,"https://your-worker.workers.dev");
        keyInput=field(root,"Device Key khusus Cloudflare (bukan 2Captcha Client Key)");
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_VARIATION_PASSWORD);
        expectedInput=field(root,"Panjang teks yang diharapkan, 0 = bebas");
        expectedInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        urlInput.setText(getPreferences(MODE_PRIVATE).getString("endpoint",""));
        expectedInput.setText(getPreferences(MODE_PRIVATE).getString("expected","0"));
        button(root,"Simpan pengaturan",this::saveSettings);
        status=label("Status: pilih gambar",13);root.addView(status);
        result=label("Hasil OCR tampil di sini",17);
        result.setBackground(panel(Color.WHITE));result.setPadding(dp(12),dp(12),dp(12),dp(12));root.addView(result);
        root.addView(label("OCR lokal tidak mengunggah gambar. Cloud AI hanya dipanggil lewat tombol Cloudflare atau fallback hybrid.",12));
        setContentView(scroll);
    }
    private void message(String msg){status.setText("Status: "+msg);}
    private void saveSettings(){
        String endpoint=urlInput.getText().toString().trim().replaceAll("/+$","");
        if(!endpoint.isEmpty()&&!endpoint.matches("https://[a-zA-Z0-9.-]+(?::443)?")){
            message("URL Cloudflare harus HTTPS tanpa path");return;
        }
        String newKey=keyInput.getText().toString().trim();
        if(!newKey.isEmpty()&&newKey.length()<24){message("Device Key minimal 24 karakter");return;}
        try{if(!newKey.isEmpty())new SecretStore(this).save(newKey);}
        catch(Exception e){message("Gagal menyimpan Device Key");return;}
        getPreferences(MODE_PRIVATE).edit().putString("endpoint",endpoint)
          .putString("expected",expectedInput.getText().toString().trim()).apply();
        keyInput.setText("");message("Tersimpan; Device Key terenkripsi");
    }
    @Override protected void onActivityResult(int code,int rc,Intent data){
        super.onActivityResult(code,rc,data);
        if(code!=PICK_IMAGE||rc!=RESULT_OK||data==null)return;
        Uri uri=data.getData();if(uri==null)return;
        try(InputStream stream=getContentResolver().openInputStream(uri)){
            Bitmap raw=BitmapFactory.decodeStream(stream);
            if(raw==null){message("Gambar tidak terbaca");return;}
            int max=Math.max(raw.getWidth(),raw.getHeight());
            float scale=Math.min(1f,1024f/max);
            bitmap=scale<1f?Bitmap.createScaledBitmap(raw,
              Math.max(1,Math.round(raw.getWidth()*scale)),
              Math.max(1,Math.round(raw.getHeight()*scale)),true):raw;
            preview.setImageBitmap(bitmap);result.setText("");message("Gambar siap");
        }catch(Exception e){message("Gagal membuka gambar");}
    }
    private int expectedLength(){
        try{return Math.max(0,Math.min(50,Integer.parseInt(expectedInput.getText().toString().trim())));}
        catch(Exception ignored){return 0;}
    }
    private void runOcr(boolean hybrid){
        if(bitmap==null){message("Pilih gambar dahulu");return;}
        message("Memproses OCR lokal...");
        final var recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        recognizer.process(InputImage.fromBitmap(bitmap,0)).addOnSuccessListener(t->{
            String words=t.getText().trim(),compact=words.replaceAll("[^a-zA-Z0-9]","");
            result.setText("OCR lokal:\n"+(words.isEmpty()?"(kosong)":words));
            message("OCR lokal selesai");
            int expected=expectedLength();
            if(hybrid&&(compact.isEmpty()||(expected>0&&compact.length()!=expected)))cloudOcr();
        }).addOnFailureListener(e->{message("OCR lokal gagal");if(hybrid)cloudOcr();})
          .addOnCompleteListener(t->recognizer.close());
    }
    private void cloudOcr(){
        if(bitmap==null){message("Pilih gambar dahulu");return;}
        String endpoint=urlInput.getText().toString().trim().replaceAll("/+$","");
        if(!endpoint.matches("https://[a-zA-Z0-9.-]+(?::443)?")){message("Isi URL Cloudflare HTTPS");return;}
        String key=keyInput.getText().toString().trim();
        if(key.isEmpty())try{key=new SecretStore(this).load();}catch(Exception e){message("Kunci tidak tersedia");return;}
        if(key.length()<24){message("Device Key belum tersimpan");return;}
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
                try(InputStream in=code<400?c.getInputStream():c.getErrorStream()){
                    ByteArrayOutputStream b=new ByteArrayOutputStream();byte[] chunk=new byte[2048];int n;
                    while((n=in.read(chunk))!=-1&&b.size()<100000)b.write(chunk,0,n);
                    JSONObject resp=new JSONObject(b.toString("UTF-8"));
                    String text=resp.optString("text",""),error=resp.optString("error","");
                    runOnUiThread(()->{
                        if(code>=400)message("AI Cloudflare: "+error+" ("+code+")");
                        else {result.setText(result.getText()+"\nAI Cloudflare:\n"+text);message("Selesai; verifikasi teks secara manual");}
                    });
                }
            }catch(Exception e){runOnUiThread(()->message("Koneksi Cloudflare gagal: "+e.getClass().getSimpleName()));}
            finally{if(c!=null)c.disconnect();}
        });
    }
    @Override protected void onDestroy(){executor.shutdownNow();super.onDestroy();}
}