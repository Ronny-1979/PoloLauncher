package de.ronny.pololauncher;
import android.content.*;
public final class SystemCanReceiverTest {
    public static final class VendorObject {
        boolean called;
        public double voltage = 12.8;
        public void reset() { called = true; }
        public int getAndClear() { called = true; return 1; }
    }
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args)throws Exception {
        android.os.SystemClock.now = 1_000_000L; // monotonic base used for freshness stamps
        SystemCanReceiver receiver=new SystemCanReceiver();Context context=new Context();
        byte[] full=CanFrameTest.frame(0x24,(byte)0,(byte)2);
        receiver.onReceive(context,new Intent("unconfirmed.transport").putExtra("raw",CanFrameTest.frame(0x24,(byte)0x80,(byte)0)));
        check(receiver.syncPacketCount()==0,"Unknown transport stays diagnostic only");
        check(!VehicleRepository.snapshot().doorsKnown,"Unconfirmed Toyota mapping cannot overwrite vehicle state");
        receiver.onReceive(context,new Intent("com.microntek.sync").putExtra("syncdata",full));
        check(receiver.syncPacketCount()==1,"Trusted full frame decoded by HCT");
        check(VehicleRepository.snapshot().doorFR,"Confirmed VW right-front door mapping retained");
        receiver.onReceive(context,new Intent("com.microntek.sync").putExtra("syncdata",new byte[]{0x24,2,0,2}));
        check(receiver.syncPacketCount()==2,"Existing normalized HCT path retained");
        receiver.onReceive(context,new Intent("com.canbus.temperature").putExtra("temperatureC",19.5));
        check(VehicleRepository.snapshot().outsideC==19.5,"Known numeric broadcast retained");
        var writer=SystemCanReceiver.class.getDeclaredMethod("applyNumeric",String.class,double.class);writer.setAccessible(true);
        Thread thread=new Thread(()->{try{for(int i=0;i<10_000;i++)writer.invoke(receiver,"test.voltage",12.5);}catch(Exception e){throw new RuntimeException(e);}});
        thread.start();while(thread.isAlive()) {VehicleState s=VehicleRepository.snapshot();check(Double.isNaN(s.voltage)||s.voltage==12.5,"No inconsistent voltage snapshot");}
        thread.join();check(VehicleRepository.snapshot().voltage==12.5,"Concurrent broadcast update completed");
        VendorObject vendor = new VendorObject();
        var inspect = SystemCanReceiver.class.getDeclaredMethod("inspectVendorObject", String.class, Object.class);
        inspect.setAccessible(true);
        synchronized (VehicleRepository.class) { VehicleRepository.mutable().voltage = Double.NaN; VehicleRepository.mutable().voltageUpdatedAtMs = 0L; }
        inspect.invoke(receiver, "vendor", vendor);
        check(!vendor.called, "Inspection must never execute arbitrary vendor methods, including non-void getters");
        check(Double.isNaN(VehicleRepository.snapshot().voltage), "Key-name guessing is OFF by default: vendor field must not reach the state");
        receiver.heuristicSniffer = true;
        inspect.invoke(receiver, "vendor", vendor);
        check(VehicleRepository.snapshot().voltage == 12.8, "Sniffer mode: passive numeric fields remain readable");
        receiver.heuristicSniffer = false;
        // Any other app can send these broadcasts: without sniffer mode they change nothing.
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.avgConsumption = 7.8; s.instConsumption = 6.2; s.voltage = 12.6; s.voltageUpdatedAtMs = VehicleRepository.now();
            s.instConsumptionUpdatedAtMs = VehicleRepository.now();
        }
        receiver.onReceive(context,new Intent("com.microntek.carinfo")
                .putExtra("average_consumption",12.0).putExtra("current_consumption",20.0)
                .putExtra("battery_voltage",7.0).putExtra("fan_speed",3));
        VehicleState mixed = VehicleRepository.snapshot();
        check(mixed.avgConsumption == 7.8 && mixed.instConsumption == 6.2, "Foreign broadcast must not overwrite fresh OBD values");
        check(mixed.voltage == 12.6, "Foreign broadcast must not overwrite the confirmed Bordnetz value");
        // Sniffer mode enabled by the user preference is honoured on the next broadcast.
        context.getSharedPreferences(SystemCanReceiver.PREFS_DEBUG, 0).edit().putBoolean(SystemCanReceiver.KEY_SNIFFER, true).apply();
        receiver.onReceive(context,new Intent("com.microntek.carinfo").putExtra("battery_voltage",13.1));
        check(VehicleRepository.snapshot().voltage == 13.1, "Sniffer preference enables key-name decoding");
        context.getSharedPreferences(SystemCanReceiver.PREFS_DEBUG, 0).edit().putBoolean(SystemCanReceiver.KEY_SNIFFER, false).apply();
        check(RangeStore.consumption(context, mixed) == 7.8, "Combined range must still use OBD average");
        synchronized (VehicleRepository.class) { VehicleRepository.mutable().instConsumptionUpdatedAtMs = 0L; }
        check(Double.isNaN(RangeStore.consumption(new Context(), VehicleRepository.snapshot())),
                "Fresh CAN cannot substitute for missing OBD baseline");
        // A broadcast whose extras cannot even be read must not crash the process.
        receiver.onReceive(context, new Intent("com.microntek.sync") {
            @Override public android.os.Bundle getExtras() { throw new IllegalStateException("BadParcelableException stand-in"); }
        });
        receiver.onReceive(null, new Intent("com.microntek.sync"));
        System.out.println("SystemCanReceiverTest: all cases passed");
    }
}
