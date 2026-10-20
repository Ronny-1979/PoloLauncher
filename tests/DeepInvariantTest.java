package de.ronny.pololauncher;

import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

/** Deterministic fuzz/property checks for small parser/geometry primitives used by live inputs. */
public final class DeepInvariantTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        Random random = new Random(0x504F4C4FL);
        AtomicInteger frames = new AtomicInteger();
        SerialCanFramer framer = new SerialCanFramer(x -> frames.incrementAndGet());

        for (int round = 0; round < 10_000; round++) {
            byte[] raw = new byte[random.nextInt(320)];
            random.nextBytes(raw);
            CanFrameNormalizer.normalize(raw);
            framer.feed(raw);

            double lat = -80d + random.nextDouble() * 160d;
            double lon = -179d + random.nextDouble() * 358d;
            double bLat = Math.max(-85d, Math.min(85d, lat + (random.nextDouble() - .5d) * .02d));
            double bLon = lon + (random.nextDouble() - .5d) * .02d;
            RoadGeometry.Projection projection = RoadGeometry.project(
                    lat, lon, bLat, bLon,
                    lat + (random.nextDouble() - .5d) * .01d,
                    lon + (random.nextDouble() - .5d) * .01d);
            check(Double.isFinite(projection.latitude)
                            && Double.isFinite(projection.longitude)
                            && Double.isFinite(projection.distanceMetres)
                            && projection.distanceMetres >= 0d,
                    "road projection stays finite");
            double axis = RoadGeometry.axisDifference(
                    random.nextDouble() * 720d - 360d,
                    random.nextDouble() * 720d - 360d);
            check(axis >= 0d && axis <= 90d, "road-axis difference remains in 0..90");

            String candidate = random.nextBoolean()
                    ? Integer.toString(random.nextInt(400))
                    : "DE:zone" + random.nextInt(400);
            int parsed = SpeedLimitValue.parse(candidate);
            check(parsed == SpeedLimitValue.UNKNOWN || parsed == SpeedLimitValue.UNRESTRICTED
                    || (parsed >= 5 && parsed <= 250), "speed parser returns only valid states");

            double direction = SpeedCameraDecision.directionDifference(
                    random.nextDouble() * 1000d - 500d,
                    random.nextDouble() * 1000d - 500d);
            double roadAxis = SpeedCameraDecision.roadAxisDifference(
                    random.nextDouble() * 1000d - 500d,
                    random.nextDouble() * 1000d - 500d);
            check(direction >= 0d && direction <= 180d, "direction difference remains in 0..180");
            check(roadAxis >= 0d && roadAxis <= 90d, "camera road axis remains in 0..90");
        }

        // Recovery immediately after a checksum-bad frame is covered by CanFrameTest.
        // Here the invariant is termination/no exception for arbitrary bounded streams.
        check(frames.get() >= 0, "fuzz stream completed");
        System.out.println("DeepInvariantTest: 10,000 deterministic parser/geometry fuzz rounds passed");
    }
}
