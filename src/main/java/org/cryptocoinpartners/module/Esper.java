package org.cryptocoinpartners.module;

import org.cryptocoinpartners.schema.Event;
import org.cryptocoinpartners.service.Service;
import org.cryptocoinpartners.util.ModuleLoaderError;
import org.cryptocoinpartners.util.ReflectionUtil;
import com.espertech.esper.client.*;
import com.espertech.esper.client.deploy.*;
import com.espertech.esper.client.time.CurrentTimeEvent;
import com.espertech.esper.client.time.CurrentTimeSpanEvent;
import com.espertech.esper.core.service.EPServiceProviderImpl;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.MapConfiguration;
import org.joda.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;


/**
 * Our wrapper around the Esper engine
 *
 * @author Tim Olson
 */
@SuppressWarnings("UnusedDeclaration")
public class Esper {


    public Esper() {
        construct();
    }


    public interface TimeProvider {
        /**
         @return the Instant the Esper should be initialized to as the starting time
         */
        Instant getInitialTime();

        /**
         @param event The event to be published after the time is advanced
         */
        Instant nextTime(Event event);
    }


    public Esper( TimeProvider timeProvider )
    {
        this.timeProvider = timeProvider;
        construct();
    }


    /**
     * Modules are located in their own packages under org.cryptocoinpartners.module.  The package name is the module
     * name.  If a module is already loaded, it will be skipped.
     * For details, see ModuleLoader
     *
     * @param moduleName simple package names in the order they will be loaded
     * @see ModuleLoader#load(Esper, String...)
     * @throws org.cryptocoinpartners.util.ModuleLoaderError
     */
    public void loadModule( AbstractConfiguration c,
                            String moduleName ) throws ModuleLoaderError {
        ModuleLoader.load(this, c, moduleName);
    }


    /**
     * Modules are located in their own packages under org.cryptocoinpartners.module.  The package name is the module
     * name.  If a module is already loaded, it will be skipped.
     * For details, see ModuleLoader
     *
     @param moduleName the simple, undotted name of the package in which the module files reside.  this simple name will
                       be searched for under the module.path
     @param config a series of key-value pairs; the array must have even length.  keys must be strings and values will
                   have toString() called on them.  the k-v pairs are put into a configuration map and passed as
                   module configuration to the ModuleLoader
     @throws ModuleLoaderError
     */
    public void loadModule( String moduleName, Object... config ) throws ModuleLoaderError
    {
        final MapConfiguration configuration = new MapConfiguration(new HashMap());
        if( config.length % 2 != 0 )
            throw new IllegalArgumentException("Must have an even number of parameters (key-value pairs) in loadModule");
        for( int i = 0; i < config.length; i++ ) {
            Object key = config[i++];
            Object value = config[i];
            configuration.setProperty(key.toString(),value);
        }
        ModuleLoader.load(this,configuration,moduleName);
    }



    public boolean isModuleLoaded( String name ) {
        return loaded.contains(name);
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
            throw new IllegalArgumentException("Can only advanceTime() when the Esper was constructed with a TimeProvider");
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


    public void loadStatements(String source) throws ParseException, DeploymentException, IOException {
        loadStatements(source,null);
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


    public void subscribe( Object listener )
    {
        for( Method method : listener.getClass().getMethods() ) {
            When when = method.getAnnotation(When.class);
            if( when != null ) {
                String statement = when.value();
                log.debug("subscribing "+method+" with statement \""+statement+"\"");
                subscribe(listener, method, statement);
            }
        }
        if( listener instanceof Service ) {
            Service service = (Service) listener;
            services.add(service);
        }
    }


    /** Returns the first instance of the given Service subtype attached to this Esper */
    @SuppressWarnings("unchecked")
    public <T extends Service> T getService( Class<T> serviceType ) {
        for( Service service : services ) {
            if( serviceType.isAssignableFrom(service.getClass()) )
                return (T) service;
        }
        return null;
    }


    /**
     * For use by ModuleLoader only
     * @see ModuleLoader
     */
    public void setModuleLoaded( String name ) {
        loaded.add(name);
    }


    public void subscribe(Object listener, Method method, String statement) {
        EPStatement epStatement = epAdministrator.createEPL(statement);
        subscribe(listener, method, epStatement);
    }


    private void subscribe(Object listener, Method method, EPStatement statement) {
        statement.setSubscriber(new Listener(listener,method,statement.getText()));
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


    private void construct() {
        Configuration config = new Configuration();
        config.addEventType(Event.class);
        Set<Class<? extends Event>> eventTypes = ReflectionUtil.getSubtypesOf(Event.class);
        for( Class<? extends Event> eventType : eventTypes )
            config.addEventType(eventType);
        config.addImport(IntoMethod.class);
        if( timeProvider != null ) {
            config.getEngineDefaults().getThreading().setInternalTimerEnabled(false);
        }
        epService = EPServiceProviderManager.getDefaultProvider(config);
        if( timeProvider != null ) {
            lastTime = timeProvider.getInitialTime();
            final EPServiceProviderImpl epService1 = (EPServiceProviderImpl) epService;
            epService1.initialize(lastTime.getMillis());
        }
        epRuntime = epService.getEPRuntime();
        epAdministrator = epService.getEPAdministrator();
    }


    private static Logger log = LoggerFactory.getLogger(Esper.class);

    private List<Service> services = new ArrayList<>();
    private TimeProvider timeProvider;
    private Instant lastTime = null;
    private Set<String> loaded = new HashSet<>();
    private EPServiceProvider epService;
    private EPRuntime epRuntime;
    private EPAdministrator epAdministrator;
}
