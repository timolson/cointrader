package org.cryptocoinpartners.util;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/**
 * Utility methods to simplify comparisons.
 * <p>
 * This is a thread-safe static utility class.
 */
public final class CompareUtils {

    /**
     * Restricted constructor.
     */
    private CompareUtils() {
    }

    //-------------------------------------------------------------------------
    /**
     * Compares two objects finding the maximum.
     * 
     * @param <T>  the object type
     * @param a  item that compareTo is called on, may be null
     * @param b  item that is being compared, may be null
     * @return the maximum of the two objects, null if both null
     */
    public static <T extends Comparable<? super T>> T max(T a, T b) {
        if (a != null && b != null) {
            return a.compareTo(b) >= 0 ? a : b;
        }
        if (a == null) {
            if (b == null) {
                return null;
            } else {
                return b;
            }
        } else {
            return a;
        }
    }

    /**
     * Compares two objects finding the minimum.
     * 
     * @param <T>  the object type
     * @param a  item that compareTo is called on, may be null
     * @param b  item that is being compared, may be null
     * @return the minimum of the two objects, null if both null
     */
    public static <T extends Comparable<? super T>> T min(T a, T b) {
        if (a != null && b != null) {
            return a.compareTo(b) <= 0 ? a : b;
        }
        if (a == null) {
            if (b == null) {
                return null;
            } else {
                return b;
            }
        } else {
            return a;
        }
    }

    //-------------------------------------------------------------------------
    /**
     * Compares two objects, either of which might be null, sorting nulls low.
     * 
     * @param <E>  the type of object being compared
     * @param a  item that compareTo is called on
     * @param b  item that is being compared
     * @return negative when a less than b, zero when equal, positive when greater
     */
    public static <E> int compareWithNullLow(Comparable<E> a, E b) {
        if (a == null) {
            return b == null ? 0 : -1;
        } else if (b == null) {
            return 1; // a not null
        } else {
            return a.compareTo(b);
        }
    }

    /**
     * Compares two objects, either of which might be null, sorting nulls high.
     * 
     * @param <E>  the type of object being compared
     * @param a  item that compareTo is called on
     * @param b  item that is being compared
     * @return negative when a less than b, zero when equal, positive when greater
     */
    public static <E> int compareWithNullHigh(Comparable<E> a, E b) {
        if (a == null) {
            return b == null ? 0 : 1;
        } else if (b == null) {
            return -1; // a not null
        } else {
            return a.compareTo(b);
        }
    }

    //-------------------------------------------------------------------------
    /**
     * Compare two doubles to see if they're 'closely' equal.
     * <p>
     * This handles rounding errors which can mean the results of double precision computations
     * lead to small differences in results.
     * The definition 'close' is that the difference is less than 10^-15 (1E-15).
     * If a different maximum allowed difference is required, use the other version of this method.
     * 
     * @param a  the first value
     * @param b  the second value
     * @return true, if a and b are equal to within 10^-15, false otherwise
     */
    public static boolean closeEquals(double a, double b) {
        if (Double.isInfinite(a)) {
            return (a == b);
        }
        return (Math.abs(a - b) < 1E-15);
    }

    /**
     * Compare two doubles to see if they're 'closely' equal.
     * <p>
     * This handles rounding errors which can mean the results of double precision computations
     * lead to small differences in results.
     * The definition 'close' is that the absolute difference is less than the specified difference.
     * 
     * @param a  the first value
     * @param b  the second value
     * @param maxDifference  the maximum difference to allow
     * @return true, if a and b are equal to within the tolerance
     */
    public static boolean closeEquals(double a, double b, double maxDifference) {
        if (Double.isInfinite(a)) {
            return (a == b);
        }
        return (Math.abs(a - b) < maxDifference);
    }

    /**
     * Compare two doubles to see if they're 'closely' equal.
     * <p>
     * This handles rounding errors which can mean the results of double precision computations
     * lead to small differences in results.
     * This method returns the difference to indicate how the first differs from the second.
     * 
     * @param a  the first value
     * @param b  the second value
     * @param maxDifference  the maximum difference to allow while still considering the values equal
     * @return the value 0 if a and b are equal to within the tolerance; a value less than 0 if a is numerically less
     *         than b; and a value greater than 0 if a is numerically greater than b.
     */
    public static int compareWithTolerance(double a, double b, double maxDifference) {
        if (a == Double.POSITIVE_INFINITY) {
            return (a == b ? 0 : 1);
        } else if (a == Double.NEGATIVE_INFINITY) {
            return (a == b ? 0 : -1);
        } else if (b == Double.POSITIVE_INFINITY) {
            return (a == b ? 0 : -1);
        } else if (b == Double.NEGATIVE_INFINITY) {
            return (a == b ? 0 : 1);
        }
        if (Math.abs(a - b) < maxDifference) {
            return 0;
        }
        return (a < b) ? -1 : 1;
    }

    /**
     * Compare two items, with the ordering determined by a list of those items.
     * <p>
     * Nulls are permitted and sort low, and if a or b are not in the list, then
     * the result of comparing the toString() output is used instead.
     * 
     * @param <T> the list type
     * @param list  the list, not null
     * @param a  the first object, may be null
     * @param b  the second object, may be null
     * @return 0, if equal, -1 if a < b, +1 if a > b
     */
    public static <T> int compareByList(List<T> list, T a, T b) {
        if (a == null) {
            if (b == null) {
                return 0;
            } else {
                return -1;
            }
        } else {
            if (b == null) {
                return 1;
            } else {
                if (list.contains(a) && list.contains(b)) {
                    return list.indexOf(a) - list.indexOf(b);
                } else {
                    return compareWithNullLow(a.toString(), b.toString());
                }
            }
        }
    }

    private static final Comparator<? super Constructor<?>> CTOR_COMPARATOR = new Comparator<Constructor<?>>() {
        @Override
        public int compare(Constructor<?> ctorA, Constructor<?> ctorB) {
            Class<?>[] params1 = ctorA.getParameterTypes();
            Class<?>[] params2 = ctorB.getParameterTypes();

            if (params1.length != params2.length)
                throw new IllegalArgumentException(ctorA + " can't be compared to " + ctorB);

            for (int i = 0; i < params1.length; i++) {
                Class<?> aClass = params1[i];
                Class<?> bClass = params2[i];
                if (!aClass.equals(bClass)) {
                    if (aClass.isAssignableFrom(bClass))
                        return 1;
                    if (bClass.isAssignableFrom(aClass))
                        return -1;
                    throw new IllegalArgumentException(ctorA + " can't be compared to " + ctorB + ": args at pos " + i + " aren't comparable: " + aClass
                            + " vs " + bClass);
                }
            }

            return 0;
        }
    };

    public static <T> T tryToCreateBestMatch(Class<T> aClass, Object[] oa) throws InstantiationException, IllegalAccessException, InvocationTargetException {
        //noinspection unchecked
        Constructor<T>[] declaredConstructors = (Constructor<T>[]) aClass.getDeclaredConstructors();
        Class<?>[] argClasses = getClasses(oa);
        List<Constructor<T>> matchedCtors = new ArrayList<>();
        for (Constructor<T> ctr : declaredConstructors) {
            Class<?>[] parameterTypes = ctr.getParameterTypes();
            if (ctorMatches(parameterTypes, argClasses)) {
                matchedCtors.add(ctr);
            }
        }

        if (matchedCtors.isEmpty())
            return null;

        Collections.sort(matchedCtors, CTOR_COMPARATOR);
        return matchedCtors.get(0).newInstance(oa);
    }

    private static boolean ctorMatches(Class<?>[] ctrParamTypes, Class<?>[] argClasses) {
        if (ctrParamTypes.length != argClasses.length)
            return false;
        for (int i = 0; i < ctrParamTypes.length; i++) {
            Class<?> ctrParamType = ctrParamTypes[i];
            Class<?> argClass = argClasses[i];

            if (!compatible(ctrParamType, argClass))
                return false;
        }
        return true;
    }

    private static boolean compatible(Class<?> ctrParamType, Class<?> argClass) {
        if (ctrParamType.isAssignableFrom(argClass))
            return true;
        if (ctrParamType.isPrimitive())
            return compareAgainstPrimitive(ctrParamType.getName(), argClass);
        return false;
    }

    private static boolean compareAgainstPrimitive(String primitiveType, Class<?> argClass) {
        switch (primitiveType) {
            case "short":
            case "byte":
            case "int":
            case "long":
                return INTEGER_WRAPPERS.contains(argClass.getName());
            case "float":
            case "dobule":
                return FP_WRAPPERS.contains(argClass.getName());
        }
        throw new IllegalArgumentException("Unexpected primitive type?!?!: " + primitiveType);
    }

    private static final HashSet<String> INTEGER_WRAPPERS = new HashSet<>(Arrays.asList("java.lang.Integer", "java.lang.Short", "java.lang.Byte",
            "java.lang.Long"));
    private static final HashSet<String> FP_WRAPPERS = new HashSet<>(Arrays.asList("java.lang.Float", "java.lang.Double"));

    private static Class<?>[] getClasses(Object[] oa) {
        if (oa == null)
            return new Class[0];
        Class<?>[] ret = new Class[oa.length];
        for (int i = 0; i < oa.length; i++) {
            ret[i] = oa[i] == null ? Object.class : oa[i].getClass();
        }
        return ret;
    }

}
