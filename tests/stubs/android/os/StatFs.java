package android.os;
public final class StatFs {
    private final java.io.File dir;
    public StatFs(String path){dir=new java.io.File(path);}
    public long getAvailableBytes(){return dir.getUsableSpace();}
}
