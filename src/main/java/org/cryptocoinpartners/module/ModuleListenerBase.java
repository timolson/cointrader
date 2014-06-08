package org.cryptocoinpartners.module;

import org.apache.commons.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * @author Tim Olson
 */
public abstract class ModuleListenerBase implements ModuleListener {


    public void initModule(Esper esper, Configuration config) {
        this.esper = esper;
        this.config = config;
        this.log = LoggerFactory.getLogger(getClass());
    }


    public void destroyModule() {
    }


    protected Configuration config;
    protected Esper esper;
    protected Logger log;
}
