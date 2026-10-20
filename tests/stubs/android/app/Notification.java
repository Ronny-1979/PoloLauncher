package android.app;
public class Notification {
  public static class Builder {
    public Builder(android.content.Context c,String channel){}
    public Builder setSmallIcon(int i){return this;}
    public Builder setContentTitle(CharSequence s){return this;}
    public Builder setContentText(CharSequence s){return this;}
    public Builder setOnlyAlertOnce(boolean b){return this;}
    public Builder setOngoing(boolean b){return this;}
    public Builder setContentIntent(PendingIntent p){return this;}
    public Builder setProgress(int max,int progress,boolean indeterminate){return this;}
    public Notification build(){return new Notification();}
  }
}
