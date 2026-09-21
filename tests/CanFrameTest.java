package de.ronny.pololauncher;
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
        check(CanFrameNormalizer.normalize(new byte[]{0x41,5,2})==null,"Truncated normalized frame rejected");
        check(CanFrameNormalizer.normalize(data)[0]==0x24,"Full frame normalized");
        for(int payload=0;payload<256;payload++) parser.feed(frame(0x24,(byte)payload));
        check(seen.size()==259,"Every possible payload byte accepted");
        HctSyncDecoder decoder=new HctSyncDecoder();
        new SerialCanFramer(decoder::feed).feed(frame(0x24,(byte)0x80));
        check(decoder.validPacketCount()==1,"Serial transport uses confirmed HCT decoder");
        System.out.println("CanFrameTest: all cases passed");
    }
}
