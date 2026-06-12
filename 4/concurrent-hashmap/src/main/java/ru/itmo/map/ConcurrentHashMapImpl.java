package ru.itmo.map;

import java.util.*;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiFunction;

public class ConcurrentHashMapImpl<K, V> extends AbstractMap<K, V> {

    private static final int DEFAULT_CAPACITY = 16;
    private static final float LOAD_FACTOR = 0.75f;
    private static final int MOVED = -1;

    static class Node<K, V> {
        final int hash;
        final K key;
        volatile V val;
        volatile Node<K, V> next;

        Node(int hash, K key, V val, Node<K, V> next) {
            this.hash = hash;
            this.key = key;
            this.val = val;
            this.next = next;
        }

        Node<K, V> find(int h, Object k) {
            for (Node<K, V> e = this; e != null; e = e.next) {
                if (e.hash == h && k.equals(e.key)) return e;
            }
            return null;
        }
    }

    static final class ForwardingNode<K, V> extends Node<K, V> {
        final AtomicReferenceArray<Node<K, V>> nextTable;

        ForwardingNode(AtomicReferenceArray<Node<K, V>> tab) {
            super(MOVED, null, null, null);
            this.nextTable = tab;
        }

        @Override
        Node<K, V> find(int h, Object k) {
            AtomicReferenceArray<Node<K, V>> tab = nextTable;
            outer:
            for (;;) {
                Node<K, V> e = tab.get((tab.length() - 1) & h);
                for (; e != null; e = e.next) {
                    if (e.hash == h && k.equals(e.key)) return e;
                    if (e instanceof ForwardingNode) {
                        tab = ((ForwardingNode<K, V>) e).nextTable;
                        continue outer;
                    }
                }
                return null;
            }
        }
    }

    private volatile AtomicReferenceArray<Node<K, V>> table;
    private final LongAdder size = new LongAdder();
    private final ReentrantLock structureLock = new ReentrantLock();

    public ConcurrentHashMapImpl() {
        this(DEFAULT_CAPACITY);
    }

    public ConcurrentHashMapImpl(int initialCapacity) {
        int cap = tableSizeFor(Math.max(initialCapacity, 1));
        this.table = new AtomicReferenceArray<>(cap);
    }

    @Override
    public V put(K key, V value) {
        if (key == null || value == null) throw new NullPointerException();
        return putVal(key, value, null);
    }

    @Override
    public V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> merger) {
        if (key == null || value == null) throw new NullPointerException();
        Objects.requireNonNull(merger);
        return putVal(key, value, merger);
    }

    private V putVal(K key, V value, BiFunction<? super V, ? super V, ? extends V> merger) {
        int h = spread(key.hashCode());
        for (;;) {
            AtomicReferenceArray<Node<K, V>> tab = table;
            int n = tab.length();
            int idx = (n - 1) & h;
            Node<K, V> f = tab.get(idx);

            if (f == null) {
                if (tab.compareAndSet(idx, null, new Node<>(h, key, value, null))) {
                    size.increment();
                    maybeResize();
                    return merger != null ? value : null;
                }
                continue;
            }

            if (f.hash == MOVED) {
                awaitResize();
                continue;
            }

            synchronized (f) {
                if (tab.get(idx) != f) continue;
                for (Node<K, V> e = f; ; e = e.next) {
                    if (e.hash == h && key.equals(e.key)) {
                        V old = e.val;
                        e.val = merger != null ? merger.apply(old, value) : value;
                        return merger != null ? e.val : old;
                    }
                    if (e.next == null) {
                        e.next = new Node<>(h, key, value, null);
                        break;
                    }
                }
            }
            size.increment();
            maybeResize();
            return merger != null ? value : null;
        }
    }

    @Override
    public V get(Object key) {
        if (key == null) throw new NullPointerException();
        int h = spread(key.hashCode());
        AtomicReferenceArray<Node<K, V>> tab = table;
        Node<K, V> e = tab.get((tab.length() - 1) & h);
        if (e == null) return null;
        Node<K, V> found = e.find(h, key);
        return found == null ? null : found.val;
    }

    @Override
    public int size() {
        return (int) Math.min(size.sum(), Integer.MAX_VALUE);
    }

    @Override
    public void clear() {
        structureLock.lock();
        try {
            AtomicReferenceArray<Node<K, V>> tab = table;
            for (int i = 0; i < tab.length(); i++) {
                for (;;) {
                    Node<K, V> f = tab.get(i);
                    if (f == null) break;
                    synchronized (f) {
                        if (tab.get(i) != f) continue;
                        long removed = 0;
                        for (Node<K, V> e = f; e != null; e = e.next) removed++;
                        tab.set(i, null);
                        size.add(-removed);
                        break;
                    }
                }
            }
        } finally {
            structureLock.unlock();
        }
    }

    @Override
    public Set<Map.Entry<K, V>> entrySet() {
        return new AbstractSet<>() {
            @Override
            public Iterator<Map.Entry<K, V>> iterator() {
                return snapshot().iterator();
            }

            @Override
            public int size() {
                return ConcurrentHashMapImpl.this.size();
            }
        };
    }

    private List<Map.Entry<K, V>> snapshot() {
        for (;;) {
            AtomicReferenceArray<Node<K, V>> tab = table;
            List<Map.Entry<K, V>> list = new ArrayList<>(size());
            boolean moved = false;
            for (int i = 0; i < tab.length() && !moved; i++) {
                for (Node<K, V> e = tab.get(i); e != null; e = e.next) {
                    if (e.hash == MOVED) { moved = true; break; }
                    list.add(new SimpleImmutableEntry<>(e.key, e.val));
                }
            }
            if (!moved) return list;
            Thread.onSpinWait();
        }
    }

    private void maybeResize() {
        AtomicReferenceArray<Node<K, V>> tab = table;
        if (size.sum() >= (long) (tab.length() * LOAD_FACTOR)) {
            resize(tab);
        }
    }

    private void resize(AtomicReferenceArray<Node<K, V>> expected) {
        if (!structureLock.tryLock()) return;
        try {
            AtomicReferenceArray<Node<K, V>> oldTab = table;
            if (oldTab != expected) return;
            if (size.sum() < (long) (oldTab.length() * LOAD_FACTOR)) return;
            int newCap = oldTab.length() * 2;
            AtomicReferenceArray<Node<K, V>> newTab = new AtomicReferenceArray<>(newCap);
            ForwardingNode<K, V> fwd = new ForwardingNode<>(newTab);

            for (int i = 0; i < oldTab.length(); i++) {
                for (;;) {
                    Node<K, V> f = oldTab.get(i);
                    if (f == null) {
                        if (oldTab.compareAndSet(i, null, fwd)) break;
                    } else {
                        synchronized (f) {
                            if (oldTab.get(i) != f) continue;
                            for (Node<K, V> e = f; e != null; e = e.next) {
                                int idx = (newCap - 1) & e.hash;
                                newTab.set(idx, new Node<>(e.hash, e.key, e.val, newTab.get(idx)));
                            }
                            oldTab.set(i, fwd);
                            break;
                        }
                    }
                }
            }
            table = newTab;
        } finally {
            structureLock.unlock();
        }
    }

    private void awaitResize() {
        structureLock.lock();
        structureLock.unlock();
    }

    private static int spread(int h) {
        return h ^ (h >>> 16);
    }

    private static int tableSizeFor(int cap) {
        int n = 1;
        while (n < cap) n <<= 1;
        return n;
    }
}
