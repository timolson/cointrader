package org.cryptocoinpartners.module;

/**
 * @author Tim Olson
 */
public class EsperError extends Error {
    public EsperError() { }
    public EsperError(String s) { super(s); }
    public EsperError(String s, Throwable throwable) { super(s, throwable); }
    public EsperError(Throwable throwable) { super(throwable); }
}
