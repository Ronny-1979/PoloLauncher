package de.ronny.pololauncher;
public final class ConsumptionLearnerTest {
    static final long START=1_800_000_000_000L;
    static void check(boolean ok,String msg){if(!ok)throw new AssertionError(msg);}
    static void near(double actual,double expected,String msg){check(Math.abs(actual-expected)<1e-8,msg+": "+actual);}
    static void stable(ConsumptionLearner l,long km,double tank,long start){
        for(int i=0;i<=3;i++)l.update(km,tank,start+i*10_000L,6.8);
    }
    public static void main(String[] args){
        ConsumptionLearner learner=new ConsumptionLearner();
        near(learner.value(6.8),6.8,"Initial fallback");
        stable(learner,100_000,35,START);
        check(learner.anchorKm==100_000 && learner.windows==0,"Initial tank confirmed without learning");
        stable(learner,100_100,27,START+3_600_000L);
        near(learner.learned,7.1,"8L per100km blended cautiously with6.8");
        check(learner.windows==1 && learner.learnedDistance==100,"Independent window recorded");
        stable(learner,100_200,27,START+7_200_000L);
        check(learner.windows==1,"Stuck tank cannot create new consumption samples");
        stable(learner,100_220,22,START+8_000_000L);
        check(learner.windows==2,"Next independent window accepted once");
        double learned=learner.learned;
        stable(learner,100_220,23,START+8_050_000L);
        near(learner.learned,learned,"One litre upward slosh not learned");
        stable(learner,100_220,24,START+8_100_000L);
        stable(learner,100_220,25,START+8_150_000L);
        check(learner.anchorTank==25,"Gradual refill recognized against unchanged low baseline");
        near(learner.learned,learned,"Refill preserves previous learned rate");
        ConsumptionLearner shortTrip=new ConsumptionLearner();
        stable(shortTrip,100_000,35,START);
        stable(shortTrip,100_050,30,START+3_600_000L);
        check(shortTrip.windows==0,"Short distance not learned");
        ConsumptionLearner lowLoss=new ConsumptionLearner();stable(lowLoss,100_000,35,START);
        stable(lowLoss,100_100,31,START+3_600_000L);
        check(lowLoss.windows==0,"Less than5L not learned");
        ConsumptionLearner spike=new ConsumptionLearner();stable(spike,100_000,35,START);
        stable(spike,100_000,25,START+40_000L);
        check(spike.windows==0,"Stationary implausible drop not learned");
        ConsumptionLearner badOdo=new ConsumptionLearner();stable(badOdo,100_000,35,START);
        badOdo.update(200_000,30,START+40_000L,6.8);
        check(badOdo.windows==0 && badOdo.anchorKm==-1,"Impossible odometer jump clears window");
        ConsumptionLearner gap=new ConsumptionLearner();
        gap.update(100_000,35,START,6.8);gap.update(100_000,35,START+60_000L,6.8);
        check(gap.anchorKm==-1,"Confirmation gap never counted as continuous30sec");
        stable(gap,100_000,35,START+70_000L);check(gap.anchorKm==100_000,"Stable data recovers after gap");
        gap.update(-1,Double.NaN,START+110_000L,6.8);check(gap.windows==0,"Stale data does not learn");
        ConsumptionLearner noisy=new ConsumptionLearner();
        for(int i=0;i<60;i++)noisy.update(100_000,35+(i%2),START+i*1000L,6.8);
        check(noisy.anchorKm==-1 && noisy.windows==0,"Alternating readings do not initialize a learning window");
        ConsumptionLearner rollback=new ConsumptionLearner();stable(rollback,100_000,35,START);
        rollback.update(99_999,34,START+40_000L,6.8);
        check(rollback.anchorKm==-1,"Backward odometer discards window");
        ConsumptionLearner clockBack=new ConsumptionLearner();stable(clockBack,100_000,35,START);
        clockBack.update(100_000,35,START-1,6.8);check(clockBack.anchorKm==-1,"Backward clock discards window");
        ConsumptionLearner capped=new ConsumptionLearner();stable(capped,100_000,35,START);
        stable(capped,100_100,15,START+3_600_000L);
        near(capped.learned,7.3,"Adjustment capped at0.5L perwindow");
        check(capped.value(6.8)>=3 && capped.value(6.8)<=20,"Learned rate remains bounded");
        ConsumptionLearner impossible=new ConsumptionLearner();stable(impossible,100_000,35,START);
        stable(impossible,100_100,10,START+3_600_000L);
        check(impossible.windows==0,"Implausible rate/drop rejected");
        System.out.println("ConsumptionLearnerTest: all cases passed");
    }
}
