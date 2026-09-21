package de.ronny.pololauncher;
import android.content.Context;
import android.os.SystemClock;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Runs the production PID polling code through simulated ELM streams, not a real adapter. */
public final class Obd2ClientTest {
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
    static void set(Object target,String name,Object value)throws Exception{
        Field f=Obd2Client.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
    }
    static void call(Object target,String name,Class<?>[] types,Object...args)throws Exception{
        Method m=Obd2Client.class.getDeclaredMethod(name,types);m.setAccessible(true);m.invoke(target,args);
    }
    static final class Elm extends InputStream {
        byte[] reply=new byte[0];int at;
        boolean missing;
        final List<String> commands=new ArrayList<>();
        final OutputStream output=new OutputStream(){
            final StringBuilder command=new StringBuilder();
            public void write(int b){
                if(b=='\r'){
                    String c=command.toString();command.setLength(0);commands.add(c);
                    String answer=missing?"NO DATA>":switch(c){
                        case "010C"->"41 0C 2E E0>";
                        case "010D"->"41 0D 64>";
                        case "010B"->"41 0B 32>";
                        case "010F"->"41 0F 41>";
                        default->"?>";
                    };
                    reply=answer.getBytes(StandardCharsets.US_ASCII);at=0;
                }else command.append((char)b);
            }
        };
        public int available(){return reply.length-at;}
        public int read(){return at<reply.length?reply[at++]&255:-1;}
    }
    public static void main(String[] args)throws Exception{
        Context context=new Context();Obd2Client client=new Obd2Client();Elm elm=new Elm();
        check(Double.isNaN(client.displayAverage(context)), "No saved average displays no fabricated value");
        set(client,"running",true);set(client,"thread",Thread.currentThread());
        set(client,"in",elm);set(client,"out",elm.output);set(client,"averageStoreContext",context);
        synchronized(VehicleRepository.class){VehicleRepository.mutable().fuelLiters=27;}
        for(int i=0;i<100;i++){
            SystemClock.now=10_000+i*1_000;
            call(client,"pollOnce",new Class<?>[]{Context.class},context);
        }
        VehicleState live=VehicleRepository.snapshot();
        check(Double.isFinite(live.instConsumption)&&Double.isFinite(live.avgConsumption),"Real polling publishes instantaneous and average figures");
        check(Double.isFinite(live.obdFuelLitersPerHour) && Double.isFinite(live.obdSpeedKmh) && live.obdRpm == 3000,
                "Polling publishes coherent hourly display inputs");
        check(VehicleRepository.mutable().fuelLiters==27,"CAN tank remains untouched");
        check(elm.commands.size()==400,"Four configured vehicle PIDs polled each round");
        set(client,"connected",true);
        String debug=client.diagnostics(context);
        check(debug.contains("Abgeschlossene Abfragerunden: 100") && debug.contains("RPM=3000"),"Debug shows actual poll count and PID values");
        check(debug.contains("Brauchbare Verbrauchsdaten: JA"),"Debug distinguishes usable data from connection alone");
        check(debug.contains("OBD gesammelte Strecke:") && debug.contains("OBD gesammelter Kraftstoff:"),
                "Diagnostic exposes average denominator and numerator");
        call(client,"saveAverage",new Class<?>[]{});
        Obd2Client restored=new Obd2Client();
        call(restored,"loadAverage",new Class<?>[]{Context.class},context);
        Field field=Obd2Client.class.getDeclaredField("averager");field.setAccessible(true);
        Obd2ConsumptionAverager average=(Obd2ConsumptionAverager)field.get(restored);
        check(Math.abs(average.value()-live.avgConsumption)<1e-9,"Production persistence restores average");
        check(Math.abs(new Obd2Client().displayAverage(context)-live.avgConsumption)<1e-9,
                "Cold display restores persisted average without connection or polling");
        elm.missing=true;SystemClock.now+=1_000;
        call(client,"pollOnce",new Class<?>[]{Context.class},context);
        VehicleState missing=VehicleRepository.snapshot();
        check(Double.isNaN(missing.avgConsumption)&&Double.isNaN(missing.instConsumption),"NO DATA not advertised as fresh usable OBD average");
        check(Math.abs(client.displayAverage(context)-live.avgConsumption)<1e-9,
                "Running display keeps accumulator while adapter returns NO DATA");
        check(Double.isNaN(VehicleRepository.snapshot().avgConsumption),
                "Display lookup never publishes cached value as fresh range data");
        check(client.diagnostics(context).contains("Brauchbare Verbrauchsdaten: NEIN"),"NO DATA immediately visible in debug");
        client.resetAverageConsumption(context);
        check(Double.isNaN(VehicleRepository.mutable().avgConsumption),"Reset immediately clears published average");
        check(Double.isNaN(client.displayAverage(context)) && Double.isNaN(new Obd2Client().displayAverage(context)),
                "Reset clears running and persisted display values");
        call(restored,"loadAverage",new Class<?>[]{Context.class},context);
        check(Double.isNaN(average.value()),"Reset persists across reloading");
        final boolean[] closed={false};
        android.bluetooth.BluetoothSocket first=new android.bluetooth.BluetoothSocket(){
            public void connect()throws IOException{throw new IOException("Clone SDP failure");}
            public void close(){closed[0]=true;}
        };
        Elm link=new Elm();
        android.bluetooth.BluetoothSocket fallback=new android.bluetooth.BluetoothSocket(){
            public InputStream getInputStream(){return link;}
            public OutputStream getOutputStream(){return link.output;}
        };
        android.bluetooth.BluetoothAdapter.adapter=new android.bluetooth.BluetoothAdapter();
        android.bluetooth.BluetoothAdapter.adapter.device=new TestDevice(first,fallback);
        Obd2Client.setDeviceAddress(context,"00:11:22:33:44:55");
        set(client,"lastSampleAtMs",123L);
        Method connect=Obd2Client.class.getDeclaredMethod("tryConnect",Context.class);connect.setAccessible(true);
        check((boolean)connect.invoke(client,context),"Fallback connects clone");
        check(closed[0],"Failed socket closed rather than leaked");
        Field sample=Obd2Client.class.getDeclaredField("lastSampleAtMs");sample.setAccessible(true);
        check(sample.getLong(client)==0,"Reconnect resets integration time");
        android.bluetooth.BluetoothAdapter.adapter=null;
        System.out.println("Obd2ClientTest: all cases passed (simulated adapter)");
    }
    public static final class TestDevice extends android.bluetooth.BluetoothDevice {
        final android.bluetooth.BluetoothSocket first,fallback;
        TestDevice(android.bluetooth.BluetoothSocket a,android.bluetooth.BluetoothSocket b){first=a;fallback=b;}
        public android.bluetooth.BluetoothSocket createRfcommSocketToServiceRecord(java.util.UUID uuid){return first;}
        public android.bluetooth.BluetoothSocket createRfcommSocket(int channel){return fallback;}
    }
}
