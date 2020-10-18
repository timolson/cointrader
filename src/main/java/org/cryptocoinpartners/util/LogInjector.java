package org.cryptocoinpartners.util;

import com.google.inject.*;
import com.google.inject.matcher.Matchers;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import com.google.inject.util.Providers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;


/**
 * @author Tim Olson
 */
public class LogInjector implements com.google.inject.Module {

    @Override
    public void configure(Binder binder) {
        // SLF4J logger injection
        binder.bind(Logger.class).toProvider(Providers.of(dummyLogger));
        binder.bindListener(Matchers.any(), new MyTypeListener());
    }


    private static class MyTypeListener implements TypeListener {

        @Override
        public <I> void hear(TypeLiteral<I> typeLiteral, TypeEncounter<I> typeEncounter) {
            for( Class<?> c = typeLiteral.getRawType(); c != Object.class; c = c.getSuperclass() ) {
                for( final Field field : c.getDeclaredFields() ) {
                    if( field.getType().isAssignableFrom(Logger.class)
                                && field.isAnnotationPresent(Inject.class) ) {
                        typeEncounter.register(new MyMembersInjector<I>(field));
                    }
                }
            }
        }
    }


    private static class MyMembersInjector<I> implements MembersInjector<I> {
        public MyMembersInjector(Field field) {
            this.field = field;
        }


        public void injectMembers(I i) {
            try {
                boolean wasAccessible = field.isAccessible();
                field.setAccessible(true);
                if( field.get(i) == null ) {
                    field.set(i, LoggerFactory.getLogger(field.getDeclaringClass()));
                }
                field.setAccessible(wasAccessible);
            }
            catch( IllegalAccessException e ) {
                e.printStackTrace();
            }
        }


        private final Field field;
    }


    private static final Logger dummyLogger = LoggerFactory.getLogger("");

}
