package org.cryptocoinpartners.util;

import org.reflections.Reflections;
import org.reflections.scanners.ResourcesScanner;
import org.reflections.scanners.SubTypesScanner;
import org.reflections.util.ClasspathHelper;
import org.springframework.util.StringUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;
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


    public static Object instantiateClassByName(String className,
                                                Object[] constructorArguments, Class[] constructorArgumentTypes) {
        return instantiateClass(className, constructorArguments, constructorArgumentTypes);
    }


    public static Object instantiateClassByName(String className, Object... constructorArguments ) {
        Class[] argTypes = new Class[constructorArguments.length];
        for( int i = 0; i < constructorArguments.length; i++ ) {
            Object argument = constructorArguments[i];
            argTypes[i] = argument.getClass();
        }
        return instantiateClass(className, constructorArguments, argTypes);
    }


    private static Object instantiateClass(String className, Object[] constructorArguments, Class[] argTypes) {
        Class<?> cls = classForName(className);
        try {
            Constructor<?> constructor = cls.getConstructor(argTypes);
            return constructor.newInstance(constructorArguments);
        }
        catch( InstantiationException | IllegalAccessException |InvocationTargetException e ) {
            throw new Error("Could not instantiate "+className);
        }
        catch( ClassCastException e ) {
            throw new Error(className+" is not a subclass of "+className);
        }
        catch( NoSuchMethodException e ) {
            throw new Error("Could not find constructor which takes arguments "+ StringUtils
                                                                                         .arrayToCommaDelimitedString(constructorArguments));
        }
    }


    public static Class<?> classForName(String className) {
        try {
            return ReflectionUtil.class.getClassLoader().loadClass(className);
        }
        catch( ClassNotFoundException e ) {
            throw new Error("Could not find "+className+" in the classpath");
        }
    }


    public static Reflections getCommandReflections() {
        if( commandReflections == null ) {
            List<String> paths = ConfigUtil.getPathProperty("command.path");
            Set<URL> urls = new HashSet<>();
            for( String path : paths )
                urls.addAll(ClasspathHelper.forPackage(path));
            commandReflections = new Reflections(urls, new SubTypesScanner());
        }
        return commandReflections;
    }


    private static Reflections reflections;


    static {
        reflections = new Reflections(ClasspathHelper.forPackage("org.cryptocoinpartners"),
                                      new SubTypesScanner(),
                                      new ResourcesScanner() /* , other scanners here */);
    }


    private static Reflections commandReflections;
}
