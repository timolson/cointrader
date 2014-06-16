package org.cryptocoinpartners.util;

import org.cryptocoinpartners.schema.BaseEntity;
import org.cryptocoinpartners.schema.Currencies;
import org.cryptocoinpartners.schema.Exchanges;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.*;
import java.util.*;


/**
 * @author Tim Olson
 */
public class PersistUtil {

    private static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");


    public static void insert(BaseEntity... entities) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            EntityTransaction transaction = em.getTransaction();
            transaction.begin();
            try {
                for( BaseEntity entity : entities )
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


    /**
     * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
     * once for each row with an Object[] of column values
     */
    public static void queryEach( Visitor<Object[]> handler, String queryStr, Object... params) {
        queryEach(handler, defaultBatchSize, queryStr, params);
    }


    /**
     * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
     * once for each row with an Object[] of column values
     */
    @SuppressWarnings("ConstantConditions")
    public static void queryEach( Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final Query query = em.createQuery(queryStr);
            if( params != null ) {
                for( int i = 0; i < params.length; i++ ) {
                    Object param = params[i];
                    query.setParameter(i+1,param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for( int start = 0; ; start += batchSize ) {
                query.setFirstResult(start);
                List list = query.getResultList();
                if( list.isEmpty() )
                    return;
                for( Object row : list ) {
                    if( row.getClass().isArray() && !handler.handleItem((Object[])row)
                        || !row.getClass().isArray() && !handler.handleItem(new Object[]{row}) )
                        return;
                }
            }
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    public static <T> void queryEach( Class<T> resultType, Visitor<T> handler,
                                                         String queryStr, Object... params ) {
        queryEach(resultType,handler, defaultBatchSize,queryStr,params);
    }


    public static <T> void queryEach( Class<T> resultType, Visitor<T> handler, int batchSize,
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
                    if( !handler.handleItem(row) )
                        return;
                }
            }
        }
        finally {
            if( em != null )
                em.close();
        }
    }


    public static <T> List<T> queryList( Class<T> resultType, String queryStr, Object... params ) {
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
    public static <T> T queryOne( Class<T> resultType, String queryStr, Object... params )
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
    public static <T> T queryZeroOne( Class<T> resultType, String queryStr, Object... params ) {
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


    public static <T extends BaseEntity> T findById(Class<T> resultType, UUID id) throws NoResultException {
        return queryOne(resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
    }


    public static EntityManager createEntityManager() {
        init(false);
        return entityManagerFactory.createEntityManager();
    }


    public static void resetDatabase() {
        init(true);
    }


    public static void init() {
        init(false);
    }


    public static void shutdown() { if( entityManagerFactory != null ) entityManagerFactory.close(); }


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
        Map<String, String> properties = new HashMap<>();
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
            entityManagerFactory = Persistence.createEntityManagerFactory("org.cryptocoinpartners.schema", properties);
            ensureSingletonsExist();
        }
        catch( Throwable t ) {
            if( entityManagerFactory != null ) {
                entityManagerFactory.close();
                entityManagerFactory = null;
            }
            throw new Error("Could not initialize db", t);
        }
    }


    private static void ensureSingletonsExist() {
        // Touch the singleton holders
        Currencies.BTC.getSymbol(); // this should load all the singletons in Currencies
        Exchanges.BITFINEX.getSymbol();  // this should load all the singletons in Exchanges
    }


    private static EntityManagerFactory entityManagerFactory;
    private static final int defaultBatchSize = 20;
}
