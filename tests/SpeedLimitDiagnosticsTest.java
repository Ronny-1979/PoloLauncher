package de.ronny.pololauncher;
public final class SpeedLimitDiagnosticsTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    public static void main(String[] args){
        SpeedLimitDiagnostics.capability(true, "Deutschland-Datenbank");
        SpeedLimitDiagnostics.lookup(7.4,22.0,"residential","maxspeed=50",50,true);
        String ok=SpeedLimitDiagnostics.summary();
        check(ok.contains("Daten verfügbar JA"),"support state");
        check(ok.contains("nächste Straße=7 m"),"road distance");
        check(ok.contains("Suchradius=22 m"),"search radius");
        check(ok.contains("OSM maxspeed=50"),"raw OSM speed");
        check(ok.contains("erkannt=50 km/h"),"resolved speed");
        SpeedLimitDiagnostics.lookup(54.0,32.0,"","",SpeedLimitValue.UNKNOWN,false);
        check(SpeedLimitDiagnostics.summary().contains("keine passende Straße im Suchradius"),"missing-road reason");
        SpeedLimitDiagnostics.capability(false, "Deutschland-Datenbank");
        check(SpeedLimitDiagnostics.summary().contains("Daten verfügbar NEIN"),"unsupported-map reason");
        System.out.println("SpeedLimitDiagnosticsTest: live diagnostic summary passed");
    }
}
