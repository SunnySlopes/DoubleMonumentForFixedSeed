package project;

/**
 * Formats a monument search hit (pair or multi-monument group) for display.
 */
@FunctionalInterface
public interface PairResultFormatter {
    /**
     * @param x     output point (pair midpoint or group AFK point)
     * @param z     output point (pair midpoint or group AFK point)
     * @param count monuments in the group (2/3/4)
     * @param area  usable spawning area (blocks) covered from the best AFK point
     * @param dx    pair: |dx| between the two monuments; group: bounding box width
     * @param dz    pair: |dz| between the two monuments; group: bounding box height
     */
    String format(int x, int z, int count, int area, int dx, int dz);
}
