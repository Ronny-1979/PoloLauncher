package de.ronny.pololauncher;
import android.os.SystemClock;
import java.util.*;
public final class CanFrameTest {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static byte[] frame(int function, byte... payload) {
        byte[] frame = new byte[payload.length + 4];
        frame[0]=0x2e; frame[1]=(byte)function; frame[2]=(byte)payload.length;
        System.arraycopy(payload,0,frame,3,payload.length);
        int sum=0; for(int i=1;i<frame.length-1;i++)sum+=frame[i]&0xff;
        frame[frame.length-1]=(byte)((sum&0xff)^0xff); return frame;
    }
    public static void main(String[] args) {
        List<byte[]> seen = new ArrayList<>();
        SerialCanFramer parser = new SerialCanFramer(seen::add);
        byte[] data = frame(0x24,(byte)0x2e,(byte)0x80);
        parser.feed(Arrays.copyOf(data,3));
        check(parser.pending() && seen.isEmpty(),"Partial frame buffered");
        parser.feed(Arrays.copyOfRange(data,3,data.length));
        check(seen.size()==1 && (seen.get(0)[2]&0xff)==0x2e,"0x2E in payload preserved");
        parser.feed(frame(0x24,(byte)0xac)); // checksum is exactly 0x2E
        check(seen.size()==2,"0x2E checksum preserved");
        byte[] broken=frame(0x24,(byte)0);broken[broken.length-1]^=1;
        parser.feed(broken);check(seen.size()==2,"Bad checksum rejected");
        parser.feed(frame(0x24,(byte)0));check(seen.size()==3,"Recovery after corruption");
        byte[] hostile = new byte[4_000];
        java.util.Arrays.fill(hostile, (byte)0x2e);
        parser.feed(hostile);
        parser.feed(frame(0x24,(byte)1));
        check(seen.size()==4,"Iterative resync survives marker-heavy corrupt stream");
        check(CanFrameNormalizer.normalize(new byte[]{0x41,5,2})==null,"Truncated normalized frame rejected");
        check(CanFrameNormalizer.normalize(data)[0]==0x24,"Full frame normalized");
        for(int payload=0;payload<256;payload++) parser.feed(frame(0x24,(byte)payload));
        check(seen.size()==260,"Every possible payload byte accepted");
        HctSyncDecoder decoder=new HctSyncDecoder();
        synchronized (VehicleRepository.class) { VehicleRepository.mutable().doorFR = false; }
        new SerialCanFramer(decoder::feed).feed(frame(0x24,(byte)0,(byte)2));
        check(VehicleRepository.snapshot().doorFR,"Serial transport uses confirmed HCT decoder");

        // Regression: the CAN box can leave the last positive RPM frozen after engine shutdown.
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.rpm = 1095;
            s.rpmUpdatedAtMs = VehicleRepository.now();
            s.engineStateKnown = false;
            s.engineStoppedByCan = false;
        }
        decoder.feed(new byte[]{0x41,0x02,0x03,(byte)0xC0}); // oil + charging warnings = engine stopped
        check(VehicleRepository.snapshot().rpm==0,
                "Mode3 engine-off state immediately forces frozen RPM to 0");

        // Even if a stale Mode2 packet repeats the old 1095 rpm afterwards, dashboard must stay at 0.
        decoder.feed(new byte[]{0x41,0x0D,0x02,0x04,0x47,0x00,0x00,0x04,(byte)0xEC,
                0x00,(byte)0xA0,0x01,(byte)0x86,(byte)0xA0,0x20});
        check(VehicleRepository.snapshot().rpm==0,
                "Frozen Mode2 RPM cannot override confirmed engine-off state");

        // Both warnings off = engine running again; fresh RPM is allowed through.
        decoder.feed(new byte[]{0x41,0x02,0x03,0x00});
        decoder.feed(new byte[]{0x41,0x0D,0x02,0x03,0x52,0x00,0x00,0x04,(byte)0xEC,
                0x00,(byte)0xA0,0x01,(byte)0x86,(byte)0xA0,0x20}); // 850 rpm
        check(VehicleRepository.snapshot().rpm==850,
                "Running state releases live RPM again");

        // A final engine-off frame must expire; otherwise one old Mode3 packet could pin RPM
        // to zero forever after the CAN source disappears.
        decoder.feed(new byte[]{0x41,0x02,0x03,(byte)0xC0});
        SystemClock.now += 6_000L;
        synchronized (VehicleRepository.class) {
            VehicleState s = VehicleRepository.mutable();
            s.rpmUpdatedAtMs = 0L;
            s.speedUpdatedAtMs = 0L;
        }
        check(VehicleRepository.snapshot().rpm==-1,
                "Stale engine-off state expires to unknown instead of permanent 0 rpm");

        System.out.println("CanFrameTest: all cases passed");
    }
}
