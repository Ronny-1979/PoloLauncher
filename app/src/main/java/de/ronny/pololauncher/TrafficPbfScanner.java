package de.ronny.pololauncher;

import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.Relation;
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.domain.v0_6.Way;
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.pbf2.v0_6.PbfReader;

import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Converts an OSM PBF into the small data set required by the launcher.
 *
 * Pass 1 remembers only node IDs of driveable ways that actually carry speed-limit metadata.
 * Pass 2 stores coordinates for exactly those IDs and emits line segments plus fixed camera nodes.
 * The large PBF can therefore be deleted after conversion.
 */
final class TrafficPbfScanner {
    interface Writer {
        void addSegment(long wayId, double lat1, double lon1, double lat2, double lon2,
                        String highway, String maxspeed, String forward, String backward,
                        String zone, String source, String type);
        void addCamera(long nodeId, double lat, double lon);
    }

    interface Progress {
        boolean cancelled();
        void update(String phase, long count, String detail);
    }

    static final class Stats {
        final long segments;
        final long cameras;
        Stats(long segments, long cameras) {
            this.segments = segments;
            this.cameras = cameras;
        }
    }

    private TrafficPbfScanner() {}

    /**
     * @param includeRoadContext when true, all driveable road segments are retained as well.
     *                           Germany uses this for road snapping / inner-vs-outside context;
     *                           legacy regional traffic imports keep the smaller speed-only DB.
     */
    static Stats scan(File pbf, Writer writer, Progress progress, boolean includeRoadContext) {
        LongCoordinateIndex nodes = new LongCoordinateIndex(1 << 18);
        LongCoordinateIndex enforcementDevices = new LongCoordinateIndex(256);
        Counter first = new Counter();
        progress.update("ANALYSE", 0, "Suche Straßen mit Tempodaten …");
        run(pbf, new Sink() {
            @Override public void initialize(Map<String, Object> metaData) {}
            @Override public void process(EntityContainer container) {
                checkCancelled(progress);
                first.entities++;
                if (container instanceof WayContainer) {
                    Way way = ((WayContainer) container).getEntity();
                    boolean speedWay = isSpeedWay(way);
                    if (speedWay) first.ways++;
                    if (speedWay || (includeRoadContext && isDrivableWay(way)))
                        for (WayNode node : way.getWayNodes()) nodes.add(node.getNodeId());
                } else if (container instanceof RelationContainer) {
                    Relation relation = ((RelationContainer) container).getEntity();
                    if (isSpeedEnforcementRelation(relation)) {
                        for (RelationMember member : relation.getMembers()) {
                            if (member.getMemberType() == EntityType.Node
                                    && "device".equalsIgnoreCase(member.getMemberRole()))
                                enforcementDevices.add(member.getMemberId());
                        }
                    }
                }
                if ((first.entities % 100_000L) == 0L)
                    progress.update("ANALYSE", first.ways,
                            first.ways + " Straßen mit Tempodaten · " + nodes.size() + " benötigte Punkte");
            }
            @Override public void complete() {}
            @Override public void close() {}
        });

        Counter second = new Counter();
        progress.update("AUFBAU", 0,
                "Baue kompakte Verkehrsdatenbank aus " + nodes.size() + " Punkten …");
        run(pbf, new Sink() {
            @Override public void initialize(Map<String, Object> metaData) {}
            @Override public void process(EntityContainer container) {
                checkCancelled(progress);
                second.entities++;
                if (container instanceof NodeContainer) {
                    Node node = ((NodeContainer) container).getEntity();
                    nodes.putCoordinateIfNeeded(node.getId(), node.getLatitude(), node.getLongitude());
                    if (isSpeedCamera(node.getTags())
                            || enforcementDevices.contains(node.getId())) {
                        writer.addCamera(node.getId(), node.getLatitude(), node.getLongitude());
                        second.cameras++;
                    }
                } else if (container instanceof WayContainer) {
                    Way way = ((WayContainer) container).getEntity();
                    boolean speedWay = isSpeedWay(way);
                    if (!speedWay && !(includeRoadContext && isDrivableWay(way))) return;
                    List<WayNode> refs = way.getWayNodes();
                    for (int i = 1; i < refs.size(); i++) {
                        Coordinate a = nodes.coordinate(refs.get(i - 1).getNodeId());
                        Coordinate b = nodes.coordinate(refs.get(i).getNodeId());
                        if (a == null || b == null) continue;
                        if (a.lat == b.lat && a.lon == b.lon) continue;
                        writer.addSegment(way.getId(), a.lat, a.lon, b.lat, b.lon,
                                tag(way.getTags(), "highway"),
                                tag(way.getTags(), "maxspeed"),
                                tag(way.getTags(), "maxspeed:forward"),
                                tag(way.getTags(), "maxspeed:backward"),
                                tag(way.getTags(), "zone:maxspeed"),
                                tag(way.getTags(), "source:maxspeed"),
                                tag(way.getTags(), "maxspeed:type"));
                        second.segments++;
                    }
                }
                if ((second.entities % 100_000L) == 0L)
                    progress.update("AUFBAU", second.segments,
                            second.segments + " Segmente · " + second.cameras + " feste Blitzer");
            }
            @Override public void complete() {}
            @Override public void close() {}
        });

        progress.update("AUFBAU", second.segments,
                second.segments + " Segmente · " + second.cameras + " feste Blitzer");
        return new Stats(second.segments, second.cameras);
    }

    private static void run(File pbf, Sink sink) {
        PbfReader reader = new PbfReader(pbf, 1);
        reader.setSink(sink);
        reader.run();
    }

    private static void checkCancelled(Progress progress) {
        if (progress.cancelled() || Thread.currentThread().isInterrupted())
            throw new Cancelled();
    }

    static boolean isDrivableWay(Way way) {
        return way != null && isDrivable(tag(way.getTags(), "highway"));
    }

    static boolean isSpeedWay(Way way) {
        if (!isDrivableWay(way)) return false;
        Collection<Tag> tags = way.getTags();
        if (SpeedLimitValue.parse(tag(tags, "maxspeed")) != SpeedLimitValue.UNKNOWN) return true;
        if (SpeedLimitValue.parse(tag(tags, "maxspeed:forward")) != SpeedLimitValue.UNKNOWN) return true;
        if (SpeedLimitValue.parse(tag(tags, "maxspeed:backward")) != SpeedLimitValue.UNKNOWN) return true;
        if (SpeedLimitValue.parse(tag(tags, "zone:maxspeed")) != SpeedLimitValue.UNKNOWN) return true;
        return SpeedLimitValue.resolve("", "", "", "",
                tag(tags, "source:maxspeed"), tag(tags, "maxspeed:type"),
                false, true) != SpeedLimitValue.UNKNOWN;
    }

    static boolean isSpeedCamera(Collection<Tag> tags) {
        return "speed_camera".equals(tag(tags, "highway"))
                || "maxspeed".equals(tag(tags, "enforcement"));
    }

    static boolean isSpeedEnforcementRelation(Relation relation) {
        if (relation == null) return false;
        Collection<Tag> tags = relation.getTags();
        return "enforcement".equalsIgnoreCase(tag(tags, "type"))
                && "maxspeed".equalsIgnoreCase(tag(tags, "enforcement"));
    }

    private static String tag(Collection<Tag> tags, String key) {
        if (tags == null) return "";
        for (Tag tag : tags)
            if (tag != null && key.equals(tag.getKey()))
                return tag.getValue() == null ? "" : tag.getValue();
        return "";
    }

    private static boolean isDrivable(String type) {
        return "motorway".equals(type) || "motorway_link".equals(type)
                || "trunk".equals(type) || "trunk_link".equals(type)
                || "primary".equals(type) || "primary_link".equals(type)
                || "secondary".equals(type) || "secondary_link".equals(type)
                || "tertiary".equals(type) || "tertiary_link".equals(type)
                || "residential".equals(type) || "living_street".equals(type)
                || "unclassified".equals(type) || "service".equals(type)
                || "road".equals(type);
    }

    static final class Cancelled extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }

    private static final class Counter {
        long entities;
        long ways;
        long segments;
        long cameras;
    }

    static final class Coordinate {
        final double lat, lon;
        Coordinate(double lat, double lon) { this.lat = lat; this.lon = lon; }
    }

    /**
     * Primitive open-addressing set/map. OSM IDs are positive, so zero can mark an empty slot.
     * Coordinates use the same slots and are filled during the second PBF pass.
     */
    static final class LongCoordinateIndex {
        private long[] keys;
        private double[] lats;
        private double[] lons;
        private byte[] coordinates;
        private int mask;
        private int size;
        private int threshold;

        LongCoordinateIndex(int expectedCapacity) {
            int capacity = 16;
            while (capacity < expectedCapacity) capacity <<= 1;
            allocate(capacity);
        }

        int size() { return size; }

        boolean contains(long key) {
            if (key <= 0L) return false;
            int slot = slot(key);
            return keys[slot] == key;
        }

        void add(long key) {
            if (key <= 0L) return;
            if (size >= threshold) rehash(keys.length << 1);
            int slot = slot(key);
            if (keys[slot] == 0L) {
                keys[slot] = key;
                size++;
            }
        }

        void putCoordinateIfNeeded(long key, double lat, double lon) {
            if (key <= 0L) return;
            int slot = slot(key);
            if (keys[slot] == key) {
                lats[slot] = lat;
                lons[slot] = lon;
                coordinates[slot] = 1;
            }
        }

        Coordinate coordinate(long key) {
            if (key <= 0L) return null;
            int slot = slot(key);
            return keys[slot] == key && coordinates[slot] != 0
                    ? new Coordinate(lats[slot], lons[slot]) : null;
        }

        private int slot(long key) {
            int slot = mix(key) & mask;
            while (keys[slot] != 0L && keys[slot] != key)
                slot = (slot + 1) & mask;
            return slot;
        }

        private void allocate(int capacity) {
            keys = new long[capacity];
            lats = new double[capacity];
            lons = new double[capacity];
            coordinates = new byte[capacity];
            mask = capacity - 1;
            threshold = (int) (capacity * 0.68d);
        }

        private void rehash(int capacity) {
            long[] oldKeys = keys;
            double[] oldLats = lats, oldLons = lons;
            byte[] oldCoordinates = coordinates;
            allocate(capacity);
            int oldSize = size;
            size = 0;
            for (int i = 0; i < oldKeys.length; i++) {
                long key = oldKeys[i];
                if (key == 0L) continue;
                int slot = slot(key);
                keys[slot] = key;
                lats[slot] = oldLats[i];
                lons[slot] = oldLons[i];
                coordinates[slot] = oldCoordinates[i];
                size++;
            }
            if (size != oldSize) throw new IllegalStateException("Node index rehash mismatch");
        }

        private static int mix(long value) {
            value ^= value >>> 33;
            value *= 0xff51afd7ed558ccdL;
            value ^= value >>> 33;
            value *= 0xc4ceb9fe1a85ec53L;
            value ^= value >>> 33;
            return (int) (value ^ (value >>> 32));
        }
    }
}
