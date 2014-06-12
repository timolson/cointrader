package org.cryptocoinpartners.module;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * @author Tim Olson
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface When {
    /** Pass an Esper statement as the argument of @When to define a trigger for the tagged method.
     * See http://esper.codehaus.org/esper-4.11.0/doc/reference/en-US/html_single/index.html#api-admin-subscriber
     * <p/>
     * Example:<br/>
     * public @When("select priceAsBigDecimal, volumeAsBigDecimal from Tick") handleNewTick( BigDecimal price, BigDecimal withAmount ) {...}
     */
    public String value();
}
