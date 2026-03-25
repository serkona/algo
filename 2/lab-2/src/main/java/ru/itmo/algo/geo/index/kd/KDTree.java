package ru.itmo.algo.geo.index.kd;

import ru.itmo.algo.geo.GeoObject;
import ru.itmo.algo.geo.index.GeoIndex;

import java.util.ArrayList;
import java.util.List;


public class KDTree implements GeoIndex {

    private static final class KDNode {
        private final GeoObject obj;
        private final double[] xyz;
        private KDNode left;
        private KDNode right;

        public KDNode(GeoObject obj) {
            this.obj = obj;
            this.xyz = KDMath.toXYZ(obj.lat(), obj.lng());
        }
    }

    private KDNode root;
    private int size;

    @Override
    public void insert(GeoObject obj) {
        root = insert(root, new KDNode(obj), 0);
        size++;
    }

    private KDNode insert(KDNode node, KDNode toInsert, int depth) {
        if (node == null) {
            return toInsert;
        }
        int dim = depth % KDMath.DIMS;
        if (toInsert.xyz[dim] < node.xyz[dim]) {
            node.left = insert(node.left, toInsert, depth + 1);
        } else {
            node.right = insert(node.right, toInsert, depth + 1);
        }
        return node;
    }

    @Override
    public GeoObject findNearest(double lat, double lng) {
        if (root == null) {
            return null;
        }
        double[] q = KDMath.toXYZ(lat, lng);
        double[] bMin = {-1, -1, -1};
        double[] bMax = {1, 1, 1};
        NNState state = new NNState();
        searchNearest(root, q, 0, bMin, bMax, state);
        return state.best;
    }


    private static final class NNState {
        private GeoObject best;
        private double bestDistSq = Double.MAX_VALUE;
    }

    private void searchNearest(KDNode node, double[] q, int depth,
                               double[] bMin, double[] bMax, NNState state) {
        if (node == null || KDMath.minDistSqToBox(q, bMin, bMax) >= state.bestDistSq) {
            return;
        }

        double d = KDMath.euclidSq(q, node.xyz);
        if (d < state.bestDistSq) {
            state.bestDistSq = d;
            state.best = node.obj;
        }

        int dim = depth % KDMath.DIMS;
        double split = node.xyz[dim];

        double savedMin = bMin[dim], savedMax = bMax[dim];
        if (q[dim] < split) {
            bMax[dim] = split;
            searchNearest(node.left, q, depth + 1, bMin, bMax, state);
            bMax[dim] = savedMax;
            bMin[dim] = split;
            searchNearest(node.right, q, depth + 1, bMin, bMax, state);
            bMin[dim] = savedMin;
        } else {
            bMin[dim] = split;
            searchNearest(node.right, q, depth + 1, bMin, bMax, state);
            bMin[dim] = savedMin;
            bMax[dim] = split;
            searchNearest(node.left, q, depth + 1, bMin, bMax, state);
            bMax[dim] = savedMax;
        }
    }

    @Override
    public List<GeoObject> findInRadius(double lat, double lng, double radiusKm) {
        List<GeoObject> result = new ArrayList<>();
        if (root == null) {
            return result;
        }
        double[] q = KDMath.toXYZ(lat, lng);
        double maxDistSq = KDMath.radiusKmToMaxChordSq(radiusKm);
        double[] bMin = {-1, -1, -1};
        double[] bMax = {1, 1, 1};
        searchRadius(root, q, maxDistSq, 0, bMin, bMax, result);
        return result;
    }

    private void searchRadius(KDNode node, double[] q, double maxDistSq, int depth,
                              double[] bMin, double[] bMax, List<GeoObject> result) {
        if (node == null || KDMath.minDistSqToBox(q, bMin, bMax) > maxDistSq) {
            return;
        }

        if (KDMath.euclidSq(q, node.xyz) <= maxDistSq) {
            result.add(node.obj);
        }

        int dim = depth % KDMath.DIMS;
        double split = node.xyz[dim];
        double savedMin = bMin[dim], savedMax = bMax[dim];

        if (q[dim] < split) {
            bMax[dim] = split;
            searchRadius(node.left, q, maxDistSq, depth + 1, bMin, bMax, result);
            bMax[dim] = savedMax;
            bMin[dim] = split;
            searchRadius(node.right, q, maxDistSq, depth + 1, bMin, bMax, result);
            bMin[dim] = savedMin;
        } else {
            bMin[dim] = split;
            searchRadius(node.right, q, maxDistSq, depth + 1, bMin, bMax, result);
            bMin[dim] = savedMin;
            bMax[dim] = split;
            searchRadius(node.left, q, maxDistSq, depth + 1, bMin, bMax, result);
            bMax[dim] = savedMax;
        }
    }

    @Override
    public int size() {
        return size;
    }
}