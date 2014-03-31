package com.cryptocoinpartners.util;

import com.cryptocoinpartners.schema.*;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Tim Olson
 */
public class PersistUtil {

    public static void insert(EntityBase... entities) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                for( EntityBase entity : entities )
                    em.persist(entity);
                transaction.commit();
            }
            catch( Error t ) {
                transaction.rollback();
                throw t;
            }
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    public static EntityManager createEntityManager() {
        init(false);
        return entityManagerFactory.createEntityManager();
    }


    public static void resetDatabase() {
        init(true);
        loadDefaultData();
    }


    private static void init(boolean resetDatabase) {
        if( entityManagerFactory != null && !resetDatabase )
            return;
        Map<String,String> properties = new HashMap<String, String>();
        String createMode;
        if(resetDatabase)
            createMode = "create";
        else
            createMode = "update";
        properties.put("hibernate.hbm2ddl.auto",createMode);
        properties.put("hibernate.connection.driver_class", Config.get().getString("db.driver"));
        properties.put("hibernate.dialect", Config.get().getString("db.dialect"));
        properties.put("hibernate.connection.url", Config.get().getString("db.url"));
        properties.put("hibernate.connection.username", Config.get().getString("db.username"));
        properties.put("hibernate.connection.password", Config.get().getString("db.password"));

        try {
            entityManagerFactory = Persistence.createEntityManagerFactory("com.cryptocoinpartners.schema", properties);
        }
        catch( Throwable t ) {
            if( entityManagerFactory != null ) {
                entityManagerFactory.close();
                entityManagerFactory = null;
            }
            throw new Error("Could not initialize db",t);
        }
    }


    /**
     * This flag is used by Currency to know whether to generate fields with new Currency objects or read them from
     * the existing rows in the database.
     * @see Currency
     */
    public static boolean generatingDefaultData = false;


    private static void loadDefaultData() {
        generatingDefaultData = true;

        loadStaticFieldsFromClass(Currency.class);
        loadStaticFieldsFromClass(Market.class);

        // LISTINGS
        PersistUtil.insert(
                 new Listing(Market.BITFINEX, Currency.BTC, Currency.USD)
                ,new Listing(Market.BITFINEX, Currency.LTC, Currency.USD)
                ,new Listing(Market.BITFINEX, Currency.LTC, Currency.BTC)
        );

        generatingDefaultData = true;
    }


    /**
     * Finds all static member fields which have the same type as the containing class, then loads those static
     * members as rows in the db
     * @param cls the class to inspect
     */
    private static <T extends EntityBase> void loadStaticFieldsFromClass(Class<T> cls) {
        List<EntityBase> all = new ArrayList<EntityBase>();
        Field[] fields = cls.getDeclaredFields();
        for( Field field : fields ) {
            if( Modifier.isStatic(field.getModifiers()) && cls.isAssignableFrom(field.getType()) ) {
                try {
                    all.add((EntityBase) field.get(null));
                }
                catch( IllegalAccessException e ) {
                    throw new Error("Could not read "+ cls.getSimpleName()+" field "+field,e);
                }
            }
        }
        PersistUtil.insert((EntityBase[]) all.toArray());
    }


    private static EntityManagerFactory entityManagerFactory;
}
