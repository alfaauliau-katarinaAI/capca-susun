package ai.katarina.visionlab;
import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Uses Android Keystore with AES-GCM; never stores cleartext Device Key. */
public final class SecretStore {
    private final Context context;
    private static final String ALIAS="katarina_vision_device_key_v1";
    private static final String PREFS="katarina_secure_settings";
    public SecretStore(Context context){this.context=context.getApplicationContext();}
    private SecretKey key() throws Exception {
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)){
            KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            g.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
              .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
              .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
              .setRandomizedEncryptionRequired(true).build());
            g.generateKey();
        }
        return (SecretKey)store.getKey(ALIAS,null);
    }
    public void save(String value) throws Exception {
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());
        String packed=Base64.encodeToString(c.getIV(),Base64.NO_WRAP)+":"+
          Base64.encodeToString(c.doFinal(value.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP);
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString("secret",packed).apply();
    }
    public String load() throws Exception {
        SharedPreferences p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
        String stored=p.getString("secret","");
        if(stored.isEmpty())return "";
        String[] parts=stored.split(":",2);if(parts.length!=2)throw new IllegalStateException("secret");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return new String(c.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),StandardCharsets.UTF_8);
    }
}