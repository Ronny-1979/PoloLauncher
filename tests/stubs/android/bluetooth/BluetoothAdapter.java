package android.bluetooth;
public class BluetoothAdapter {
    public static BluetoothAdapter adapter;
    public BluetoothDevice device = new BluetoothDevice();
    public static BluetoothAdapter getDefaultAdapter(){return adapter;}
    public boolean isEnabled(){return true;}
    public boolean cancelDiscovery(){return true;}
    public BluetoothDevice getRemoteDevice(String address){return device;}
}
