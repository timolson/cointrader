package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.persistence.EntityManager;
import javax.persistence.NoResultException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.Currencies;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Exchanges;
import org.cryptocoinpartners.schema.Prompts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class PersistUtil {

    private static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
    private static Object lock = new Object();

    public static void insert(EntityBase... entities) {
        synchronized (lock) {
            EntityManager em = null;
            boolean persited = true;
            try {
                em = createEntityManager();
                PersistUtilHelper.beginTransaction();

                try {
                    for (EntityBase entity : entities) {
                        // em.refresh(entity);
                        // if (em.contains(entity))
                        ///    em.merge(entity);
                        // else
                        em.persist(entity);
                        PersistUtilHelper.commit();
                    }

                } catch (Exception | Error e) {
                    persited = false;
                    e.printStackTrace();
                    if (PersistUtilHelper.isActive())
                        PersistUtilHelper.rollback();
                }
            } finally {
                if (persited)
                    for (EntityBase entity : entities)
                        log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");
                else
                    for (EntityBase entity : entities)
                        log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
                if (em != null && em.isOpen())
                    PersistUtilHelper.closeEntityManager();

            }
        }
    }

    public static void merge(EntityBase... entities) {
        synchronized (lock) {
            EntityManager em = null;
            boolean persited = true;
            try {
                em = createEntityManager();
                PersistUtilHelper.beginTransaction();

                try {
                    for (EntityBase entity : entities) {
                        // em.find(entity.getClass(), entity.getId());
                        // em.
                        // em.refresh(entity);
                        //if (em.contains(entity))
                        //   em.merge(entity);
                        //else
                        em.merge(entity);
                        PersistUtilHelper.commit();
                    }

                } catch (Exception | Error e) {
                    persited = false;
                    e.printStackTrace();
                    if (PersistUtilHelper.isActive())
                        PersistUtilHelper.rollback();
                }
            } finally {
                if (persited)
                    for (EntityBase entity : entities)
                        log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " saved to database");
                else
                    for (EntityBase entity : entities)
                        log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not saved to database");
                if (em != null && em.isOpen())
                    PersistUtilHelper.closeEntityManager();

            }
        }
    }

    /**
     * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
     * once for each row with an Object[] of column values
     */
    public static void queryEach(Visitor<Object[]> handler, String queryStr, Object... params) {
        queryEach(handler, defaultBatchSize, queryStr, params);
    }

    /**
     * Use this method if you do not know the number of columns or rows in the result set.  The visitor will be called
     * once for each row with an Object[] of column values
     */
    @SuppressWarnings("ConstantConditions")
    public static void queryEach(Visitor<Object[]> handler, int batchSize, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final Query query = em.createQuery(queryStr);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for (int start = 0;; start += batchSize) {
                query.setFirstResult(start);
                List list = query.getResultList();
                if (list.isEmpty())
                    return;
                for (Object row : list) {
                    if (row.getClass().isArray() && !handler.handleItem((Object[]) row) || !row.getClass().isArray()
                            && !handler.handleItem(new Object[] { row }))
                        return;
                }
            }
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, String queryStr, Object... params) {
        queryEach(resultType, handler, defaultBatchSize, queryStr, params);
    }

    public static <T> void queryEach(Class<T> resultType, Visitor<T> handler, int batchSize, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            query.setMaxResults(batchSize);
            for (int start = 0;; start += batchSize) {
                query.setFirstResult(start);
                final List<T> list = query.getResultList();
                if (list.isEmpty())
                    return;
                for (T row : list) {
                    if (!handler.handleItem(row))
                        return;
                }
            }
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();

        }
    }

    public static <T> List<T> queryList(Class<T> resultType, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> List<T> queryNativeList(Class<T> resultType, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final Query query = em.createNativeQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getResultList();
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    /**
     returns a single result entity.  if none found, a javax.persistence.NoResultException is thrown.
     */
    public static <T> T queryOne(Class<T> resultType, String queryStr, Object... params) throws NoResultException {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    public static <T> T namedQueryOne(Class<T> resultType, String namedQuery, Object... params) throws NoResultException {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createNamedQuery(namedQuery, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            return query.getSingleResult();
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    /**
     returns a single result entity or null if not found
     */
    public static <T> T queryZeroOne(Class<T> resultType, String queryStr, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createQuery(queryStr, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            try {
                return query.getSingleResult();
            } catch (NoResultException x) {
                return null;
            }
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    public static <T> T namedQueryZeroOne(Class<T> resultType, String namedQuery, Object... params) {
        EntityManager em = null;
        try {
            em = createEntityManager();
            final TypedQuery<T> query = em.createNamedQuery(namedQuery, resultType);
            if (params != null) {
                for (int i = 0; i < params.length; i++) {
                    Object param = params[i];
                    query.setParameter(i + 1, param); // JPA uses 1-based indexes
                }
            }
            try {
                return query.getSingleResult();
            } catch (NoResultException x) {
                return null;
            }
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
        }
    }

    public static <T extends EntityBase> T findById(Class<T> resultType, UUID id) throws NoResultException {
        return queryOne(resultType, "select x from " + resultType.getSimpleName() + " x where x.id = ?1", id);
    }

    public static EntityManager createEntityManager() {
        init(false);
        return PersistUtilHelper.getEntityManager();
        // return entityManagerFactory.createEntityManager();
    }

    public static void resetDatabase() {
        init(true);
    }

    public static void init() {
        init(false);
    }

    public static void shutdown() {
        if (PersistUtilHelper.getEntityManagerFactory() != null)
            PersistUtilHelper.closeEntityManagerFactory();
    }

    private static void init(boolean resetDatabase) {
        if (PersistUtilHelper.getEntityManagerFactory() != null) {
            if (!PersistUtilHelper.isOpen()) {
                log.warn("entityManagerFactory was closed.  Re-initializing");
                PersistUtilHelper.reset();
            } else if (!resetDatabase) {
                // entityManagerFactory exists, is open, and a reset is not requested.  continue to use existing EMF
                return;
            }
        }

        if (resetDatabase) {
            log.info("resetting database");
        } else
            log.info("initializing persistence");
        Map<String, String> properties = new HashMap<>();
        String createMode;
        if (resetDatabase)
            createMode = "create";
        else
            createMode = "update";
        properties.put("hibernate.hbm2ddl.auto", createMode);
        properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
        properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
        properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
        properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
        properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
        properties.put("hibernate.ejb.naming_strategy", "org.hibernate.cfg.ImprovedNamingStrategy");
        properties.put("hibernate.connection.autocommit", "true");
        properties.put("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.c3p0.min_size", "1");
        properties.put("hibernate.c3p0.max_size", ConfigUtil.combined().getString("db.pool.size"));
        properties.put("hibernate.c3p0.acquire_increment", ConfigUtil.combined().getString("db.pool.growth"));
        properties.put("hibernate.c3p0.idle_test_period", ConfigUtil.combined().getString("db.idle.test.period"));
        properties.put("hibernate.c3p0.max_statements", "0");
        properties.put("hibernate.c3p0.timeout", "0");
        properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1 from exchange");
        properties.put("hibernate.c3p0.maxConnectionAge", ConfigUtil.combined().getString("db.max.connection.age"));
        final String testConnection = resetDatabase ? "false" : ConfigUtil.combined().getString("db.test.connection");
        properties.put("hibernate.c3p0.testConnectionOnCheckout", testConnection);
        properties.put("hibernate.c3p0.acquireRetryDelay", "1000");
        properties.put("hibernate.c3p0.acquireRetryAttempts", "0");
        properties.put("hibernate.c3p0.breakAfterAcquireFailure", "false");
        properties.put("javax.persistence.sharedCache.mode", "ENABLE_SELECTIVE");

        try {
            PersistUtilHelper emh = new PersistUtilHelper(properties);
            ensureSingletonsExist();

        } catch (Throwable t) {
            if (PersistUtilHelper.getEntityManagerFactory() != null) {
                PersistUtilHelper.closeEntityManagerFactory();

            }
            throw new Error("Could not initialize db", t);
        }
    }

    private static void ensureSingletonsExist() {
        // Touch the singleton holders
        Currencies.BTC.getSymbol(); // this should load all the singletons in Currencies
        Exchanges.BITFINEX.getSymbol(); // this should load all the singletons in Exchanges
        Prompts.THIS_WEEK.getSymbol();
    }

    //private static EntityManagerFactory entityManagerFactory;
    private static final int defaultBatchSize = 20;
}
