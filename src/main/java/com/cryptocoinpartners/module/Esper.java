package com.cryptocoinpartners.module;

import com.cryptocoinpartners.schema.Event;
import com.cryptocoinpartners.util.ModuleLoaderError;
import com.cryptocoinpartners.util.ReflectionUtil;
import com.espertech.esper.client.*;
import com.espertech.esper.client.deploy.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;


/**
 * Our wrapper around the Esper engine
 *
 * @author Tim Olson
 */
public class Esper {


    public Esper() {
        construct();
    }


    /**
     * Modules are located in their own packages under com.cryptocoinpartners.module.  The package name is the module
     * name.  If a module is already loaded, it will be skipped.
     * For details, see ModuleLoader
     *
     * @param moduleNames simple package names in the order they will be loaded
     * @see ModuleLoader#load(Esper, String...)
     * @throws ModuleLoaderError
     */
    public void loadModule( String... moduleNames ) throws ModuleLoaderError {
        loadModule(null, moduleNames);
    }


    public void loadModule( org.apache.commons.configuration.Configuration c,
                            String... moduleNames ) throws ModuleLoaderError {
        for( String name : moduleNames )
            ModuleLoader.load(this, c, name);
    }


    public boolean isModuleLoaded( String name ) {
        return loaded.contains(name);
    }


    public void publish(Event e) {
        epRuntime.sendEvent(e);
    }


    public void destroy() {
        epService.destroy();
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
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epRuntime = epService.getEPRuntime();
        epAdministrator = epService.getEPAdministrator();
    }


    private static Logger log = LoggerFactory.getLogger(Esper.class);

    private Set<String> loaded = new HashSet<String>();
    private EPServiceProvider epService;
    private EPRuntime epRuntime;
    private EPAdministrator epAdministrator;
}
