package android.database;
public interface Cursor extends AutoCloseable {
    boolean moveToFirst(); int getInt(int column); long getLong(int column);
    int getColumnIndexOrThrow(String column); void close();
}
