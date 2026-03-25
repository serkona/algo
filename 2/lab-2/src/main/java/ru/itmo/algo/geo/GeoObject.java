package ru.itmo.algo.geo;

public record GeoObject(double lat, double lng, String name) {

    public GeoObject {
        if (lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be in [-90, 90]");
        }
        if (lng < -180 || lng > 180) {
            throw new IllegalArgumentException("lng must be in [-180, 180]");
        }
    }

    @Override
    public String toString() {
        return name + "(" + lat + ", " + lng + ")";
    }
}