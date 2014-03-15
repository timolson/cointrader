package com.cryptocoinpartners.service;

import com.cryptocoinpartners.module.ModuleLoader;
import com.cryptocoinpartners.schema.Event;
import com.cryptocoinpartners.schema.MarketData;
import com.cryptocoinpartners.schema.Pricing;
import com.cryptocoinpartners.schema.Trade;
import com.cryptocoinpartners.util.ModuleLoaderError;
import com.cryptocoinpartners.util.ReflectionUtil;
import com.espertech.esper.client.*;
import com.espertech.esper.client.soda.EPStatementObjectModel;

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
        Configuration config = new Configuration();
        config.addEventType(MarketData.class);
        config.addEventType(Pricing.class);
        config.addEventType(Trade.class);
        //Set<Class<? extends Event>> eventTypes = ReflectionUtil.getSubtypesOf(Event.class);
        //for( Class<? extends Event> eventType : eventTypes )
        //    config.addEventType(eventType);
        epService = EPServiceProviderManager.getDefaultProvider(config);
        epRuntime = epService.getEPRuntime();
        epAdministrator = epService.getEPAdministrator();
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
        for( String name : moduleNames )
            ModuleLoader.load(this, name);
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


    public void loadStatements(String source) {
        EPStatementObjectModel objectModel = epAdministrator.compileEPL(source);
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
        epStatement.setSubscriber(new Listener(listener,method,statement));
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


    private Set<String> loaded = new HashSet<String>();
    private final EPServiceProvider epService;
    private final EPRuntime epRuntime;
    private final EPAdministrator epAdministrator;
}
