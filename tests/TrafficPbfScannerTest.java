package de.ronny.pololauncher;

import java.io.File;
import java.util.*;
import org.openstreetmap.osmosis.core.container.v0_6.*;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;

public final class TrafficPbfScannerTest {
    static void check(boolean value,String message){if(!value)throw new AssertionError(message);}

    public static void main(String[] args) {
        Node n1 = new Node(1,51.0,13.0,List.of());
        Node n2 = new Node(2,51.001,13.001,List.of());
        Node camera = new Node(3,51.0005,13.0005,
                List.of(new Tag("highway","speed_camera")));
        Node relationCamera = new Node(4,51.0007,13.0007,List.of());
        Relation enforcement = new Relation(20,
                List.of(new RelationMember(4, EntityType.Node, "device")),
                List.of(new Tag("type","enforcement"), new Tag("enforcement","maxspeed")));
        Way speed = new Way(10,List.of(new WayNode(1),new WayNode(2)),
                List.of(new Tag("highway","residential"),new Tag("maxspeed","50")));
        Way irrelevant = new Way(11,List.of(new WayNode(1),new WayNode(2)),
                List.of(new Tag("highway","residential")));
        Way unparseable = new Way(12,List.of(new WayNode(1),new WayNode(2)),
                List.of(new Tag("highway","residential"),new Tag("source:maxspeed","sign")));
        Way contextual = new Way(13,List.of(new WayNode(1),new WayNode(2)),
                List.of(new Tag("highway","residential"),new Tag("maxspeed:type","DE:urban")));
        Way directional = new Way(14,List.of(new WayNode(1),new WayNode(2)),
                List.of(new Tag("highway","secondary"),new Tag("maxspeed:forward","70")));
        check(!TrafficPbfScanner.isSpeedWay(unparseable),
                "source:maxspeed metadata without a resolvable limit is ignored");
        check(TrafficPbfScanner.isSpeedWay(contextual),
                "resolvable maxspeed:type context is retained");
        check(TrafficPbfScanner.isSpeedWay(directional),
                "directional numeric maxspeed is retained");

        check(TrafficPbfScanner.isSpeedEnforcementRelation(enforcement),
                "maxspeed enforcement relation recognized");
        PbfReader.entities = List.of(
                new NodeContainer(n1), new NodeContainer(n2), new NodeContainer(camera),
                new NodeContainer(relationCamera), new WayContainer(speed), new WayContainer(irrelevant),
                new RelationContainer(enforcement));

        final long[] segments={0}, cameras={0};
        TrafficPbfScanner.Stats stats = TrafficPbfScanner.scan(new File("fake.pbf"),
                new TrafficPbfScanner.Writer() {
                    @Override public void addSegment(long wayId,double lat1,double lon1,double lat2,double lon2,
                            String highway,String maxspeed,String forward,String backward,
                            String zone,String source,String type) {
                        check(wayId==10,"only speed-tagged way emitted");
                        check("50".equals(maxspeed),"maxspeed preserved");
                        segments[0]++;
                    }
                    @Override public void addCamera(long nodeId,double lat,double lon) {
                        check(nodeId==3 || nodeId==4,"camera node emitted");
                        cameras[0]++;
                    }
                }, new TrafficPbfScanner.Progress() {
                    @Override public boolean cancelled(){return false;}
                    @Override public void update(String phase,long count,String detail){}
                }, false);

        check(stats.segments==1 && segments[0]==1,"one segment emitted");
        check(stats.cameras==2 && cameras[0]==2,
                "direct speed camera and enforcement device emitted");

        final long[] fullSegments = {0};
        TrafficPbfScanner.Stats full = TrafficPbfScanner.scan(new File("fake.pbf"),
                new TrafficPbfScanner.Writer() {
                    @Override public void addSegment(long wayId,double lat1,double lon1,double lat2,double lon2,
                            String highway,String maxspeed,String forward,String backward,
                            String zone,String source,String type) { fullSegments[0]++; }
                    @Override public void addCamera(long nodeId,double lat,double lon) {}
                }, new TrafficPbfScanner.Progress() {
                    @Override public boolean cancelled(){return false;}
                    @Override public void update(String phase,long count,String detail){}
                }, true);
        check(full.segments==2 && fullSegments[0]==2,
                "Germany full-road profile retains driveable roads without speed tags");

        TrafficPbfScanner.LongCoordinateIndex index =
                new TrafficPbfScanner.LongCoordinateIndex(16);
        for(long i=1;i<=10000;i++)index.add(i);
        for(long i=1;i<=10000;i++)index.putCoordinateIfNeeded(i,i/1000d,i/2000d);
        check(index.size()==10000,"primitive node index rehash preserves all IDs");
        check(index.coordinate(9999)!=null,"coordinates survive rehash");

        System.out.println("TrafficPbfScannerTest: two-pass speed/camera/enforcement extraction passed");
    }
}
