package org.cryptocoinpartners.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Tagging an interface with @Service indicates that when implementations of the tagged interface
 * are attached to a Context, the context will create a dependency injection binding between the
 * interface type and the implementation type.  For example:
 *
 * <pre>@Service
 * interface MyService {}
 *
 * class MyServiceImpl implements MyService {}
 *
 *
 * context.attach(MyServiceImpl.class);
 * </pre>
 *
 * After this, any subsequent attachments will have their <pre>@Inject MyService foo;</pre> fields
 * populated with an instance of MyServiceImpl.  Use the @Singleton annotation to have one instance
 * of the MyServiceImpl per Context.  Otherwise, a new instance is created for every binding.
 *
 *
 * @author Tim Olson
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Service {
}
