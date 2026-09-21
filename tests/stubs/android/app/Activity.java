package android.app;
public class Activity extends android.content.Context {
    public android.content.Context app;
    public boolean finishing, destroyed;
    @Override public android.content.Context getApplicationContext(){return app == null ? this : app;}
    public boolean isFinishing(){return finishing;}
    public boolean isDestroyed(){return destroyed;}
}
