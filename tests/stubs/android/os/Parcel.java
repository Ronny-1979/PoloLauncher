package android.os;
public class Parcel {
    private IBinder binder;
    private byte[] bytes;
    public static Parcel obtain(){return new Parcel();}
    public void recycle(){}
    public void writeInterfaceToken(String s){}
    public void writeStrongBinder(IBinder b){binder=b;}
    public IBinder readStrongBinder(){return binder;}
    public void readException(){}
    public void writeNoException(){}
    public void writeString(String s){}
    public String readString(){return "test";}
    public int readInt(){return 1;}
    public int[] createIntArray(){return new int[0];}
    public void enforceInterface(String s){}
    public byte[] createByteArray(){return bytes == null ? new byte[0] : bytes.clone();}
    public void writeByteArray(byte[] b){bytes=b == null ? null : b.clone();}
}
