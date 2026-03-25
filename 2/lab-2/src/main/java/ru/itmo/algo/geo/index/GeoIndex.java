package ru.itmo.algo.geo.index;

import ru.itmo.algo.geo.GeoObject;

import java.util.List;

public interface GeoIndex {

    void insert(GeoObject obj);

    GeoObject findNearest(double lat, double lng);

    List<GeoObject> findInRadius(double lat, double lng, double radiusKm);

    int size();
}