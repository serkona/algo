package ru.itmo.search.compress;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Codecs {

    private Codecs() {
    }

    private static final Map<String, IntCodec> REGISTRY = new LinkedHashMap<>();

    static {
        register(new RawIntCodec());
        register(new VarByteCodec());
        register(new BitPackingCodec());
        register(new PForDeltaCodec());
        register(new DeltaCodec(new VarByteCodec()));
        register(new DeltaCodec(new BitPackingCodec()));
        register(new DeltaCodec(new PForDeltaCodec()));
    }

    private static void register(IntCodec codec) {
        REGISTRY.put(codec.name(), codec);
    }

    public static IntCodec byName(String name) {
        IntCodec c = REGISTRY.get(name);
        if (c == null) {
            throw new IllegalArgumentException("Unknown codec: " + name + ", known: " + REGISTRY.keySet());
        }
        return c;
    }

    public static List<String> names() {
        return List.copyOf(REGISTRY.keySet());
    }
}
