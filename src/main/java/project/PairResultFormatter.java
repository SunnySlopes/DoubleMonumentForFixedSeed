package project;

/**
 * Formats a monument pair hit for display.
 */
@FunctionalInterface
public interface PairResultFormatter {
    String format(int hangX, int hangZ, int dx, int dz, boolean lowEfficiency);
}
