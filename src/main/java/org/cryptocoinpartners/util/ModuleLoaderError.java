package org.cryptocoinpartners.util;

/**
* @author Tim Olson
*/
public class ModuleLoaderError extends Error {
    public ModuleLoaderError() { }
    public ModuleLoaderError(String s) { super(s); }
    public ModuleLoaderError(String s, Throwable throwable) { super(s, throwable); }
    public ModuleLoaderError(Throwable throwable) { super(throwable); }
}
