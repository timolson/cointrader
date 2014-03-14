package com.cryptocoinpartners.util;

import org.apache.commons.configuration.Configuration;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;


/**
 * @author Tim Olson
 */
public class PersistUtil {


    public static EntityManager getEntityManager() {
        return entityManager;
    }


    private static EntityManager entityManager;


    static {
        Map<String,String> properties = new HashMap<String, String>();
        properties.put("hibernate.connection.driver_class", Config.get().getString("db.driver"));
        properties.put("hibernate.dialect", Config.get().getString("db.dialect"));
        properties.put("hibernate.connection.url", Config.get().getString("db.url"));
        properties.put("hibernate.connection.username", Config.get().getString("db.username"));
        properties.put("hibernate.connection.password", Config.get().getString("db.password"));
        properties.put("hibernate.hbm2ddl.auto", Config.get().getBoolean("db.autocreate",false)?"create":"");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("com.cryptocoinpartners",properties);
        entityManager = entityManagerFactory.createEntityManager();

    }


}
