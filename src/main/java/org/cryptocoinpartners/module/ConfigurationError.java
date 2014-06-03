package org.cryptocoinpartners.module;

/**
 * @author Tim Olson
 */
public class ConfigurationError extends Error {
    public ConfigurationError() {
    }


    public ConfigurationError(String s) {
        super(s);
    }


    public ConfigurationError(String s, Throwable throwable) {
        super(s, throwable);
    }


    public ConfigurationError(Throwable throwable) {
        super(throwable);
    }
}
