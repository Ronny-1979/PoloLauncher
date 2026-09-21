package de.ronny.pololauncher;
import android.content.Context;
public final class RangeLearningStoreTest {
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void near(double actual,double expected,String msg){check(Math.abs(actual-expected)<1e-8,msg+": "+actual);}
    static VehicleState state(long km,double tank){VehicleState s=new VehicleState();s.odometerKm=km;s.fuelLiters=tank;return s;}
    static void stable(RangeStore store,Context context,long[] clock,long km,double tank,long start){
        for(int i=0;i<=3;i++){clock[0]=start+i*10_000L;store.update(state(km,tank),context);}
    }
    public static void main(String[] args){
        Context context=new Context();long start=1_800_000_000_000L;long[] clock={start};
        RangeStore.setIncludeObd2InRange(context,false); // Explicit CAN learning mode.
        RangeStore store=new RangeStore(context,()->clock[0]);
        check(RangeStore.automaticLearning(context),"New installation automatic learning enabled");
        near(RangeStore.consumption(context),6.8,"Initial6.8fallback");
        stable(store,context,clock,100_000,35,start);
        store.save();
        store=new RangeStore(context,()->clock[0]); // Trip anchor persists, confirmation not assumed.
        stable(store,context,clock,100_100,27,start+3_600_000L);
        near(RangeStore.consumption(context),7.1,"Learner feeds persisted consumption");
        check(RangeStore.learnedWindows(context)==1,"Window survived restart");
        int learnedRange=store.update(state(100_100,27),context);
        RangeStore.setAutomaticLearning(context,false);
        int manualRange=store.update(state(100_100,27),context);
        near(RangeStore.consumption(context),6.8,"Disabled automatics uses manual value");
        check(manualRange>learnedRange,"Effective consumption changes range computation");
        RangeStore.setAutomaticLearning(context,true);
        store.update(state(100_100,27),context);
        near(RangeStore.consumption(context),7.1,"Reenable retains learned rate");
        RangeStore.resetLearning(context);
        store.save(); // Existing object must not overwrite the reset with old values.
        near(RangeStore.consumption(context),6.8,"Reset survives stale-object save");
        check(RangeStore.learnedWindows(context)==0,"Reset clears sample counter");
        context.getSharedPreferences("cockpit_range",0).edit().putInt("consumption_tenths",75).apply();
        near(RangeStore.consumption(context),7.5,"Manual startup value remains configurable");
        RangeStore restored=new RangeStore(context,()->clock[0]);
        check(restored.update(state(-1,Double.NaN),context)==-1,"Stale CAN still hides estimated range");
        restored.save();near(RangeStore.consumption(context),7.5,"Invalid data never updates learned consumption");
        RangeStore.setAutomaticLearning(context,false);
        stable(restored,context,clock,100_200,25,start+7_200_000L);
        stable(restored,context,clock,100_300,17,start+10_800_000L);
        check(RangeStore.learnedWindows(context)==0,"Disabled mode never learns from trips");
        RangeStore disabledRestored=new RangeStore(context,()->clock[0]);
        check(!RangeStore.automaticLearning(context),"Disabled state persists across restart");
        disabledRestored.save();near(RangeStore.consumption(context),7.5,"Disabled restarted store retains manual rate");
        System.out.println("RangeLearningStoreTest: all cases passed");
    }
}
