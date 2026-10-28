package project;

/**
 * Formats a monument search hit (pair or multi-monument group) as one {@link ResultTable} record.
 *
 * <p>The returned string is tab separated with the cells in {@link ResultTable#KEYS} order; it is
 * the value the result panel and the CSV export are built from (no {@code /tp} prefixes, labels or
 * brackets - the header row carries the column names).</p>
 */
@FunctionalInterface
public interface PairResultFormatter {
    /**
     * @param x     output point (pair midpoint or best AFK point; dual mode: first dummy)
     * @param z     output point (pair midpoint or best AFK point; dual mode: first dummy)
     * @param x2    second dummy point (dual mode; equals x otherwise)
     * @param z2    second dummy point (dual mode; equals z otherwise)
     * @param sx    optimal single-dummy point
     * @param sz    optimal single-dummy point
     * @param count monuments in the group (2/3/4)
     * @param area  usable spawning area (blocks) from the output point(s)
     * @param singleArea optimal single-dummy usable area
     * @param areaMax ceiling for the group with unlimited dummies under the same max-min&lt;=256
     *                footprint (reference only; area &lt;= areaMax)
     * @param areaMaxX overworld centre of the ceiling footprint (portal / gate location)
     * @param areaMaxZ overworld centre of the ceiling footprint (portal / gate location)
     * @param dx    pair: |dx| between the two monuments; group: bounding box width
     * @param dz    pair: |dz| between the two monuments; group: bounding box height
     * @param dual  true when x2/z2 carry a distinct second dummy point
     * @return one result record (see {@link ResultTable})
     */
    String format(int x, int z, int x2, int z2, int sx, int sz, int count, int area,
                  int singleArea, int areaMax, int areaMaxX, int areaMaxZ, int dx, int dz,
                  boolean dual);
}
