package ru.itmo.algo.geo.index;

import ru.itmo.algo.geo.GeoObject;
import ru.itmo.algo.geo.GeoUtils;

import java.util.ArrayList;
import java.util.List;

public class BruteForceIndex implements GeoIndex {

    private final List<GeoObject> objects = new ArrayList<>();

    @Override
    public void insert(GeoObject obj) {
        objects.add(obj);
    }

    @Override
    public GeoObject findNearest(double lat, double lng) {
        GeoObject best = null;
        double bestDist = Double.MAX_VALUE;
        for (GeoObject o : objects) {
            double d = GeoUtils.haversineKm(lat, lng, o.lat(), o.lng());
            if (d < bestDist) {
                bestDist = d;
                best = o;
            }
        }
        return best;
    }

    @Override
    public List<GeoObject> findInRadius(double lat, double lng, double radiusKm) {
        List<GeoObject> result = new ArrayList<>();
        for (GeoObject o : objects) {
            if (GeoUtils.haversineKm(lat, lng, o.lat(), o.lng()) <= radiusKm) {
                result.add(o);
            }
        }
        return result;
    }

    @Override
    public int size() {
        return objects.size();
    }
}