package android.os;
import java.util.*;
public class Bundle {
    private final Map<String,Object> data=new HashMap<>();
    public Object get(String key){return data.get(key);}
    public boolean isEmpty(){return data.isEmpty();}
    public boolean containsKey(String key){return data.containsKey(key);}
    public Set<String> keySet(){return data.keySet();}
    public void put(String key,Object value){data.put(key,value);}
    public String getString(String key){return (String)data.get(key);}
    public String getString(String key,String defaultValue){return (String)data.getOrDefault(key,defaultValue);}
}
