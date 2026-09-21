package android.content;
import java.util.*;
public class Context {
    public static final int MODE_PRIVATE=0,BIND_AUTO_CREATE=1,RECEIVER_EXPORTED=2;
    public int binds, unbinds;
    public static final String DOWNLOAD_SERVICE="download";
    public java.io.File externalFilesDir;
    public final Map<String,Object> services=new HashMap<>();
    public java.io.File getExternalFilesDir(String type){return externalFilesDir;}
    public Object getSystemService(String name){return services.get(name);}
    private final Map<String,Prefs> preferences=new HashMap<>();
    public Context getApplicationContext(){return this;}
    public boolean bindService(Intent intent,ServiceConnection connection,int flags){binds++;return true;}
    public void unbindService(ServiceConnection connection){unbinds++;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter){return null;}
    public Intent registerReceiver(BroadcastReceiver receiver,IntentFilter filter,int flags){return null;}
    public void unregisterReceiver(BroadcastReceiver receiver){}
    public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,n->new Prefs());}
    static final class Prefs implements SharedPreferences {
        final Map<String,Object> values=new HashMap<>();
        public String getString(String k,String d){return (String)values.getOrDefault(k,d);}
        public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}
        public int getInt(String k,int d){return (Integer)values.getOrDefault(k,d);}
        public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
        public Editor edit(){return new Editor(){
            public Editor putString(String k,String v){values.put(k,v);return this;}
            public Editor putLong(String k,long v){values.put(k,v);return this;}
            public Editor putInt(String k,int v){values.put(k,v);return this;}
            public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
            public Editor remove(String k){values.remove(k);return this;}
            public void apply(){}
        };}
    }
}
