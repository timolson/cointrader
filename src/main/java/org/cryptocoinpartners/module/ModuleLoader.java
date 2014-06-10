package org.cryptocoinpartners.module;

import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.ParseException;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.cryptocoinpartners.service.Service;
import org.cryptocoinpartners.util.Config;
import org.cryptocoinpartners.util.ModuleLoaderError;
import org.reflections.Reflections;
import org.reflections.scanners.SubTypesScanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Modules are subpackages of org.cryptocoinpartners.module, and any files/classes in this package are loaded/executed
 * according to their type:
 * <ol>
 * <li>Any files named conf.* are loaded into an Apache Commons Configuration object</li>
 * <li>Classes implementing @ModuleListener have singletons created with their default constructor, and lifecycle
 * notifications are sent to those instances.</li>
 * <li>*.epl files are loaded as Esper Event Processing Language source</li>
 * <li>Any file named *.epl is loaded as Esper Event Processing Language source.  If the file has the same name as a
 * Java ModuleListener in the same module, then the EPL file may use the @IntoMethod annotation on its esper statements
 * to push results into the module listener singleton's fields.</li>
 * </ol>
 *
 * @author Tim Olson
 * @see ModuleListener
 * @see IntoMethod
 * @see When
 */
public class ModuleLoader {

    public static void load(Esper esper, String... moduleNames) throws ModuleLoaderError {
        load(esper, null, moduleNames);
    }


    public static void load(Esper esper, @Nullable AbstractConfiguration config, String... moduleNames) throws ModuleLoaderError {
        try {
            init();
            for( String name : moduleNames ) {
                if( esper.isModuleLoaded(name) ) {
                    log.debug("skipping loaded module " + name);
                    break;
                }
                String modulePackageName = findModule(name);
                log.info("loading module "+modulePackageName);
                Configuration fullConfiguration = buildConfig(name,modulePackageName,config);
                Collection<ModuleListener> lifecycles = initListenerClasses(esper,name);
                for( ModuleListener lifecycle : lifecycles )
                    lifecycle.initModule(esper,fullConfiguration);
                loadEsperFiles(esper, modulePackageName);
            }
        }
        catch( Throwable e ) {
            throw new ModuleLoaderError(e);
        }
    }


    private static AbstractConfiguration buildConfig(String name, String modulePackageName, @Nullable AbstractConfiguration c)
            throws ConfigurationException {
        final ClassLoader classLoader = ModuleLoader.class.getClassLoader();
        final ArrayList<AbstractConfiguration> moduleConfigs = new ArrayList<>();

        // first priority is the caller's configuration
        if( c != null )
            moduleConfigs.add(c);

        // then add the package-specific props file
        String slashPackage = modulePackageName.replaceAll("\\.", "/");
        String propsFilePath = slashPackage +"/"+name+".properties";
        URL resource = classLoader.getResource(propsFilePath);
        if (resource != null) {
            PropertiesConfiguration packageConfig = new PropertiesConfiguration(resource);
            moduleConfigs.add(packageConfig);
        }

        // then the more generic config.properties
        propsFilePath = slashPackage+"/config.properties";
        resource = classLoader.getResource(propsFilePath);
        if (resource != null) {
            PropertiesConfiguration packageConfig = new PropertiesConfiguration(resource);
            moduleConfigs.add(packageConfig);
        }

        return Config.module(moduleConfigs);
    }


    private static Collection<ModuleListener> initListenerClasses(Esper esper, String name)
            throws IllegalAccessException, InstantiationException {
        Collection<Class<? extends ModuleListener>> listenerClasses = moduleListenerClasses.get(name);
        ArrayList<ModuleListener> listeners = new ArrayList<>();
        for( Class<? extends ModuleListener> listenerClass : listenerClasses ) {
            log.debug("instantiating ModuleListener "+listenerClass.getSimpleName());
            ModuleListener listener = listenerClass.newInstance();
            bindServices(esper,listener);
            listeners.add(listener);
            esper.subscribe(listener);
        }
        return listeners;
    }


    private static void bindServices(Esper esper, ModuleListener listener) {
        for( Field field : listener.getClass().getDeclaredFields() ) {
            if( Service.class.isAssignableFrom(field.getType()) ) {
                // the field has a Service type
                @SuppressWarnings("unchecked")
                Service service = esper.getService((Class<Service>) field.getType());
                if( service == null )
                    throw new ModuleLoaderError("No "+field.getType().getSimpleName()+" service found attached to the Esper.  Make sure to load dependent Services before loading "+listener.getClass().getName());
                try {
                    field.set(listener,service);
                }
                catch( IllegalAccessException e ) {
                    throw new ModuleLoaderError("Could not bind service "+field.getType()+" "+field.getName(),e);
                }
            }
        }

    }


    private static void load(Esper esper, File file) throws IOException, ParseException, DeploymentException {
        esper.loadStatements(FileUtils.readFileToString(file));
    }


    private static void loadEsperFiles(Esper esper, String modulePackageName) throws Exception {
        String path = modulePackageName.replaceAll("\\.","/");
        File[] files = new File(path).listFiles();
        if( files != null ) {
            for( File file : files ) {
                if( file.getName().toLowerCase().endsWith(".epl") ) {
                    log.debug("loading epl file "+file.getName());
                    load(esper, file);
                }
            }
        }
    }


    static void init() throws IllegalAccessException, InstantiationException {
        if( moduleListenerClasses != null )
            return;
        moduleListenerClasses = new HashMap<>();

        for( String modulePath : getModulePathList() ) {
            Reflections reflections = new Reflections(modulePath, new SubTypesScanner());
            Set<Class<? extends ModuleListener>> subs = reflections.getSubTypesOf(ModuleListener.class);
            for( Class<? extends ModuleListener> subclass : subs ) {
                if( Modifier.isAbstract(subclass.getModifiers()) )
                    continue;
                String packageName = subclass.getPackage().getName();
                Matcher matcher = Pattern.compile("(?:^|\\.)([^\\.]+)$").matcher(packageName);
                matcher.find();
                String moduleName = matcher.group(1);
                Collection<Class<? extends ModuleListener>> listeners = moduleListenerClasses.get(moduleName);
                if( listeners == null ) {
                    listeners = new ArrayList<>();
                    moduleListenerClasses.put(moduleName, listeners);
                }
                listeners.add(subclass);
            }
        }
    }


    /** searches the module.path for the given module name and returns the complete package name where the module was found */
    private static String findModule( String name ) {
        for( String path : getModulePathList() ) {
            String packageName = path + "." + name;
            if( Package.getPackage(packageName) != null )
                return packageName;
        }
        return null;
    }


    private static List<String> getModulePathList() {
        String modulePath = Config.combined().getString("module.path", "");
        List<String> paths = new ArrayList<>(Arrays.asList(modulePath.split(":")));
        paths.add("org.cryptocoinpartners.module");
        paths.remove("");
        return paths;
    }


    private static Logger log = LoggerFactory.getLogger(ModuleLoader.class);
    private static Map<String,Collection<Class<? extends ModuleListener>>> moduleListenerClasses;
}
