package org.cryptocoinpartners.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * This annotation is registered with every Esper instance to allow the result of select statements to trigger a setter
 * in the EPL file's matching Java class.
 * <p/>
 *
 * @author Tim Olson
 * @see Context
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface IntoMethod {
    /**
     * @return a comma-separated list of field names, one for each column in the select statement.  The setter for
     * each field will be called, or if no setter, the member variable will be set directly.
     */
    String value();
}
