package de.ronny.pololauncher;
import android.content.Context;
public final class RangeModeTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    static VehicleState state(){VehicleState s=new VehicleState();s.odometerKm=100_000;s.fuelLiters=30;return s;}
    public static void main(String[] args){
        android.os.SystemClock.now = 1_000_000L; // monotonic base used for freshness stamps
        Context context=new Context();
        VehicleState s=state();s.avgConsumption=10;
        check(RangeStore.includeObd2InRange(context),"Imported default CAN+OBD preserved");
        check(RangeStore.consumption(context,s)==10,"OBD average preferred in combined mode");
        RangeStore store=new RangeStore(context);
        int combined=store.update(s,context);
        RangeStore.setIncludeObd2InRange(context,false);
        check(RangeStore.consumption(context,s)==6.8,"CAN-only ignores valid OBD rate");
        int canOnly=store.update(s,context);
        check(canOnly>combined,"Mode selection affects actual range calculation");
        store.save();store=new RangeStore(context);
        check(!RangeStore.includeObd2InRange(context),"Mode survives reload");
        RangeStore.setIncludeObd2InRange(context,true);
        Obd2Client.preferences(context).edit()
                .putLong("avg_weighted_liters_bits", Double.doubleToLongBits(0.1))
                .putLong("avg_weighted_km_bits", Double.doubleToLongBits(1d)).apply();
        for(double invalid:new double[]{Double.NaN,0,2,21,Double.POSITIVE_INFINITY}){
            s.avgConsumption=invalid;
            check(RangeStore.consumption(context,s)==10,"Bad OBD value restores cumulative OBD, never CAN");
            check(store.update(s,context)==combined,"Actual estimator uses saved OBD too");
        }
        check(RangeStore.consumption(context)==10,"Saved average available before any new sample");
        store.save();store=new RangeStore(context);
        check(store.update(s,context)==combined,"Combined range preserved after reload");
        RangeTrackingSession session=new RangeTrackingSession(context,()->1000L,()->{},()->{});
        session.start();session.update(s);session.stop();
        int cached=RangeDisplayStore.remainingKm(context);
        check(cached==combined && RangeDisplayStore.tankLitres(context)==30,"Combined display saved");
        session=new RangeTrackingSession(context,()->1000L,()->{},()->{});
        session.start();session.update(new VehicleState());
        check(RangeDisplayStore.remainingKm(context)==cached,"No CAN after restart preserves combined display");
        session.update(s);
        check(session.remainingKm()==combined,"New CAN resumes with saved OBD average");
        session.stop();
        Obd2Runtime.resetAverageConsumption(context);
        check(Double.isNaN(RangeStore.consumption(context,s)),"Reset removes range baseline too");
        Obd2Client.preferences(context).edit()
                .putLong("avg_weighted_liters_bits", Double.doubleToLongBits(0.1))
                .putLong("avg_weighted_km_bits", Double.doubleToLongBits(1d)).apply();
        Context empty=new Context();
        check(Double.isNaN(RangeStore.consumption(empty,state())),"No saved OBD: no CAN-only substitution");
        check(new RangeStore(empty).update(state(),empty)==-1,"No OBD baseline pauses estimator");
        synchronized(VehicleRepository.class){
            VehicleState raw=VehicleRepository.mutable();raw.avgConsumption=9;raw.instConsumption=12;
            raw.instConsumptionUpdatedAtMs=VehicleRepository.now()-16_000;
        }
        VehicleState expired=VehicleRepository.snapshot();
        check(Double.isNaN(expired.avgConsumption)&&Double.isNaN(expired.instConsumption),"Expired OBD readings stripped before range calculation");
        check(RangeStore.consumption(context,expired)==10,"Expired sample still uses saved cumulative OBD baseline");
        synchronized(VehicleRepository.class){VehicleRepository.mutable().instConsumptionUpdatedAtMs=VehicleRepository.now();}
        check(VehicleRepository.snapshot().avgConsumption==9,"Fresh OBD average survives snapshot copy");
        Context mixed=new Context();long[] now={1_800_000_000_000L};
        RangeStore mixedStore=new RangeStore(mixed,()->now[0]);
        VehicleState mixedState=state();mixedState.avgConsumption=9;
        for(int i=0;i<4;i++){mixedStore.update(mixedState,mixed);now[0]+=10_000;}
        now[0]+=3_600_000;mixedState.odometerKm+=100;mixedState.fuelLiters=22;
        for(int i=0;i<4;i++){mixedStore.update(mixedState,mixed);now[0]+=10_000;}
        check(RangeStore.learnedWindows(mixed)==1,"CAN learning continues while OBD determines range");
        check(RangeStore.consumption(mixed,mixedState)==9,"Combined mode still uses OBD after CAN learns");
        RangeStore.setIncludeObd2InRange(mixed,false);
        check(Math.abs(RangeStore.consumption(mixed,mixedState)-7.1)<1e-9,"CAN-only uses independently learned fallback");
        System.out.println("RangeModeTest: all cases passed");
    }
}
