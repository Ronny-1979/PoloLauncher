package android.database;
public interface Cursor extends AutoCloseable {
    boolean moveToFirst(); boolean moveToNext();
    int getInt(int column); long getLong(int column); double getDouble(int column);
    String getString(int column); byte[] getBlob(int column); boolean isNull(int column);
    int getColumnIndexOrThrow(String column); void close();
}
