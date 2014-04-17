package com.cryptocoinpartners.util;

import com.cryptocoinpartners.schema.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.util.*;


/**
 * @author Tim Olson
 */
public class PersistUtil {

    private static Logger log = LoggerFactory.getLogger("com.cryptocoinpartners.persist");


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


    public static interface RowHandler<T> {
        /**
         @param row an entity returned from the queryEach
         @return true to continue with the next result row, or false to halt iteration
         @see #queryEach(Class, com.cryptocoinpartners.util.PersistUtil.RowHandler, int, String, Object...)
         */
        boolean handleEntity(T row);
    }


    public static <T> void queryEach( Class<T> resultType, RowHandler<T> handler,
                                                         String queryStr, Object... params ) {
        queryEach(resultType,handler,20,queryStr,params);
    }


    public static <T> void queryEach( Class<T> resultType, RowHandler<T> handler, int batchSize,
                                                         String queryStr, Object... params ) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if( params != null ) {
                for( int i = 0; i < params.length; i++ ) {
                    Object param = params[i];
                    query.setParameter(i+1,param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for( int start = 0; ; start += batchSize ) {
                query.setFirstResult(start);
                final List<T> list = query.getResultList();
                if( list.isEmpty() )
                    return;
                for( T row : list ) {
                    if( !handler.handleEntity(row) )
                        return;
                }
            }
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    public static <T extends EntityBase> List<T> queryList( Class<T> resultType, String queryStr, Object... params ) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if( params != null ) {
                for( int i = 0; i < params.length; i++ ) {
                    Object param = params[i];
                    query.setParameter(i+1,param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    /**
     returns a single result entity.  if none found, a javax.persistence.NoResultException is thrown.
     */
    public static <T extends EntityBase> T queryOne( Class<T> resultType, String queryStr, Object... params )
        throws NoResultException
    {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr,resultType);
            if( params != null ) {
                for( int i = 0; i < params.length; i++ ) {
                    Object param = params[i];
                    query.setParameter(i+1,param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    /**
     returns a single result entity or null if not found
     */
    public static <T extends EntityBase> T queryZeroOne( Class<T> resultType, String queryStr, Object... params ) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr,resultType);
            if( params != null ) {
                for( int i = 0; i < params.length; i++ ) {
                    Object param = params[i];
                    query.setParameter(i+1,param); // JPA uses 1-based indexes
                }
            }
            try {
                return query.getSingleResult();
            }
            catch( NoResultException x ) {
                return null;
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


    /*
    static {
        MarketListing.class.getClass();
    }
    */


    public static void resetDatabase() {
        init(true);
    }


    public static void shutdown() { entityManagerFactory.close(); }


    private static void init(boolean resetDatabase) {
        if( entityManagerFactory != null ) {
            if( !entityManagerFactory.isOpen() ) {
                log.warn("entityManagerFactory was closed.  Re-initializing");
                entityManagerFactory = null;
            }
            else if( !resetDatabase ) {
                // entityManagerFactory exists, is open, and a reset is not requested.  continue to use existing EMF
                return;
            }
        }
        if( resetDatabase ) {
            log.info("resetting database");
        }
        else
            log.info("initializing persistence");
        Map<String, String> properties = new HashMap<String, String>();
        String createMode;
        if( resetDatabase )
            createMode = "create";
        else
            createMode = "update";
        properties.put("hibernate.hbm2ddl.auto", createMode);
        properties.put("hibernate.connection.driver_class", Config.combined().getString("db.driver"));
        properties.put("hibernate.dialect", Config.combined().getString("db.dialect"));
        properties.put("hibernate.connection.url", Config.combined().getString("db.url"));
        properties.put("hibernate.connection.username", Config.combined().getString("db.username"));
        properties.put("hibernate.connection.password", Config.combined().getString("db.password"));

        try {
            entityManagerFactory = Persistence.createEntityManagerFactory("com.cryptocoinpartners.schema", properties);
            if( resetDatabase ) {
                loadDefaultData();
            }
        }
        catch( Throwable t ) {
            if( entityManagerFactory != null ) {
                entityManagerFactory.close();
                entityManagerFactory = null;
            }
            throw new Error("Could not initialize db", t);
        }
    }


    private static void loadDefaultData() {
        // Touch the singleton holders
        Currencies.class.getClass();
        Market.class.getClass();
    }


    private static EntityManagerFactory entityManagerFactory;
}
