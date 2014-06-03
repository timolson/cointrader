package org.cryptocoinpartners.util;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;

import java.util.Set;
import java.util.regex.Pattern;


/**
 * @author Tim Olson
 */
public class ReflectionUtil {


    public static <T> Set<Class<? extends T>> getSubtypesOf(Class<T> cls) {
        return reflections.getSubTypesOf(cls);
    }


    public static Set<String> searchResources(String regex) {
        return reflections.getResources(Pattern.compile(regex));
    }


    private static Reflections reflections;


    static {
        reflections = new Reflections(ClasspathHelper.forPackage("org.cryptocoinpartners"),
                                      new SubTypesScanner(),
                                      new ResourcesScanner() /* , other scanners here */);
    }
}
