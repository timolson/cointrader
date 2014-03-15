package com.cryptocoinpartners.util;

import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;

import java.util.Set;


/**
 * @author Tim Olson
 */
public class ReflectionUtil {


    public static <T> Set<Class<? extends T>> getSubtypesOf(Class<T> cls) {
        return reflections.getSubTypesOf(cls);
    }


    private static Reflections reflections;


    static {
        reflections = new Reflections(ClasspathHelper.forPackage("com.cryptocoinpartners"),
                                      new SubTypesScanner() /* , other scanners here */);
    }
}
