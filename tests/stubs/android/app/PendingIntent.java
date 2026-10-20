package android.app;
public class PendingIntent {
 public static final int FLAG_UPDATE_CURRENT=1, FLAG_IMMUTABLE=2;
 public static PendingIntent getActivity(android.content.Context c,int r,
      android.content.Intent i,int f){return new PendingIntent();}
}
