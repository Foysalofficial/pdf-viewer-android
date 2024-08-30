
package com.github.pdfviewer.util;

public class MathUtils {

    static private final int BIG_ENOUGH_INT = 16 * 1024;
    static private final double BIG_ENOUGH_FLOOR = BIG_ENOUGH_INT;
    static private final double BIG_ENOUGH_CEIL = 16384.999999999996;

    private MathUtils() {
        // Prevents instantiation
    }

    
    public static int limit(int number, int between, int and) {
        if (number <= between) {
            return between;
        }
        if (number >= and) {
            return and;
        }
        return number;
    }

    
    public static float limit(float number, float between, float and) {
        if (number <= between) {
            return between;
        }
        if (number >= and) {
            return and;
        }
        return number;
    }

    public static float max(float number, float max) {
        if (number > max) {
            return max;
        }
        return number;
    }

    public static float min(float number, float min) {
        if (number < min) {
            return min;
        }
        return number;
    }

    public static int max(int number, int max) {
        if (number > max) {
            return max;
        }
        return number;
    }

    public static int min(int number, int min) {
        if (number < min) {
            return min;
        }
        return number;
    }

    

    
    static public int floor(float value) {
        return (int) (value + BIG_ENOUGH_FLOOR) - BIG_ENOUGH_INT;
    }

    
    static public int ceil(float value) {
        return (int) (value + BIG_ENOUGH_CEIL) - BIG_ENOUGH_INT;
    }
}
