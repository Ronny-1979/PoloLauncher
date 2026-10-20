package android.app;
public class Service extends android.content.Context {
    public static final int START_NOT_STICKY=2;
    public static final int STOP_FOREGROUND_REMOVE=1;
    public void onCreate(){}
    public int onStartCommand(android.content.Intent intent,int flags,int startId){return START_NOT_STICKY;}
    public void onDestroy(){}
    public android.os.IBinder onBind(android.content.Intent intent){return null;}
    public void startForeground(int id,Notification notification){}
    public void stopForeground(int flags){}
    public void stopSelf(){}
}
