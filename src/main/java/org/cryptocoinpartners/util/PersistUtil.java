package org.cryptocoinpartners.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.persistence.Cache;
import javax.persistence.EntityManager;
import javax.persistence.EntityNotFoundException;
import javax.persistence.NoResultException;
import javax.persistence.OptimisticLockException;
import javax.persistence.PersistenceException;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import org.cryptocoinpartners.schema.Currencies;
import org.cryptocoinpartners.schema.Currency;
import org.cryptocoinpartners.schema.EntityBase;
import org.cryptocoinpartners.schema.Exchange;
import org.cryptocoinpartners.schema.Exchanges;
import org.cryptocoinpartners.schema.Listing;
import org.cryptocoinpartners.schema.Market;
import org.cryptocoinpartners.schema.Prompt;
import org.cryptocoinpartners.schema.Prompts;
import org.hibernate.TransientObjectException;
import org.hibernate.TransientPropertyValueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Tim Olson
 */
public class PersistUtil {

    private static Logger log = LoggerFactory.getLogger("org.cryptocoinpartners.persist");
    private static Object lock = new Object();

    // private static final BlockingQueue<EntityBase> insertQueue = new DelayQueue();
    // private static final BlockingQueue<EntityBase> mergeQueue = new DelayQueue();
    private static final BlockingQueue<EntityBase[]> insertQueue = new LinkedBlockingQueue<EntityBase[]>();
    private static final BlockingQueue<EntityBase[]> mergeQueue = new LinkedBlockingQueue<EntityBase[]>();

    private static boolean running = false;
    private static boolean shutdown = false;
    private static Future<?> persitanceTask = null;
    private static ExecutorService service;

    public static void insert(EntityBase... entities) {

        try {
            insertQueue.put(entities);
        } catch (InterruptedException e) {
            log.error("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
            e.printStackTrace();

        } finally {

        }

    }

    private static void merge(EntityBase... entities) {

        try {
            mergeQueue.put(entities);
        } catch (InterruptedException e) {
            log.error("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
            e.printStackTrace();

        } finally {

        }

    }

    public static void persist(EntityBase... entities) {
        EntityManager em = null;
        boolean persited = true;
        try {
            em = createEntityManager();
            //    em.setProperty("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);

            PersistUtilHelper.beginTransaction();

            try {
                for (EntityBase entity : entities) {
                    // em.lock(entity, LockModeType.PESSIMISTIC_WRITE);
                    // em.refresh(entity);
                    //  if (em.contains(entity))
                    //em.merge(entity);
                    // else
                    em.persist(entity);
                    PersistUtilHelper.commit();

                }

            } catch (OptimisticLockException ole) {
                persited = false;
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }

                for (EntityBase entity : entities) {
                    if (entity.getRetryCount() <= retryCount) {
                        entity.incermentRetryCount();
                        try {
                            mergeQueue.put(entities);
                        } catch (InterruptedException e) {
                            log.error("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
                            e.printStackTrace();

                        } finally {
                            log.error(entity.getClass().getSimpleName() + ": Later verion of " + entity.getId().toString()
                                    + " already persisted to database, entity was not inserted to database after " + entity.getRetryCount() + " attempts.");
                        }
                    } else {
                        log.error("Unable to save " + entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " after " + entity.getRetryCount()
                                + " attempts.", ole);

                    }
                }

            } catch (IllegalStateException ise) {
                if (TransientPropertyValueException.class.isInstance(ise.getCause()))

                {
                    persited = false;
                    if (PersistUtilHelper.isActive())
                        PersistUtilHelper.rollback();
                    if (em != null) {
                        PersistUtilHelper.closeEntityManager();
                        em = null;
                    }

                    for (EntityBase entity : entities) {
                        if (entity.getRetryCount() <= retryCount) {
                            entity.incermentRetryCount();
                            try {
                                insertQueue.put(entities);
                            } catch (InterruptedException e) {
                                log.error("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
                                e.printStackTrace();

                            } finally {
                                log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString()
                                        + " had unsaved transient values. Resubmitting insert request.", ise);
                            }
                        } else {
                            log.error(
                                    "Unable to save " + entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " after "
                                            + entity.getRetryCount() + " attempts.", ise);
                        }
                    }
                }

            } catch (PersistenceException pe) {
                persited = false;

                //  for (EntityBase entity : entities)
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }

                for (EntityBase entity : entities) {

                    if (entity.getRetryCount() <= retryCount) {
                        entity.incermentRetryCount();

                        try {
                            mergeQueue.put(entities);
                        } catch (InterruptedException e) {
                            log.error("Unable to resubmit insert request in org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
                            e.printStackTrace();

                        } finally {
                            log.error(entity.getClass().getSimpleName() + ":" + entity.getId().toString() + " already exists, we need to merge records.");

                        }
                    } else {
                        log.error("Unable to save " + entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " after " + entity.getRetryCount()
                                + " attempts.", pe);
                    }
                }

            } catch (Exception | Error e) {
                persited = false;

                log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);
                e.printStackTrace();
                //log.error(e.getCause().toString());
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }

            }

        } finally {
            if (PersistUtilHelper.isActive())
                PersistUtilHelper.rollback();
            if (em != null)
                PersistUtilHelper.closeEntityManager();

            if (persited)
                for (EntityBase entity : entities)
                    log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " inserted to database");
            else
                for (EntityBase entity : entities)
                    log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not inserted to database");

        }

    }

    public static boolean cached(EntityBase... entities) {
        EntityManager em = null;
        boolean cached = false;
        try {
            for (EntityBase entity : entities) {
                Cache cache = PersistUtilHelper.getEntityManagerFactory().getCache();
                cached = cache.contains(entity.getClass(), entity.getId());

            }

        } catch (Exception | Error e) {
            log.error("Threw a Execpton or Error, full stack trace follows:", e);

        }
        return cached;

    }

    public static void update(EntityBase... entities) {
        EntityManager em = null;
        boolean persited = true;
        try {
            em = createEntityManager();
            //  em.setProperty("javax.persistence.cache.storeMode", CacheStoreMode.REFRESH);
            //   em.setProperty("javax.persistence.cache.storeMode", CacheStoreMode.USE);

            PersistUtilHelper.beginTransaction();

            try {
                for (EntityBase entity : entities) {
                    // em.lock(entity, LockModeType.PESSIMISTIC_WRITE);
                    //  em.find(entity.getClass(), entity.getId());
                    //  em.refresh(entity.getId());

                    em.merge(entity);
                    PersistUtilHelper.commit();

                }

            } catch (EntityNotFoundException enf) {
                persited = false;
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }

                for (EntityBase entity : entities) {
                    if (entity.getRetryCount() <= retryCount) {
                        entity.incermentRetryCount();
                        try {
                            insertQueue.put(entities);
                        } catch (InterruptedException e) {
                            log.error("Unable to resubmit merge request in org.cryptocoinpartners.util.persistUtil::merge, full stack trace follows:", e);
                            e.printStackTrace();

                        } finally {
                            log.error(entity.getClass().getSimpleName() + ": Entity " + entity.getId().toString()
                                    + " was not already persisted to database, entity was not merged to database after " + entity.getRetryCount()
                                    + " attempts.");
                        }
                    } else {
                        log.error("Unable to save " + entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " after " + entity.getRetryCount()
                                + " attempts.", enf);
                    }
                }

            } catch (OptimisticLockException ole) {

                persited = false;
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }
                //
                for (EntityBase entity : entities) {
                    if (entity.getRetryCount() <= retryCount) {
                        entity.incermentRetryCount();
                        entity.setVersion(entity.getVersion() + 1);
                        try {
                            mergeQueue.put(entities);
                        } catch (Exception | Error e) {
                            log.error("Unable to resubmit merge request in org.cryptocoinpartners.util.persistUtil::merge, full stack trace follows:", e);
                            e.printStackTrace();

                        } finally {
                            log.error(entity.getClass().getSimpleName() + ": Later verion of " + entity.getId().toString()
                                    + " already persisted to database, entity was not merged to database after " + entity.getRetryCount() + " attempts.");
                        }
                    } else {
                        log.error("Unable to save " + entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " after " + entity.getRetryCount()
                                + " attempts. stack trade", ole);
                    }
                }
            }

            //                  PersistUtilHelper.beginTransaction();

            //                        if (entity.getRetryCount() <= retryCount) {
            //                            em.persist(entity);
            //                            em.flush();
            //                        } else {
            //                            log.error(entity.getClass().getSimpleName() + ": Later verion of " + entity.getId().toString()
            //                                    + " already persisted to database, entity was not saved to database");
            //                        }
            //
            //                    }

            //            } catch (OptimisticLockException ole) {
            //                log.error("Optimistic Lock");
            //                persited = false;
            //                if (PersistUtilHelper.isActive())
            //                    PersistUtilHelper.rollback();
            //
            //            }

            catch (Exception | Error e) {
                persited = false;
                log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);

                e.printStackTrace();
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
                if (em != null) {
                    PersistUtilHelper.closeEntityManager();
                    em = null;
                }

            }
        } finally {
            if (PersistUtilHelper.isActive())
                PersistUtilHelper.rollback();

            if (em != null)
                PersistUtilHelper.closeEntityManager();

            if (persited)
                for (EntityBase entity : entities)
                    log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " merged to database");
            else
                for (EntityBase entity : entities)
                    log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not merged to database");

        }
        // }

    }

    private static class insertRunnable implements Runnable {
        // private final Book book;

        // protected Logger log;

        public insertRunnable() {

        }

        @Override
        public void run() {

            while (!shutdown) {

                try {
                    EntityBase[] entities = insertQueue.take();
                    PersistUtil.persist(entities);
                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    // entities = null;
                    // PersistUtilHelper.closeEntityManager();
                }

            }

            //read of insert queue and insert

            //read of merge queue and merge

        }
    }

    private static class mergeRunnable implements Runnable {
        // private final Book book;

        // protected Logger log;

        public mergeRunnable() {

        }

        @Override
        public void run() {

            while (!shutdown) {

                try {
                    EntityBase[] entities = mergeQueue.take();
                    PersistUtil.update(entities);
                } catch (Exception e) {
                    e.printStackTrace();

                } finally {
                    // entities = null;
                    // PersistUtilHelper.closeEntityManager();
                }

            }

            //read of insert queue and insert

            //read of merge queue and merge

        }
    }

    public static EntityBase find(EntityBase... entities) {
        EntityManager em = null;
        boolean found = true;
        EntityBase foundEntity = null;
        try {
            em = createEntityManager();
            PersistUtilHelper.beginTransaction();

            try {
                for (EntityBase entity : entities) {
                    foundEntity = em.find(entity.getClass(), entity.getId());
                    PersistUtilHelper.commit();
                }

            } catch (Exception | Error e) {
                found = false;
                log.error("Threw a Execpton or Error in  org.cryptocoinpartners.util.persistUtil::insert, full stack trace follows:", e);

                e.printStackTrace();
                if (PersistUtilHelper.isActive())
                    PersistUtilHelper.rollback();
            }
        } finally {
            if (em != null)
                PersistUtilHelper.closeEntityManager();
            if (foundEntity != null)
                for (EntityBase entity : entities)
                    log.debug(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " found in database");

            else
                for (EntityBase entity : entities)
                    log.error(entity.getClass().getSimpleName() + ": " + entity.getId().toString() + " not found in database");
            return foundEntity;
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
        } catch (TransientObjectException toe) {
            log.debug("what happened");
            return null;
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
    }

    public static void resetDatabase() {
        init(true);
    }

    public static void init() {
        init(false);
    }

    public static void shutdown() {
        if (persitanceTask != null) {
            shutdown = true;
            service.shutdown();
            try {
                service.awaitTermination(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            running = false;

        }

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
        retryCount = ConfigUtil.combined().getInt("db.persist.retry");

        properties.put("hibernate.hbm2ddl.auto", createMode);
        properties.put("hibernate.connection.driver_class", ConfigUtil.combined().getString("db.driver"));
        properties.put("hibernate.dialect", ConfigUtil.combined().getString("db.dialect"));
        properties.put("hibernate.connection.url", ConfigUtil.combined().getString("db.url"));
        properties.put("hibernate.connection.username", ConfigUtil.combined().getString("db.username"));
        properties.put("hibernate.connection.password", ConfigUtil.combined().getString("db.password"));
        properties.put("hibernate.ejb.naming_strategy", "org.hibernate.cfg.ImprovedNamingStrategy");
        properties.put("hibernate.connection.autocommit", "true");
        properties.put("hibernate.connection.autocommit", "true");
        properties.put("hibernate.connection.release_mode", "auto");
        properties.put("hibernate.connection.provider_class", "org.hibernate.connection.C3P0ConnectionProvider");
        properties.put("hibernate.cache.region.factory_class", "org.hibernate.cache.ehcache.SingletonEhCacheRegionFactory");
        properties.put("hibernate.cache.use_second_level_cache", "true");
        properties.put("hibernate.cache.use_query_cache", "true");
        properties.put("hibernate.c3p0.min_size", "10");
        properties.put("hibernate.c3p0.max_size", ConfigUtil.combined().getString("db.pool.size"));
        properties.put("hibernate.c3p0.acquire_increment", ConfigUtil.combined().getString("db.pool.growth"));
        properties.put("show_sql", "true");
        properties.put("format_sql", "true");
        properties.put("use_sql_comments", "true");
        //   properties.put("hibernate.c3p0.debugUnreturnedConnectionStackTraces", "true");
        // properties.put("hibernate.c3p0.unreturnedConnectionTimeout", "120");
        //    properties.put("hibernate.c3p0.idle_test_period", ConfigUtil.combined().getString("db.idle.test.period"));
        //  properties.put("hibernate.c3p0.max_statements", "0");
        // properties.put("hibernate.c3p0.maxIdleTimeExcessConnections", "2");
        //  properties.put("hibernate.c3p0.timeout", "300");
        //properties.put("hibernate.c3p0.checkoutTimeout", "500");
        properties.put("hibernate.c3p0.preferredTestQuery", "SELECT 1 from exchange");
        //  properties.put("hibernate.c3p0.maxConnectionAge", ConfigUtil.combined().getString("db.max.connection.age"));
        final String testConnection = resetDatabase ? "false" : ConfigUtil.combined().getString("db.test.connection");
        properties.put("hibernate.c3p0.testConnectionOnCheckin", testConnection);
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

        //  if (!shutdown && running && persitanceTask == null && !running) {
        //EntityManager em = createEntityManager();

        service = Executors.newFixedThreadPool(2);

        //insertRunnable insertRunnableThread = new insertRunnable();
        // persitanceTask = 

        service.execute(new insertRunnable());
        service.execute(new mergeRunnable());

        // .submit(new insertRunnable());
        running = true;
        shutdown = false;
        // }
    }

    private static void ensureSingletonsExist() {
        // Touch the singleton holders
        Currencies.BTC.getSymbol(); // this should load all the singletons in Currencies
        Exchanges.BITFINEX.getSymbol(); // this should load all the singletons in Exchanges
        Prompts.THIS_WEEK.getSymbol();
        queryList(Currency.class, "select c from Currency c");
        queryList(Prompt.class, "select p from Prompt p");
        queryList(Listing.class, "select l from Listing l");
        queryList(Exchange.class, "select e from Exchange e");
        queryList(Market.class, "select m from Market m");

    }

    private static final int defaultBatchSize = 20;
    private static int retryCount = 2;
}
