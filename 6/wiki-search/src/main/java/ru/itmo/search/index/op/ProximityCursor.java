package ru.itmo.search.index.op;

import java.util.Arrays;
import ru.itmo.search.index.PositionalCursor;

public final class ProximityCursor implements PositionalCursor {

    private final PositionalCursor left;
    private final PositionalCursor right;
    private final int slop;
    private final boolean ordered;

    private int current = -1;
    private int[] matchPos = new int[8];
    private int matchCount = 0;

    public ProximityCursor(PositionalCursor left, PositionalCursor right, int slop, boolean ordered) {
        this.left = left;
        this.right = right;
        this.slop = slop;
        this.ordered = ordered;
    }

    public static ProximityCursor adj(PositionalCursor left, PositionalCursor right, int slop) {
        return new ProximityCursor(left, right, slop, true);
    }

    public static ProximityCursor near(PositionalCursor left, PositionalCursor right, int slop) {
        return new ProximityCursor(left, right, slop, false);
    }

    @Override
    public int docId() {
        return current;
    }

    @Override
    public int nextDoc() {
        return findMatch(current + 1);
    }

    @Override
    public int advance(int target) {
        return findMatch(target);
    }

    private int findMatch(int target) {
        int d = left.advance(target);
        while (d != NO_MORE) {
            int d2 = right.advance(d);
            if (d2 != d) {
                if (d2 == NO_MORE) {
                    break;
                }
                d = left.advance(d2);
                continue;
            }
            if (matchPositions()) {
                return current = d;
            }
            d = left.advance(d + 1);
        }
        return current = NO_MORE;
    }

    private boolean matchPositions() {
        int[] lp = left.positions();
        int ln = left.freq();
        int[] rp = right.positions();
        int rn = right.freq();
        matchCount = 0;
        int i = 0;
        for (int j = 0; j < rn; j++) {
            int r = rp[j];
            if (ordered) {
                int low = r - slop;
                while (i < ln && lp[i] < low) {
                    i++;
                }
                if (i < ln && lp[i] <= r - 1) {
                    addMatch(r);
                }
            } else {
                int low = r - slop;
                int high = r + slop;
                while (i < ln && lp[i] < low) {
                    i++;
                }
                int t = i;
                while (t < ln && lp[t] <= high) {
                    if (lp[t] != r) {
                        addMatch(r);
                        break;
                    }
                    t++;
                }
            }
        }
        return matchCount > 0;
    }

    private void addMatch(int pos) {
        if (matchCount == matchPos.length) {
            matchPos = Arrays.copyOf(matchPos, matchPos.length << 1);
        }
        matchPos[matchCount++] = pos;
    }

    @Override
    public int freq() {
        return matchCount;
    }

    @Override
    public int[] positions() {
        return matchPos;
    }

    @Override
    public long cost() {
        return Math.min(left.cost(), right.cost());
    }
}
