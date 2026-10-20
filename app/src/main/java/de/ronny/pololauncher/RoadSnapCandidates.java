package de.ronny.pololauncher;

import android.location.Location;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Turns road segments (from the database or from the rendered map) into scored candidates. */
final class RoadSnapCandidates {
    private RoadSnapCandidates() {}

    /** @return null if the segment is too far away or runs against the driving direction. */
    static RoadSnapSelector.Candidate build(double lat1, double lon1, double lat2, double lon2,
                                            RoadGeometry.Projection p, Location source,
                                            boolean directionKnown, double maxDistance,
                                            double maxDirectionError, boolean fromTiles) {
        if (p.distanceMetres > maxDistance) return null;
        double axisError = directionKnown
                ? RoadGeometry.axisDifference(source.getBearing(), p.roadBearing) : 0d;
        if (directionKnown && axisError > maxDirectionError) return null;
        double score = p.distanceMetres + axisError * 0.35d;
        return new RoadSnapSelector.Candidate(lat1, lon1, lat2, lon2, p.latitude, p.longitude,
                p.distanceMetres, score, fromTiles);
    }

    /** Flat segments {lat1, lon1, lat2, lon2, ...}; only those within reach become candidates. */
    static List<RoadSnapSelector.Candidate> fromFlat(double[] segments, Location source,
                                                     double maxDistance, double maxDirectionError,
                                                     boolean fromTiles) {
        ArrayList<RoadSnapSelector.Candidate> found = new ArrayList<>();
        if (segments == null) return found;
        boolean directionKnown = source.hasBearing() && source.hasSpeed()
                && source.getSpeed() >= 2.5f
                && (!source.hasBearingAccuracy() || source.getBearingAccuracyDegrees() <= 40f);
        double lat = source.getLatitude(), lon = source.getLongitude();
        for (int i = 0; i + 3 < segments.length; i += 4) {
            RoadGeometry.Projection p = RoadGeometry.project(segments[i], segments[i + 1],
                    segments[i + 2], segments[i + 3], lat, lon);
            RoadSnapSelector.Candidate c = build(segments[i], segments[i + 1], segments[i + 2],
                    segments[i + 3], p, source, directionKnown, maxDistance, maxDirectionError, fromTiles);
            if (c != null) found.add(c);
        }
        return found;
    }

    static List<RoadSnapSelector.Candidate> best(List<RoadSnapSelector.Candidate> list, int limit) {
        Collections.sort(list, new Comparator<RoadSnapSelector.Candidate>() {
            @Override public int compare(RoadSnapSelector.Candidate a, RoadSnapSelector.Candidate b) {
                return Double.compare(a.score, b.score);
            }
        });
        return list.size() > limit ? new ArrayList<>(list.subList(0, limit)) : list;
    }
}
