package android.app;
import android.database.Cursor;
public class DownloadManager {
    public static final int STATUS_PENDING=1,STATUS_RUNNING=2,STATUS_PAUSED=4,STATUS_SUCCESSFUL=8;
    public static final String COLUMN_STATUS="status",COLUMN_REASON="reason",COLUMN_BYTES_DOWNLOADED_SO_FAR="bytes",COLUMN_TOTAL_SIZE_BYTES="total";
    public volatile Runnable queryHook;
    public Cursor query(Query query){
        if(queryHook!=null)queryHook.run();
        return new Cursor(){
            public boolean moveToFirst(){return true;}
            public int getInt(int c){return c==0?STATUS_SUCCESSFUL:0;}
            public long getLong(int c){return 1_000_000L;}
            public int getColumnIndexOrThrow(String s){return s.equals(COLUMN_STATUS)?0:1;}
            public void close(){}
        };
    }
    public long enqueue(Request r){return 1;}
    public int remove(long... ids){return ids.length;}
    public static class Query {public Query setFilterById(long... ids){return this;}}
    public static class Request {
        public static final int VISIBILITY_VISIBLE_NOTIFY_COMPLETED=1;
        public Request(android.net.Uri uri){}
        public void setTitle(String s){} public void setDescription(String s){}
        public void setAllowedOverMetered(boolean b){} public void setAllowedOverRoaming(boolean b){}
        public void setNotificationVisibility(int v){}
        public void setDestinationInExternalFilesDir(android.content.Context c,String t,String p){}
    }
}
