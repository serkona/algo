package ru.itmo.algo.geo;

import ru.itmo.algo.geo.index.kd.KDTree;

import java.util.List;

public class Main {

    public static void main(String[] args) {
        KDTree tree = getKdTree();

        System.out.println("=== Nearest neighbour ===");
        System.out.printf("Nearest to (55.76, 37.62): %s%n",
                tree.findNearest(55.76, 37.62));
        System.out.printf("Nearest to (51.51, -0.13): %s%n",
                tree.findNearest(51.51, -0.13));

        System.out.println("\n=== Radius search ===");
        List<GeoObject> r50  = tree.findInRadius(55.76, 37.62, 50.0);
        List<GeoObject> r800 = tree.findInRadius(55.76, 37.62, 800.0);
        System.out.printf("Within  50 km of Moscow: %s%n", r50);
        System.out.printf("Within 800 km of Moscow: %s%n", r800);

        System.out.println("\n=== Distances ===");
        System.out.printf("Moscow -> Saint Petersburg: %.1f km%n",
                GeoUtils.haversineKm(55.7558, 37.6173, 59.9343, 30.3351));
        System.out.printf("Moscow -> Paris: %.1f km%n",
                GeoUtils.haversineKm(55.7558, 37.6173, 48.8566, 2.3522));
    }

    private static KDTree getKdTree() {
        KDTree tree = new KDTree();

        tree.insert(new GeoObject(55.7558,  37.6173, "Moscow"));
        tree.insert(new GeoObject(59.9343,  30.3351, "Saint Petersburg"));
        tree.insert(new GeoObject(48.8566,   2.3522, "Paris"));
        tree.insert(new GeoObject(51.5074,  -0.1278, "London"));
        tree.insert(new GeoObject(40.7128,  -74.006, "New York"));
        tree.insert(new GeoObject(35.6762, 139.6503, "Tokyo"));
        tree.insert(new GeoObject(-33.8688, 151.2093,"Sydney"));
        tree.insert(new GeoObject(55.7522,  37.6156, "Kremlin"));
        tree.insert(new GeoObject(59.9502,  30.3163, "Hermitage"));
        return tree;
    }
}