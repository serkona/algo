package ru.itmo.search.rank;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TopKCollector {

    private final int k;
    private final double[] scores;
    private final int[] docs;
    private int size = 0;

    public TopKCollector(int k) {
        this.k = Math.max(1, k);
        this.scores = new double[this.k];
        this.docs = new int[this.k];
    }

    public double threshold() {
        return size < k ? Double.NEGATIVE_INFINITY : scores[0];
    }

    public boolean isFull() {
        return size == k;
    }

    public void collect(int doc, double score) {
        if (size < k) {
            scores[size] = score;
            docs[size] = doc;
            size++;
            siftUp(size - 1);
        } else if (score > scores[0]) {
            scores[0] = score;
            docs[0] = doc;
            siftDown(0);
        }
    }

    private void siftUp(int i) {
        while (i > 0) {
            int parent = (i - 1) >>> 1;
            if (scores[parent] <= scores[i]) {
                break;
            }
            swap(i, parent);
            i = parent;
        }
    }

    private void siftDown(int i) {
        while (true) {
            int l = 2 * i + 1;
            int r = 2 * i + 2;
            int smallest = i;
            if (l < size && scores[l] < scores[smallest]) {
                smallest = l;
            }
            if (r < size && scores[r] < scores[smallest]) {
                smallest = r;
            }
            if (smallest == i) {
                break;
            }
            swap(i, smallest);
            i = smallest;
        }
    }

    private void swap(int a, int b) {
        double s = scores[a];
        scores[a] = scores[b];
        scores[b] = s;
        int d = docs[a];
        docs[a] = docs[b];
        docs[b] = d;
    }

    public List<ScoreDoc> toSortedList() {
        List<ScoreDoc> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(new ScoreDoc(docs[i], scores[i]));
        }
        Collections.sort(out);
        return out;
    }
}
