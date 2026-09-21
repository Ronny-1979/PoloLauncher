package android.content;
public class Intent {
    private final String action;
    private final android.os.Bundle extras=new android.os.Bundle();
    public Intent(String action){this.action=action;}
    public String getAction(){return action;}
    public android.os.Bundle getExtras(){return extras;}
    public Intent putExtra(String key,Object value){extras.put(key,value);return this;}
    public Intent setClassName(String pkg,String name){return this;}
}
