package org.cryptocoinpartners.util;

/**
 * @author Tim Olson
 */
public class MathUtil {
    public static int getPoissonRandom(double mean) {
        double L = Math.exp(-mean);
        int k = 0;
        double p = 1.0;
        do {
            p = p * Math.random();
            k++;
        } while (p > L);
        return k - 1;
    }
}
