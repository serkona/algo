package ru.itmo.algo.geo.index.kd;

public final class KDMath {

    static final int DIMS = 3;
    static final double EARTH_R_KM = 6371.0;

    private KDMath() {
    }

    static double[] toXYZ(double lat, double lng) {
        double latR = Math.toRadians(lat);
        double lngR = Math.toRadians(lng);
        return new double[]{
                Math.cos(latR) * Math.cos(lngR),
                Math.cos(latR) * Math.sin(lngR),
                Math.sin(latR)
        };
    }

    static double euclidSq(double[] a, double[] b) {
        double dx = a[0] - b[0], dy = a[1] - b[1], dz = a[2] - b[2];
        return dx * dx + dy * dy + dz * dz;
    }

    static double minDistSqToBox(double[] q, double[] boxMin, double[] boxMax) {
        double distSq = 0;
        for (int i = 0; i < DIMS; i++) {
            double v = q[i];
            if (v < boxMin[i]) {
                double d = boxMin[i] - v;
                distSq += d * d;
            } else if (v > boxMax[i]) {
                double d = v - boxMax[i];
                distSq += d * d;
            }
        }
        return distSq;
    }

    static double radiusKmToMaxChordSq(double radiusKm) {
        double halfAngle = (radiusKm / EARTH_R_KM) / 2.0;
        double maxChord = 2.0 * Math.sin(halfAngle);
        return maxChord * maxChord;
    }
}