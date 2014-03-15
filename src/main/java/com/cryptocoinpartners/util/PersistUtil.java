package com.cryptocoinpartners.util;

import com.cryptocoinpartners.schema.*;

import javax.persistence.*;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Tim Olson
 */
public class PersistUtil {


    public static void insert(EntityBase... entities) {
        EntityManager em = createEntityManager();
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
        finally {
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

        entityManagerFactory = Persistence.createEntityManagerFactory("com.cryptocoinpartners.schema", properties);
    }


    private static void loadDefaultData() {
        PersistUtil.insert(

                // DEFAULT ENTITIES GO HERE

                // BITFINEX
                new Forex(Market.BITFINEX, Currency.BTC, Currency.USD)
                ,new Forex(Market.BITFINEX, Currency.LTC, Currency.USD)
                ,new Forex(Market.BITFINEX, Currency.LTC, Currency.BTC)

        );
    }


    private static EntityManagerFactory entityManagerFactory;
}
