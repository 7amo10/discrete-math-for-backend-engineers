package com.backend.discrete.week02_hashing;

import java.nio.charset.StandardCharsets;

/**
 * 32-bit MurmurHash3 implementation mapping arbitrary keys to unsigned integers
 * on the [0, 2^32 - 1] ring.
 */
public final class HashFunction {

    public static final long RING_SIZE = 1L << 32;
    public static final long RING_MASK = 0xFFFFFFFFL;

    private static final int C1 = 0xcc9e2d51;
    private static final int C2 = 0x1b873593;
    private static final int R1 = 15;
    private static final int R2 = 13;
    private static final int M = 5;
    private static final int N = 0xe6546b64;

    private final int seed;

    public HashFunction() {
        this(0);
    }

    public HashFunction(int seed) {
        this.seed = seed;
    }

    /**
     * Hashes a string and returns an unsigned 32-bit long in the range [0, 2^32 - 1].
     */
    public long hash(String input) {
        if (input == null) {
            return 0L;
        }
        byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
        return hash(bytes, 0, bytes.length);
    }

    /**
     * Hashes byte array using 32-bit MurmurHash3.
     */
    public long hash(byte[] data, int offset, int length) {
        int h = seed;
        int nblocks = length / 4;

        for (int i = 0; i < nblocks; i++) {
            int index = offset + (i * 4);
            int k = (data[index] & 0xFF)
                    | ((data[index + 1] & 0xFF) << 8)
                    | ((data[index + 2] & 0xFF) << 16)
                    | ((data[index + 3] & 0xFF) << 24);

            k *= C1;
            k = Integer.rotateLeft(k, R1);
            k *= C2;

            h ^= k;
            h = Integer.rotateLeft(h, R2);
            h = h * M + N;
        }

        int index = offset + (nblocks * 4);
        int k1 = 0;
        int tail = length & 3;

        switch (tail) {
            case 3 -> {
                k1 ^= (data[index + 2] & 0xFF) << 16;
                k1 ^= (data[index + 1] & 0xFF) << 8;
                k1 ^= (data[index] & 0xFF);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, R1);
                k1 *= C2;
                h ^= k1;
            }
            case 2 -> {
                k1 ^= (data[index + 1] & 0xFF) << 8;
                k1 ^= (data[index] & 0xFF);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, R1);
                k1 *= C2;
                h ^= k1;
            }
            case 1 -> {
                k1 ^= (data[index] & 0xFF);
                k1 *= C1;
                k1 = Integer.rotateLeft(k1, R1);
                k1 *= C2;
                h ^= k1;
            }
            default -> {}
        }

        h ^= length;
        h ^= (h >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);

        return Integer.toUnsignedLong(h);
    }
}
