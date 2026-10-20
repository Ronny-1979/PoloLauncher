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
        android.os.SystemClock.now = 1_000_000L;
        SystemCanReceiver receiver=new SystemCanReceiver();Context context=new Context();
        byte[] full=CanFrameTest.frame(0x24,(byte)0,(byte)2);
        receiver.onReceive(context,new Intent("unconfirmed.transport").putExtra("raw",CanFrameTest.frame(0x24,(byte)0x80,(byte)0)));
        check(!VehicleRepository.snapshot().doorsKnown,"Unconfirmed mapping cannot overwrite vehicle state");
        receiver.onReceive(context,new Intent("com.microntek.sync").putExtra("syncdata",full));
        check(VehicleRepository.snapshot().doorFR,"Confirmed VW right-front door mapping retained");
        receiver.onReceive(context,new Intent("com.microntek.sync").putExtra("syncdata",new byte[]{0x24,2,0,2}));
        receiver.onReceive(context,new Intent("com.microntek.sync").putExtra("syncdata",new byte[]{0x41,0x0D,0x02,0x03,0x4A,0x15,(byte)0x9C,0x05,0x6E,0x00,(byte)0xC3,0x03,(byte)0xFD,0x7E,0x18}));
        VehicleState driving = VehicleRepository.snapshot();
        check(driving.rpm==842,"Confirmed HCT mode 2 RPM reaches dashboard state");
        check(Math.abs(driving.speedKmh-55.32)<0.001,"Confirmed HCT mode 2 speed reaches dashboard state");
        receiver.onReceive(context,new Intent("com.canbus.temperature").putExtra("temperatureC",19.5));
        check(VehicleRepository.snapshot().outsideC==19.5,"Known numeric broadcast retained");
        synchronized (VehicleRepository.class) {
            VehicleState s=VehicleRepository.mutable();s.voltage=12.6;s.voltageUpdatedAtMs=VehicleRepository.now();
            s.rangeConsumption=7.8;s.instConsumption=6.2;s.instConsumptionUpdatedAtMs=VehicleRepository.now();
        }
        receiver.onReceive(context,new Intent("com.microntek.carinfo").putExtra("rpm",7777).putExtra("battery_voltage",13.1).putExtra("fuel_level",99).putExtra("door_fl",true).putExtra("average_consumption",12.0));
        VehicleState mixed=VehicleRepository.snapshot();
        check(mixed.voltage==12.6,"Unknown key names cannot overwrite Bordnetz");
        check(mixed.rangeConsumption==7.8,"Unknown key names cannot overwrite OBD average");
        check(!mixed.doorFL,"Unknown key names cannot overwrite door state");
        VendorObject vendor=new VendorObject();
        var inspect=SystemCanReceiver.class.getDeclaredMethod("inspectVendorObject",String.class,Object.class);inspect.setAccessible(true);
        inspect.invoke(receiver,"vendor",vendor);
        check(!vendor.called,"Inspection must never execute arbitrary vendor methods");
        check(VehicleRepository.snapshot().voltage==12.6,"Vendor scalar fields remain diagnostic-only");
        check(RangeStore.consumption(context,mixed)==7.8,"Combined range still uses OBD average");
        receiver.onReceive(context,new Intent("com.microntek.sync"){@Override public android.os.Bundle getExtras(){throw new IllegalStateException("BadParcelableException stand-in");}});
        receiver.onReceive(null,new Intent("com.microntek.sync"));
        System.out.println("SystemCanReceiverTest: all cases passed");
    }
}
