package org.cryptocoinpartners.module;

import com.espertech.esper.client.*;
import com.espertech.esper.client.deploy.DeploymentException;
import com.espertech.esper.client.deploy.DeploymentResult;
import com.espertech.esper.client.deploy.EPDeploymentAdmin;
import com.espertech.esper.client.deploy.ParseException;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.client.time.CurrentTimeSpanEvent;
import com.espertech.esper.core.service.EPServiceProviderImpl;
import com.google.inject.*;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.ProvisionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.io.FileUtils;
import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.schema.StrategyFundManager;
import org.cryptocoinpartners.service.Service;
import org.cryptocoinpartners.util.Config;
import org.cryptocoinpartners.util.ReflectionUtil;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;


/**
 * This is a wrapper around Esper with dependency injection functionality as well.
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Context {


    public Context() {
        construct();
    }


    public interface TimeProvider {
        /**
         @return the Instant the Context should be initialized to as the starting time
         */
        Instant getInitialTime();

        /**
         @param event The event to be published after the time is advanced
         */
        Instant nextTime(Event event);
    }


    public Context(TimeProvider timeProvider)
    {
        this.timeProvider = timeProvider;
        construct();
    }


    /**
     * This is the main way to register modules with the Context.  Attaching a class to a Context has
     * many effects:
     * <ul>
     *     <li>If the class c has any superclasses or interfaces tagged with @Service, this class is registered as
     *     an implementation of that service interface.  Other instances in this Context will have their @Injected
     *     fields of service types set to an instance of this class c when the types match.</li>
     *     <li>The class will be instantiated and the instance will be <pre>@Inject</pre>ed by Guice, binding any other
     *     objects which have been attached to this Context previously</li>
     *     <li>The created instance is <pre>subscribe()</pre>'d to the Context's esper, binding any @When annotations
     *     on the instances's methods to esper statements</li>
     *     <li>The new instance is returned after configuration</li>
     * </ul>
     */
    public <T> T attach( Class<T> c ) {
        return attach(c,(Configuration)null);
    }


    public <T> T attach( Class<T> c, Configuration moduleConfig ) {
        registerBindings(c);
        return injectorForConfig(moduleConfig).getInstance(c);
    }


    public Object attach( String name ) {
        Class<?> c = findModuleClass(name);
        if( c == null )
            throw new Error("Could not find service implementation named "+name);
        //noinspection RedundantCast
        return attach(c, (Configuration) null);
    }


    public Object attach( String name, Configuration config ) {
        Class<?> c = findModuleClass(name);
        if( c == null )
            throw new Error("Could not find service implementation named "+name);
        return attach(c,config);
    }


    public <T> T attach( String name, Class<T> cls ) {
        return attach(name,cls,null);
    }


    @SuppressWarnings("unchecked")
    public <T> T attach( String name, Class<T> cls, Configuration config ) {
        Class<?> c = findModuleClass(name);
        if( c == null )
            throw new Error("Could not find service implementation named "+name);
        if( !c.isAssignableFrom(cls) )
            throw new Error("Module name "+name+" loaded a "+c.getName()+" but expected a "+cls.getName());
        return (T) attach(c,config);
    }


    public <T> void attachInstance( T instance ) {
        injector.injectMembers(instance);
        registerBindings(instance.getClass(), instance);
    }


    public <T> void attach( Class<? super T> cls, T instance ) {
        injector.injectMembers(instance);
        register(cls,instance);
    }


    public void publish(Event e)
    {
        if( timeProvider != null ) {
            Instant time = timeProvider.nextTime(e);
            advanceTime(time);
        }
        epRuntime.sendEvent(e);
    }


    public void destroy() {
        epService.destroy();
        // todo shutdown loaded modules
    }


    public void advanceTime( Instant now ) {
        if( timeProvider == null )
            throw new IllegalArgumentException("Can only advanceTime() when the Context was constructed with a TimeProvider");
        if( lastTime == null ) {
            // jump to the start time instead of stepping to it
            epRuntime.sendEvent(new CurrentTimeEvent(now.getMillis()));
        }
        else if( now.isBefore(lastTime) )
            throw new IllegalArgumentException("advanceTime must always move time forward. "+now+" < "+lastTime);
        else if( now.isAfter(lastTime) ) {
            // step time up to now
            epRuntime.sendEvent(new CurrentTimeSpanEvent(now.getMillis()));
        }
        lastTime = now;
    }


    public void subscribe( Object listener )
    {
        // todo search for EPL files and load them
        for( Method method : listener.getClass().getMethods() ) {
            When when = method.getAnnotation(When.class);
            if( when != null ) {
                String statement = when.value();
                log.debug("subscribing "+method+" with statement \""+statement+"\"");
                subscribe(listener, method, statement);
            }
        }
    }


    public void subscribe(Object listener, Method method, String statement) {
        EPStatement epStatement = epAdministrator.createEPL(statement);
        subscribe(listener, method, epStatement);
    }


    public void loadStatements(String source) throws ParseException, DeploymentException, IOException {
        loadStatements(source, null);
    }

    /**
     * @param source a string containing EPL statements
     * @param intoFieldBean if not null, any @IntoMethod annotations on Esper statements will bind the columns from
     *                      the select statement into the fields of the intoFieldBean instance.
     */
    public void loadStatements(String source, Object intoFieldBean )
            throws ParseException, DeploymentException, IOException {
        EPDeploymentAdmin deploymentAdmin = epAdministrator.getDeploymentAdmin();
        DeploymentResult deployment = deploymentAdmin.parseDeploy(source);
        if( intoFieldBean != null ) {
            for( EPStatement statement : deployment.getStatements() ) {
                boolean subscribed = false;
                for( Annotation annotation : statement.getAnnotations() ) {
                    if( annotation instanceof IntoMethod ) {
                        IntoMethod intoField = (IntoMethod) annotation;
                        String methodName = intoField.value();
                        Method[] methods = intoFieldBean.getClass().getMethods();
                        for( Method method : methods ) {
                            if( method.getName().equals(methodName) ) {
                                if( !subscribed ) {
                                    subscribe(intoFieldBean,method,statement);
                                    subscribed = true;
                                }
                                else
                                    throw new Error(intoFieldBean.getClass().getSimpleName()+" has multiple methods named "+methodName+".  No overriding allowed from an @IntoMethod binding.");
                            }
                        }
                    }
                }
            }
        }
    }


    public Injector getInjector() { return injector; }


    /** Use this to attach a StrategyFundManager to the Context */
    @Deprecated
    public void loadStrategyFundManager( final StrategyFundManager strategyFundManager ) {
        // todo tim how to bind instance-specific configuration on the StrategyHandler?
        /*
        final StrategyHandler managerBinder = new StrategyHandler(strategyFundManager);
        MapConfiguration config = new MapConfiguration(strategyFundManager.getConfig());
        attach(managerBinder);
        if( !managerBinder.foundStrategy ) {
            log.warn("Module "+strategyFundManager.getModuleName()+" does not contain a Strategy class");
        }
        */
    }


    //
    // End of Public Interface
    //


    private void subscribe(Object listener, Method method, EPStatement statement) {
        statement.setSubscriber(new Listener(listener,method,statement.getText()));
    }


    private Class<?> findModuleClass(String name) {
        Class<?> found;
        for( String path : getModulePathList() ) {
            String pdot = path + ".";
            if( (found = findClass( pdot + name )) != null ) return found;
            if( (found = findClass( pdot + name + "Module" )) != null ) return found;
        }
        return null;
    }


    private Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        }
        catch( ClassNotFoundException e ) {
            return null;
        }
    }


    private static void load(Context context, File file) throws IOException, ParseException, DeploymentException {
        context.loadStatements(FileUtils.readFileToString(file));
    }


    private static void loadEsperFiles(Context context, String modulePackageName) throws Exception {
        String path = modulePackageName.replaceAll("\\.","/");
        File[] files = new File(path).listFiles();
        if( files != null ) {
            for( File file : files ) {
                if( file.getName().toLowerCase().endsWith(".epl") ) {
                    log.debug("loading epl file "+file.getName());
                    load(context, file);
                }
            }
        }
    }


    private static List<String> getModulePathList() {
        String pathProperty = "module.path";
        return Config.getPathProperty(pathProperty);
    }


    // todo how to bring in module-specific config now?
    private static AbstractConfiguration buildConfig(String name, String modulePackageName, @Nullable AbstractConfiguration c)
            throws ConfigurationException {
        final ClassLoader classLoader = Context.class.getClassLoader();
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


    private boolean registerBindings(Class<?> c) {
        return registerBindings(c,c);
    }


    // recursion method walks up the superclass and interface parent tree looking for parent classes to register
    private boolean registerBindings(Class service, Object implementationClassOrObject) {
        boolean injectorWasUpdated = conditionalRegister(service, implementationClassOrObject);
        Class<?> superclass = service.getSuperclass();
        if( superclass != null && registerBindings(superclass,implementationClassOrObject) )
            injectorWasUpdated = true;
        for( Class<?> interfaceClass : service.getInterfaces() )
            if( registerBindings(interfaceClass, implementationClassOrObject) )
                injectorWasUpdated = true;
        return injectorWasUpdated;
    }


    private boolean conditionalRegister(final Class interfaceClass, final Object implementationClassOrObject) {
        if( interfaceClass.getAnnotation(Service.class) != null ) {
            doRegister(interfaceClass, implementationClassOrObject);
            return true;
        }
        return false;
    }


    private void doRegister(final Class interfaceClass, final Object implementationClassOrObject) {
        injector = injector.createChildInjector(new Module() {
            @SuppressWarnings("unchecked")
            public void configure(Binder binder) {
                if( Class.class.isAssignableFrom(implementationClassOrObject.getClass()) )
                    binder.bind(interfaceClass).to((Class)implementationClassOrObject);
                else
                    binder.bind(interfaceClass).toInstance(implementationClassOrObject);
            }
        });
    }


    private <T> void register(final Class<? super T> interfaceClass, final T instance) {
        injector = injector.createChildInjector(new Module() {
            public void configure(Binder binder) {
                binder.bind(interfaceClass).toInstance(instance);
            }
        });
    }


    @SuppressWarnings("unchecked")
    private Injector injectorForConfig(final Configuration configParams) {
        if( configParams == null )
            return injector;
        return injector.createChildInjector(new Module() {
            public void configure(Binder binder) {
                binder.bind(Configuration.class).toInstance(configParams);
            }
        });
    }


    private void construct() {
        final com.espertech.esper.client.Configuration esperConfig = new com.espertech.esper.client.Configuration();
        esperConfig.addEventType(Event.class);
        Set<Class<? extends Event>> eventTypes = ReflectionUtil.getSubtypesOf(Event.class);
        for( Class<? extends Event> eventType : eventTypes )
            esperConfig.addEventType(eventType);
        esperConfig.addImport(IntoMethod.class);
        if( timeProvider != null ) {
            esperConfig.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
        }
        epService = EPServiceProviderManager.getDefaultProvider(esperConfig);
        if( timeProvider != null ) {
            lastTime = timeProvider.getInitialTime();
            final EPServiceProviderImpl epService1 = (EPServiceProviderImpl) epService;
            epService1.initialize(lastTime.getMillis());
        }
        epRuntime = epService.getEPRuntime();
        epAdministrator = epService.getEPAdministrator();
        config = Config.combined();
        injector = Guice.createInjector(new Module() {
            public void configure(Binder binder) {

                // bind this Context
                binder.bind(Context.class).toInstance(Context.this);

                // bind config
                binder.bind(Configuration.class).toInstance(Context.this.config);

                // listen for any new instances and subscribe them to the esper
                binder.bindListener(Matchers.any(),new ProvisionListener() {
                    public <T> void onProvision(ProvisionInvocation<T> tProvisionInvocation) {
                        T provision = tProvisionInvocation.provision();
                        subscribe(provision);
                    }
                });

                // SLF4J logger injection
                binder.bindListener(Matchers.any(), new TypeListener() {
                    public <I> void hear(TypeLiteral<I> typeLiteral, TypeEncounter<I> typeEncounter) {
                        for (Class<?> c = typeLiteral.getRawType(); c != Object.class; c = c.getSuperclass())
                        {
                            for( final Field field : c.getDeclaredFields() )
                            {
                                if( field.getType().isAssignableFrom(Logger.class) ) {
                                    typeEncounter.register(new MembersInjector<I>() {
                                        public void injectMembers(I i) {
                                            try {
                                                boolean wasAccessible = field.isAccessible();
                                                field.setAccessible(true);
                                                if( field.get(i) == null ) {
                                                    field.set(i, LoggerFactory.getLogger(field.getDeclaringClass()));
                                                }
                                                field.setAccessible(wasAccessible);
                                            }
                                            catch( IllegalAccessException e ) {
                                                e.printStackTrace();
                                            }
                                        }
                                    });
                                }
                            }
                        }
                    }
                });
            }
        });
    }


    /**
     * this class conforms to the callback specs for an Esper subscriber
     * http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html_single/index.html#api-admin-subscriber
     * then forwards that invocation to the original listener
     */
    private class Listener {
        public void update(Object[] row) {
            try {
                method.invoke(delegate,row);
            }
            catch( IllegalAccessException e ) {
                throw new EsperError("Could not invoke method "+method+" on statement trigger "+statement,e);
            }
            catch( InvocationTargetException e ) {
                e.printStackTrace();
            }
        }


        private Listener(Object delegate, Method method, String statement) {
            this.delegate = delegate;
            this.method = method;
            this.statement = statement;
        }


        private Object delegate;
        private Method method;
        private String statement;
    }


    private static Logger log = LoggerFactory.getLogger(Context.class);

    private Configuration config;
    private Injector injector;
    private TimeProvider timeProvider;
    private Instant lastTime = null;
    private EPServiceProvider epService;
    private EPRuntime epRuntime;
    private EPAdministrator epAdministrator;
}
