package algo;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class FileSystemHashTable implements Closeable {

    private final Path baseDir;
    private final int pageSize;

    private static final int BHDR = 8; // bucket header: localDepth(4) + usedBytes(4)
    private static final byte ACTIVE = 0;
    private static final byte TOMBSTONE = 1;
    private static final int MAX_DEPTH = 24;

    private int globalDepth;
    private int[] dir;
    private int nextId;

    private final Map<Integer, RandomAccessFile> rafs = new HashMap<>();
    private final Map<Integer, FileChannel> chs = new HashMap<>();
    private final Map<Integer, MappedByteBuffer> maps = new HashMap<>();
    private final Map<Integer, Integer> caps = new HashMap<>();

    public FileSystemHashTable(Path baseDir, int pageSize) throws IOException {
        this.baseDir = baseDir;
        this.pageSize = Math.max(pageSize, 512);
        Files.createDirectories(baseDir);

        if (Files.exists(metaPath())) {
            loadMeta();
            Set<Integer> ids = new LinkedHashSet<>();
            for (int id : dir) ids.add(id);
            for (int id : ids) openBucket(id);
        } else {
            globalDepth = 0;
            dir = new int[]{0};
            nextId = 1;
            createBucket(0, 0);
            saveMeta();
        }
    }

    private Path metaPath() { return baseDir.resolve("meta.dat"); }
    private Path bucketPath(int id) { return baseDir.resolve("bucket_" + id + ".dat"); }

    private int hash(String key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return h;
    }

    private int dirIndex(String key) {
        if (globalDepth == 0) return 0;
        return hash(key) & ((1 << globalDepth) - 1);
    }

    private int bucketFor(String key) {
        return dir[dirIndex(key)];
    }

    private int localDepth(int b) { return maps.get(b).getInt(0); }
    private void setLocalDepth(int b, int d) { maps.get(b).putInt(0, d); }
    private int used(int b) { return maps.get(b).getInt(4); }
    private void setUsed(int b, int u) { maps.get(b).putInt(4, u); }

    private void createBucket(int id, int ld) throws IOException {
        RandomAccessFile raf = new RandomAccessFile(bucketPath(id).toFile(), "rw");
        raf.setLength(pageSize);
        FileChannel ch = raf.getChannel();
        MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_WRITE, 0, pageSize);
        m.putInt(0, ld);
        m.putInt(4, 0);
        rafs.put(id, raf);
        chs.put(id, ch);
        maps.put(id, m);
        caps.put(id, pageSize);
    }

    private void openBucket(int id) throws IOException {
        if (maps.containsKey(id)) return;
        RandomAccessFile raf = new RandomAccessFile(bucketPath(id).toFile(), "rw");
        int cap = (int) raf.length();
        FileChannel ch = raf.getChannel();
        MappedByteBuffer m = ch.map(FileChannel.MapMode.READ_WRITE, 0, cap);
        rafs.put(id, raf);
        chs.put(id, ch);
        maps.put(id, m);
        caps.put(id, cap);
    }

    private void grow(int b, int need) throws IOException {
        int total = BHDR + need;
        int cap = caps.get(b);
        if (total <= cap) return;
        while (cap < total) cap *= 2;
        maps.get(b).force();
        rafs.get(b).setLength(cap);
        maps.put(b, chs.get(b).map(FileChannel.MapMode.READ_WRITE, 0, cap));
        caps.put(b, cap);
    }

    private void closeBucket(int id) throws IOException {
        MappedByteBuffer m = maps.remove(id);
        if (m != null) m.force();
        FileChannel ch = chs.remove(id);
        if (ch != null) ch.close();
        RandomAccessFile raf = rafs.remove(id);
        if (raf != null) raf.close();
        caps.remove(id);
    }

    private void saveMeta() throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(8 + dir.length * 4);
        buf.putInt(globalDepth);
        buf.putInt(nextId);
        for (int id : dir) buf.putInt(id);
        buf.flip();
        Files.write(metaPath(), buf.array());
    }

    private void loadMeta() throws IOException {
        ByteBuffer buf = ByteBuffer.wrap(Files.readAllBytes(metaPath()));
        globalDepth = buf.getInt();
        nextId = buf.getInt();
        dir = new int[1 << globalDepth];
        for (int i = 0; i < dir.length; i++) dir[i] = buf.getInt();
    }


    public String get(String key) throws IOException {
        int b = bucketFor(key);
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        MappedByteBuffer m = maps.get(b);
        int end = BHDR + used(b);

        int pos = BHDR;
        while (pos < end) {
            int kLen = m.getInt(pos); pos += 4;
            boolean hit = kLen == kb.length && match(m, pos, kb);
            pos += kLen;
            int vLen = m.getInt(pos); pos += 4;
            byte flag = m.get(pos + vLen);
            if (hit && flag == ACTIVE) {
                byte[] vb = new byte[vLen];
                m.get(pos, vb, 0, vLen);
                return new String(vb, StandardCharsets.UTF_8);
            }
            pos += vLen + 1;
        }
        return null;
    }

    public void put(String key, String value) throws IOException {
        int b = bucketFor(key);
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        byte[] vb = value.getBytes(StandardCharsets.UTF_8);
        MappedByteBuffer m = maps.get(b);
        int u = used(b);
        int end = BHDR + u;

        int pos = BHDR;
        while (pos < end) {
            int kLen = m.getInt(pos); pos += 4;
            boolean hit = kLen == kb.length && match(m, pos, kb);
            pos += kLen;
            int vLen = m.getInt(pos); pos += 4;
            int flagPos = pos + vLen;
            byte flag = m.get(flagPos);
            if (hit && flag == ACTIVE) {
                if (vLen == vb.length) {
                    m.put(pos, vb, 0, vb.length);
                    return;
                }
                m.put(flagPos, TOMBSTONE);
                break;
            }
            pos = flagPos + 1;
        }

        int recSize = 4 + kb.length + 4 + vb.length + 1;
        grow(b, u + recSize);
        m = maps.get(b);
        appendRaw(m, BHDR + u, kb, vb);
        setUsed(b, u + recSize);

        if (BHDR + u + recSize > pageSize && localDepth(b) < MAX_DEPTH) {
            split(b);
        }
    }

    public boolean delete(String key) {
        int b = bucketFor(key);
        byte[] kb = key.getBytes(StandardCharsets.UTF_8);
        MappedByteBuffer m = maps.get(b);
        int end = BHDR + used(b);

        int pos = BHDR;
        while (pos < end) {
            int kLen = m.getInt(pos); pos += 4;
            boolean hit = kLen == kb.length && match(m, pos, kb);
            pos += kLen;
            int vLen = m.getInt(pos); pos += 4;
            int flagPos = pos + vLen;
            if (hit && m.get(flagPos) == ACTIVE) {
                m.put(flagPos, TOMBSTONE);
                return true;
            }
            pos = flagPos + 1;
        }
        return false;
    }

    private void split(int bid) throws IOException {
        int ld = localDepth(bid);

        if (ld == globalDepth) {
            int[] newDir = new int[dir.length * 2];
            System.arraycopy(dir, 0, newDir, 0, dir.length);
            System.arraycopy(dir, 0, newDir, dir.length, dir.length);
            dir = newDir;
            globalDepth++;
        }

        int newLd = ld + 1;
        int newBid = nextId++;
        createBucket(newBid, newLd);
        setLocalDepth(bid, newLd);

        int bit = 1 << ld;
        // Перевешиваем половину ссылок на новый бакет
        for (int i = 0; i < dir.length; i++) {
            if (dir[i] == bid && (i & bit) != 0) {
                dir[i] = newBid;
            }
        }

        redistribute(bid, newBid, bit);
        saveMeta();
    }

    private void redistribute(int oldB, int newB, int bit) throws IOException {
        MappedByteBuffer om = maps.get(oldB);
        int u = used(oldB);
        int end = BHDR + u;

        List<byte[]> keys = new ArrayList<>();
        List<byte[]> vals = new ArrayList<>();
        List<Boolean> toNew = new ArrayList<>();

        int pos = BHDR;
        while (pos < end) {
            int kLen = om.getInt(pos); pos += 4;
            byte[] kb = new byte[kLen];
            om.get(pos, kb, 0, kLen); pos += kLen;
            int vLen = om.getInt(pos); pos += 4;
            byte[] vb = new byte[vLen];
            om.get(pos, vb, 0, vLen);
            byte flag = om.get(pos + vLen);
            pos += vLen + 1;

            if (flag == ACTIVE) {
                int h = hash(new String(kb, StandardCharsets.UTF_8));
                keys.add(kb);
                vals.add(vb);
                toNew.add((h & bit) != 0);
            }
        }

        setUsed(oldB, 0);
        setUsed(newB, 0);

        for (int i = 0; i < keys.size(); i++) {
            int target = toNew.get(i) ? newB : oldB;
            int tu = used(target);
            int recSize = 4 + keys.get(i).length + 4 + vals.get(i).length + 1;
            grow(target, tu + recSize);
            appendRaw(maps.get(target), BHDR + tu, keys.get(i), vals.get(i));
            setUsed(target, tu + recSize);
        }
    }

    private void appendRaw(MappedByteBuffer m, int wp, byte[] kb, byte[] vb) {
        m.putInt(wp, kb.length); wp += 4;
        m.put(wp, kb, 0, kb.length); wp += kb.length;
        m.putInt(wp, vb.length); wp += 4;
        m.put(wp, vb, 0, vb.length); wp += vb.length;
        m.put(wp, ACTIVE);
    }

    private boolean match(MappedByteBuffer m, int off, byte[] target) {
        for (int i = 0; i < target.length; i++) {
            if (m.get(off + i) != target[i]) return false;
        }
        return true;
    }

    @Override
    public void close() throws IOException {
        for (int id : new ArrayList<>(maps.keySet())) closeBucket(id);
        saveMeta();
    }

    public void destroy() throws IOException {
        for (int id : new ArrayList<>(maps.keySet())) closeBucket(id);
        for (int i = 0; i < nextId; i++) Files.deleteIfExists(bucketPath(i));
        Files.deleteIfExists(metaPath());
        Files.deleteIfExists(baseDir);
    }

    public int getGlobalDepth() { return globalDepth; }
    public int getNumBuckets() {
        Set<Integer> s = new HashSet<>();
        for (int id : dir) s.add(id);
        return s.size();
    }
}
