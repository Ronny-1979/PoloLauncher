package android.database.sqlite;
import java.io.File;
import android.database.Cursor;
public class SQLiteDatabase {
    public static final int OPEN_READONLY=1;
    public static SQLiteDatabase openDatabase(String p,Object f,int flags){return new SQLiteDatabase();}
    public static SQLiteDatabase openOrCreateDatabase(File p,Object f){return new SQLiteDatabase();}
    public static SQLiteDatabase openOrCreateDatabase(String p,Object f){return new SQLiteDatabase();}
    public void execSQL(String s){}
    public void execSQL(String s,Object[] args){}
    public SQLiteStatement compileStatement(String s){return new SQLiteStatement();}
    public void beginTransaction(){}
    public void setTransactionSuccessful(){}
    public void endTransaction(){}
    public void close(){}
    public Cursor rawQuery(String s,String[] args){return new EmptyCursor();}
    private static final class EmptyCursor implements Cursor {
        public boolean moveToFirst(){return false;}
        public boolean moveToNext(){return false;}
        public int getInt(int c){return 0;}
        public long getLong(int c){return 0L;}
        public double getDouble(int c){return 0d;}
        public String getString(int c){return "";}
        public byte[] getBlob(int c){return null;}
        public boolean isNull(int c){return true;}
        public int getColumnIndexOrThrow(String c){return 0;}
        public void close(){}
    }
}
