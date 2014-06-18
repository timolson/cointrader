package org.cryptocoinpartners.util;

import com.google.inject.Binder;
import com.google.inject.Module;
import org.apache.commons.configuration.Configuration;


/**
* @author Tim Olson
*/
public class CombinedConfigInjector implements Module {
    public void configure(Binder binder) {
        binder.bind(Configuration.class).toInstance(Config.combined());
    }
}
