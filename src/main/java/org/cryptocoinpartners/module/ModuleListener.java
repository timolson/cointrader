package org.cryptocoinpartners.module;

import org.apache.commons.configuration.Configuration;


/**
 * Any class which implements this interface will be instantiated via default constructor and the various lifecycle
 * methods will be invoked on this singleton.
 *
 * Also, any methods tagged with @When will be executed whenever the @When clause matches an event in Esper. These
 * methods must have the form
 * void methodName( EventSubtype y )
 * or
 * void methodName( Esper x, EventSubtype y )
 *
 * @author Tim Olson
 */
public interface ModuleListener {
    void initModule(Esper esper,Configuration config);
    void destroyModule();
}
