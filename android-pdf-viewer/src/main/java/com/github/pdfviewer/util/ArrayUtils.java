
package com.github.pdfviewer.util;

import java.util.ArrayList;
import java.util.List;

public class ArrayUtils {

    private ArrayUtils() {
        // Prevents instantiation
    }

    
    public static int[] deleteDuplicatedPages(int[] pages) {
        List<Integer> result = new ArrayList<>();
        int lastInt = -1;
        for (Integer currentInt : pages) {
            if (lastInt != currentInt) {
                result.add(currentInt);
            }
            lastInt = currentInt;
        }
        int[] arrayResult = new int[result.size()];
        for (int i = 0; i < result.size(); i++) {
            arrayResult[i] = result.get(i);
        }
        return arrayResult;
    }

    
    public static int[] calculateIndexesInDuplicateArray(int[] originalUserPages) {
        int[] result = new int[originalUserPages.length];
        if (originalUserPages.length == 0) {
            return result;
        }

        int index = 0;
        result[0] = index;
        for (int i = 1; i < originalUserPages.length; i++) {
            if (originalUserPages[i] != originalUserPages[i - 1]) {
                index++;
            }
            result[i] = index;
        }

        return result;
    }

    public static String arrayToString(int[] array) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < array.length; i++) {
            builder.append(array[i]);
            if (i != array.length - 1) {
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }
}
