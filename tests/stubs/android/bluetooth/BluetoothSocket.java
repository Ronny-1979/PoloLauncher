package android.bluetooth;
import java.io.*;
public class BluetoothSocket {
    public void connect()throws IOException{}
    public void close()throws IOException{}
    public InputStream getInputStream()throws IOException{return InputStream.nullInputStream();}
    public OutputStream getOutputStream()throws IOException{return OutputStream.nullOutputStream();}
}
