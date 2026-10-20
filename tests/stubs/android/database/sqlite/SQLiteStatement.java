package android.database.sqlite;
public class SQLiteStatement implements AutoCloseable {
    public void clearBindings(){}
    public void bindLong(int i,long v){}
    public void bindDouble(int i,double v){}
    public void bindString(int i,String v){}
    public void bindNull(int i){}
    public long executeInsert(){return 1L;}
    public void close(){}
}
