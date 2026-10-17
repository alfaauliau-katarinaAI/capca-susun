package ai.katarina.visionlab;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import javax.crypto.Cipher;
import org.json.JSONObject;

/**
 * Secure, user-approved Android enrollment.
 * The app never asks for the GitHub APP_DEVICE_KEY.
 * Cloudflare encrypts a separate per-device token to an Android Keystore public key.
 */
public final class PairingHelper {
    private static final String BASE="https://katarina-vision-lab.alfaaulia8.workers.dev";
    private static final String ALIAS="katarina_pairing_rsa_v1";
    private static final String PREFS="katarina_pending_pairing";
    private final Context ctx;
    public PairingHelper(Context ctx){this.ctx=ctx.getApplicationContext();}
    private SharedPreferences prefs(){return ctx.getSharedPreferences(PREFS,Context.MODE_PRIVATE);}

    private KeyStore keyStore() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        return store;
    }
    private PublicKey ensurePublicKey() throws Exception {
        KeyStore store=keyStore();
        if(store.containsAlias(ALIAS))return store.getCertificate(ALIAS).getPublicKey();
        KeyPairGenerator gen=KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA,"AndroidKeyStore");
        gen.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_DECRYPT)
            .setKeySize(2048)
            .setDigests(KeyProperties.DIGEST_SHA1,KeyProperties.DIGEST_SHA256)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_RSA_OAEP)
            .build());
        return gen.generateKeyPair().getPublic();
    }
    private static JSONObject post(String path,JSONObject body) throws Exception {
        HttpURLConnection conn=null;
        try{
            conn=(HttpURLConnection)new URL(BASE+path).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(9000);
            conn.setReadTimeout(12000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type","application/json");
            byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);
            if(bytes.length>1800)throw new IllegalArgumentException("Oversized pairing");
            try(OutputStream out=conn.getOutputStream()){out.write(bytes);}
            int code=conn.getResponseCode();
            InputStream in=code<400?conn.getInputStream():conn.getErrorStream();
            ByteArrayOutputStream b=new ByteArrayOutputStream();
            try(InputStream input=in){byte[] chunk=new byte[1024];int n;
                while((n=input.read(chunk))!=-1 && b.size()<8192)b.write(chunk,0,n);
            }
            JSONObject response=new JSONObject(b.toString("UTF-8"));
            if(code>=400)throw new IllegalStateException(response.optString("error","pairing_error")+" (HTTP "+code+")");
            return response;
        }finally{if(conn!=null)conn.disconnect();}
    }
    public String begin() throws Exception {
        PublicKey pub=ensurePublicKey();
        String encoded=Base64.encodeToString(pub.getEncoded(),Base64.NO_WRAP);
        JSONObject body=new JSONObject();
        body.put("public_key",encoded);
        JSONObject response=post("/v1/pair/start",body);
        String code=response.getString("code");
        String claim=response.getString("claim");
        if(!code.matches("[A-Z2-9]{4}-[A-Z2-9]{4}-[A-Z2-9]{4}")||
            claim.length()<30)throw new IllegalStateException("Invalid pairing response");
        prefs().edit().putString("code",code).putString("claim",claim).commit();
        return code;
    }
    public String pendingCode(){return prefs().getString("code","");}
    public String finish() throws Exception {
        String code=pendingCode(),claim=prefs().getString("claim","");
        if(code.isEmpty()||claim.isEmpty())return "Belum ada kode pairing. Tekan Hubungkan terlebih dahulu.";
        JSONObject body=new JSONObject();
        body.put("code",code);body.put("claim",claim);
        JSONObject response=post("/v1/pair/claim",body);
        if("pending".equals(response.optString("status")))
            return "Menunggu persetujuan kode "+code+". Kirim kodenya ke chat lalu coba lagi.";
        if(!"approved".equals(response.optString("status")))throw new IllegalStateException("Status pairing tidak dikenal");
        byte[] encrypted=Base64.decode(response.getString("encrypted_token"),Base64.DEFAULT);
        KeyStore store=keyStore();
        PrivateKey privateKey=(PrivateKey)store.getKey(ALIAS,null);
        if(privateKey==null)throw new IllegalStateException("Android encryption key missing");
        Cipher cipher=Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding");
        cipher.init(Cipher.DECRYPT_MODE,privateKey);
        String token=new String(cipher.doFinal(encrypted),StandardCharsets.UTF_8);
        if(token.length()<30)throw new IllegalStateException("Invalid device token");
        new SecretStore(ctx).save(token);
        prefs().edit().remove("code").remove("claim").commit();
        return "Berhasil! Katarina terhubung ke Cloudflare tanpa Device Key lama.";
    }
}
