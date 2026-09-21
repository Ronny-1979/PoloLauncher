package android.bluetooth;
import java.util.UUID;
import java.io.IOException;
public class BluetoothDevice {
    public BluetoothSocket createRfcommSocketToServiceRecord(UUID uuid)throws IOException{return new BluetoothSocket();}
    public BluetoothSocket createRfcommSocket(int channel)throws IOException{return new BluetoothSocket();}
}
