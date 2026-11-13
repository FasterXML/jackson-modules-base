package tools.jackson.module.blackbird.internal;

import java.util.Objects;

/**
 * Bit masks over property or record component indexes. Each mask is one
 * {@code long}, so a codec that uses masks supports at most
 * {@link #CAPACITY} indexes, and the factories keep larger beans off those
 * codecs with {@link #fits}. {@link #bit} and {@link #isSet} throw
 * IndexOutOfBoundsException for an index outside 0 to 63, and
 * {@link #lowBits} for a count outside 0 to 64, because a shift of a
 * {@code long} by 64 or more wraps instead of failing.
 */
public final class IndexMasks
{
    public static final int CAPACITY = Long.SIZE;

    private IndexMasks() { }

    /** Whether one mask holds a bit for each of {@code count} indexes. */
    public static boolean fits(int count) {
        return count <= CAPACITY;
    }

    /** The bit of one index. */
    public static long bit(int index) {
        return 1L << Objects.checkIndex(index, CAPACITY);
    }

    /** Whether the mask has the bit of the index. */
    public static boolean isSet(long mask, int index) {
        return (mask & bit(index)) != 0L;
    }

    /** The bits of indexes 0 to {@code count - 1}. */
    public static long lowBits(int count) {
        Objects.checkIndex(count, CAPACITY + 1);
        return (count == CAPACITY) ? -1L : bit(count) - 1;
    }
}
