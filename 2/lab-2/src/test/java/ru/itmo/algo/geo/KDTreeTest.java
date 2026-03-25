package ru.itmo.algo.geo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.itmo.algo.geo.index.BruteForceIndex;
import ru.itmo.algo.geo.index.kd.KDTree;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class KDTreeTest {

    private KDTree tree;
    private BruteForceIndex brute;

    private static final GeoObject MOSCOW = new GeoObject(55.7558, 37.6173, "Moscow");
    private static final GeoObject SPB = new GeoObject(59.9343, 30.3351, "Saint Petersburg");
    private static final GeoObject PARIS = new GeoObject(48.8566, 2.3522, "Paris");
    private static final GeoObject LONDON = new GeoObject(51.5074, -0.1278, "London");
    private static final GeoObject NEW_YORK = new GeoObject(40.7128, -74.006, "New York");
    private static final GeoObject TOKYO = new GeoObject(35.6762, 139.6503, "Tokyo");
    private static final GeoObject KREMLIN = new GeoObject(55.7522, 37.6156, "Kremlin");

    @BeforeEach
    void setUp() {
        tree = new KDTree();
        brute = new BruteForceIndex();
        for (GeoObject o : List.of(MOSCOW, SPB, PARIS, LONDON, NEW_YORK, TOKYO, KREMLIN)) {
            tree.insert(o);
            brute.insert(o);
        }
    }

    @Test
    void sizeIsCorrect() {
        assertEquals(7, tree.size());
    }

    @Test
    void emptyTreeReturnsNull() {
        KDTree empty = new KDTree();
        assertNull(empty.findNearest(0, 0));
        assertEquals(0, empty.findInRadius(0, 0, 100).size());
    }

    @Test
    void nearestToMoscowCoord_isMoscowOrKremlin() {
        GeoObject result = tree.findNearest(55.7558, 37.6173);
        assertTrue(result == MOSCOW || result == KREMLIN,
                "Expected Moscow or Kremlin, got: " + result);
    }

    @Test
    void nearestMatchesBruteForce_randomPoints() {
        Random rng = new Random(42);
        for (int i = 0; i < 200; i++) {
            double lat = -80 + rng.nextDouble() * 160;
            double lng = -170 + rng.nextDouble() * 340;
            GeoObject kd = tree.findNearest(lat, lng);
            GeoObject bf = brute.findNearest(lat, lng);
            double dKD = GeoUtils.haversineKm(lat, lng, kd.lat(), kd.lng());
            double dBF = GeoUtils.haversineKm(lat, lng, bf.lat(), bf.lng());
            assertEquals(dBF, dKD, 0.001,
                    "Distance mismatch at (" + lat + ", " + lng + "): KD=" + kd + " BF=" + bf);
        }
    }

    @Test
    void radiusSearchFindsOnlyMoscowAndKremlinWithin50km() {
        List<GeoObject> result = tree.findInRadius(55.76, 37.62, 50.0);
        Set<GeoObject> set = new HashSet<>(result);
        assertTrue(set.contains(MOSCOW), "Should contain Moscow");
        assertTrue(set.contains(KREMLIN), "Should contain Kremlin");
        assertFalse(set.contains(SPB), "Should NOT contain SPb");
        assertEquals(2, result.size());
    }

    @Test
    void radiusSearchMatchesBruteForce_randomPoints() {
        Random rng = new Random(99);
        double[] radii = {10, 100, 500, 2000, 5000};
        for (double r : radii) {
            for (int i = 0; i < 50; i++) {
                double lat = -80 + rng.nextDouble() * 160;
                double lng = -170 + rng.nextDouble() * 340;
                Set<String> kdSet = namesOf(tree.findInRadius(lat, lng, r));
                Set<String> bfSet = namesOf(brute.findInRadius(lat, lng, r));
                assertEquals(bfSet, kdSet,
                        "Mismatch at (" + lat + ", " + lng + ") r=" + r);
            }
        }
    }

    @Test
    void radiusSearchReturnsEmptyWhenNothingInRange() {
        assertTrue(tree.findInRadius(0, -150, 1000).isEmpty());
    }

    @Test
    void largeDataset_nearestMatchesBruteForce() {
        KDTree bigTree = new KDTree();
        BruteForceIndex bigBrute = new BruteForceIndex();
        Random rng = new Random(7);
        for (int i = 0; i < 5_000; i++) {
            double lat = -90 + rng.nextDouble() * 180;
            double lng = -180 + rng.nextDouble() * 360;
            GeoObject o = new GeoObject(lat, lng, "p" + i);
            bigTree.insert(o);
            bigBrute.insert(o);
        }
        for (int i = 0; i < 200; i++) {
            double lat = -80 + rng.nextDouble() * 160;
            double lng = -170 + rng.nextDouble() * 340;
            GeoObject kd = bigTree.findNearest(lat, lng);
            GeoObject bf = bigBrute.findNearest(lat, lng);
            double dKD = GeoUtils.haversineKm(lat, lng, kd.lat(), kd.lng());
            double dBF = GeoUtils.haversineKm(lat, lng, bf.lat(), bf.lng());
            assertEquals(dBF, dKD, 0.001,
                    "Large dataset mismatch at (" + lat + ", " + lng + ")");
        }
    }

    private static Set<String> namesOf(List<GeoObject> list) {
        Set<String> s = new HashSet<>();
        for (GeoObject o : list) {
            s.add(o.name());
        }
        return s;
    }
}